"""Uploads the App Store listing in ios/fastlane (texts and screenshots, made by tools/export_appstore.py) to the
version being prepared in App Store Connect, through the App Store Connect API. Does not submit for review.

Run by the "listing" job in .github/workflows/ios.yml:
    ASC_KEY_ID=… ASC_ISSUER_ID=… ASC_KEY_P8=… python3 tools/asc_listing.py 1.0.0
Needs: pip install pyjwt cryptography
"""
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.request

import jwt

API = 'https://api.appstoreconnect.apple.com'
BUNDLE_ID = 'com.calocare.app'
ROOT = os.path.join(os.path.dirname(__file__), '..', 'ios', 'fastlane')
# 1320 x 2868 screenshots (iPhone 6.9") go in the largest iPhone set.
DISPLAY_TYPE = 'APP_IPHONE_67'

_token = {'value': '', 'at': 0.0}


def token():
    if time.time() - _token['at'] > 600:
        now = int(time.time())
        _token['value'] = jwt.encode({'iss': os.environ['ASC_ISSUER_ID'], 'iat': now, 'exp': now + 1200, 'aud': 'appstoreconnect-v1'},
                                     os.environ['ASC_KEY_P8'], algorithm='ES256', headers={'kid': os.environ['ASC_KEY_ID'], 'typ': 'JWT'})
        _token['at'] = time.time()
    return _token['value']


def call(method, path, body=None):
    request = urllib.request.Request(API + path, method=method, data=json.dumps(body).encode() if body is not None else None,
                                     headers={'Authorization': 'Bearer ' + token(), 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        fail(f'{method} {path.split("?")[0]} -> {e.code} {describe(e)}')


def describe(e):
    """Apple's error text, including the "associated errors" that say which fields block a submission."""
    errors = json.loads(e.read() or b'{}').get('errors', [])
    lines = []
    for x in errors:
        lines.append(f"{x.get('title', '')}: {x.get('detail', '')}")
        for where, more in (x.get('meta', {}).get('associatedErrors') or {}).items():
            for m in more:
                lines.append(f"  - {where.split('/')[-2] if '/' in where else where}: {m.get('detail') or m.get('title') or m.get('code')}")
    return '\n'.join(lines) or str(e)


def fail(message):
    print('::error title=TestFlight::' + message.replace('%', '%25').replace('\n', '%0A'))
    sys.exit(1)


def read(*parts):
    path = os.path.join(ROOT, 'metadata', *parts)
    return open(path, encoding='utf-8').read().strip() if os.path.exists(path) else None


def upsert(kind, existing, parent_rel, parent_type, parent_id, locale, attributes, list_path=None):
    """PATCH the localization for `locale` if it exists, otherwise create it."""
    attributes = {k: v for k, v in attributes.items() if v}
    same = lambda x: x['attributes']['locale'].lower().split('-')[0] == locale.lower().split('-')[0] if '-' not in locale else x['attributes']['locale'] == locale
    found = next((x for x in existing if same(x)), None)
    if not found and list_path:
        # The first listing can lag behind a localization made moments ago in App Store Connect: ask again.
        again = call('GET', list_path + '?limit=200')['data']
        found = next((x for x in again if same(x)), None)
        if not found:
            print(f'::notice title=TestFlight::{kind}: no {locale} among ' + ', '.join(x['attributes']['locale'] for x in again))
    if found:
        call('PATCH', f'/v1/{kind}/{found["id"]}', {'data': {'type': kind, 'id': found['id'], 'attributes': attributes}})
        return found['id']
    created = call('POST', f'/v1/{kind}', {'data': {'type': kind, 'attributes': {'locale': locale, **attributes},
                                                   'relationships': {parent_rel: {'data': {'type': parent_type, 'id': parent_id}}}}})
    return created['data']['id']


def upload_screenshots(localization_id, folder):
    files = sorted(f for f in os.listdir(folder) if f.endswith('.png')) if os.path.isdir(folder) else []
    if not files:
        return 0
    sets = call('GET', f'/v1/appStoreVersionLocalizations/{localization_id}/appScreenshotSets')['data']
    shot_set = next((s for s in sets if s['attributes']['screenshotDisplayType'] == DISPLAY_TYPE), None)
    if shot_set:
        for old in call('GET', f'/v1/appScreenshotSets/{shot_set["id"]}/appScreenshots')['data']:
            call('DELETE', f'/v1/appScreenshots/{old["id"]}')
    else:
        shot_set = call('POST', '/v1/appScreenshotSets', {'data': {'type': 'appScreenshotSets', 'attributes': {'screenshotDisplayType': DISPLAY_TYPE},
                        'relationships': {'appStoreVersionLocalization': {'data': {'type': 'appStoreVersionLocalizations', 'id': localization_id}}}}})['data']
    for name in files:
        data = open(os.path.join(folder, name), 'rb').read()
        shot = call('POST', '/v1/appScreenshots', {'data': {'type': 'appScreenshots', 'attributes': {'fileName': name, 'fileSize': len(data)},
                    'relationships': {'appScreenshotSet': {'data': {'type': 'appScreenshotSets', 'id': shot_set['id']}}}}})['data']
        for op in shot['attributes']['uploadOperations']:
            part = data[op['offset']:op['offset'] + op['length']]
            headers = {h['name']: h['value'] for h in op.get('requestHeaders', [])}
            urllib.request.urlopen(urllib.request.Request(op['url'], data=part, method=op['method'], headers=headers), timeout=300).read()
        call('PATCH', f'/v1/appScreenshots/{shot["id"]}', {'data': {'type': 'appScreenshots', 'id': shot['id'],
             'attributes': {'uploaded': True, 'sourceFileChecksum': hashlib.md5(data).hexdigest()}}})
    return len(files)


def main(version_string):
    app = call('GET', f'/v1/apps?filter[bundleId]={BUNDLE_ID}')['data']
    if not app:
        fail(f'No app with bundle id {BUNDLE_ID} in App Store Connect')
    app_id = app[0]['id']

    # App-level texts: name, subtitle, privacy URL, categories. The editable app info is the one not yet live.
    infos = call('GET', f'/v1/apps/{app_id}/appInfos')['data']
    info = next((i for i in infos if i['attributes'].get('appStoreState') != 'READY_FOR_SALE'), infos[0])
    call('PATCH', f'/v1/appInfos/{info["id"]}', {'data': {'type': 'appInfos', 'id': info['id'], 'relationships': {
        'primaryCategory': {'data': {'type': 'appCategories', 'id': read('primary_category.txt')}},
        'secondaryCategory': {'data': {'type': 'appCategories', 'id': read('secondary_category.txt')}}}}})
    info_locs = call('GET', f'/v1/appInfos/{info["id"]}/appInfoLocalizations')['data']

    versions = call('GET', f'/v1/apps/{app_id}/appStoreVersions?filter[platform]=IOS&filter[versionString]={version_string}')['data']
    if not versions:
        fail(f'No iOS version {version_string} in App Store Connect; create it on the app page first')
    version = versions[0]
    call('PATCH', f'/v1/appStoreVersions/{version["id"]}', {'data': {'type': 'appStoreVersions', 'id': version['id'],
         'attributes': {'copyright': read('copyright.txt')}}})
    version_locs = call('GET', f'/v1/appStoreVersions/{version["id"]}/appStoreVersionLocalizations')['data']
    first_version = len(infos) == 1 and version['attributes'].get('appStoreState') == 'PREPARE_FOR_SUBMISSION'

    for locale in sorted(os.listdir(os.path.join(ROOT, 'metadata'))):
        if not os.path.isdir(os.path.join(ROOT, 'metadata', locale)):
            continue
        upsert('appInfoLocalizations', info_locs, 'appInfo', 'appInfos', info['id'], locale, list_path=f'/v1/appInfos/{info["id"]}/appInfoLocalizations', attributes={
            'name': read(locale, 'name.txt'), 'subtitle': read(locale, 'subtitle.txt'), 'privacyPolicyUrl': read(locale, 'privacy_url.txt')})
        loc_id = upsert('appStoreVersionLocalizations', version_locs, 'appStoreVersion', 'appStoreVersions', version['id'], locale, list_path=f'/v1/appStoreVersions/{version["id"]}/appStoreVersionLocalizations', attributes={
            'description': read(locale, 'description.txt'), 'keywords': read(locale, 'keywords.txt'),
            'promotionalText': read(locale, 'promotional_text.txt'), 'supportUrl': read(locale, 'support_url.txt'),
            # "What's New" cannot be set on an app's first version.
            'whatsNew': None if first_version else read(locale, 'release_notes.txt')})
        count = upload_screenshots(loc_id, os.path.join(ROOT, 'screenshots', locale))
        print(f'{locale}: texts and {count} screenshots uploaded')


if __name__ == '__main__':
    try:
        main(sys.argv[1])
    except SystemExit:
        raise
    except Exception:
        # Anything unexpected becomes a run annotation, readable without opening the log.
        import traceback
        fail(traceback.format_exc()[-1500:])

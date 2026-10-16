"""Submits the iOS version being prepared for App Review, together with the CaloCare Pro subscription group and
its subscriptions (a first subscription can only be reviewed with an app version). Uses the App Store Connect API.

Run by the "submit" job in .github/workflows/ios.yml:
    ASC_KEY_ID=… ASC_ISSUER_ID=… ASC_KEY_P8=… python3 tools/asc_submit.py 1.0.0
"""
import sys
import urllib.error

import asc_listing as asc
from asc_listing import call, fail


def call_ok(method, path, body=None, allow=()):
    """Like call(), but returns None for the listed "already done" conflicts instead of stopping."""
    request = asc.urllib.request.Request(asc.API + path, method=method, data=asc.json.dumps(body).encode() if body is not None else None,
                                         headers={'Authorization': 'Bearer ' + asc.token(), 'Content-Type': 'application/json'})
    try:
        with asc.urllib.request.urlopen(request, timeout=120) as response:
            raw = response.read()
            return asc.json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        errors = asc.json.loads(e.read() or b'{}').get('errors', [])
        text = '; '.join(f"{x.get('title', '')}: {x.get('detail', '')}" for x in errors)
        if e.code in allow:
            print(f'  ({method} {path.split("?")[0]}: {e.code} {text}; continuing)')
            return None
        fail(f'{method} {path.split("?")[0]} -> {e.code} {text}')


def main(version_string):
    app_id = call('GET', f'/v1/apps?filter[bundleId]={asc.BUNDLE_ID}')['data'][0]['id']
    version = call('GET', f'/v1/apps/{app_id}/appStoreVersions?filter[platform]=IOS&filter[versionString]={version_string}')['data'][0]
    print('version', version_string, version['attributes'].get('appStoreState'))

    # The newest processed build of this version.
    builds = call('GET', f'/v1/builds?filter[app]={app_id}&filter[preReleaseVersion.version]={version_string}&filter[processingState]=VALID&sort=-uploadedDate&limit=1')['data']
    if not builds:
        fail(f'No processed build for {version_string} yet; wait for TestFlight to finish processing')
    build = builds[0]
    call('PATCH', f'/v1/appStoreVersions/{version["id"]}/relationships/build', {'data': {'type': 'builds', 'id': build['id']}})
    print('build', build['attributes']['version'], 'attached')

    # Subscriptions: the group and each subscription go in with the version.
    for group in call('GET', f'/v1/apps/{app_id}/subscriptionGroups')['data']:
        subs = call('GET', f'/v1/subscriptionGroups/{group["id"]}/subscriptions')['data']
        for sub in subs:
            print('subscription', sub['attributes']['productId'], sub['attributes'].get('state'))
        call_ok('POST', '/v1/subscriptionGroupSubmissions',
                {'data': {'type': 'subscriptionGroupSubmissions', 'relationships': {'subscriptionGroup': {'data': {'type': 'subscriptionGroups', 'id': group['id']}}}}},
                allow=(409,))
        for sub in subs:
            if sub['attributes'].get('state') in ('READY_TO_SUBMIT', 'DEVELOPER_ACTION_NEEDED', 'REJECTED'):
                call_ok('POST', '/v1/subscriptionSubmissions',
                        {'data': {'type': 'subscriptionSubmissions', 'relationships': {'subscription': {'data': {'type': 'subscriptions', 'id': sub['id']}}}}},
                        allow=(409,))

    # One review submission for the app, holding the version; the subscriptions added above travel with it.
    open_ones = call('GET', f'/v1/apps/{app_id}/reviewSubmissions?filter[platform]=IOS&filter[state]=READY_FOR_REVIEW,UNRESOLVED_ISSUES')['data']
    if open_ones:
        submission = open_ones[0]
    else:
        submission = call('POST', '/v1/reviewSubmissions', {'data': {'type': 'reviewSubmissions', 'attributes': {'platform': 'IOS'},
                          'relationships': {'app': {'data': {'type': 'apps', 'id': app_id}}}}})['data']
    items = call('GET', f'/v1/reviewSubmissions/{submission["id"]}/items?include=appStoreVersion')['data']
    has_version = any((i.get('relationships', {}).get('appStoreVersion', {}).get('data') or {}).get('id') == version['id'] for i in items)
    if not has_version:
        call('POST', '/v1/reviewSubmissionItems', {'data': {'type': 'reviewSubmissionItems', 'relationships': {
            'reviewSubmission': {'data': {'type': 'reviewSubmissions', 'id': submission['id']}},
            'appStoreVersion': {'data': {'type': 'appStoreVersions', 'id': version['id']}}}}})
    print('review submission', submission['id'], 'holds the version')

    call('PATCH', f'/v1/reviewSubmissions/{submission["id"]}', {'data': {'type': 'reviewSubmissions', 'id': submission['id'], 'attributes': {'submitted': True}}})
    print('::notice title=TestFlight::Submitted CaloCare ' + version_string + ' (build ' + build['attributes']['version'] + ') with CaloCare Pro for App Review.')


if __name__ == '__main__':
    try:
        main(sys.argv[1])
    except SystemExit:
        raise
    except Exception:
        # Anything unexpected becomes a run annotation, readable without opening the log.
        import traceback
        fail(traceback.format_exc()[-1500:])

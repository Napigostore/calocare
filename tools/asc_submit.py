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
        text = asc.describe(e)
        if e.code in allow:
            # Shown as a run annotation: a conflict here can explain a later refusal.
            print(f'::warning title=TestFlight::{method} {path.split("?")[0]}: {e.code} ' + text.replace('\n', '%0A'))
            return None
        fail(f'{method} {path.split("?")[0]} -> {e.code} {text}')


def reset():
    """Empties unsubmitted review submissions, so the version page can take its first subscriptions again."""
    app_id = call('GET', f'/v1/apps?filter[bundleId]={asc.BUNDLE_ID}')['data'][0]['id']
    drafts = call('GET', f'/v1/apps/{app_id}/reviewSubmissions?filter[platform]=IOS&filter[state]=READY_FOR_REVIEW,UNRESOLVED_ISSUES')['data']
    for draft in drafts:
        items = call('GET', f'/v1/reviewSubmissions/{draft["id"]}/items')['data']
        for item in items:
            call_ok('DELETE', f'/v1/reviewSubmissionItems/{item["id"]}', allow=(409,))
        call_ok('PATCH', f'/v1/reviewSubmissions/{draft["id"]}', {'data': {'type': 'reviewSubmissions', 'id': draft['id'], 'attributes': {'canceled': True}}}, allow=(409,))
        print(f'::notice title=TestFlight::Draft submission {draft["id"]} emptied ({len(items)} items removed)')
    if not drafts:
        print('::notice title=TestFlight::No draft submissions to empty')


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

    # App Review details: CaloCare has no accounts, so no demo login; notes tell the reviewer where things are.
    detail = call('GET', f'/v1/appStoreVersions/{version["id"]}/appStoreReviewDetail').get('data')
    notes = ('No account or sign-in is needed. Meal photo analysis: Food tab, camera button. CaloCare Pro subscriptions: '
             'Profile tab, "Upgrade to Pro" (also shown when the free daily AI allowance is used up). Apple Health is optional: '
             'Profile tab, "Connect Apple Health"; it is read only and never leaves the device. The app is in English or Vietnamese '
             'following the device language.')
    attributes = {'demoAccountRequired': False, 'notes': notes}
    if detail:
        call('PATCH', f'/v1/appStoreReviewDetails/{detail["id"]}', {'data': {'type': 'appStoreReviewDetails', 'id': detail['id'], 'attributes': attributes}})
    else:
        call('POST', '/v1/appStoreReviewDetails', {'data': {'type': 'appStoreReviewDetails', 'attributes': attributes,
             'relationships': {'appStoreVersion': {'data': {'type': 'appStoreVersions', 'id': version['id']}}}}})
    print('review details: no sign-in required, notes set')

    # Subscriptions: the group and each subscription go in with the version.
    for group in call('GET', f'/v1/apps/{app_id}/subscriptionGroups')['data']:
        subs = call('GET', f'/v1/subscriptionGroups/{group["id"]}/subscriptions')['data']
        for sub in subs:
            print('subscription', sub['attributes']['productId'], sub['attributes'].get('state'))
        # A group can only be reviewed with a display name in at least one language.
        names = call('GET', f'/v1/subscriptionGroups/{group["id"]}/subscriptionGroupLocalizations')['data']
        have = {n['attributes']['locale'] for n in names}
        print(f'::notice title=TestFlight::Group {group["attributes"].get("referenceName")} names: ' + ', '.join(f'{n["attributes"]["locale"]}={n["attributes"].get("name")} ({n["attributes"].get("state")})' for n in names))
        for locale in ('en-US', 'vi'):
            if locale not in have:
                # 409 when the group is locked by a pending submission: its names are then already fixed.
                call_ok('POST', '/v1/subscriptionGroupLocalizations', {'data': {'type': 'subscriptionGroupLocalizations',
                        'attributes': {'locale': locale, 'name': 'CaloCare Pro'},
                        'relationships': {'subscriptionGroup': {'data': {'type': 'subscriptionGroups', 'id': group['id']}}}}}, allow=(409,))
        result = call_ok('POST', '/v1/subscriptionGroupSubmissions',
                         {'data': {'type': 'subscriptionGroupSubmissions', 'relationships': {'subscriptionGroup': {'data': {'type': 'subscriptionGroups', 'id': group['id']}}}}},
                         allow=(409,))
        print('subscription group', group['attributes'].get('referenceName'), 'added' if result is not None else 'already added')
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
        reset() if sys.argv[1] == '--reset' else main(sys.argv[1])
    except SystemExit:
        raise
    except Exception:
        # Anything unexpected becomes a run annotation, readable without opening the log.
        import traceback
        fail(traceback.format_exc()[-1500:])

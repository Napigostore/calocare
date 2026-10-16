#!/usr/bin/env bash
# Waits for the newest iOS cloud build of this repository to finish, then prints its result and
# the compiler errors the workflow published as an annotation. Needs no GitHub login (public repo).
#   bash tools/ios_build.sh [commit sha prefix to wait for]
REPO="${REPO:-Napigostore/calocare}"
WANT="$1"
api() { curl -s -m 20 "https://api.github.com/repos/$REPO/$1"; }
latest() { api "actions/runs?per_page=1" | python -c "import sys,json; r=(json.load(sys.stdin).get('workflow_runs') or [{}])[0]; print(r.get('status'), r.get('conclusion'), r.get('id'), (r.get('head_sha') or '')[:7])"; }
until S=$(latest) && case "$S" in completed*) case "$S" in *"$WANT") true ;; *) false ;; esac ;; *) false ;; esac; do sleep 150; done
echo "$S"
RUN=$(echo "$S" | awk '{print $3}')
api "actions/runs/$RUN/jobs" | python -c "
import sys,json,urllib.request
for j in json.load(sys.stdin).get('jobs',[]):
    if j['conclusion'] == 'skipped': continue
    print(j['name'], '->', j['conclusion'], '; failed steps:', [s['name'] for s in j['steps'] if s['conclusion'] == 'failure'])
    notes = json.load(urllib.request.urlopen('https://api.github.com/repos/$REPO/check-runs/%d/annotations' % j['id']))
    for n in notes:
        if n.get('title') in ('Swift errors', 'TestFlight'): print('[' + n['title'] + ']', n['message'])
"

"""Downloads the photos already listed in manifest.json again at another size, for testing whether
a larger image makes recognition more accurate.

    python test/eval/refetch_size.py 768        -> test/eval/images-768/

Same photos, same file names, same centre crop as images/ — only the resolution differs.
"""
import io
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SIDE = int(sys.argv[1]) if len(sys.argv) > 1 else 768
OUT = os.path.join(HERE, f'images-{SIDE}')
USER_AGENT = 'CaloCareEval/1.0 (accuracy test set for a calorie tracking app)'


def get(url):
    # Commons throttles bursts of thumbnail requests: wait and retry instead of losing the photo.
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': USER_AGENT}), timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == 4:
                raise
            time.sleep(30 * (attempt + 1))


os.makedirs(OUT, exist_ok=True)
done = 0
for entry in json.load(io.open(os.path.join(HERE, 'manifest.json'), encoding='utf-8')):
    target = os.path.join(OUT, entry['file'])
    if os.path.exists(target):
        done += 1
        continue
    title = urllib.parse.unquote(entry['source'].rsplit('/wiki/', 1)[1])
    query = urllib.parse.urlencode({'action': 'query', 'titles': title, 'prop': 'imageinfo', 'iiprop': 'url', 'iiurlwidth': SIDE * 2, 'format': 'json'})
    try:
        pages = json.loads(get('https://commons.wikimedia.org/w/api.php?' + query))['query']['pages']
        image = Image.open(io.BytesIO(get(next(iter(pages.values()))['imageinfo'][0]['thumburl']))).convert('RGB')
        w, h = image.size
        m = min(w, h)
        image.crop(((w - m) // 2, (h - m) // 2, (w + m) // 2, (h + m) // 2)).resize((SIDE, SIDE), Image.LANCZOS).save(target, quality=80)
        done += 1
    except Exception as e:
        print(f'skipped {entry["file"]}: {e}', file=sys.stderr)
    time.sleep(2)
print(f'{done} photos at {SIDE}px -> {os.path.relpath(OUT, HERE)}')

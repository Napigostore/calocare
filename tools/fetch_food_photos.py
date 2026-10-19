"""Fetches a real photo for each built-in food from Wikimedia Commons, keeping only images whose licence allows
use in an app with credit (CC0, public domain, CC BY, CC BY-SA). Foods without a suitable photo keep their emoji.

    python tools/fetch_food_photos.py

Writes ios/CaloCare/FoodPhotos/<id>.jpg (400 px) and ios/CaloCare/FoodPhotos/CREDITS.txt, and the same photos to
app/src/main/assets/foodphotos/. Files already fetched are skipped, so the script can be run again.
"""
import io
import json
import os
import re
import shutil
import time
import unicodedata
import urllib.parse
import urllib.request

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..')
IOS_SEED = os.path.join(ROOT, 'ios/CaloCare/seed.json')
IOS_OUT = os.path.join(ROOT, 'ios/CaloCare/FoodPhotos')
ANDROID_OUT = os.path.join(ROOT, 'app/src/main/assets/foodphotos')
UA = 'CaloCare/1.1 (food photos for a calorie app; truongtumoc@gmail.com)'
ALLOWED = ('CC0', 'Public domain', 'CC BY 2.0', 'CC BY 3.0', 'CC BY 4.0', 'CC BY-SA 2.0', 'CC BY-SA 3.0', 'CC BY-SA 4.0')
# Matches checked by eye and found to show another dish: left with the emoji.
EXCLUDE = {'pho_bo', 'kebab', 'kombucha', 'lasagna', 'mocha', 'nuoc_chanh', 'pho_mai', 'ruou_de', 'nho', 'mit', 'bo', 'latte'}
STOP = {'the', 'and', 'with', 'of', 'a', 'in', 'on', 'style', 'food', 'dish', 'bowl', 'plate', 'serving'}


def api(params):
    url = 'https://commons.wikimedia.org/w/api.php?' + urllib.parse.urlencode({**params, 'format': 'json'})
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=30) as r:
        return json.load(r)


def fold(text):
    """Lower case without accents, so 'phở' and 'pho' match each other."""
    decomposed = unicodedata.normalize('NFKD', text.lower().replace('đ', 'd'))
    return ''.join(c for c in decomposed if not unicodedata.combining(c))


def key_words(name):
    words = re.findall(r'[a-z]+', fold(name))
    return [w for w in words if w not in STOP and len(w) > 2]


def find(name):
    """The first image that is licensed for use and whose title names the dish."""
    words = key_words(name)
    if not words:
        return None
    data = api({'action': 'query', 'generator': 'search', 'gsrsearch': f'filetype:bitmap {name} food', 'gsrnamespace': 6,
                'gsrlimit': 12, 'prop': 'imageinfo', 'iiprop': 'url|extmetadata', 'iiurlwidth': 400})
    for page in sorted((data.get('query', {}).get('pages') or {}).values(), key=lambda p: p.get('index', 0)):
        info = (page.get('imageinfo') or [{}])[0]
        meta = info.get('extmetadata', {})
        licence = meta.get('LicenseShortName', {}).get('value', '')
        if not any(licence == a or licence.startswith(a) for a in ALLOWED):
            continue
        # Every distinctive word of the name must be in the title, or the photo may show another dish.
        title = fold(page['title'])
        if not all(w in title for w in words):
            continue
        artist = re.sub(r'<[^>]+>', '', meta.get('Artist', {}).get('value', '')).strip()
        return {'url': info.get('thumburl'), 'title': page['title'], 'licence': licence, 'artist': artist,
                'page': info.get('descriptionurl', '')}
    return None


def download(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=60) as r:
        return r.read()


def main():
    foods = json.load(io.open(IOS_SEED, encoding='utf-8'))
    names = json.load(io.open(os.path.join(ROOT, 'ios/fastlane/food_names_en.json'), encoding='utf-8')) \
        if os.path.exists(os.path.join(ROOT, 'ios/fastlane/food_names_en.json')) else {}
    os.makedirs(IOS_OUT, exist_ok=True)
    os.makedirs(ANDROID_OUT, exist_ok=True)
    credits = []
    found = 0
    for food in foods:
        fid = food['id']
        target = os.path.join(IOS_OUT, fid + '.jpg')
        english = food.get('en') or food['name']
        if fid in EXCLUDE:
            continue
        if os.path.exists(target):
            found += 1
            continue
        try:
            hit = find(english)
            time.sleep(0.5)
            if not hit or not hit['url']:
                print('  no licensed photo:', fid, '-', english)
                continue
            image = Image.open(io.BytesIO(download(hit['url']))).convert('RGB')
            image.thumbnail((400, 400))
            image.save(target, 'JPEG', quality=82)
            shutil.copy(target, os.path.join(ANDROID_OUT, fid + '.jpg'))
            found += 1
            credits.append(f"{fid}\t{english}\t{hit['title']}\t{hit['licence']}\t{hit['artist']}\t{hit['page']}")
            print('  ok', fid, '-', hit['licence'])
            time.sleep(0.5)
        except Exception as e:
            print('  failed', fid, e)
    if credits:
        with io.open(os.path.join(IOS_OUT, 'CREDITS.tsv'), 'a', encoding='utf-8') as f:
            f.write('\n'.join(credits) + '\n')
    # Android looks up which foods have a bundled photo; iOS checks the file itself.
    ids = sorted(os.path.splitext(f)[0] for f in os.listdir(IOS_OUT) if f.endswith('.jpg'))
    lines = [
        'package com.calocare.app.data',
        '',
        '/** Foods with a real photo bundled in assets/foodphotos (written by tools/fetch_food_photos.py). */',
        'object FoodPhotos {',
        '    private val ids = setOf(',
    ] + [f'        "{i}",' for i in ids] + [
        '    )',
        '',
        '    /** The bundled photo of a food, or null when it has none (the emoji is shown instead). */',
        '    fun url(id: String): String? = if (id in ids) "file:///android_asset/foodphotos/$id.jpg" else null',
    ]
    kotlin = '\n'.join(lines) + '\n'
    io.open(os.path.join(ROOT, 'app/src/main/java/com/calocare/app/data/FoodPhotos.kt'), 'w', encoding='utf-8', newline='\n').write(kotlin)
    print(f'{found} of {len(foods)} foods have a licensed photo')


if __name__ == '__main__':
    main()

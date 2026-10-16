"""Generates the files the iOS app takes from the Android app: the built-in dish table and the app icon.

    python tools/export_ios.py

ios/CaloCare/seed.json is the table the iOS app uses until its first sync with the server's library.
Run again after editing FoodDb.kt.
"""
import io
import json
import os
import re

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..')
SRC = os.path.join(ROOT, 'app/src/main/java/com/calocare/app/data/FoodDb.kt')
IOS = os.path.join(ROOT, 'ios/CaloCare')

LINE = re.compile(
    r'f\("(?P<id>[^"]+)", "(?P<name>[^"]+)", "(?P<emoji>[^"]*)", "(?P<serving>[^"]+)", (?P<kcal>\d+), (?P<p>[\d.]+), (?P<c>[\d.]+), (?P<f>[\d.]+), '
    r'(?P<course>[A-Z]+)(?:, "(?P<tags>[^"]*)")?(?:, (?:region = )?(?P<region>[AI]))?\)'
)
# English names and servings, by id (FoodNames.kt).
NAMES = os.path.join(ROOT, 'app/src/main/java/com/calocare/app/data/FoodNames.kt')
EN = {m[0]: (m[1], m[2]) for m in re.findall(r'"([^"]+)" to \("((?:[^"\\]|\\.)*)" to "((?:[^"\\]|\\.)*)"\)', io.open(NAMES, encoding='utf-8').read())}

foods = []
for line in io.open(SRC, encoding='utf-8'):
    if not line.lstrip().startswith('f("'):
        continue
    m = LINE.search(line)
    assert m, 'unrecognised food line in FoodDb.kt: ' + line.strip()
    foods.append({
        'id': m['id'], 'name': m['name'], 'emoji': m['emoji'], 'serving': m['serving'], 'kcal': int(m['kcal']),
        'protein': float(m['p']), 'carbs': float(m['c']), 'fat': float(m['f']), 'course': m['course'].lower(),
        'tags': (m['tags'] or '').split(), 'region': {'A': 'all', 'I': 'intl', None: 'vn'}[m['region']], 'custom': False,
        'en': EN[m['id']][0], 'servingEn': EN[m['id']][1],
    })
io.open(os.path.join(IOS, 'seed.json'), 'w', encoding='utf-8', newline='\n').write(json.dumps(foods, ensure_ascii=False, indent=0))

# App icon: iOS wants one opaque 1024 px square; the Play Store icon is the same artwork at 512 px.
icon_dir = os.path.join(IOS, 'Assets.xcassets/AppIcon.appiconset')
os.makedirs(icon_dir, exist_ok=True)
Image.open(os.path.join(ROOT, 'play-store/icon-512.png')).convert('RGB').resize((1024, 1024), Image.LANCZOS).save(os.path.join(icon_dir, 'icon-1024.png'))
io.open(os.path.join(icon_dir, 'Contents.json'), 'w', newline='\n').write(json.dumps(
    {'images': [{'filename': 'icon-1024.png', 'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'}], 'info': {'author': 'xcode', 'version': 1}}, indent=2))
io.open(os.path.join(IOS, 'Assets.xcassets/Contents.json'), 'w', newline='\n').write(json.dumps({'info': {'author': 'xcode', 'version': 1}}, indent=2))
print(f'{len(foods)} foods -> ios/CaloCare/seed.json, icon -> Assets.xcassets')

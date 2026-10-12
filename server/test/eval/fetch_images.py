"""Builds a small labelled test set of Vietnamese dish photos from Wikimedia Commons.

    python test/eval/fetch_images.py        (from the server folder)

Each dish in DISHES is searched on Commons; the first few photos are downloaded, centre-cropped to a
square and scaled to 512 px — the same shape the app's camera sends. manifest.json records where
every photo came from, with its author and licence. The photos are for measuring accuracy only and
are not shipped with the app.

Search results are not perfect: review contact_sheet.jpg and list wrong photos in exclude.txt
(one file name per line); eval.mjs skips those.
"""
import io
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
IMAGES = os.path.join(HERE, 'images')
PER_DISH = 4
USER_AGENT = 'CaloCareEval/1.0 (accuracy test set for a calorie tracking app)'

# label (exactly as in the app's food table) -> Commons search text
DISHES = {
    'Phở bò': 'phở bò', 'Phở gà': 'phở gà', 'Bánh mì thịt': 'bánh mì thịt sandwich', 'Cơm tấm sườn bì chả': 'cơm tấm',
    'Bún chả': 'bún chả', 'Bún bò Huế': 'bún bò Huế', 'Bún riêu cua': 'bún riêu', 'Gỏi cuốn': 'gỏi cuốn', 'Chả giò': 'chả giò',
    'Bánh xèo': 'bánh xèo', 'Bánh cuốn': 'bánh cuốn', 'Mì Quảng': 'mì Quảng', 'Hủ tiếu Nam Vang': 'hủ tiếu Nam Vang', 'Cao lầu': 'cao lầu',
    'Bánh chưng': 'bánh chưng', 'Bánh bao nhân thịt': 'bánh bao', 'Bún đậu mắm tôm': 'bún đậu mắm tôm', 'Bún thịt nướng': 'bún thịt nướng',
    'Bò kho bánh mì': 'bò kho', 'Bánh canh cua': 'bánh canh cua', 'Cà phê sữa đá': 'cà phê sữa đá', 'Trà sữa trân châu': 'bubble tea cup',
    'Bánh flan': 'bánh flan', 'Trứng vịt lộn': 'balut egg', 'Cơm trắng': 'bowl of steamed white rice', 'Canh chua cá': 'canh chua',
    'Thịt kho trứng': 'thịt kho trứng', 'Rau muống xào tỏi': 'rau muống xào tỏi', 'Bánh bột lọc': 'bánh bột lọc', 'Nem nướng cuốn': 'nem nướng',
    'Xôi gà': 'xôi gà', 'Cháo lòng': 'cháo lòng', 'Bánh tráng trộn': 'bánh tráng trộn', 'Chè Thái': 'chè Thái', 'Bánh trung thu thập cẩm': 'bánh trung thu',
}


def api(params):
    url = 'https://commons.wikimedia.org/w/api.php?' + urllib.parse.urlencode({**params, 'format': 'json'})
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': USER_AGENT}), timeout=30) as r:
        return json.load(r)


def strip_html(text):
    return re.sub(r'<[^>]+>', '', text or '').strip()[:120]


def square(image, side=512):
    image = image.convert('RGB')
    w, h = image.size
    m = min(w, h)
    return image.crop(((w - m) // 2, (h - m) // 2, (w + m) // 2, (h + m) // 2)).resize((side, side), Image.LANCZOS)


def slug(label):
    import unicodedata
    folded = unicodedata.normalize('NFD', label.lower().replace('đ', 'd'))
    return re.sub(r'[^a-z0-9]+', '_', ''.join(c for c in folded if unicodedata.category(c) != 'Mn')).strip('_')


os.makedirs(IMAGES, exist_ok=True)
manifest = []
for label, query in DISHES.items():
    found = api({
        'action': 'query', 'generator': 'search', 'gsrnamespace': 6, 'gsrsearch': f'{query} filetype:bitmap', 'gsrlimit': 12,
        'prop': 'imageinfo', 'iiprop': 'url|extmetadata|mime|size', 'iiurlwidth': 640,
    }).get('query', {}).get('pages', {})
    pages = sorted(found.values(), key=lambda p: p.get('index', 0))
    kept = 0
    for page in pages:
        if kept >= PER_DISH:
            break
        info = (page.get('imageinfo') or [{}])[0]
        # Skip non-photos and tiny or very elongated pictures (logos, maps, banners).
        if info.get('mime') != 'image/jpeg' or min(info.get('width', 0), info.get('height', 0)) < 400:
            continue
        if max(info['width'], info['height']) / min(info['width'], info['height']) > 2:
            continue
        name = f'{slug(label)}_{kept + 1}.jpg'
        try:
            with urllib.request.urlopen(urllib.request.Request(info['thumburl'], headers={'User-Agent': USER_AGENT}), timeout=60) as r:
                square(Image.open(io.BytesIO(r.read()))).save(os.path.join(IMAGES, name), quality=80)
        except Exception as e:  # one broken file must not stop the run
            print(f'  skipped {page.get("title")}: {e}', file=sys.stderr)
            continue
        meta = info.get('extmetadata', {})
        manifest.append({
            'file': name, 'label': label, 'source': info.get('descriptionurl'),
            'author': strip_html(meta.get('Artist', {}).get('value')), 'licence': meta.get('LicenseShortName', {}).get('value'),
        })
        kept += 1
        time.sleep(0.3)
    print(f'{label}: {kept}')

json.dump(manifest, io.open(os.path.join(HERE, 'manifest.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

# Contact sheet for a quick visual check of the labels.
cols, cell = 12, 110
rows = (len(manifest) + cols - 1) // cols
sheet = Image.new('RGB', (cols * cell, rows * (cell + 14)), 'white')
draw = ImageDraw.Draw(sheet)
for i, entry in enumerate(manifest):
    x, y = (i % cols) * cell, (i // cols) * (cell + 14)
    sheet.paste(Image.open(os.path.join(IMAGES, entry['file'])).resize((cell, cell)), (x, y))
    draw.text((x + 2, y + cell + 1), entry['file'][:-4][:17], fill='black')
sheet.save(os.path.join(HERE, 'contact_sheet.jpg'), quality=80)
print(f'{len(manifest)} photos, contact_sheet.jpg written')

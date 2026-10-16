"""Writes the App Store listing in fastlane's format (ios/fastlane/metadata, ios/fastlane/screenshots) for
the "listing" job in .github/workflows/ios.yml, which uploads it with tools/asc_listing.py.

    python tools/store_assets.py      # frames the simulator screenshots first
    python tools/export_appstore.py
"""
import io
import os
import shutil

ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'ios/fastlane')
PRIVACY = 'https://calocare-api.calocare-api.workers.dev/privacy'
TERMS = 'https://www.apple.com/legal/internet-services/itunes/dev/stdeula/'

LISTING = {
    'vi': {
        'name': 'CaloCare: Tính calo & Coach AI',
        'subtitle': 'Đếm calo món Việt bằng ảnh',
        'promotional_text': 'Chụp ảnh bữa ăn, AI tính calo món Việt trong vài giây. Coach dinh dưỡng gợi ý bữa tiếp theo theo mục tiêu giảm cân, tăng cơ hay chạy bộ.',
        'keywords': 'calo,dem calo,giam can,tang co,an kieng,dinh duong,thuc don,mon viet,pho,gym,chay bo,keto,tieu duong',
        'support_url': PRIVACY,
        'privacy_url': PRIVACY,
        'release_notes': 'Phiên bản đầu tiên trên App Store: chụp ảnh tính calo, hơn 240 món Việt và quốc tế, coach AI, kết nối Apple Health và gói CaloCare Pro.',
        'description': f'''CaloCare giúp bạn biết mình ăn bao nhiêu, tiêu hao bao nhiêu và nên ăn gì tiếp theo, lấy món ăn Việt Nam làm trung tâm.

● Ghi bữa ăn bằng một chạm
Hơn 240 món quen thuộc: phở, cơm tấm, bún chả, bánh mì, trà sữa… cùng các món quốc tế. Chạm một lần là thêm một phần. Tìm kiếm không cần gõ dấu, tự tạo món riêng.

● Chụp ảnh bữa ăn
Đưa món ăn vào vòng tròn, AI nhận diện từng món, ước tính calo, đạm, tinh bột, chất béo. Chỉnh khẩu phần (nhỏ, vừa, lớn) hoặc tính theo từng thành phần trước khi lưu.

● Coach dinh dưỡng
Gợi ý bữa tiếp theo dựa trên lượng calo còn lại, theo mục tiêu giảm cân, tăng cân, tăng cơ hay chạy bộ, kèm thứ tự ăn hợp lý trong bữa.

● Chế độ sức khỏe
Khai báo tiểu đường, cao huyết áp, gout, mỡ máu, dị ứng hoặc ăn chay, keto. App cảnh báo khi món ăn không phù hợp.

● Kết nối Apple Watch và đồng hồ thông minh
Đọc calo vận động, bước chân, phút tập, giấc ngủ, cân nặng từ Apple Health (Apple Watch, Garmin, Mi Fitness, Zepp…), so calo nạp vào với calo tiêu hao mỗi ngày.

● Riêng tư
Không cần tài khoản. Dữ liệu nằm trên iPhone của bạn, dữ liệu Apple Health không rời khỏi máy. Không quảng cáo.

● CaloCare Pro
Miễn phí: 30 ảnh AI và 50 câu hỏi đầu tiên, sau đó mỗi ngày 2 ảnh và 5 câu hỏi. CaloCare Pro (theo tháng hoặc theo năm, gói năm có 7 ngày dùng thử) cho phép tới 40 ảnh và 100 câu hỏi mỗi ngày. Gói tự gia hạn qua Apple ID cho tới khi bạn hủy trong Cài đặt, ít nhất 24 giờ trước kỳ gia hạn.

Lưu ý: CaloCare cung cấp ước tính và gợi ý tham khảo, không thay thế chẩn đoán hay chỉ định của bác sĩ.

Điều khoản sử dụng: {TERMS}
Chính sách quyền riêng tư: {PRIVACY}''',
    },
    'en-US': {
        'name': 'CaloCare: Calorie Counter & AI',
        'subtitle': 'Snap a meal, get the calories',
        'promotional_text': 'Snap a photo of your meal and AI counts the calories in seconds. A personal nutrition coach suggests your next meal for weight loss, muscle or running.',
        'keywords': 'calorie counter,food diary,diet,weight loss,macro,nutrition,meal planner,fitness,keto,protein,ai',
        'support_url': PRIVACY + '?lang=en',
        'privacy_url': PRIVACY + '?lang=en',
        'release_notes': 'First App Store release: meal photos to calories, over 240 international and Vietnamese foods, an AI coach, Apple Health and CaloCare Pro.',
        'description': f'''CaloCare shows how much you eat, how much you burn and what to eat next — without typing every meal.

● Log a meal in one tap
Over 240 everyday foods and drinks: eggs, oatmeal, salads, burgers, pizza, pasta, sushi, ramen, curries, lattes, smoothies… plus Vietnamese favourites like phở and bánh mì. Tap once to add a serving and create your own foods.

● Snap your meal
Fit the plate inside the circle and the AI identifies each item, estimating calories, protein, carbs, fat, fibre, sugar and sodium. Adjust the size or amount, or rebuild a dish from its ingredients in grams.

● A coach that knows you
Ask what to eat tonight, get a menu for tomorrow or a review of your week. Suggestions follow your calories left and your goal: lose weight, maintain, gain, build muscle or run.

● Health mode
Tell the app about diabetes, high blood pressure, gout, high cholesterol, food allergies or a vegetarian, vegan, keto or halal diet. It warns you about foods that don't suit you.

● Apple Watch and smartwatches
Reads active calories, steps, exercise minutes, sleep and weight from Apple Health (Apple Watch, Garmin, Fitbit, Mi Fitness, Zepp…) and compares calories in with calories out every day.

● Private
No account needed. Your data stays on your iPhone, and Apple Health data never leaves it. No ads.

● CaloCare Pro
Free: your first 30 AI photos and 50 questions, then 2 photos and 5 questions a day. CaloCare Pro (monthly, or yearly with a 7-day free trial) gives up to 40 photos and 100 questions a day. Subscriptions renew automatically through your Apple ID until you cancel in Settings, at least 24 hours before renewal.

Note: CaloCare provides estimates and general suggestions. It is not a substitute for medical diagnosis or your doctor's advice.

Terms of Use: {TERMS}
Privacy Policy: {PRIVACY}?lang=en''',
    },
}

LIMITS = {'name': 30, 'subtitle': 30, 'promotional_text': 170, 'keywords': 100, 'description': 4000, 'release_notes': 4000}

for lang, fields in LISTING.items():
    folder = os.path.join(OUT, 'metadata', lang)
    os.makedirs(folder, exist_ok=True)
    for key, value in fields.items():
        if key in LIMITS:
            assert len(value) <= LIMITS[key], (lang, key, len(value))
        io.open(os.path.join(folder, key + '.txt'), 'w', encoding='utf-8', newline='\n').write(value + '\n')

meta = os.path.join(OUT, 'metadata')
for name, value in {'copyright': '2026 CaloCare', 'primary_category': 'HEALTH_AND_FITNESS', 'secondary_category': 'FOOD_AND_DRINK'}.items():
    io.open(os.path.join(meta, name + '.txt'), 'w', encoding='utf-8', newline='\n').write(value + '\n')

# Screenshots: the framed 6.9" sets made by tools/store_assets.py.
shots = os.path.join(ROOT, 'play-store/ios-screenshots')
for lang, prefix in (('vi', ''), ('en-US', 'en-')):
    folder = os.path.join(OUT, 'screenshots', lang)
    shutil.rmtree(folder, ignore_errors=True)
    os.makedirs(folder)
    for name in sorted(os.listdir(shots)):
        english = name.startswith('en-')
        if english == (prefix == 'en-'):
            shutil.copy(os.path.join(shots, name), os.path.join(folder, name[len(prefix):]))
    print(lang, sorted(os.listdir(folder)))

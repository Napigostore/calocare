# CaloCare – hồ sơ đăng Google Play

Tệp tải lên: `app-release.aab` (trong thư mục này). Build lại: `gradlew :app:bundleRelease`, nhớ tăng `versionCode` trong `app/build.gradle.kts` cho mỗi lần tải lên.

## Thông tin cửa hàng

**Tên ứng dụng (≤30 ký tự)**
CaloCare: Tính calo & Coach AI

**Mô tả ngắn (≤80 ký tự)**
Đếm calo món Việt bằng một chạm, chụp ảnh bữa ăn, coach dinh dưỡng cá nhân.

**Mô tả đầy đủ**

CaloCare giúp bạn biết mình ăn bao nhiêu, tiêu hao bao nhiêu và nên ăn gì tiếp theo — với món ăn Việt Nam làm trung tâm.

● Ghi bữa ăn bằng một chạm
Gần 150 món quen thuộc: phở, cơm tấm, bún chả, bánh mì, trà sữa… Chạm một lần là thêm một phần. Tìm kiếm không cần gõ dấu, tự tạo món riêng, và app tự gợi ý những món bạn hay ăn.

● Chụp ảnh bữa ăn
AI nhận diện từng món, ước tính calo và đa lượng. Bạn chỉnh khẩu phần trước khi lưu, và app ghi nhớ khẩu phần quen của bạn.

● Coach biết bạn là ai
Gợi ý bữa tiếp theo dựa trên lượng calo còn lại, thực đơn theo mục tiêu giảm cân, tăng cân, tăng cơ hay chạy bộ, kèm thứ tự ăn hợp lý trong bữa.

● Chế độ sức khỏe
Khai báo tiểu đường, cao huyết áp, gout, mỡ máu, dị ứng hoặc chế độ ăn chay, keto, halal. App cảnh báo khi món ăn không phù hợp và loại chúng khỏi thực đơn gợi ý.

● Kết nối đồng hồ và vòng tay
Đọc calo vận động, số bước, giấc ngủ, cân nặng từ Health Connect (Wear OS, Samsung Health, Fitbit, Garmin, Mi Fitness…).

● Tiến độ rõ ràng
Biểu đồ calo nạp/tiêu, cân nặng, đa lượng, bước chân theo 7 và 30 ngày. Mục tiêu calo tự đề xuất điều chỉnh theo cân nặng thực tế.

● Riêng tư
Không cần tài khoản. Dữ liệu nằm trên điện thoại của bạn. Không quảng cáo.

Lưu ý: CaloCare cung cấp ước tính và gợi ý tham khảo, không thay thế chẩn đoán hay chỉ định của bác sĩ.

**Danh mục:** Sức khỏe & Thể hình
**Biểu tượng:** `icon-512.png`
**Còn thiếu (bạn tự làm):** ảnh bìa 1024×500, tối thiểu 2 ảnh chụp màn hình điện thoại.

## Chính sách quyền riêng tư

Google Play bắt buộc có URL công khai (app có Health Connect và dữ liệu sức khỏe). Điền email hỗ trợ vào `privacy-policy.html` rồi đưa lên một địa chỉ công khai (GitHub Pages, Google Sites…). Điền cùng email đó vào `Privacy.CONTACT_EMAIL` trong `app/src/main/java/com/calocare/app/ui/PrivacyScreen.kt`.

## Biểu mẫu An toàn dữ liệu (Data safety)

Trả lời theo đúng hành vi hiện tại của app:

| Câu hỏi | Trả lời |
|---|---|
| App có thu thập hoặc chia sẻ dữ liệu người dùng không? | Có (do tính năng AI gửi dữ liệu ra khỏi thiết bị) |
| Dữ liệu có được mã hóa khi truyền không? | Có (HTTPS) |
| Người dùng có thể yêu cầu xóa dữ liệu không? | Có — nút “Xóa toàn bộ dữ liệu” trong Hồ sơ |
| Thông tin sức khỏe, thể hình | Khi người dùng dùng AI: tóm tắt hồ sơ và nhật ký được gửi qua máy chủ CaloCare tới Anthropic, không lưu lại. Mục đích: chức năng ứng dụng. Tùy chọn. |
| Ảnh | Khi người dùng chụp ảnh bữa ăn: gửi qua máy chủ CaloCare tới Anthropic, không lưu lại. Mục đích: chức năng ứng dụng. Tùy chọn. |
| Mã thiết bị hoặc mã nhận dạng khác | Có thu thập: mã băm của ANDROID_ID, lưu trên máy chủ cùng số lượt AI đã dùng. Mục đích: chức năng ứng dụng, chống gian lận. |
| Hoạt động trong ứng dụng (nội dung do người dùng tạo khác) | Khi người dùng sửa tên món AI nhận nhầm: cặp tên món (tên AI đưa ra, tên người dùng chọn) gửi về máy chủ CaloCare kèm mã thiết bị ẩn danh, để cải thiện nhận diện. Không kèm ảnh hay hồ sơ. Mục đích: chức năng ứng dụng. |
| Hoạt động trong ứng dụng (nội dung do người dùng tạo khác) – món tự tạo | Khi người dùng tự tạo món mới hoặc chỉnh món theo thành phần: tên món và số liệu dinh dưỡng gửi về máy chủ CaloCare kèm mã thiết bị ẩn danh, làm đề xuất cho thư viện món chung (có kiểm duyệt). Không kèm ảnh hay hồ sơ. Mục đích: chức năng ứng dụng. |
| Hoạt động trong ứng dụng (từ khóa tìm kiếm) | Từ khóa tìm món gửi tới Open Food Facts. Mục đích: chức năng ứng dụng. |
| Dữ liệu dùng cho quảng cáo / theo dõi | Không |

## Khai báo Health Connect

Trong Play Console → Nội dung ứng dụng → Health Connect, khai báo các quyền chỉ đọc sau và lý do:

| Quyền | Lý do |
|---|---|
| READ_ACTIVE_CALORIES_BURNED, READ_TOTAL_CALORIES_BURNED | Tính calo tiêu hao trong ngày để so với calo nạp vào |
| READ_STEPS | Biểu đồ bước chân và ước tính vận động khi không có dữ liệu calo |
| READ_SLEEP | Lời khuyên phục hồi khi thiếu ngủ |
| READ_WEIGHT, READ_HEIGHT | Cập nhật cân nặng, chiều cao để tính BMR và theo dõi tiến độ |

## Các khai báo khác

- **Ứng dụng sức khỏe (Health apps declaration):** chọn nhóm Dinh dưỡng và quản lý cân nặng, Thể hình. Không phải thiết bị y tế.
- **Đối tượng:** từ 13 tuổi trở lên (nên chọn 18+ để đơn giản phần chính sách trẻ em).
- **Quảng cáo:** Không.
- **Xếp hạng nội dung:** trả lời bảng hỏi; app không có nội dung nhạy cảm.

## Khóa ký

`keystore/upload.jks` và `keystore.properties` là khóa tải lên. Sao lưu cả hai ra nơi an toàn ngoài máy này. Khi tạo app trên Play Console, giữ bật Play App Signing để có thể cấp lại khóa tải lên nếu mất.

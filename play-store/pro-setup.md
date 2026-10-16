# CaloCare Pro – cài đặt trên cửa hàng

## Giá đã chọn

| | Quốc tế (USD) | Việt Nam |
|---|---|---|
| Gói tháng | $1.99 | mức Apple/Google tự quy đổi |
| Gói năm | $19.99, dùng thử miễn phí 7 ngày | mức tự quy đổi, dùng thử miễn phí 7 ngày |

Apple không cho giá gói năm vượt quá một mức nhất định so với 12 tháng gói tháng, nên gói tháng là $1.99 (gói năm rẻ hơn khoảng 16%).

**Miễn phí:**
- 30 ảnh AI và 50 câu hỏi đầu tiên.
- Sau đó mỗi ngày 2 ảnh và 5 câu hỏi.
- Ghi món, thực đơn, biểu đồ và đồng hồ luôn miễn phí.

**Pro:** tới 40 ảnh và 100 câu hỏi mỗi ngày.

Bạn có thể đổi các con số này bất cứ lúc nào trong `server/src/index.ts` (mục `LIMITS`), không cần cập nhật app.

## Google Play

1. **Thiết lập hồ sơ thanh toán:** Play Console → Cài đặt → Hồ sơ thanh toán. Đăng ký tài khoản bán hàng, điền ngân hàng và thuế.
2. **Tải bản 1.2.0 lên một kênh thử nghiệm:** tải `app-release.aab` (versionCode 3) lên kênh Kiểm thử nội bộ. Play chỉ cho tạo gói thuê bao sau khi đã có bản có quyền thanh toán.
3. **Tạo gói thuê bao:** Kiếm tiền → Sản phẩm → Gói thuê bao → Tạo gói thuê bao.
   - **Mã sản phẩm:** `calocare_pro` (phải đúng chính xác). Tên: CaloCare Pro.
   - **Gói cơ bản thứ nhất:**
     - Mã: `monthly`.
     - Tự động gia hạn, chu kỳ 1 tháng.
     - Đặt giá 1,99 USD, bấm "Cập nhật giá cho mọi quốc gia".
     - Giữ giá Việt Nam do Play tự quy đổi, hoặc sửa theo ý bạn.
     - Kích hoạt.
   - **Gói cơ bản thứ hai:**
     - Mã: `yearly`.
     - Chu kỳ 1 năm.
     - Giá 19,99 USD.
     - Kích hoạt.
   - **Ưu đãi dùng thử:** trong gói `yearly`, chọn Thêm ưu đãi.
     - Mã ưu đãi: `trial7`.
     - Đối tượng: khách hàng mới.
     - Giai đoạn: Dùng thử miễn phí 7 ngày.
     - Kích hoạt.
4. **Cho phép server kiểm tra giao dịch:**
   1. Mở https://console.cloud.google.com → tạo dự án (ví dụ "calocare").
   2. Thư viện API → bật **Google Play Android Developer API**.
   3. IAM → Tài khoản dịch vụ → Tạo, tên `calocare-billing` → Khóa → Thêm khóa → JSON. Một file .json sẽ được tải về. Hãy giữ kín file này.
   4. Trong Play Console → Người dùng và quyền → Mời người dùng mới, dán email của tài khoản dịch vụ (dạng `...@...iam.gserviceaccount.com`).
   5. Cấp quyền cho ứng dụng CaloCare: **Xem dữ liệu tài chính** và **Quản lý đơn đặt hàng và gói thuê bao**.
   6. Lưu 2 giá trị vào server. Mình không nhập khóa bí mật thay bạn, nên bạn tự chạy hai lệnh dưới đây trong thư mục `server`, rồi dán khi được hỏi.
      - Lệnh thứ nhất hỏi giá trị `client_email` trong file JSON.
      - Lệnh thứ hai hỏi giá trị `private_key`: dán nguyên chuỗi, kể cả `-----BEGIN PRIVATE KEY-----` và các ký tự `\n`.
      ```bash
      npx wrangler secret put GOOGLE_SA_EMAIL
      ```
      ```bash
      npx wrangler secret put GOOGLE_SA_KEY
      ```
   Quyền của tài khoản dịch vụ có thể mất vài giờ mới có hiệu lực.
5. **Mua thử không mất tiền:** Cài đặt → Thử nghiệm giấy phép → thêm email Gmail của bạn và người thử. Các tài khoản này mua sẽ được tính là giao dịch thử.
6. **Cập nhật mục An toàn dữ liệu:** thêm Thông tin tài chính → **Lịch sử mua hàng**.
   - Có thu thập.
   - Mục đích: chức năng ứng dụng.
   - Không chia sẻ.
   - Không bắt buộc.

## App Store

1. **Ký hợp đồng ứng dụng trả phí:** App Store Connect → Business (Thỏa thuận, Thuế, Ngân hàng) → ký **Paid Apps Agreement**, điền ngân hàng và thuế. Chưa ký thì không bán được.
2. **Tạo nhóm và gói thuê bao:** CaloCare → Monetization → Subscriptions → tạo nhóm **CaloCare Pro**, rồi thêm 2 gói.
   - **Gói năm:**
     - Product ID: `com.napigo.calocare.pro.yearly` (đã tạo; app dùng đúng mã này).
     - Thời hạn 1 năm, giá $19.99.
     - Việt Nam để Apple tự quy đổi.
   - **Gói tháng:**
     - Product ID: `com.napigo.calocare.pro.Monthly` (đã tạo, chữ M viết hoa).
     - Thời hạn 1 tháng, giá $1.99.
     - Việt Nam để Apple tự quy đổi.
   - **Với mỗi gói:**
     - Thêm Localization tiếng Anh và tiếng Việt: tên "CaloCare Pro" và mô tả ngắn, ví dụ "Chụp mọi bữa ăn, AI tính calo" / "Snap every meal, AI counts the calories".
     - Thêm ảnh chụp màn hình bảng nâng cấp Pro (Review Screenshot).
   - **Ưu đãi dùng thử cho gói năm:** Subscription Prices → Introductory Offers → Free, 1 week, áp dụng mọi quốc gia.
3. **Gửi duyệt:** gửi 2 gói cùng lần gửi duyệt app tiếp theo. Trên trang phiên bản, chọn các gói ở mục In-App Purchases and Subscriptions.
4. **Thêm điều khoản vào mô tả App Store:** Apple yêu cầu có dòng điều khoản ở cuối mô tả. Dán dòng sau:
   `Terms of Use: https://www.apple.com/legal/internet-services/itunes/dev/stdeula/`
5. **Cập nhật App Privacy:** thêm Purchases → Purchase History.
   - Mục đích: App Functionality.
   - Không liên kết danh tính.
   - Không theo dõi.
6. **Mua thử:** mua trong bản TestFlight không mất tiền. Gói thuê bao trong sandbox gia hạn rất nhanh, 1 tháng tương đương vài phút.

Phía iOS không cần khóa bí mật nào. Server tự kiểm tra chữ ký của Apple trên mỗi giao dịch.

## Kiểm tra sau khi cài đặt

- Trên Android, vào Hồ sơ → ✨ Nâng cấp Pro. Giá phải hiện theo tiền tệ của bạn. Mua bằng tài khoản thử giấy phép, sau đó dòng hạn mức đổi thành "✨ Bạn đang dùng Pro".
- Trên iPhone (TestFlight), làm tương tự.
- Nếu Android báo "Chưa kiểm tra được giao dịch với cửa hàng", nghĩa là bước 4 của Google Play chưa xong hoặc quyền chưa có hiệu lực.

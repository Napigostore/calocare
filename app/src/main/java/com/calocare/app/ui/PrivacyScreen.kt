package com.calocare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shown from the profile and when Health Connect asks the app to explain its data use. */
object Privacy {
    /** Support address shown in the policy. Must match the one on the Play Store listing. */
    const val CONTACT_EMAIL = "truongtumoc@gmail.com"

    val sections: List<Pair<String, String>> = listOf(
        "Tóm tắt" to
            "CaloCare lưu hồ sơ và nhật ký của bạn ngay trên điện thoại. Ứng dụng không yêu cầu tài khoản, không quảng cáo và không bán dữ liệu. " +
            "Dữ liệu chỉ rời khỏi thiết bị khi bạn chủ động dùng tính năng AI hoặc tìm món trên mạng, như mô tả bên dưới.",
        "Dữ liệu ứng dụng lưu trên máy" to
            "• Hồ sơ: tên gọi, giới tính, năm sinh, chiều cao, cân nặng, mức vận động, mục tiêu.\n" +
            "• Sức khỏe do bạn tự khai: bệnh lý cần lưu ý, dị ứng, chế độ ăn.\n" +
            "• Nhật ký ăn uống, buổi tập, lịch sử cân nặng, món ăn bạn tự tạo.\n" +
            "Dữ liệu này nằm trong bộ nhớ riêng của ứng dụng và không được sao lưu ra ngoài.",
        "Dữ liệu từ đồng hồ, vòng tay (Health Connect)" to
            "Khi bạn bấm kết nối và cấp quyền, CaloCare chỉ ĐỌC: calo vận động, tổng calo tiêu hao, số bước, giấc ngủ, cân nặng và chiều cao. " +
            "Dữ liệu này chỉ dùng để tính calo tiêu hao, vẽ biểu đồ tiến độ và đưa ra lời khuyên trong ứng dụng. " +
            "CaloCare không ghi vào Health Connect, không gửi dữ liệu này ra khỏi thiết bị, không dùng cho quảng cáo. " +
            "Bạn có thể thu hồi quyền bất cứ lúc nào trong Health Connect.",
        "Tính năng AI" to
            "Khi bạn dùng tính năng AI, dữ liệu sau được gửi qua máy chủ của CaloCare tới nhà cung cấp AI Anthropic để xử lý:\n" +
            "• Chụp ảnh bữa ăn: ảnh và tóm tắt hồ sơ (giới tính, tuổi, chiều cao, cân nặng, mục tiêu, bệnh lý, dị ứng, chế độ ăn).\n" +
            "• Chat với coach: nội dung trò chuyện, tóm tắt hồ sơ và nhật ký 7 ngày gần nhất.\n" +
            "• Ước tính món ăn: tên món bạn nhập.\n" +
            "Máy chủ CaloCare chỉ chuyển tiếp yêu cầu và không lưu ảnh, nội dung trò chuyện hay hồ sơ của bạn. " +
            "Để tính số lượt dùng, máy chủ lưu một mã thiết bị ẩn danh (mã băm, không chứa tên, số điện thoại hay email) cùng số lượt đã dùng.\n" +
            "Khi bạn sửa tên một món AI nhận nhầm, hai cái tên (tên AI đưa ra và tên bạn chọn) được gửi về máy chủ CaloCare, không kèm ảnh hay hồ sơ, " +
            "để lần sau AI nhận đúng món đó cho mọi người dùng.\n" +
            "Khi bạn tự tạo một món mới, hoặc chỉnh một món theo từng thành phần, tên món và số liệu dinh dưỡng của món đó (không kèm ảnh, hồ sơ hay nhật ký của bạn) " +
            "được gửi về máy chủ CaloCare làm đề xuất cho thư viện món ăn chung. Món chỉ đến với người dùng khác sau khi được đội ngũ CaloCare kiểm duyệt.\n" +
            "Nếu bạn nhập API key riêng (Anthropic hoặc DeepSeek) trong phần Nâng cao, dữ liệu được gửi thẳng tới nhà cung cấp đó và xử lý theo chính sách của họ.",
        "Tìm món ăn trên mạng" to
            "Khi bạn bấm “Tìm trên mạng”, từ khóa tìm kiếm được gửi tới Open Food Facts (openfoodfacts.org). Không kèm thông tin cá nhân nào.",
        "Camera và ảnh" to
            "Ứng dụng chỉ dùng camera (quyền Camera) hoặc thư viện ảnh khi bạn chủ động chọn chụp bữa ăn. Chỉ phần ảnh trong vòng tròn hướng dẫn được gửi đi phân tích; ảnh không được lưu vào nhật ký hay thư viện của bạn.",
        "Xóa dữ liệu" to
            "Vào Hồ sơ → “Xóa toàn bộ dữ liệu” để xóa hồ sơ, nhật ký và API key khỏi thiết bị. Gỡ ứng dụng cũng xóa toàn bộ dữ liệu.",
        "Trẻ em" to
            "CaloCare không dành cho người dưới 13 tuổi. Mục tiêu calo trong ứng dụng được tính cho người trưởng thành.",
        "Không phải tư vấn y khoa" to
            "CaloCare cung cấp ước tính và gợi ý tham khảo về dinh dưỡng và vận động. Ứng dụng không chẩn đoán, điều trị hay thay thế ý kiến của bác sĩ.",
    )
}

@Composable
fun PrivacyContent() {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Privacy.sections.forEach { (title, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (Privacy.CONTACT_EMAIL.isNotBlank()) {
            Text("Liên hệ: ${Privacy.CONTACT_EMAIL}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

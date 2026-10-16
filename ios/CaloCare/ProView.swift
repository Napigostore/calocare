import StoreKit
import SwiftUI

/// What CaloCare Pro adds, the two plans with the App Store's prices, and the purchase button.
struct ProView: View {
    @EnvironmentObject var pro: ProStore
    @Environment(\.dismiss) private var dismiss
    @State private var picked = "calocare.pro.yearly"

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if pro.isPro { proActive } else { offer }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("✨ CaloCare Pro")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("Đóng", "Close")) { dismiss() } } }
            .task { await pro.refresh() }
        }
    }

    @ViewBuilder private var proActive: some View {
        Text(L("Bạn đang dùng CaloCare Pro 🎉", "You're on CaloCare Pro 🎉")).font(.title2.bold())
        if let until = pro.quota?.pro_until, until > 0 {
            Text(L("Gia hạn hoặc hết hạn: ", "Renews or ends: ") + Date(timeIntervalSince1970: until / 1000).formatted(date: .long, time: .omitted))
        }
        if let q = pro.quota {
            Text(L("📷 Còn \(q.photos_left_today) ảnh · 💬 \(q.chats_left_today) câu hỏi hôm nay", "📷 \(q.photos_left_today) photos · 💬 \(q.chats_left_today) questions left today"))
        }
        Link(L("Quản lý gói trong App Store", "Manage subscription in the App Store"), destination: URL(string: "https://apps.apple.com/account/subscriptions")!)
    }

    @ViewBuilder private var offer: some View {
        Text(L("Chụp mọi bữa ăn, AI tính calo cho bạn", "Snap every meal and let AI count the calories")).font(.title2.bold())
        comparison
        if pro.products.isEmpty {
            HStack { ProgressView(); Text(L("Đang tải giá từ App Store…", "Loading prices from the App Store…")) }
        }
        let monthly = pro.products.first { $0.id == "calocare.pro.monthly" }
        ForEach(pro.products, id: \.id) { product in
            planCard(product, monthly: monthly)
        }
        let product = pro.products.first { $0.id == picked } ?? pro.products.first
        let trial = product.flatMap { trialDays($0) } ?? 0
        Button {
            if let product { Task { await pro.buy(product) } }
        } label: {
            Text(trial > 0 ? L("Dùng thử miễn phí", "Start free trial") : L("Nâng cấp Pro", "Upgrade to Pro")).font(.headline).frame(maxWidth: .infinity).padding(.vertical, 6)
        }
        .buttonStyle(.borderedProminent)
        .disabled(product == nil || pro.busy)
        if let note = pro.note { Text(note).foregroundColor(Palette.green).font(.subheadline.weight(.medium)) }
        Text(L("Gói tự gia hạn cho tới khi bạn hủy. Hủy bất cứ lúc nào trong Cài đặt → Apple ID → Đăng ký, ít nhất 24 giờ trước kỳ gia hạn. Ghi món bằng tay, thực đơn, biểu đồ và Apple Health luôn miễn phí.",
               "Renews automatically until you cancel. Cancel any time in Settings → Apple ID → Subscriptions, at least 24 hours before renewal. Manual logging, menus, charts and Apple Health always stay free."))
            .font(.caption).foregroundColor(.secondary)
        HStack {
            Button(L("Khôi phục giao dịch", "Restore purchase")) { Task { await pro.restore() } }
            Spacer()
            Link(L("Điều khoản", "Terms"), destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!)
            Link(L("Quyền riêng tư", "Privacy"), destination: URL(string: "https://calocare-api.calocare-api.workers.dev/privacy" + (Lang.en ? "?lang=en" : ""))!)
        }
        .font(.footnote)
    }

    private func planCard(_ product: Product, monthly: Product?) -> some View {
        let yearly = product.id == "calocare.pro.yearly"
        let saving: Int = {
            guard yearly, let monthly, monthly.price > 0 else { return 0 }
            let ratio = (product.price as NSDecimalNumber).doubleValue / ((monthly.price as NSDecimalNumber).doubleValue * 12)
            return Int((100 - ratio * 100).rounded())
        }()
        let selected = picked == product.id
        let trial = trialDays(product) ?? 0
        return VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(yearly ? L("Theo năm", "Yearly") : L("Theo tháng", "Monthly")).font(.headline)
                Spacer()
                if saving > 0 { Text(L("Tiết kiệm \(saving)%", "Save \(saving)%")).font(.subheadline.bold()).foregroundColor(Palette.green) }
            }
            Text(product.displayPrice + (yearly ? L(" / năm", " / year") : L(" / tháng", " / month"))).font(.title3)
            if trial > 0 { Text(L("Dùng thử miễn phí \(trial) ngày", "\(trial)-day free trial")).foregroundColor(Palette.green) }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(selected ? Palette.green.opacity(0.12) : Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(selected ? Palette.green : Color(.separator), lineWidth: selected ? 2 : 1))
        .onTapGesture { picked = product.id }
    }

    private func trialDays(_ product: Product) -> Int? {
        guard let offer = product.subscription?.introductoryOffer, offer.paymentMode == .freeTrial else { return nil }
        let p = offer.period
        switch p.unit {
        case .day: return p.value
        case .week: return p.value * 7
        case .month: return p.value * 30
        case .year: return p.value * 365
        @unknown default: return nil
        }
    }

    private var comparison: some View {
        let freePhotos = pro.quota?.free_photos_per_day ?? 2
        let freeChats = pro.quota?.free_chats_per_day ?? 5
        let rows: [(String, String, String)] = [
            (L("📷 Ảnh AI mỗi ngày", "📷 AI photos a day"), "\(freePhotos)", L("Thoải mái*", "Plenty*")),
            (L("💬 Câu hỏi coach mỗi ngày", "💬 Coach questions a day"), "\(freeChats)", "100"),
            (L("🧮 Tính theo thành phần", "🧮 Ingredient breakdown"), L("Theo lượt", "Uses chats"), "✓"),
            (L("✍️ Ghi món, thực đơn, biểu đồ", "✍️ Logging, menus, charts"), "✓", "✓"),
        ]
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("").frame(maxWidth: .infinity)
                Text(L("Miễn phí", "Free")).font(.subheadline.weight(.semibold)).frame(width: 80)
                Text("Pro").font(.subheadline.bold()).foregroundColor(Palette.green).frame(width: 80)
            }
            ForEach(rows, id: \.0) { row in
                HStack {
                    Text(row.0).font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
                    Text(row.1).frame(width: 80)
                    Text(row.2).bold().foregroundColor(Palette.green).frame(width: 80)
                }
            }
            Text(L("* Tới 40 ảnh mỗi ngày, đủ cho mọi bữa ăn.", "* Up to 40 photos a day, enough for every meal.")).font(.caption2).foregroundColor(.secondary)
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }
}

import StoreKit
import SwiftUI

extension Notification.Name {
    /// The server refused an AI request because the free allowance is used up.
    static let proRequired = Notification.Name("CaloCareProRequired")
}

/// CaloCare Pro on the App Store: two auto-renewable subscriptions in one group. The App Store takes the
/// payment; every transaction is then sent to the CaloCare server, which checks Apple's signature and turns
/// on the larger AI allowance for this device.
@MainActor
final class ProStore: ObservableObject {
    static let productIds = ["calocare.pro.yearly", "calocare.pro.monthly"]

    @Published var products: [Product] = []
    @Published var quota: Quota?
    @Published var showPaywall = false
    @Published var busy = false
    @Published var note: String?

    var isPro: Bool { quota?.pro == true }

    private var updates: Task<Void, Never>?

    init() {
        // Renewals, refunds and purchases approved later (Ask to Buy) arrive here, also while the app is closed.
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let tx) = result { await self?.send(result.jwsRepresentation); await tx.finish() }
            }
        }
        NotificationCenter.default.addObserver(forName: .proRequired, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.showPaywall = true }
        }
    }

    deinit { updates?.cancel() }

    /// Loads the prices and re-sends active subscriptions, so the server knows about renewals and reinstalls.
    func refresh() async {
        if products.isEmpty, let loaded = try? await Product.products(for: Self.productIds) {
            products = loaded.sorted { Self.productIds.firstIndex(of: $0.id) ?? 0 < Self.productIds.firstIndex(of: $1.id) ?? 0 }
        }
        for await result in Transaction.currentEntitlements {
            if case .verified = result { await send(result.jwsRepresentation) }
        }
        quota = await API.shared.refreshQuota()
    }

    func buy(_ product: Product) async {
        busy = true
        defer { busy = false }
        note = nil
        do {
            switch try await product.purchase() {
            case .success(let result):
                guard case .verified(let tx) = result else { note = L("Apple không xác nhận được giao dịch.", "Apple couldn't verify the purchase."); return }
                let ok = await send(result.jwsRepresentation)
                await tx.finish()
                note = ok ? L("🎉 Đã bật CaloCare Pro. Cảm ơn bạn!", "🎉 CaloCare Pro is on. Thank you!")
                    : L("Đã thanh toán, nhưng chưa kết nối được máy chủ. Pro sẽ tự bật khi có mạng.", "Payment done, but the server couldn't be reached. Pro turns on automatically once you're online.")
            case .pending:
                note = L("Giao dịch đang chờ duyệt. Pro sẽ bật khi được chấp thuận.", "Purchase pending approval. Pro turns on once it's approved.")
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            note = L("Không mua được. Hãy thử lại.", "The purchase didn't go through. Please try again.")
        }
    }

    func restore() async {
        busy = true
        defer { busy = false }
        try? await AppStore.sync()
        await refresh()
        note = isPro ? L("Đã khôi phục CaloCare Pro.", "CaloCare Pro restored.") : L("Không tìm thấy gói Pro nào trên Apple ID này.", "No Pro subscription found for this Apple ID.")
    }

    @discardableResult
    private func send(_ jws: String) async -> Bool {
        guard let q = await API.shared.activatePro(transaction: jws) else { return false }
        quota = q
        return true
    }
}

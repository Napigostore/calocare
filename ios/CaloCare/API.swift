import CryptoKit
import Foundation
import UIKit

struct APIError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

struct Quota: Decodable {
    var trial_photos_left = 0
    var trial_chats_left = 0
    var photos_left_today = 0
    var chats_left_today = 0
    // Optional: servers before CaloCare Pro do not send them.
    var pro: Bool?
    var pro_until: Double?
    var free_photos_per_day: Int?
    var free_chats_per_day: Int?
}

/// One dish the AI found or worked out. kcal and the macros are for ONE serving; qty is how many.
struct DetectedItem: Decodable, Hashable {
    var name: String
    var kcal: Int
    var alternatives: [String]?
    var emoji: String?
    var portion: String?
    var size: String?
    var qty: Double?
    var grams: Int?
    var protein_g: Double?
    var carbs_g: Double?
    var fat_g: Double?
    var fiber_g: Double?
    var sugar_g: Double?
    var sodium_mg: Double?
    var course: String?
    var tags: [String]?
    var confidence: Double?
}

struct MealAnalysis: Decodable {
    var items: [DetectedItem]
    var advice: String?
    var title: String?
    var score: Int?
    var pros: [String]?
    var cons: [String]?
}

struct Ingredient: Decodable, Identifiable, Hashable {
    var id: String { name }
    var name: String
    var grams: Int
    var kcal_100g: Double
    var protein_100g: Double
    var carbs_100g: Double
    var fat_100g: Double

    var kcal: Double { kcal_100g * Double(grams) / 100 }
    var protein: Double { protein_100g * Double(grams) / 100 }
    var carbs: Double { carbs_100g * Double(grams) / 100 }
    var fat: Double { fat_100g * Double(grams) / 100 }
}

struct RemoteDish: Decodable {
    var id: String
    var name: String
    var emoji: String?
    var serving: String?
    var kcal: Int
    var protein_g: Double?
    var carbs_g: Double?
    var fat_g: Double?
    var course: String?
    var tags: [String]?
    var region: String?

    var food: Food {
        Food(id: id, name: name, emoji: emoji ?? "🍽️", serving: serving ?? L("1 phần", "1 serving"), kcal: kcal, protein: protein_g ?? 0, carbs: carbs_g ?? 0,
             fat: fat_g ?? 0, course: course ?? "main", tags: tags ?? [], region: region ?? "vn", custom: false)
    }
}

struct InsightResult: Decodable {
    var title: String
    var patterns: [String] = []
    var strengths: [String] = []
    var improvements: [String] = []
    var substitutionTip: String?
}

struct NutritionSummary: Encodable {
    var period: String // "week" or "month"
    var startDate: String // yyyy-MM-dd
    var endDate: String
    var daysCount: Int
    var totalDays: Int
    var totalKcal: Int
    var totalProtein: Double
    var totalCarbs: Double
    var totalFat: Double
    var avgKcal: Double
    var avgProtein: Double
    var avgCarbs: Double
    var avgFat: Double
    var mealsPerDay: Double
    var courseBreakdown: [String: Double] // e.g. { "main": 0.45, "soup": 0.20 }
}

struct LibraryUpdate: Decodable {
    var version: Int
    var dishes: [RemoteDish]
    var removed: [String]
}

private struct Envelope<R: Decodable>: Decodable {
    var result: R
}

private struct QuotaEnvelope: Decodable {
    var quota: Quota?
}

private struct BreakdownResult: Decodable {
    var ingredients: [Ingredient]
}

private struct ChatResult: Decodable {
    var text: String
}

private struct ServerMessage: Decodable {
    var error: String?
    var message: String?
}

/// The CaloCare server: it holds the AI key, the dish library and the per-device allowance.
/// The iOS app is a client of exactly the same endpoints as the Android app.
final class API {
    static let shared = API()

    private let host = (Bundle.main.object(forInfoDictionaryKey: "BackendHost") as? String) ?? ""
    private let appKey = (Bundle.main.object(forInfoDictionaryKey: "BackendAppKey") as? String) ?? ""

    var configured: Bool { !host.isEmpty }

    /// identifierForVendor survives app updates; only its hash leaves the phone.
    private lazy var deviceId: String = {
        let raw = "calocare:" + (UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString)
        return SHA256.hash(data: Data(raw.utf8)).map { String(format: "%02x", $0) }.joined()
    }()

    /// Last allowance the server reported, for the profile screen.
    private(set) var lastQuota: Quota?

    private func send(_ path: String, body: [String: Any]? = nil) async throws -> Data {
        guard configured, let url = URL(string: "https://\(host)\(path)") else { throw APIError(message: L("Ứng dụng chưa được cấu hình máy chủ AI.", "This build has no AI server configured.")) }
        var request = URLRequest(url: url, timeoutInterval: 120)
        request.setValue(appKey, forHTTPHeaderField: "X-App-Key")
        request.setValue(deviceId, forHTTPHeaderField: "X-Device")
        request.setValue(Lang.code, forHTTPHeaderField: "X-Lang")
        if let body {
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw APIError(message: L("Không kết nối được tới máy chủ AI. Kiểm tra mạng.", "Couldn't reach the AI server. Check your connection."))
        }
        if let quota = (try? JSONDecoder().decode(QuotaEnvelope.self, from: data))?.quota { lastQuota = quota }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            // The server's messages are already written for the user (allowance used up, busy, ...).
            let server = try? JSONDecoder().decode(ServerMessage.self, from: data)
            // The free AI allowance is used up: the app offers Pro.
            if server?.error == "pro_required" { await MainActor.run { NotificationCenter.default.post(name: .proRequired, object: nil) } }
            throw APIError(message: server?.message ?? L("Máy chủ AI đang gặp sự cố (\(status)). Hãy thử lại sau.", "The AI server has a problem (\(status)). Please try again later."))
        }
        return data
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        do { return try JSONDecoder().decode(type, from: data) } catch { throw APIError(message: L("Không đọc được kết quả AI. Hãy thử lại.", "Couldn't read the AI's answer. Please try again.")) }
    }

    /// Sends an App Store transaction to the server, which checks Apple's signature and turns Pro on for this device.
    func activatePro(transaction: String) async -> Quota? {
        guard (try? await send("/v1/pro", body: ["platform": "ios", "transaction": transaction])) != nil else { return nil }
        return lastQuota
    }

    func refreshQuota() async -> Quota? {
        _ = try? await send("/v1/quota")
        return lastQuota
    }

    func analyzePhoto(jpeg: Data, context: String) async throws -> MealAnalysis {
        let data = try await send("/v1/photo", body: ["image": jpeg.base64EncodedString(), "context": context])
        return try decode(Envelope<MealAnalysis>.self, from: data).result
    }

    /// A dish by name, by ingredients with amounts, or by comparison ("tương đương 1 tô phở").
    func estimate(_ text: String) async throws -> DetectedItem? {
        let data = try await send("/v1/estimate", body: ["name": text])
        return try decode(Envelope<MealAnalysis>.self, from: data).result.items.first
    }

    func breakdown(_ text: String) async throws -> [Ingredient] {
        let data = try await send("/v1/breakdown", body: ["text": text])
        return try decode(Envelope<BreakdownResult>.self, from: data).result.ingredients
    }

    func chat(context: String, history: [(fromUser: Bool, text: String)]) async throws -> String {
        let turns = history.map { ["role": $0.fromUser ? "user" : "assistant", "text": $0.text] }
        let data = try await send("/v1/chat", body: ["context": context, "history": turns])
        return try decode(Envelope<ChatResult>.self, from: data).result.text
    }

    func library(since: Int) async -> LibraryUpdate? {
        guard let data = try? await send("/v1/dishes?since=\(since)") else { return nil }
        return try? JSONDecoder().decode(LibraryUpdate.self, from: data)
    }

    /// "The AI called it aiName, it was really chosen." Names only; improves recognition for everyone.
    func sendCorrection(ai: DetectedItem, chosen: String) async {
        let numbers: [String: Any] = ["kcal": ai.kcal, "protein_g": ai.protein_g ?? 0, "carbs_g": ai.carbs_g ?? 0, "fat_g": ai.fat_g ?? 0]
        _ = try? await send("/v1/correction", body: ["ai_name": ai.name, "chosen": chosen, "ai": numbers])
    }

    /// Offers a dish the user created to the shared library; it reaches others only after review.
    func submitDish(_ food: Food) async {
        _ = try? await send("/v1/dish", body: [
            "name": food.name, "emoji": food.emoji, "serving": food.serving, "kcal": food.kcal,
            "protein_g": food.protein, "carbs_g": food.carbs, "fat_g": food.fat, "course": food.course, "tags": food.tags,
        ])
    }

    /// Get AI coaching insights for a week/month of nutrition data.
    func reportInsights(summary: NutritionSummary) async throws -> InsightResult? {
        let body: [String: Any] = [
            "summary": [
                "period": summary.period,
                "startDate": summary.startDate,
                "endDate": summary.endDate,
                "daysCount": summary.daysCount,
                "totalDays": summary.totalDays,
                "totalKcal": summary.totalKcal,
                "totalProtein": summary.totalProtein,
                "totalCarbs": summary.totalCarbs,
                "totalFat": summary.totalFat,
                "avgKcal": summary.avgKcal,
                "avgProtein": summary.avgProtein,
                "avgCarbs": summary.avgCarbs,
                "avgFat": summary.avgFat,
                "mealsPerDay": summary.mealsPerDay,
                "courseBreakdown": summary.courseBreakdown,
            ]
        ]
        let data = try await send("/v1/report/insights", body: body)
        if let envelope = try JSONDecoder().decode(Envelope<InsightResult>?.self, from: data) {
            return envelope?.result
        }
        return nil
    }
}

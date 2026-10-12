import Foundation

/// A dish or food with nutrition for one serving. Same shape as the Android app's Food.
struct Food: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var emoji: String
    var serving: String
    var kcal: Int
    var protein: Double
    var carbs: Double
    var fat: Double
    var course: String
    var tags: [String]
    var region: String
    var custom: Bool
}

/// One diary line. Nutrition is copied from the food so later edits never rewrite history.
struct LogEntry: Codable, Identifiable, Hashable {
    var id: UUID
    var date: String // yyyy-MM-dd
    var meal: String
    var foodId: String
    var name: String
    var emoji: String
    var serving: String
    var kcal: Int // per serving
    var protein: Double
    var carbs: Double
    var fat: Double
    var tags: [String]
    var qty: Double

    var totalKcal: Double { Double(kcal) * qty }
}

struct Profile: Codable, Hashable {
    var onboarded = false
    var name = ""
    var male = true
    var birthYear = 1995
    var heightCm = 165.0
    var weightKg = 60.0
    var activity = 1.375
    var goal = Goal.maintain
    var weeklyRateKg = 0.5
    var conditions: [String] = []
    var allergies: [String] = []
    var diet = Diet.none
}

struct AppData: Codable {
    var profile = Profile()
    var customFoods: [Food] = []
    var entries: [LogEntry] = []
}

enum Goal {
    static let lose = "lose", maintain = "maintain", gain = "gain", gym = "gym", run = "run"
    static let all: [(id: String, label: String)] = [
        (lose, "🔥 Giảm cân"), (maintain, "⚖️ Giữ cân"), (gain, "🍚 Tăng cân"), (gym, "🏋️ Gym · tăng cơ"), (run, "🏃 Chạy bộ"),
    ]
    static func label(_ id: String) -> String { all.first { $0.id == id }?.label ?? id }
}

enum Diet {
    static let none = "none"
    static let all: [(id: String, label: String)] = [
        (none, "Không kiêng"), ("vegetarian", "Ăn chay (có trứng sữa)"), ("vegan", "Thuần chay"), ("keto", "Keto / ít tinh bột"), ("halal", "Halal"),
    ]
}

enum Meal {
    static let all: [(id: String, label: String)] = [("breakfast", "Sáng"), ("lunch", "Trưa"), ("dinner", "Tối"), ("snack", "Ăn vặt")]
    static func label(_ id: String) -> String { all.first { $0.id == id }?.label ?? id }
    static func forHour(_ hour: Int) -> String {
        switch hour {
        case 4...9: return "breakfast"
        case 10...13: return "lunch"
        case 17...20: return "dinner"
        default: return "snack"
        }
    }
}

enum HealthTags {
    static let allergens: [(id: String, label: String)] = [
        ("peanut", "Đậu phộng"), ("nuts", "Hạt cây"), ("shellfish", "Tôm cua, hải sản có vỏ"), ("fish", "Cá"), ("milk", "Sữa / lactose"),
        ("egg", "Trứng"), ("gluten", "Gluten (lúa mì)"), ("soy", "Đậu nành"), ("sesame", "Mè (vừng)"),
    ]
    static let conditions: [(id: String, label: String)] = [
        ("diabetes", "Tiểu đường"), ("hypertension", "Cao huyết áp"), ("gout", "Gout"), ("cholesterol", "Mỡ máu cao"),
    ]
}

func dayKey(_ date: Date) -> String {
    let f = DateFormatter()
    f.calendar = Calendar(identifier: .gregorian)
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd"
    return f.string(from: date)
}

func formatQty(_ q: Double) -> String {
    if q == q.rounded() { return String(Int(q)) }
    var text = String(format: "%.2f", q)
    while text.hasSuffix("0") { text.removeLast() }
    return text
}

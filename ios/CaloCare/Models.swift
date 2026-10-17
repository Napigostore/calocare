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
    // Optional: files saved by earlier versions do not have them.
    var targetWeightKg: Double?
    var region: String?
    var kcalAdjust: Int?
    var kcalAdjustDate: String?

    var targetWeight: Double { targetWeightKg ?? weightKg }
    /** Which cuisine menus and the food grid favour: "vn" or "intl". */
    var cuisine: String { region ?? (Lang.en ? "intl" : "vn") }
    var adjust: Int { kcalAdjust ?? 0 }
}

struct ExerciseEntry: Codable, Identifiable, Hashable {
    var id: UUID
    var date: String
    var name: String
    var emoji: String
    var minutes: Int
    var kcal: Int
}

struct WeightEntry: Codable, Hashable {
    var date: String
    var kg: Double
}

struct AppData: Codable {
    var profile = Profile()
    var customFoods: [Food] = []
    var entries: [LogEntry] = []
    // Optional for the same reason as the new profile fields.
    var exercises: [ExerciseEntry]?
    var weights: [WeightEntry]?
}

enum Region {
    static var all: [(id: String, label: String)] { [("vn", L("Việt Nam", "Vietnamese")), ("intl", L("Quốc tế / Âu Mỹ", "International / Western"))] }
}

enum Course {
    static var labels: [String: String] {
        ["soup": L("Canh / khai vị", "Soups & starters"), "veg": L("Rau", "Vegetables"), "protein": L("Đạm", "Protein"), "main": L("Món chính", "Mains"),
         "fruit": L("Trái cây", "Fruit"), "dessert": L("Tráng miệng", "Desserts"), "drink": L("Đồ uống", "Drinks"), "snack": L("Ăn vặt", "Snacks")]
    }
}

enum Goal {
    static let lose = "lose", maintain = "maintain", gain = "gain", gym = "gym", run = "run"
    static let all: [(id: String, label: String)] = [
        (lose, L("🔥 Giảm cân", "🔥 Lose weight")), (maintain, L("⚖️ Giữ cân", "⚖️ Maintain")), (gain, L("🍚 Tăng cân", "🍚 Gain weight")), (gym, L("🏋️ Gym · tăng cơ", "🏋️ Gym · build muscle")), (run, L("🏃 Chạy bộ", "🏃 Running")),
    ]
    static func label(_ id: String) -> String { all.first { $0.id == id }?.label ?? id }
}

enum Diet {
    static let none = "none"
    static let all: [(id: String, label: String)] = [
        (none, L("Không kiêng", "No restrictions")), ("vegetarian", L("Ăn chay (có trứng sữa)", "Vegetarian (eggs & dairy)")), ("vegan", L("Thuần chay", "Vegan")), ("keto", L("Keto / ít tinh bột", "Keto / low-carb")), ("halal", "Halal"),
    ]
}

enum Meal {
    static let all: [(id: String, label: String)] = [("breakfast", L("Sáng", "Breakfast")), ("lunch", L("Trưa", "Lunch")), ("dinner", L("Tối", "Dinner")), ("snack", L("Ăn vặt", "Snack"))]
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
        ("peanut", L("Đậu phộng", "Peanuts")), ("nuts", L("Hạt cây", "Tree nuts")), ("shellfish", L("Tôm cua, hải sản có vỏ", "Shellfish")), ("fish", L("Cá", "Fish")), ("milk", L("Sữa / lactose", "Milk / lactose")),
        ("egg", L("Trứng", "Eggs")), ("gluten", L("Gluten (lúa mì)", "Gluten (wheat)")), ("soy", L("Đậu nành", "Soy")), ("sesame", L("Mè (vừng)", "Sesame")),
    ]
    static let conditions: [(id: String, label: String)] = [
        ("diabetes", L("Tiểu đường", "Diabetes")), ("hypertension", L("Cao huyết áp", "High blood pressure")), ("gout", "Gout"), ("cholesterol", L("Mỡ máu cao", "High cholesterol")),
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

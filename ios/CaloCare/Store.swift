import Foundation
import SwiftUI

/// Everything the user enters lives in one JSON file in the app's private storage, as on Android.
@MainActor
final class AppStore: ObservableObject {
    @Published var data: AppData
    @Published var library: [Food]
    @Published var date = Date()
    @Published var meal = Meal.forHour(Calendar.current.component(.hour, from: Date()))
    /// One-line message shown briefly at the bottom of the screen.
    @Published var toast: String?
    /// Apple Health is linked; kept outside AppData so older saved files still load.
    @Published var healthLinked = UserDefaults.standard.bool(forKey: "healthLinked")
    /// Activity for the selected day, from Apple Health.
    @Published var activity: DayActivity?
    /// The last 30 days from Apple Health, keyed by day.
    @Published var history: [String: DayActivity] = Demo.active ? Demo.history : [:]
    /// A food that conflicts with a declared allergy, waiting for the user to confirm adding it.
    @Published var pendingDanger: Food?
    /// The tab on screen, so one screen can send the user to another (e.g. "Ask AI" opens the coach).
    @Published var tab = Demo.tab
    @Published var showProfile = Demo.screen == "profile"

    private var libraryVersion: Int
    private let folder: URL

    private struct LibraryCache: Codable {
        var version: Int
        var foods: [Food]
    }

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        folder = dir
        if Demo.active {
            data = Demo.data
            library = Bundle.main.url(forResource: "seed", withExtension: "json").flatMap { try? Data(contentsOf: $0) }
                .flatMap { try? JSONDecoder().decode([Food].self, from: $0) } ?? []
            libraryVersion = Int.max
            healthLinked = true
            activity = Demo.activity
            meal = "breakfast"
            return
        }
        data = (try? JSONDecoder().decode(AppData.self, from: Data(contentsOf: dir.appendingPathComponent("calocare.json")))) ?? AppData()
        if let cache = try? JSONDecoder().decode(LibraryCache.self, from: Data(contentsOf: dir.appendingPathComponent("library.json"))), !cache.foods.isEmpty {
            library = cache.foods
            libraryVersion = cache.version
        } else {
            // The table shipped inside the app, used until the first sync with the server.
            let seed = Bundle.main.url(forResource: "seed", withExtension: "json").flatMap { try? Data(contentsOf: $0) }
            library = seed.flatMap { try? JSONDecoder().decode([Food].self, from: $0) } ?? []
            libraryVersion = 0
        }
    }

    private func save() {
        guard !Demo.active else { return }
        if let encoded = try? JSONEncoder().encode(data) {
            try? encoded.write(to: folder.appendingPathComponent("calocare.json"), options: .atomic)
        }
    }

    func update(_ change: (inout AppData) -> Void) {
        change(&data)
        save()
    }

    func say(_ text: String) {
        toast = text
        Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if toast == text { toast = nil }
        }
    }

    // MARK: Library

    /// The user's own dishes first, then the shared library.
    var foods: [Food] {
        let own = Set(data.customFoods.map { $0.id })
        let shared = library.filter { !own.contains($0.id) }
        guard Lang.en else { return data.customFoods + shared }
        let order = ["intl": 0, "all": 1]
        return data.customFoods + shared.enumerated().sorted { a, b in
            let ra = order[a.element.region] ?? 2, rb = order[b.element.region] ?? 2
            return ra != rb ? ra < rb : a.offset < b.offset
        }.map { $0.element }
    }

    /// Picks up dishes added or corrected in the library since the last sync; no app update needed.
    func syncLibrary() async {
        guard !Demo.active else { return }
        guard let update = await API.shared.library(since: libraryVersion), update.version > libraryVersion else { return }
        var merged = libraryVersion == 0 ? [] : library
        for dish in update.dishes {
            if let index = merged.firstIndex(where: { $0.id == dish.id }) { merged[index] = dish.food } else { merged.append(dish.food) }
        }
        merged.removeAll { update.removed.contains($0.id) }
        guard !merged.isEmpty else { return }
        library = merged
        libraryVersion = update.version
        if let encoded = try? JSONEncoder().encode(LibraryCache(version: update.version, foods: merged)) {
            try? encoded.write(to: folder.appendingPathComponent("library.json"), options: .atomic)
        }
    }

    // MARK: Weight, workouts, adaptive target

    func logWeight(_ kg: Double) {
        let key = dayKey(Date())
        update { d in
            var weights = d.weights ?? []
            weights.removeAll { $0.date == key }
            weights.append(WeightEntry(date: key, kg: kg))
            d.weights = weights.sorted { $0.date < $1.date }
            d.profile.weightKg = kg
        }
        say(L("Đã lưu \(formatQty(kg)) kg", "Saved \(formatQty(kg)) kg"))
    }

    func addExercise(name: String, emoji: String, met: Double, minutes: Int) {
        let kcal = Nutrition.exerciseKcal(met: met, weightKg: data.profile.weightKg, minutes: minutes)
        update { d in d.exercises = (d.exercises ?? []) + [ExerciseEntry(id: UUID(), date: dayKey(date), name: name, emoji: emoji, minutes: minutes, kcal: kcal)] }
        say("\(emoji) \(name) \(minutes) " + L("phút", "min") + " · −\(kcal) kcal")
    }

    func removeExercise(_ id: UUID) {
        update { d in d.exercises?.removeAll { $0.id == id } }
    }

    func applyAdaptive(_ delta: Int) {
        update { d in
            d.profile.kcalAdjust = d.profile.adjust + delta
            d.profile.kcalAdjustDate = dayKey(Date())
        }
        say(L("Đã điều chỉnh mục tiêu \(delta > 0 ? "+" : "")\(delta) kcal/ngày", "Target adjusted by \(delta > 0 ? "+" : "")\(delta) kcal/day"))
    }

    /// Logs a suggested meal in one go.
    func addPlanned(_ meal: MenuPlanner.PlannedMeal) {
        for item in meal.items { add(item.food, qty: item.qty, to: meal.meal, quiet: true) }
        say(L("Đã thêm \(meal.items.count) món vào bữa \(Meal.label(meal.meal).lowercased())", "Added \(meal.items.count) item(s) to \(Meal.label(meal.meal).lowercased())"))
    }

    /// The foods this person logs most at this meal, for the "your usual foods" row.
    func frequentFoods(for meal: String, limit: Int = 8) -> [Food] {
        var counts: [String: Int] = [:]
        for e in data.entries.suffix(400) where e.meal == meal { counts[e.foodId, default: 0] += 1 }
        let byId = Dictionary(foods.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        return counts.filter { $0.value >= 2 }.sorted { $0.value > $1.value }.prefix(limit).compactMap { byId[$0.key] }
    }

    func course(of entry: LogEntry) -> String {
        foods.first { $0.id == entry.foodId }?.course ?? "main"
    }

    // MARK: Apple Health

    func linkHealth() async {
        guard HealthService.shared.available else { say(L("Thiết bị này không có Apple Health", "This device doesn't have Apple Health")); return }
        guard await HealthService.shared.requestAccess() else { say(L("Chưa kết nối được Apple Health", "Couldn't connect to Apple Health")); return }
        healthLinked = true
        UserDefaults.standard.set(true, forKey: "healthLinked")
        await refreshHealth()
        say(L("Đã kết nối Apple Health", "Apple Health connected"))
    }

    func unlinkHealth() {
        healthLinked = false
        activity = nil
        UserDefaults.standard.set(false, forKey: "healthLinked")
    }

    /// Reads the selected day's activity, and takes a newer weight or height from Health into the profile.
    func refreshHealth() async {
        guard healthLinked, !Demo.active else { return }
        activity = await HealthService.shared.activity(on: date)
        history = await HealthService.shared.history(days: 30)
        Nutrition.history = history
        let body = await HealthService.shared.latestBody()
        var profile = data.profile
        if let kg = body.weightKg, (25...300).contains(kg) { profile.weightKg = (kg * 10).rounded() / 10 }
        if let cm = body.heightCm, (100...230).contains(cm) { profile.heightCm = cm.rounded() }
        if profile != data.profile { update { $0.profile = profile } }
    }

    // MARK: Diary

    var todaysEntries: [LogEntry] {
        let key = dayKey(date)
        return data.entries.filter { $0.date == key }
    }

    func totals(_ entries: [LogEntry]) -> (kcal: Double, protein: Double, carbs: Double, fat: Double) {
        entries.reduce((0.0, 0.0, 0.0, 0.0)) { sum, e in (sum.0 + e.totalKcal, sum.1 + e.protein * e.qty, sum.2 + e.carbs * e.qty, sum.3 + e.fat * e.qty) }
    }

    /// Tapping the same food again in the same meal raises its quantity instead of adding a new line.
    func add(_ food: Food, qty: Double = 1, to meal: String? = nil, quiet: Bool = false) {
        let key = dayKey(date)
        let target = meal ?? self.meal
        update { d in
            if let index = d.entries.firstIndex(where: { $0.date == key && $0.meal == target && $0.foodId == food.id }) {
                d.entries[index].qty += qty
            } else {
                d.entries.append(LogEntry(id: UUID(), date: key, meal: target, foodId: food.id, name: food.name, emoji: food.emoji, serving: food.serving,
                                          kcal: food.kcal, protein: food.protein, carbs: food.carbs, fat: food.fat, tags: food.tags, qty: qty))
            }
        }
        if !quiet {
            let warning = Nutrition.warnings(tags: food.tags, profile: data.profile).first.map { "\n⚠️ \($0.text)" } ?? ""
            say("+\(formatQty(qty)) \(food.label) · \(Int((Double(food.kcal) * qty).rounded())) kcal\(warning)")
        }
    }

    /// A tap on a food: added at once, unless it contains something the user is allergic to.
    func tap(_ food: Food) {
        if Nutrition.warnings(tags: food.tags, profile: data.profile).contains(where: { $0.danger }) { pendingDanger = food } else { add(food) }
    }

    /// The user's own dish: deleted. A built-in dish they edited: back to the original.
    func removeCustom(_ food: Food) {
        update { d in d.customFoods.removeAll { $0.id == food.id } }
        say(library.contains { $0.id == food.id } ? L("Đã khôi phục món gốc", "Original restored") : L("Đã xóa \(food.label)", "Deleted \(food.label)"))
    }

    func isCustom(_ food: Food) -> Bool { data.customFoods.contains { $0.id == food.id } }

    /// Remembers a photo correction for this user: next time the AI says this name, the chosen food is used, and
    /// the amount follows what they usually eat.
    func learn(aiName: String, aiQty: Double, foodId: String?, amount: Double) {
        let key = aiName.lowercased().trimmingCharacters(in: .whitespaces)
        update { d in
            if let foodId { var fixes = d.nameFixes ?? [:]; fixes[key] = foodId; d.nameFixes = fixes }
            if aiQty > 0 {
                let ratio = min(3, max(0.5, amount / aiQty))
                var bias = d.portionBias ?? [:]
                // A running average, so one unusual meal does not decide the next estimate.
                bias[key] = ((bias[key] ?? 1) * 2 + ratio) / 3
                if abs(bias[key]! - 1) < 0.05 { bias[key] = nil }
                d.portionBias = bias
            }
        }
    }

    func changeQty(_ entry: LogEntry, by delta: Double) {
        update { d in
            guard let index = d.entries.firstIndex(where: { $0.id == entry.id }) else { return }
            let next = d.entries[index].qty + delta
            if next <= 0.01 { d.entries.remove(at: index) } else { d.entries[index].qty = next }
        }
    }

    /// Saves a dish the user created and offers it to the shared library (name and numbers only).
    func saveCustom(_ food: Food) {
        update { d in
            d.customFoods.removeAll { $0.id == food.id }
            d.customFoods.append(food)
        }
        if !library.contains(where: { $0.name.lowercased() == food.name.lowercased() }) {
            Task { await API.shared.submitDish(food) }
        }
    }

    func wipe() {
        data = AppData()
        save()
    }

    // MARK: Context for the AI

    var profileSummary: String {
        let p = data.profile
        var parts = [
            "\(p.male ? "male" : "female"), \(Nutrition.age(p)) years, \(Int(p.heightCm)) cm, \(p.weightKg) kg",
            "goal: \(Goal.label(p.goal))", "daily target \(Nutrition.targetKcal(p)) kcal",
        ]
        if !p.conditions.isEmpty { parts.append("conditions: " + HealthTags.conditions.filter { p.conditions.contains($0.id) }.map { $0.label }.joined(separator: ", ")) }
        if !p.allergies.isEmpty { parts.append("allergies: " + HealthTags.allergens.filter { p.allergies.contains($0.id) }.map { $0.label }.joined(separator: ", ")) }
        if p.diet != Diet.none { parts.append("diet: \(p.diet)") }
        return parts.joined(separator: "; ")
    }

    /// Who is eating, which meal it is, and what this person usually eats: helps tell look-alike dishes apart.
    var photoContext: String {
        var counts: [String: Int] = [:]
        for entry in data.entries.suffix(300) { counts[entry.label, default: 0] += 1 }
        let usual = counts.filter { $0.value >= 2 }.sorted { $0.value > $1.value }.prefix(10).map { $0.key }.joined(separator: ", ")
        return profileSummary + "; meal: \(Meal.label(meal))" + (usual.isEmpty ? "" : "; often eats: \(usual)")
    }

    /// The user's numbers in few tokens: sent with every chat message.
    var coachContext: String {
        let p = data.profile
        let macros = Nutrition.macroTargets(p)
        var lines = [
            "User: \(p.name.isEmpty ? "(no name)" : p.name), " + (Lang.en ? "prefers international / Western food" : "lives in Việt Nam") + " (suggest dishes they can easily find).",
            "Profile: \(profileSummary); BMI \(String(format: "%.1f", Nutrition.bmi(p))).",
            "Daily macro targets: P \(macros.protein)g, C \(macros.carbs)g, F \(macros.fat)g.",
            "Last 7 days (date: kcal eaten, protein/carbs/fat g); days with nothing logged are omitted:",
        ]
        for back in stride(from: 6, through: 0, by: -1) {
            guard let day = Calendar.current.date(byAdding: .day, value: -back, to: Date()) else { continue }
            let key = dayKey(day)
            let t = totals(data.entries.filter { $0.date == key })
            if t.kcal > 0 { lines.append("\(key.suffix(5)): \(Int(t.kcal)), \(Int(t.protein))/\(Int(t.carbs))/\(Int(t.fat))") }
        }
        let today = data.entries.filter { $0.date == dayKey(Date()) }
        let eaten = Int(totals(today).kcal)
        lines.append("Today so far: \(eaten) kcal eaten, \(Nutrition.targetKcal(p) - eaten) kcal left of the target.")
        lines.append("Eaten today: " + (today.isEmpty ? "nothing logged yet" : today.map { "\($0.label) x\(formatQty($0.qty)) (\(Meal.label($0.meal)))" }.joined(separator: ", ")))
        return lines.joined(separator: "\n")
    }
}

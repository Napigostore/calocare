import Foundation

struct Macros {
    var protein: Int
    var carbs: Int
    var fat: Int
}

struct HealthWarning: Hashable {
    var danger: Bool
    var text: String
}

/// Same formulas as the Android app, so both platforms give one person the same targets.
enum Nutrition {
    static func age(_ p: Profile) -> Int {
        min(100, max(10, Calendar.current.component(.year, from: Date()) - p.birthYear))
    }

    /// Mifflin-St Jeor.
    static func bmr(_ p: Profile) -> Double {
        10 * p.weightKg + 6.25 * p.heightCm - 5 * Double(age(p)) + (p.male ? 5 : -161)
    }

    static func tdee(_ p: Profile) -> Double { bmr(p) * p.activity }

    /// Energy out for a day, as on Android: BMR plus the watch's active energy, or plus a light
    /// daily-living allowance (20% of BMR) when there is no watch data.
    static func kcalOut(_ p: Profile, _ activity: DayActivity?) -> (total: Int, active: Int, measured: Bool) {
        let base = bmr(p)
        let active: Double
        if let a = activity, a.activeKcal > 0 { active = a.activeKcal }
        else if let a = activity, a.steps > 0 { active = Double(a.steps) * 0.04 * p.weightKg / 70 }
        else { return (Int((base * 1.2).rounded()), Int((base * 0.2).rounded()), false) }
        return (Int((base + active).rounded()), Int(active.rounded()), true)
    }

    static func bmi(_ p: Profile) -> Double {
        let h = p.heightCm / 100
        return h > 0 ? p.weightKg / (h * h) : 0
    }

    static func targetKcal(_ p: Profile) -> Int {
        let perDay = p.weeklyRateKg * 7700 / 7
        var raw = tdee(p)
        switch p.goal {
        case Goal.lose: raw -= min(perDay, 1000)
        case Goal.gain: raw += min(perDay, 700)
        case Goal.gym: raw += 250
        default: break
        }
        // Never recommend a crash diet: below these floors micronutrient needs are hard to meet.
        return Int(max(raw, p.male ? 1500 : 1200).rounded())
    }

    static func macroTargets(_ p: Profile) -> Macros {
        let kcal = Double(targetKcal(p))
        func split(_ pr: Double, _ c: Double, _ f: Double) -> Macros {
            Macros(protein: Int((kcal * pr / 4).rounded()), carbs: Int((kcal * c / 4).rounded()), fat: Int((kcal * f / 9).rounded()))
        }
        if p.diet == "keto" { return split(0.25, 0.08, 0.67) }
        switch p.goal {
        case Goal.lose: return split(0.30, 0.40, 0.30)
        case Goal.gain: return split(0.20, 0.55, 0.25)
        case Goal.run: return split(0.18, 0.60, 0.22)
        case Goal.gym:
            let protein = Int((2 * p.weightKg).rounded())
            let fat = Int((kcal * 0.25 / 9).rounded())
            return Macros(protein: protein, carbs: max(0, (Int(kcal) - protein * 4 - fat * 9) / 4), fat: fat)
        default: return split(0.20, 0.50, 0.30)
        }
    }

    /// Conflicts between what a food contains and what this person declared.
    static func warnings(tags: [String], profile p: Profile) -> [HealthWarning] {
        var out: [HealthWarning] = []
        for allergen in HealthTags.allergens where tags.contains(allergen.id) && p.allergies.contains(allergen.id) {
            out.append(HealthWarning(danger: true, text: L("Chứa \(allergen.label.lowercased()) — bạn đã khai dị ứng", "Contains \(allergen.label.lowercased()) — you declared an allergy")))
        }
        if p.conditions.contains("diabetes") {
            if tags.contains("high_sugar") { out.append(HealthWarning(danger: false, text: L("Nhiều đường — dễ làm tăng đường huyết", "High in sugar — can raise blood sugar"))) }
            else if tags.contains("high_gi") { out.append(HealthWarning(danger: false, text: L("Chỉ số đường huyết cao — ăn ít và ăn sau rau, đạm", "High glycemic index — eat a little, after vegetables and protein"))) }
        }
        if p.conditions.contains("hypertension") && tags.contains("high_sodium") {
            out.append(HealthWarning(danger: false, text: L("Nhiều muối — không tốt cho huyết áp", "High in salt — bad for blood pressure")))
        }
        if p.conditions.contains("gout") && (tags.contains("high_purine") || tags.contains("alcohol")) {
            out.append(HealthWarning(danger: false, text: L("Nhiều purin hoặc rượu bia — có thể gây cơn gout", "High in purines or alcohol — may trigger gout")))
        }
        if p.conditions.contains("cholesterol") && (tags.contains("high_satfat") || tags.contains("fried")) {
            out.append(HealthWarning(danger: false, text: L("Nhiều chất béo bão hòa / chiên rán — hạn chế khi mỡ máu cao", "Saturated fat / fried — limit with high cholesterol")))
        }
        let animal = ["meat", "fish", "shellfish", "pork"]
        switch p.diet {
        case "vegetarian":
            if tags.contains(where: { animal.contains($0) }) { out.append(HealthWarning(danger: false, text: L("Có thịt / cá — không hợp chế độ ăn chay", "Contains meat / fish — not vegetarian"))) }
        case "vegan":
            if tags.contains(where: { (animal + ["animal", "milk", "egg"]).contains($0) }) { out.append(HealthWarning(danger: false, text: L("Có nguồn gốc động vật — không hợp thuần chay", "Animal-based — not vegan"))) }
        case "keto":
            if tags.contains(where: { ["high_carb", "high_sugar", "high_gi"].contains($0) }) { out.append(HealthWarning(danger: false, text: L("Nhiều tinh bột / đường — vượt ngưỡng keto", "High in carbs / sugar — over the keto limit"))) }
        case "halal":
            if tags.contains("pork") || tags.contains("alcohol") { out.append(HealthWarning(danger: false, text: L("Có thịt heo / rượu — không Halal", "Contains pork / alcohol — not halal"))) }
        default: break
        }
        return out
    }
}

/// Matches a dish name written by the AI or typed by the user to a food the app knows.
enum FoodMatcher {
    /// Lower-case, without Vietnamese diacritics or punctuation: "Phở Bò!" → "pho bo".
    static func fold(_ s: String) -> String {
        let plain = s.lowercased().replacingOccurrences(of: "đ", with: "d").folding(options: .diacriticInsensitive, locale: Locale(identifier: "vi"))
        let parts = plain.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
        return parts.joined(separator: " ")
    }

    private static func similarity(_ query: String, _ name: String) -> Double {
        if query == name { return 1 }
        let short = query.count <= name.count ? query : name
        let long = query.count <= name.count ? name : query
        if " \(long) ".contains(" \(short) ") { return 0.7 + 0.3 * Double(short.count) / Double(long.count) }
        let q = Set(query.split(separator: " ")), n = Set(name.split(separator: " "))
        let shared = q.intersection(n).count
        return Double(shared) / Double(q.count + n.count - shared)
    }

    /// Foods that share anything with the query, best match first.
    static func ranked(_ query: String, in foods: [Food]) -> [(food: Food, score: Double)] {
        // Both names count, so a dish is found by its Vietnamese or its English name.
        let q = fold(query)
        if q.isEmpty { return [] }
        return foods.map { (food: $0, score: max(similarity(q, fold($0.name)), similarity(q, fold($0.label)))) }.filter { $0.score > 0 }.sorted { $0.score > $1.score }
    }

    /// The food this name most likely means, or nil when nothing is close enough to trust.
    static func best(_ query: String, in foods: [Food]) -> Food? {
        guard let top = ranked(query, in: foods).first, top.score >= 0.6 else { return nil }
        return top.food
    }
}

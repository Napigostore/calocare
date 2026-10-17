import Foundation

/// Packaged foods from Open Food Facts (ODbL data, CC BY-SA photos — credited in the app), as on Android.
enum FoodSearch {
    private static let allergens: [String: String] = [
        "en:peanuts": "peanut", "en:nuts": "nuts", "en:milk": "milk", "en:eggs": "egg", "en:gluten": "gluten", "en:soybeans": "soy",
        "en:fish": "fish", "en:crustaceans": "shellfish", "en:molluscs": "shellfish", "en:sesame-seeds": "sesame",
    ]

    static func search(_ query: String) async throws -> [Food] {
        var url = URLComponents(string: "https://search.openfoodfacts.org/search")!
        url.queryItems = [
            URLQueryItem(name: "q", value: query), URLQueryItem(name: "page_size", value: "24"), URLQueryItem(name: "langs", value: "vi,en"),
            URLQueryItem(name: "fields", value: "code,product_name,product_name_vi,product_name_en,brands,nutriments,serving_size,allergens_tags"),
        ]
        var request = URLRequest(url: url.url!, timeoutInterval: 25)
        request.setValue("CaloCare/1.0 (iOS; personal calorie tracker)", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200,
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any], let hits = root["hits"] as? [[String: Any]] else { return [] }

        func str(_ p: [String: Any], _ key: String) -> String? { (p[key] as? String).flatMap { $0.isEmpty ? nil : $0 } }
        func num(_ n: [String: Any], _ key: String) -> Double? { (n[key] as? NSNumber)?.doubleValue ?? (n[key] as? String).flatMap(Double.init) }

        var seen = Set<String>()
        return hits.compactMap { p -> Food? in
            let name = (Lang.en ? str(p, "product_name_en") : str(p, "product_name_vi")) ?? str(p, "product_name")
            guard let name, let n = p["nutriments"] as? [String: Any] else { return nil }
            // Per serving when the label gives it, otherwise per 100 g/ml.
            let perServing = num(n, "energy-kcal_serving").flatMap { $0 > 0 ? $0 : nil }
            let suffix = perServing != nil ? "_serving" : "_100g"
            guard let kcal = perServing ?? num(n, "energy-kcal_100g"), kcal > 0 else { return nil }
            var tags = Set((p["allergens_tags"] as? [String] ?? []).compactMap { allergens[$0] })
            if (num(n, "sugars_100g") ?? 0) >= 10 { tags.insert("high_sugar") }
            if (num(n, "salt_100g") ?? 0) >= 1.5 { tags.insert("high_sodium") }
            if (num(n, "saturated-fat_100g") ?? 0) >= 5 { tags.insert("high_satfat") }
            if (num(n, "carbohydrates_100g") ?? 0) >= 45 { tags.insert("high_carb") }
            // A list in the current API, a comma-separated string in older records.
            let brand = (p["brands"] as? [String])?.first ?? str(p, "brands")?.components(separatedBy: ",").first
            let full = brand.map { name.localizedCaseInsensitiveContains($0) ? name : "\(name) · \($0)" } ?? name
            guard seen.insert(full.lowercased()).inserted else { return nil }
            return Food(id: "off_" + (str(p, "code") ?? String(full.hashValue)), name: full, emoji: "🛒",
                        serving: perServing != nil ? (str(p, "serving_size") ?? L("1 phần", "1 serving")) : "100g", kcal: Int(kcal.rounded()),
                        protein: num(n, "proteins" + suffix) ?? 0, carbs: num(n, "carbohydrates" + suffix) ?? 0, fat: num(n, "fat" + suffix) ?? 0,
                        course: "snack", tags: Array(tags), region: "all", custom: true)
        }
    }
}

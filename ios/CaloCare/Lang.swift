import Foundation

/// The app speaks Vietnamese on phones set to Vietnamese and English everywhere else (English is the
/// development language, so any other language falls back to it). iOS also lets the user pick the app's
/// language in Settings → CaloCare → Language.
enum Lang {
    static let en: Bool = !(Bundle.main.preferredLocalizations.first ?? "en").hasPrefix("vi")

    /// The language AI answers should be written in, sent to the server with every request.
    static var code: String { en ? "en" : "vi" }
}

/// The text in the user's language: `vi` on Vietnamese phones, `en` elsewhere.
func L(_ vi: String, _ en: String) -> String { Lang.en ? en : vi }

/// English name and serving of every built-in dish, by id, from the table shipped in the app. The library
/// synchronised from the server keeps the same ids; dishes users add keep the name they were given.
enum FoodNames {
    private struct Row: Decodable {
        var id: String
        var en: String?
        var servingEn: String?
    }

    static let en: [String: (name: String, serving: String)] = {
        guard let url = Bundle.main.url(forResource: "seed", withExtension: "json"), let data = try? Data(contentsOf: url),
              let rows = try? JSONDecoder().decode([Row].self, from: data) else { return [:] }
        var map: [String: (name: String, serving: String)] = [:]
        for row in rows { if let name = row.en { map[row.id] = (name, row.servingEn ?? "") } }
        return map
    }()
}

extension Food {
    var label: String { Lang.en ? FoodNames.en[id]?.name ?? name : name }
    var servingLabel: String { Lang.en ? FoodNames.en[id].map { $0.serving.isEmpty ? serving : $0.serving } ?? serving : serving }
}

extension LogEntry {
    var label: String { Lang.en ? FoodNames.en[foodId]?.name ?? name : name }
    var servingLabel: String { Lang.en ? FoodNames.en[foodId].map { $0.serving.isEmpty ? serving : $0.serving } ?? serving : serving }
}

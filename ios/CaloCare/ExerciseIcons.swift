import Foundation

/// The activity artwork, by position in `Nutrition.activities`. Activities without artwork keep their emoji.
enum ExerciseIcons {
    private static let names: [Int: String] = [
        0: "activity_hiking",        // walking
        1: "activity_running",
        2: "activity_cycling",
        3: "activity_swimming",
        4: "activity_weightlifting",
        6: "activity_yoga",
        7: "activity_badminton",
        8: "activity_basketball",    // football
    ]

    /// Activities without artwork get a gold system symbol instead of an emoji.
    private static let symbols: [Int: String] = [
        5: "bolt.fill",              // HIIT
        9: "figure.jumprope",
        10: "figure.stairs",
        11: "house.fill",            // housework
    ]

    static func image(for index: Int) -> String? { names[index] }

    static func symbol(for index: Int) -> String? { symbols[index] }
}

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

    static func image(for index: Int) -> String? { names[index] }
}

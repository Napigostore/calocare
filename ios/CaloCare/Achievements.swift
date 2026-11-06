import SwiftUI

/// One achievement: a gold symbol, its value, and what it counts.
struct AchievementBadge: View {
    var symbol: String
    var value: String
    var label: String

    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: symbol)
                .font(.title3)
                .foregroundColor(Palette.gold)
                .frame(width: 44, height: 44)
                .background(Palette.paper, in: Circle())
            Text(value).font(.headline).monospacedDigit()
            Text(label).font(.caption2).foregroundColor(.secondary).multilineTextAlignment(.center).lineLimit(2)
        }
        .frame(maxWidth: .infinity)
    }
}

/// A suggestion on the Coach screen: a photo with a short line on top of it.
struct SuggestionCard: View {
    var image: String
    var title: String
    var detail: String

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Image(image).resizable().scaledToFill().frame(height: 150).clipped()
            LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .center, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.weight(.semibold)).foregroundColor(.white)
                Text(detail).font(.caption).foregroundColor(.white.opacity(0.8))
            }
            .padding(12)
        }
        .frame(height: 150)
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

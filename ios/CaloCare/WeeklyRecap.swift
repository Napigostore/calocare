import SwiftUI

/// A card that sums up the last seven days. It is drawn as a picture so it can be shared.
struct WeeklyRecapCard: View {
    var data: AppData

    var body: some View {
        let days = (0..<7).reversed().map { Nutrition.day($0) }
        let logged = days.map { Nutrition.totals(Nutrition.entries(data, on: $0)).kcal }.filter { $0 > 0 }
        let average = logged.isEmpty ? 0 : Int(logged.reduce(0, +) / Double(logged.count))
        let hits = days.filter { Nutrition.onTarget(data, on: $0) }.count
        let favourites = Dictionary(grouping: days.flatMap { Nutrition.entries(data, on: $0) }, by: { $0.label })
            .mapValues { $0.count }
            .sorted { $0.value > $1.value }
            .prefix(3)
            .map { $0.key }

        VStack(alignment: .leading, spacing: 14) {
            Text("CALOGOLD").font(.caption.weight(.bold)).tracking(3).foregroundColor(Palette.gold)
            Text(L("Tuần này của bạn", "Your week")).font(.system(size: 30, weight: .semibold, design: .serif))
            Rectangle().fill(Palette.gold).frame(width: 56, height: 2)
            row(L("Calo trung bình", "Average calories"), "\(average) kcal/" + L("ngày", "day"))
            row(L("Ngày đạt mục tiêu", "Days on target"), "\(hits)/7")
            row(L("Chuỗi ghi nhật ký", "Logging streak"), "\(Nutrition.streak(data)) " + L("ngày", "days"))
            if !favourites.isEmpty {
                Text(L("Món hay ăn", "Most eaten")).font(.caption).foregroundColor(.secondary)
                Text(favourites.joined(separator: " · ")).font(.system(.body, design: .serif))
            }
        }
        .padding(22)
        .frame(width: 340, alignment: .leading)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(Palette.gold.opacity(0.5), lineWidth: 1))
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.subheadline).foregroundColor(.secondary)
            Spacer()
            Text(value).font(.subheadline.weight(.semibold)).monospacedDigit()
        }
    }

    /// The card as a picture, ready to share. Nil if it could not be drawn.
    @MainActor static func image(for data: AppData) -> Image? {
        let renderer = ImageRenderer(content: WeeklyRecapCard(data: data).padding(16).background(Palette.paper))
        renderer.scale = 3
        guard let picture = renderer.uiImage else { return nil }
        return Image(uiImage: picture)
    }
}

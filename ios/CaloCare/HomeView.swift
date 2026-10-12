import SwiftUI

struct HomeView: View {
    @EnvironmentObject var store: AppStore

    var body: some View {
        let profile = store.data.profile
        let entries = store.todaysEntries
        let eaten = store.totals(entries)
        let target = Double(Nutrition.targetKcal(profile))
        let macros = Nutrition.macroTargets(profile)

        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    DayPicker()

                    Card {
                        HStack(spacing: 20) {
                            CalorieRing(eaten: eaten.kcal, target: target).frame(width: 150, height: 150)
                            VStack(alignment: .leading, spacing: 10) {
                                Stat(label: "Nạp vào", value: Int(eaten.kcal), color: Palette.green)
                                Stat(label: "Mục tiêu", value: Int(target), color: .secondary)
                                Stat(label: "Trao đổi chất cơ bản", value: Int(Nutrition.bmr(profile)), color: Palette.orange)
                            }
                        }
                    }

                    Card("Dinh dưỡng đa lượng") {
                        MacroBar(label: "Đạm", value: eaten.protein, target: macros.protein, color: Palette.protein)
                        MacroBar(label: "Tinh bột", value: eaten.carbs, target: macros.carbs, color: Palette.carbs)
                        MacroBar(label: "Chất béo", value: eaten.fat, target: macros.fat, color: Palette.fat)
                    }

                    Card("7 ngày gần đây") { WeekBars(target: target) }

                    let flagged = entries.flatMap { e in Nutrition.warnings(tags: e.tags, profile: profile).map { HealthWarning(danger: $0.danger, text: "\(e.name): \($0.text)") } }
                    if !flagged.isEmpty {
                        Card("🩺 Lưu ý sức khỏe hôm nay") {
                            ForEach(Array(Set(flagged)).prefix(5), id: \.self) { WarningLine(warning: $0) }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(profile.name.isEmpty ? "Xin chào 👋" : "Xin chào, \(profile.name) 👋")
        }
    }
}

struct DayPicker: View {
    @EnvironmentObject var store: AppStore

    private var label: String {
        if Calendar.current.isDateInToday(store.date) { return "Hôm nay" }
        if Calendar.current.isDateInYesterday(store.date) { return "Hôm qua" }
        let f = DateFormatter()
        f.locale = Locale(identifier: "vi")
        f.dateFormat = "EEEE, d/M"
        return f.string(from: store.date).capitalized
    }

    var body: some View {
        HStack {
            Button { shift(-1) } label: { Image(systemName: "chevron.left") }
            Text(label).font(.headline).frame(maxWidth: .infinity)
            Button { shift(1) } label: { Image(systemName: "chevron.right") }
                .disabled(Calendar.current.isDateInToday(store.date))
        }
        .padding(.horizontal, 8)
    }

    private func shift(_ days: Int) {
        if let next = Calendar.current.date(byAdding: .day, value: days, to: store.date) { store.date = next }
    }
}

struct CalorieRing: View {
    var eaten: Double
    var target: Double

    var body: some View {
        let ratio = target > 0 ? eaten / target : 0
        let left = Int((target - eaten).rounded())
        ZStack {
            Circle().stroke(Color(.systemGray5), lineWidth: 14)
            Circle().trim(from: 0, to: min(ratio, 1))
                .stroke(Palette.green, style: StrokeStyle(lineWidth: 14, lineCap: .round))
                .rotationEffect(.degrees(-90))
            // A second, red lap shows how far past the target the day went.
            if ratio > 1 {
                Circle().trim(from: 0, to: min(ratio - 1, 1))
                    .stroke(Color.red, style: StrokeStyle(lineWidth: 14, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
            VStack(spacing: 2) {
                Text("\(abs(left))").font(.system(size: 30, weight: .bold)).foregroundColor(left < 0 ? .red : .primary)
                Text(left >= 0 ? "kcal còn lại" : "kcal vượt").font(.caption).foregroundColor(.secondary)
            }
        }
        .padding(8)
        .animation(.easeOut(duration: 0.6), value: eaten)
    }
}

private struct Stat: View {
    var label: String
    var value: Int
    var color: Color

    var body: some View {
        HStack(spacing: 8) {
            Circle().fill(color).frame(width: 10, height: 10)
            VStack(alignment: .leading, spacing: 0) {
                Text(label).font(.caption).foregroundColor(.secondary)
                Text("\(value) kcal").font(.headline)
            }
        }
    }
}

struct MacroBar: View {
    var label: String
    var value: Double
    var target: Int
    var color: Color

    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(label).font(.subheadline)
                Spacer()
                Text("\(Int(value.rounded())) / \(target) g").font(.subheadline.weight(.medium))
            }
            ProgressView(value: target > 0 ? min(value / Double(target), 1) : 0).tint(color)
        }
    }
}

/// Calories eaten on each of the last seven days against the target.
private struct WeekBars: View {
    @EnvironmentObject var store: AppStore
    var target: Double

    var body: some View {
        let days: [Date] = (0..<7).reversed().compactMap { Calendar.current.date(byAdding: .day, value: -$0, to: Date()) }
        let values: [Double] = days.map { day in
            let key = dayKey(day)
            return store.totals(store.data.entries.filter { $0.date == key }).kcal
        }
        let top = max(values.max() ?? 0, target, 1) * 1.1
        let names = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"]

        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(days.indices, id: \.self) { i in
                    VStack(spacing: 4) {
                        RoundedRectangle(cornerRadius: 5)
                            .fill(values[i] > target * 1.05 ? Palette.orange : Palette.green)
                            .frame(height: max(2, CGFloat(values[i] / top) * 110))
                        Text(names[Calendar.current.component(.weekday, from: days[i]) - 1]).font(.caption2)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 130, alignment: .bottom)
            let logged = values.filter { $0 > 0 }
            if logged.isEmpty {
                Text("Ghi nhật ký vài ngày để thấy xu hướng của bạn.").font(.caption).foregroundColor(.secondary)
            } else {
                Text("Trung bình \(Int(logged.reduce(0, +) / Double(logged.count))) kcal/ngày · mục tiêu \(Int(target))").font(.caption).foregroundColor(.secondary)
            }
        }
    }
}

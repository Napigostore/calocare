import SwiftUI

struct HomeView: View {
    @EnvironmentObject var store: AppStore

    var body: some View {
        let profile = store.data.profile
        let entries = store.todaysEntries
        let eaten = store.totals(entries)
        let target = Double(Nutrition.targetKcal(profile))
        let macros = Nutrition.macroTargets(profile)
        let full = Nutrition.out(store.data, on: store.date, activity: store.activity)
        let out = (total: full.total, active: full.active, measured: full.measured)
        let cheer = Nutrition.cheer(store.data, on: store.date)
        let streak = Nutrition.streak(store.data)

        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    DayPicker()

                    // Encouragement and the logging streak, as on Android.
                    HStack(alignment: .top, spacing: 10) {
                        Text(cheer.emoji).font(.title)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(cheer.text).font(.subheadline)
                            if streak >= 2 { Text(L("🔥 Chuỗi \(streak) ngày ghi nhật ký", "🔥 \(streak)-day logging streak")).font(.caption).foregroundColor(.secondary) }
                            if entries.isEmpty { Button(L("Thêm món", "Add food")) { store.tab = 1 }.buttonStyle(.borderedProminent).controlSize(.small) }
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(14)
                    .background(Palette.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 20))

                    Card {
                        HStack(spacing: 20) {
                            CalorieRing(eaten: eaten.kcal, target: target).frame(width: 150, height: 150)
                            VStack(alignment: .leading, spacing: 10) {
                                Stat(label: L("Nạp vào", "Eaten"), value: Int(eaten.kcal), color: Palette.green)
                                Stat(label: L("Mục tiêu", "Target"), value: Int(target), color: .secondary)
                                Stat(label: out.measured ? L("Tiêu hao (đồng hồ)", "Burned (watch)") : L("Tiêu hao ước tính", "Burned (estimate)"), value: out.total, color: Palette.orange)
                            }
                        }
                    }

                    ActivityCard(eaten: eaten.kcal, out: out)

                    Card(L("Dinh dưỡng đa lượng", "Macronutrients")) {
                        MacroBar(label: L("Đạm", "Protein"), value: eaten.protein, target: macros.protein, color: Palette.protein)
                        MacroBar(label: L("Tinh bột", "Carbs"), value: eaten.carbs, target: macros.carbs, color: Palette.carbs)
                        MacroBar(label: L("Chất béo", "Fat"), value: eaten.fat, target: macros.fat, color: Palette.fat)
                    }

                    if Calendar.current.isDateInToday(store.date) { NextMealCard() }

                    Card(L("🩺 Chuyên gia khuyên", "🩺 Expert tips")) {
                        ForEach(Nutrition.tips(store.data, on: store.date, activity: store.activity).prefix(4), id: \.self) { Text("• " + $0).font(.subheadline) }
                        Button(L("Hỏi AI", "Ask AI")) { store.tab = 4 }.buttonStyle(.bordered)
                    }

                    Card(L("7 ngày gần đây", "Last 7 days")) { WeekBars(target: target) }

                    let flagged = entries.flatMap { e in Nutrition.warnings(tags: e.tags, profile: profile).map { HealthWarning(danger: $0.danger, text: "\(e.label): \($0.text)") } }
                    if !flagged.isEmpty {
                        Card(L("🩺 Lưu ý sức khỏe hôm nay", "🩺 Health notes today")) {
                            ForEach(Array(Set(flagged)).prefix(5), id: \.self) { WarningLine(warning: $0) }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .refreshable { await store.refreshHealth() }
            .task(id: dayKey(store.date)) { await store.refreshHealth() }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                Task { await store.refreshHealth() }
            }
            .navigationTitle(profile.name.isEmpty ? L("Xin chào 👋", "Hi 👋") : L("Xin chào, \(profile.name) 👋", "Hi, \(profile.name) 👋"))
            .toolbar { Button { store.showProfile = true } label: { Image(systemName: "person.crop.circle").font(.title3) }.accessibilityLabel(L("Hồ sơ", "Profile")) }
        }
    }
}

/// "What to eat next": the next meal not yet logged, sized from the calories left, with one-tap logging.
private struct NextMealCard: View {
    @EnvironmentObject var store: AppStore
    @State private var seed = 0

    var body: some View {
        let next = MenuPlanner.nextMeal(store.data, foods: store.foods, seed: seed + Calendar.current.component(.hour, from: Date()))
        Card(L("🍽️ Bữa tiếp theo nên ăn · ", "🍽️ What to eat next · ") + Meal.label(next.meal.meal)) {
            Text(next.note).font(.caption).foregroundColor(.secondary)
            ForEach(next.meal.items, id: \.self) { item in
                HStack {
                    Text("\(item.food.emoji) \(item.food.label) × \(formatQty(item.qty))").font(.subheadline)
                    Spacer()
                    Text("\(Int((Double(item.food.kcal) * item.qty).rounded())) kcal").font(.caption).foregroundColor(.secondary)
                }
            }
            HStack {
                if !next.meal.items.isEmpty {
                    Button(L("Tôi ăn bữa này · \(next.meal.kcal) kcal", "I'll eat this · \(next.meal.kcal) kcal")) { store.addPlanned(next.meal) }.buttonStyle(.borderedProminent)
                }
                Button(L("Đổi món", "Shuffle")) { seed += 1 }.buttonStyle(.bordered)
            }
        }
    }
}

/// Calories in against calories out, and what the watch recorded; offers to link Apple Health when it is not.
private struct ActivityCard: View {
    @EnvironmentObject var store: AppStore
    var eaten: Double
    var out: (total: Int, active: Int, measured: Bool)

    var body: some View {
        if store.healthLinked {
            let a = store.activity ?? DayActivity()
            let balance = Int(eaten.rounded()) - out.total
            Card(L("⌚ Vận động hôm nay", "⌚ Today's activity")) {
                HStack(spacing: 0) {
                    tile("🔥", "\(Int(a.activeKcal))", L("kcal vận động", "active kcal"))
                    tile("👟", a.steps.formatted(), L("bước", "steps"))
                    tile("⏱️", "\(a.exerciseMinutes)", L("phút tập", "exercise min"))
                    tile("😴", a.sleepHours.map { String(format: "%.1f", $0) } ?? "–", L("giờ ngủ", "hours slept"))
                }
                Text(balance <= 0 ? L("Cân bằng: thâm hụt \(-balance) kcal so với tiêu hao", "Balance: \(-balance) kcal deficit") : L("Cân bằng: dư \(balance) kcal so với tiêu hao", "Balance: \(balance) kcal surplus"))
                    .font(.subheadline.weight(.medium)).foregroundColor(balance <= 0 ? Palette.green : Palette.orange)
                if let sleep = a.sleepHours, sleep < 6 {
                    Text(L("Ngủ chưa đủ 6 tiếng: dễ thèm ngọt hơn. Ưu tiên đạm và rau, tập nhẹ hôm nay.", "Less than 6 hours of sleep makes sugar cravings stronger. Favour protein and vegetables, and train lightly today.")).font(.caption).foregroundColor(.secondary)
                }
            }
        } else {
            Card(L("⌚ Kết nối đồng hồ", "⌚ Connect a watch")) {
                Text(L("Đọc calo vận động, bước chân, giấc ngủ và cân nặng từ Apple Health. Apple Watch ghi trực tiếp; Garmin, Mi Fitness, Zepp, Huawei Health… đồng bộ vào Apple Health từ cài đặt của app đồng hồ.", "Reads active calories, steps, sleep and weight from Apple Health. Apple Watch records there directly; Garmin, Fitbit, Mi Fitness, Zepp, Huawei Health… can sync to Apple Health from their own app's settings."))
                    .font(.caption).foregroundColor(.secondary)
                Button { Task { await store.linkHealth() } } label: { Label(L("Kết nối Apple Health", "Connect Apple Health"), systemImage: "heart.fill") }
                    .buttonStyle(.borderedProminent)
            }
        }
    }

    private func tile(_ icon: String, _ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(icon)
            Text(value).font(.headline)
            Text(label).font(.caption2).foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

struct DayPicker: View {
    @EnvironmentObject var store: AppStore

    private var label: String {
        if Calendar.current.isDateInToday(store.date) { return L("Hôm nay", "Today") }
        if Calendar.current.isDateInYesterday(store.date) { return L("Hôm qua", "Yesterday") }
        let f = DateFormatter()
        f.locale = Lang.en ? Locale.current : Locale(identifier: "vi")
        f.dateFormat = Lang.en ? "EEEE, MMM d" : "EEEE, d/M"
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
                Text(left >= 0 ? L("kcal còn lại", "kcal left") : L("kcal vượt", "kcal over")).font(.caption).foregroundColor(.secondary)
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
        let names = Lang.en ? ["S", "M", "T", "W", "T", "F", "S"] : ["CN", "T2", "T3", "T4", "T5", "T6", "T7"]

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
                Text(L("Ghi nhật ký vài ngày để thấy xu hướng của bạn.", "Log a few days to see your trend.")).font(.caption).foregroundColor(.secondary)
            } else {
                Text(L("Trung bình \(Int(logged.reduce(0, +) / Double(logged.count))) kcal/ngày · mục tiêu \(Int(target))", "Average \(Int(logged.reduce(0, +) / Double(logged.count))) kcal/day · target \(Int(target))")).font(.caption).foregroundColor(.secondary)
            }
        }
    }
}

import Charts
import SwiftUI

/// Progress, as on Android: calories in and out over 7 or 30 days, weight, average macros, workouts,
/// the adaptive calorie target, weighing in and logging exercise.
struct ProgressScreen: View {
    @EnvironmentObject var store: AppStore
    @State private var range = 7
    @State private var weighing = false
    @State private var exercising = false

    private struct DayPoint: Identifiable {
        var id: String { key }
        var key: String
        var date: Date
        var eaten: Double
        var burned: Int
    }

    var body: some View {
        let data = store.data
        let p = data.profile
        let target = Double(Nutrition.targetKcal(p))
        let points: [DayPoint] = (0..<range).reversed().map { back in
            let day = Nutrition.day(back)
            return DayPoint(key: dayKey(day), date: day, eaten: Nutrition.totals(Nutrition.entries(data, on: day)).kcal,
                            burned: Nutrition.out(data, on: day, activity: store.activity).total)
        }
        let logged = points.filter { $0.eaten > 0 }

        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    Picker("", selection: $range) { Text(L("7 ngày", "7 days")).tag(7); Text(L("30 ngày", "30 days")).tag(30) }.pickerStyle(.segmented)

                    if let a = Nutrition.adaptive(data) {
                        Card(L("🧠 Mục tiêu calo thích nghi", "🧠 Adaptive calorie target")) {
                            Text(L("Trong \(a.days) ngày qua cân nặng của bạn thay đổi \(signed(a.actualPerWeek)) kg/tuần, trong khi kế hoạch là \(signed(a.plannedPerWeek)) kg/tuần.",
                                   "Over the last \(a.days) days your weight changed \(signed(a.actualPerWeek)) kg/week against a plan of \(signed(a.plannedPerWeek)) kg/week."))
                                .font(.subheadline)
                            Button(L("Điều chỉnh mục tiêu \(a.delta > 0 ? "+" : "")\(a.delta) kcal/ngày", "Adjust target by \(a.delta > 0 ? "+" : "")\(a.delta) kcal/day")) { store.applyAdaptive(a.delta) }
                                .buttonStyle(.borderedProminent)
                        }
                    }

                    Card(L("Calo nạp vào / tiêu hao", "Calories in / out")) {
                        Chart {
                            ForEach(points) { d in
                                BarMark(x: .value("Day", d.date, unit: .day), y: .value("kcal", d.eaten)).foregroundStyle(Palette.green)
                                LineMark(x: .value("Day", d.date, unit: .day), y: .value("Burned", d.burned)).foregroundStyle(Palette.orange)
                            }
                            RuleMark(y: .value("Target", target)).foregroundStyle(.secondary).lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                        }
                        .frame(height: 180)
                        HStack(spacing: 12) {
                            legend(L("Nạp vào", "Eaten"), Palette.green); legend(L("Tiêu hao", "Burned"), Palette.orange); legend(L("Mục tiêu", "Target"), .secondary)
                        }
                        if logged.isEmpty {
                            Text(L("Chưa có nhật ký trong khoảng này.", "Nothing logged in this period.")).font(.caption).foregroundColor(.secondary)
                        } else {
                            let avgIn = Int(logged.reduce(0) { $0 + $1.eaten } / Double(logged.count))
                            let avgOut = logged.reduce(0) { $0 + $1.burned } / logged.count
                            let balance = logged.reduce(0.0) { $0 + $1.eaten - Double($1.burned) }
                            let hits = points.filter { Nutrition.onTarget(data, on: $0.date) }.count
                            Text(L("Trung bình nạp \(avgIn) · tiêu \(avgOut) kcal/ngày", "Average in \(avgIn) · out \(avgOut) kcal/day")).font(.subheadline)
                            Text(L("\(logged.count)/\(range) ngày có ghi · \(balance <= 0 ? "thâm hụt" : "dư") \(Int(abs(balance))) kcal ≈ \(signed(balance / 7700)) kg · đạt mục tiêu \(hits) ngày",
                                   "\(logged.count)/\(range) days logged · \(balance <= 0 ? "deficit" : "surplus") \(Int(abs(balance))) kcal ≈ \(signed(balance / 7700)) kg · on target \(hits) days"))
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }

                    weightCard(data)

                    if !logged.isEmpty {
                        let n = Double(logged.count)
                        let sums = logged.reduce((0.0, 0.0, 0.0)) { s, d in
                            let t = Nutrition.totals(Nutrition.entries(data, on: d.date))
                            return (s.0 + t.protein, s.1 + t.carbs, s.2 + t.fat)
                        }
                        let macros = Nutrition.macroTargets(p)
                        Card(L("Đa lượng trung bình mỗi ngày", "Average daily macros")) {
                            MacroBar(label: L("Đạm", "Protein"), value: sums.0 / n, target: macros.protein, color: Palette.protein)
                            MacroBar(label: L("Tinh bột", "Carbs"), value: sums.1 / n, target: macros.carbs, color: Palette.carbs)
                            MacroBar(label: L("Chất béo", "Fat"), value: sums.2 / n, target: macros.fat, color: Palette.fat)
                        }
                    }

                    activityCard(data)
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(L("Tiến độ", "Progress"))
            .toolbar { Button(L("⚖️ Cân hôm nay", "⚖️ Weigh in")) { weighing = true } }
            .sheet(isPresented: $weighing) { WeighIn() }
            .sheet(isPresented: $exercising) { ExercisePicker() }
        }
    }

    private func weightCard(_ data: AppData) -> some View {
        let p = data.profile
        let weights = (data.weights ?? []).filter { $0.date >= dayKey(Nutrition.day(max(range, 30) - 1)) }
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        let series: [(date: Date, kg: Double)] = weights.compactMap { w in f.date(from: w.date).map { ($0, w.kg) } }
        return Card(L("Cân nặng", "Weight")) {
            if series.count >= 2 {
                Chart {
                    ForEach(series.indices, id: \.self) { i in
                        LineMark(x: .value("Day", series[i].date, unit: .day), y: .value("kg", series[i].kg)).foregroundStyle(Palette.fat)
                        PointMark(x: .value("Day", series[i].date, unit: .day), y: .value("kg", series[i].kg)).foregroundStyle(Palette.fat)
                    }
                    RuleMark(y: .value("Target", p.targetWeight)).foregroundStyle(Palette.green).lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                }
                .chartYScale(domain: .automatic(includesZero: false))
                .frame(height: 160)
            }
            let diff = p.targetWeight - p.weightKg
            Text(abs(diff) < 0.1 ? L("🏆 Đã chạm cân nặng mục tiêu!", "🏆 You've reached your target weight!")
                 : L("\(String(format: "%.1f", p.weightKg)) kg · còn \(String(format: "%.1f", abs(diff))) kg tới mục tiêu \(String(format: "%.1f", p.targetWeight)) kg",
                     "\(String(format: "%.1f", p.weightKg)) kg · \(String(format: "%.1f", abs(diff))) kg to go to your \(String(format: "%.1f", p.targetWeight)) kg target"))
                .font(.subheadline)
            Text("BMI \(String(format: "%.1f", Nutrition.bmi(p)))").font(.caption).foregroundColor(.secondary)
            if series.count < 2 {
                Text(L("Cân mỗi tuần vào cùng một buổi sáng để thấy đường xu hướng.", "Weigh yourself weekly on the same morning to see the trend line.")).font(.caption).foregroundColor(.secondary)
            }
        }
    }

    private func activityCard(_ data: AppData) -> some View {
        let today = Nutrition.exercises(data, on: store.date)
        let out = Nutrition.out(data, on: store.date, activity: store.activity)
        return Card(L("🔥 Calo tiêu hao: \(out.total) kcal", "🔥 Calories burned: \(out.total) kcal")) {
            row(L("Trao đổi chất cơ bản (BMR)", "Basal metabolism (BMR)"), out.bmr)
            row(out.measured ? L("Vận động (Apple Health ⌚)", "Activity (Apple Health ⌚)") : L("Sinh hoạt hằng ngày (ước tính)", "Daily living (estimate)"), out.active)
            if out.exercise > 0 { row(L("Tập luyện tự nhập", "Logged workouts"), out.exercise) }
            ForEach(today) { e in
                HStack {
                    Text("\(e.emoji) \(e.name) · \(e.minutes) " + L("phút", "min"))
                    Spacer()
                    Text("−\(e.kcal) kcal").foregroundColor(.secondary)
                    Button { store.removeExercise(e.id) } label: { Image(systemName: "xmark.circle.fill").foregroundColor(.secondary) }.buttonStyle(.plain)
                }
                .font(.subheadline)
            }
            Button { exercising = true } label: { Label(L("Thêm vận động", "Add activity"), systemImage: "plus") }.buttonStyle(.bordered)
            if store.healthLinked {
                Text(L("Lưu ý: nếu Apple Watch đã ghi buổi tập này thì không cần nhập tay, tránh tính hai lần.", "Note: if your Apple Watch already recorded this workout, don't add it by hand or it counts twice."))
                    .font(.caption).foregroundColor(Palette.warn)
            }
        }
    }

    private func row(_ label: String, _ value: Int) -> some View {
        HStack { Text(label).font(.subheadline); Spacer(); Text("\(value) kcal").font(.subheadline.weight(.medium)) }
    }

    private func legend(_ label: String, _ color: Color) -> some View {
        HStack(spacing: 4) { Circle().fill(color).frame(width: 8, height: 8); Text(label).font(.caption2) }
    }

    private func signed(_ v: Double) -> String { (v > 0 ? "+" : "") + String(format: "%.2f", v) }
}

private struct WeighIn: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var kg: Double = 0

    var body: some View {
        NavigationStack {
            Form {
                HStack {
                    Text(L("Cân nặng (kg)", "Weight (kg)"))
                    Spacer()
                    TextField("0", value: $kg, format: .number.grouping(.never)).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 100)
                }
            }
            .navigationTitle(L("Cân nặng hôm nay", "Today's weight"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Hủy", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(L("Lưu", "Save")) { store.logWeight(kg); dismiss() }.disabled(!(25...300).contains(kg)) }
            }
            .onAppear { kg = store.data.profile.weightKg }
        }
        .presentationDetents([.medium])
    }
}

private struct ExercisePicker: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var picked: Int?
    @State private var minutes = 30

    var body: some View {
        let activities = Nutrition.activities
        NavigationStack {
            Form {
                Section {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 90))], spacing: 10) {
                        ForEach(activities.indices, id: \.self) { i in
                            VStack(spacing: 4) {
                                Text(activities[i].emoji).font(.title2)
                                Text(activities[i].name).font(.caption).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                            .background(picked == i ? Palette.green.opacity(0.2) : Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                            .onTapGesture { picked = i }
                        }
                    }
                }
                if let i = picked {
                    Section {
                        Stepper("\(minutes) " + L("phút", "min"), value: $minutes, in: 5...300, step: 5)
                        Text(L("≈ \(Nutrition.exerciseKcal(met: activities[i].met, weightKg: store.data.profile.weightKg, minutes: minutes)) kcal với cân nặng \(Int(store.data.profile.weightKg)) kg",
                               "≈ \(Nutrition.exerciseKcal(met: activities[i].met, weightKg: store.data.profile.weightKg, minutes: minutes)) kcal at \(Int(store.data.profile.weightKg)) kg"))
                            .bold()
                    }
                }
            }
            .navigationTitle(L("Thêm vận động", "Add activity"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Hủy", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Thêm", "Add")) {
                        if let i = picked { store.addExercise(name: activities[i].name, emoji: activities[i].emoji, met: activities[i].met, minutes: minutes) }
                        dismiss()
                    }
                    .disabled(picked == nil)
                }
            }
        }
    }
}

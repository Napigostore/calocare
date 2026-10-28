import Charts
import SwiftUI

/// Progress, as on Android: calories in and out over 7 or 30 days, weight, average macros, workouts,
/// the adaptive calorie target, weighing in and logging exercise.
struct ProgressScreen: View {
    @EnvironmentObject var store: AppStore
    @State private var range = 7
    @State private var weighing = false
    @State private var exercising = false
    @State private var insights: InsightResult?
    @State private var insightsLoading = false
    @State private var insightsError: String?
    @State private var customGoalExpanded = false
    @State private var customMacros: MacroTargets?
    @State private var customMacrosLoading = false
    @State private var habitScore: HabitScore?
    @State private var habitScoreLoading = false

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
                    watchCard()

                    customGoalsCard(data)

                    if !logged.isEmpty {
                        habitScoreCard(data, logged)
                        insightsCardView(data, logged)
                    }
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

    /// Steps and sleep from Apple Health over the chosen range, as on Android.
    @ViewBuilder private func watchCard() -> some View {
        let days: [(date: Date, a: DayActivity)] = (0..<range).reversed().compactMap { back in
            let day = Nutrition.day(back)
            return store.history[dayKey(day)].map { (day, $0) }
        }
        let stepDays = days.filter { $0.a.steps > 0 }
        let sleeps = days.compactMap { $0.a.sleepHours }.filter { $0 > 0 }
        if store.healthLinked && (!stepDays.isEmpty || !sleeps.isEmpty) {
            Card(L("Vận động & phục hồi", "Activity & recovery")) {
                if !stepDays.isEmpty {
                    Chart {
                        ForEach(stepDays.indices, id: \.self) { i in
                            BarMark(x: .value("Day", stepDays[i].date, unit: .day), y: .value("Steps", stepDays[i].a.steps)).foregroundStyle(Palette.fat)
                        }
                        RuleMark(y: .value("Goal", 8000)).foregroundStyle(.secondary).lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    }
                    .frame(height: 140)
                    let avg = stepDays.reduce(0) { $0 + $1.a.steps } / stepDays.count
                    Text(L("👟 Trung bình \(avg.formatted()) bước/ngày (đường kẻ: 8.000 bước)", "👟 Average \(avg.formatted()) steps/day (line: 8,000 steps)")).font(.subheadline)
                }
                if !sleeps.isEmpty {
                    let avg = sleeps.reduce(0, +) / Double(sleeps.count)
                    Text(L("😴 Ngủ trung bình \(String(format: "%.1f", avg)) giờ mỗi đêm", "😴 Average sleep \(String(format: "%.1f", avg)) hours a night")
                         + (avg < 6.5 ? L(" — dưới mức 7 giờ khuyến nghị", " — below the recommended 7 hours") : "")).font(.subheadline)
                }
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

    @ViewBuilder private func insightsCardView(_ data: AppData, _ logged: [DayPoint]) -> some View {
        Card(L("💡 Nhận xét của Coach AI", "💡 AI Coach Insights")) {
            if insightsLoading {
                ProgressView().frame(maxWidth: .infinity, alignment: .center)
            } else if let insights = insights {
                VStack(alignment: .leading, spacing: 12) {
                    Text(insights.title).font(.headline)

                    if !insights.patterns.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(L("📊 Nhận xét", "📊 Observations")).font(.subheadline.weight(.semibold))
                            ForEach(insights.patterns, id: \.self) { p in
                                Text("• " + p).font(.caption).foregroundColor(.secondary)
                            }
                        }
                    }

                    if !insights.strengths.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(L("✅ Điểm mạnh", "✅ Strengths")).font(.subheadline.weight(.semibold))
                            ForEach(insights.strengths, id: \.self) { s in
                                Text("• " + s).font(.caption).foregroundColor(.secondary)
                            }
                        }
                    }

                    if !insights.improvements.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(L("🎯 Cải thiện", "🎯 Improvements")).font(.subheadline.weight(.semibold))
                            ForEach(insights.improvements, id: \.self) { i in
                                Text("• " + i).font(.caption).foregroundColor(.secondary)
                            }
                        }
                    }

                    if let tip = insights.substitutionTip {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(L("💭 Gợi ý thay thế", "💭 Food swap")).font(.subheadline.weight(.semibold))
                            Text(tip).font(.caption).foregroundColor(.secondary)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else if insightsError != nil {
                Text(L("Không lấy được insights. Kiểm tra quota AI.", "Couldn't fetch insights. Check your AI quota.")).font(.caption).foregroundColor(Palette.warn)
                Button(L("Thử lại", "Retry")) { loadInsights(data, logged) }.buttonStyle(.bordered)
            } else {
                Button(L("Tải nhận xét từ Coach", "Load Coach insights")) { loadInsights(data, logged) }.buttonStyle(.bordered)
            }
        }
        .onAppear { loadInsights(data, logged) }
    }

    private func loadInsights(_ data: AppData, _ logged: [DayPoint]) {
        insightsLoading = true
        insightsError = nil

        Task {
            do {
                let summary = buildNutritionSummary(data, logged, range)
                let result = try await API.shared.reportInsights(summary: summary)
                await MainActor.run {
                    insights = result
                    insightsLoading = false
                }
            } catch {
                await MainActor.run {
                    insightsError = error.localizedDescription
                    insightsLoading = false
                }
            }
        }
    }

    @ViewBuilder private func habitScoreCard(_ data: AppData, _ logged: [DayPoint]) -> some View {
        let p = data.profile
        let macros = Nutrition.macroTargets(p)
        let target = UserGoal(kcal: Nutrition.targetKcal(p), protein_g: Double(macros.protein), carbs_g: Double(macros.carbs), fat_g: Double(macros.fat))

        Card(L("🏆 Điểm ăn uống hôm nay", "🏆 Today's Eating Score")) {
            if habitScoreLoading {
                ProgressView().frame(maxWidth: .infinity, alignment: .center)
            } else if let score = habitScore {
                VStack(spacing: 12) {
                    HStack(spacing: 16) {
                        // Overall score
                        VStack(alignment: .center, spacing: 4) {
                            Text(L("Tổng điểm", "Overall")).font(.caption2).foregroundColor(.secondary)
                            Text("\(score.overall)").font(.title2.weight(.bold)).foregroundColor(scoreColor(score.overall))
                            Text("/ 100").font(.caption2).foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)

                        Divider()

                        // Streak
                        VStack(alignment: .center, spacing: 4) {
                            Text(L("Chuỗi liên tiếp", "Streak")).font(.caption2).foregroundColor(.secondary)
                            Text("\(score.streak)").font(.title2.weight(.bold)).foregroundColor(Palette.green)
                            Text(L("ngày", "days")).font(.caption2).foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.vertical, 8)

                    Divider()

                    // Component scores
                    HStack(spacing: 12) {
                        scoreBar(L("Đa dạng", "Variety"), score.diversity)
                        scoreBar(L("Macro", "Macro"), score.macroBalance)
                    }

                    // Badge info
                    if let badge = score.nextBadge {
                        HStack {
                            Image(systemName: "star.fill").foregroundColor(Palette.warn)
                            Text(L("Bạn sắp nhận huy chương: ", "Next badge: ") + badgeName(badge)).font(.caption)
                            Spacer()
                        }
                        .padding(.vertical, 6)
                        .padding(.horizontal, 8)
                        .background(Palette.warn.opacity(0.1))
                        .cornerRadius(6)
                    }
                }
            } else {
                Button(L("Tính điểm hôm nay", "Calculate today's score")) {
                    loadHabitScore(data, logged, target)
                }
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity, alignment: .center)
                .onAppear { loadHabitScore(data, logged, target) }
            }
        }
    }

    private func scoreColor(_ score: Int) -> Color {
        if score >= 80 { return Palette.green }
        if score >= 60 { return Palette.orange }
        return Palette.warn
    }

    private func scoreBar(_ label: String, _ value: Int) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(label).font(.caption2)
                Spacer()
                Text("\(value)%").font(.caption2.weight(.semibold))
            }
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Color(.systemGray5).frame(height: 6).cornerRadius(3)
                    scoreColor(value).frame(width: g.size.width * CGFloat(value) / 100, height: 6).cornerRadius(3)
                }
            }
            .frame(height: 6)
        }
    }

    private func badgeName(_ badge: String) -> String {
        switch badge {
        case "week_warrior": return L("Chiến binh tuần", "Week Warrior")
        case "month_master": return L("Thạc sĩ tháng", "Month Master")
        case "food_explorer": return L("Khám phá ẩm thực", "Food Explorer")
        case "macro_precision": return L("Độ chính xác macro", "Macro Precision")
        default: return badge
        }
    }

    private func loadHabitScore(_ data: AppData, _ logged: [DayPoint], _ target: UserGoal) {
        habitScoreLoading = true
        Task {
            do {
                let today = buildDayNutrition(data, logged.first(where: { dayKey($0.date) == dayKey(Date()) }))
                let pastDays = logged.dropFirst().map { buildDayNutrition(data, $0) }
                let result = try await API.shared.habitScore(today: today, target: target, pastDays: Array(pastDays))
                await MainActor.run {
                    habitScore = result
                    habitScoreLoading = false
                }
            } catch {
                await MainActor.run { habitScoreLoading = false }
            }
        }
    }

    private func buildDayNutrition(_ data: AppData, _ dayPoint: DayPoint?) -> DayNutrition {
        guard let dayPoint = dayPoint else {
            return DayNutrition(date: dayKey(Date()), foods: [], kcal: 0, protein_g: 0, carbs_g: 0, fat_g: 0)
        }
        let entries = Nutrition.entries(data, on: dayPoint.date)
        let foods = entries.map { $0.name }
        let totals = Nutrition.totals(entries)
        return DayNutrition(date: dayPoint.key, foods: foods, kcal: Int(totals.kcal), protein_g: totals.protein, carbs_g: totals.carbs, fat_g: totals.fat)
    }

    private func buildNutritionSummary(_ data: AppData, _ logged: [DayPoint], _ days: Int) -> NutritionSummary {
        let endDate = Nutrition.day(0)
        let startDate = Nutrition.day(days - 1)
        let n = Double(logged.count)

        let sums = logged.reduce((kcal: 0, protein: 0.0, carbs: 0.0, fat: 0.0, meals: 0)) { s, d in
            let entries = Nutrition.entries(data, on: d.date)
            let totals = Nutrition.totals(entries)
            return (s.kcal + Int(totals.kcal), s.protein + totals.protein, s.carbs + totals.carbs, s.fat + totals.fat, s.meals + entries.count)
        }

        // Compute course breakdown from foods in library
        var courseCounts: [String: Int] = [:]
        for day in logged {
            for entry in Nutrition.entries(data, on: day.date) {
                // Try to find the food to get its course, otherwise default to "main"
                let course = (data.customFoods.first { $0.id == entry.foodId }?.course ?? "main")
                courseCounts[course, default: 0] += 1
            }
        }
        let totalMeals = courseCounts.values.reduce(0, +)
        var courseBreakdown: [String: Double] = [:]
        if totalMeals > 0 {
            for (course, count) in courseCounts {
                courseBreakdown[course] = Double(count) / Double(totalMeals)
            }
        }

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")

        return NutritionSummary(
            period: days <= 7 ? "week" : "month",
            startDate: formatter.string(from: startDate),
            endDate: formatter.string(from: endDate),
            daysCount: logged.count,
            totalDays: days,
            totalKcal: sums.kcal,
            totalProtein: sums.protein,
            totalCarbs: sums.carbs,
            totalFat: sums.fat,
            avgKcal: n > 0 ? Double(sums.kcal) / n : 0,
            avgProtein: n > 0 ? sums.protein / n : 0,
            avgCarbs: n > 0 ? sums.carbs / n : 0,
            avgFat: n > 0 ? sums.fat / n : 0,
            mealsPerDay: n > 0 ? Double(sums.meals) / n : 0,
            courseBreakdown: courseBreakdown
        )
    }

    @ViewBuilder private func customGoalsCard(_ data: AppData) -> some View {
        let p = data.profile
        Card(L("🎯 Mục tiêu riêng", "🎯 Custom goals")) {
            VStack(spacing: 12) {
                HStack {
                    Text(L("Mục tiêu: ", "Goal: ")).font(.subheadline)
                    Menu {
                        ForEach(Goal.all, id: \.id) { g in
                            Button(g.label) { store.data.profile.goal = g.id; customMacros = nil }
                        }
                    } label: {
                        Label(Goal.label(p.goal), systemImage: "chevron.down").lineLimit(1)
                    }
                }

                if p.goal == Goal.lose || p.goal == Goal.gain {
                    HStack {
                        Text(L("Mục tiêu: ", "Target: ") + String(format: "%.1f", p.weeklyRateKg) + L(" kg/tuần", " kg/week"))
                            .font(.caption)
                        Spacer()
                        Stepper("", value: Binding(get: { p.weeklyRateKg }, set: { store.data.profile.weeklyRateKg = max(0.25, min(2, $0)) }), in: 0.25...2, step: 0.25)
                            .labelsHidden()
                    }
                }

                Button {
                    loadCustomMacros(p)
                } label: {
                    Label(L("Tính lại macro", "Calculate macros"), systemImage: "arrow.clockwise")
                }
                .buttonStyle(.bordered)

                if customMacrosLoading {
                    ProgressView().frame(maxWidth: .infinity, alignment: .center)
                } else if let macros = customMacros {
                    VStack(spacing: 8) {
                        Text(L("Mục tiêu đề xuất", "Recommended targets")).font(.subheadline.weight(.semibold))
                        Text(macros.explanation).font(.caption).foregroundColor(.secondary)

                        HStack(spacing: 12) {
                            macroBox(L("Đạm", "Protein"), "\(macros.proteinG)g", Palette.protein)
                            macroBox(L("Tinh bột", "Carbs"), "\(macros.carbsG)g", Palette.carbs)
                            macroBox(L("Chất béo", "Fat"), "\(macros.fatG)g", Palette.fat)
                            macroBox(L("Calo", "Kcal"), "\(macros.kcal)", .secondary)
                        }
                    }
                }
            }
        }
    }

    private func macroBox(_ label: String, _ value: String, _ color: Color) -> some View {
        VStack(spacing: 4) {
            Text(label).font(.caption2).foregroundColor(.secondary)
            Text(value).font(.headline).foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
        .padding(8)
        .background(Color(UIColor.systemGray6))
        .cornerRadius(8)
    }

    private func loadCustomMacros(_ profile: Profile) {
        customMacrosLoading = true
        Task {
            do {
                let metrics = UserMetrics(
                    weightKg: profile.weightKg,
                    heightCm: profile.heightCm,
                    age: Nutrition.age(profile),
                    male: profile.male,
                    activityLevel: profile.activity,
                    goal: profile.goal,
                    weeklyRateKg: profile.weeklyRateKg,
                    dietRestriction: profile.diet == Diet.none ? nil : profile.diet
                )
                let result = try await API.shared.macroTargets(metrics: metrics)
                await MainActor.run {
                    customMacros = result
                    customMacrosLoading = false
                }
            } catch {
                await MainActor.run {
                    customMacrosLoading = false
                }
            }
        }
    }
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

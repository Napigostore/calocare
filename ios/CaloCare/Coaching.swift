import Foundation

/// The offline coaching of the Android app: daily tips, encouragement, streaks, eating order, the adaptive
/// target and suggested menus. Same rules and numbers on both platforms.
extension Nutrition {
    static func day(_ back: Int, from date: Date = Date()) -> Date {
        Calendar.current.date(byAdding: .day, value: -back, to: date) ?? date
    }

    static func totals(_ entries: [LogEntry]) -> (kcal: Double, protein: Double, carbs: Double, fat: Double) {
        entries.reduce((0.0, 0.0, 0.0, 0.0)) { s, e in (s.0 + e.totalKcal, s.1 + e.protein * e.qty, s.2 + e.carbs * e.qty, s.3 + e.fat * e.qty) }
    }

    static func entries(_ data: AppData, on date: Date) -> [LogEntry] {
        let key = dayKey(date)
        return data.entries.filter { $0.date == key }
    }

    static func exercises(_ data: AppData, on date: Date) -> [ExerciseEntry] {
        let key = dayKey(date)
        return (data.exercises ?? []).filter { $0.date == key }
    }

    /// kcal = MET × kg × hours (Compendium of Physical Activities).
    static func exerciseKcal(met: Double, weightKg: Double, minutes: Int) -> Int { Int((met * weightKg * Double(minutes) / 60).rounded()) }

    static var activities: [(name: String, emoji: String, met: Double)] {
        [(L("Đi bộ", "Walking"), "🚶", 3.5), (L("Chạy bộ", "Running"), "🏃", 9.8), (L("Đạp xe", "Cycling"), "🚴", 7.5),
         (L("Bơi lội", "Swimming"), "🏊", 7), (L("Tập tạ", "Weight training"), "🏋️", 5), ("HIIT", "⚡", 9),
         ("Yoga", "🧘", 2.8), (L("Cầu lông", "Badminton"), "🏸", 5.5), (L("Bóng đá", "Football"), "⚽", 8),
         (L("Nhảy dây", "Jump rope"), "🤸", 11), (L("Leo cầu thang", "Stair climbing"), "🪜", 8), (L("Việc nhà", "Housework"), "🧹", 3)]
    }

    /// Energy out for a day: BMR plus the watch's active energy (today) or a light daily-living allowance,
    /// plus workouts logged by hand.
    static func out(_ data: AppData, on date: Date, activity: DayActivity?) -> (total: Int, bmr: Int, active: Int, exercise: Int, measured: Bool) {
        let base = kcalOut(data.profile, Calendar.current.isDateInToday(date) ? activity : historyFor(date))
        let exercise = exercises(data, on: date).reduce(0) { $0 + $1.kcal }
        return (base.total + exercise, Int(bmr(data.profile).rounded()), base.active, exercise, base.measured)
    }

    /// Apple Health's numbers for a past day, set by the store when it reads the month's history.
    nonisolated(unsafe) static var history: [String: DayActivity] = Demo.active ? Demo.history : [:]
    static func historyFor(_ date: Date) -> DayActivity? { history[dayKey(date)] }

    static func onTarget(_ data: AppData, on date: Date) -> Bool {
        let eaten = totals(entries(data, on: date)).kcal
        guard eaten > 0 else { return false }
        let target = Double(targetKcal(data.profile))
        switch data.profile.goal {
        case Goal.lose: return eaten <= target * 1.05 && eaten >= target * 0.7
        case Goal.gain, Goal.gym: return eaten >= target * 0.95
        default: return eaten >= target * 0.9 && eaten <= target * 1.1
        }
    }

    /// Days in a row (ending today or yesterday) with at least one diary entry.
    static func streak(_ data: AppData) -> Int {
        let days = Set(data.entries.map { $0.date })
        var back = days.contains(dayKey(Date())) ? 0 : 1
        var n = 0
        while days.contains(dayKey(day(back))) { n += 1; back += 1 }
        return n
    }

    static func cheer(_ data: AppData, on date: Date) -> (emoji: String, text: String) {
        let p = data.profile
        let eaten = totals(entries(data, on: date)).kcal
        let target = Double(targetKcal(p))
        let ratio = eaten / target
        let streak = streak(data)
        let who = p.name.isEmpty ? L("bạn", "there") : p.name
        let over = Int((eaten - target).rounded()), left = Int((target - eaten).rounded())
        let hour = Calendar.current.component(.hour, from: Date())
        if eaten <= 0 { return ("👋", L("Chào \(who)! Chạm vào món đầu tiên để bắt đầu ngày mới nhé.", "Hi \(who)! Tap your first food to start the day.")) }
        if p.goal == Goal.lose && ratio > 1.1 {
            return ("💪", L("Hôm nay vượt mục tiêu \(over) kcal. Không sao — đi bộ 30 phút và quay lại nhịp vào bữa tới.", "\(over) kcal over today's target. No worries — take a 30-minute walk and get back on track at the next meal."))
        }
        if (p.goal == Goal.gain || p.goal == Goal.gym) && ratio < 0.6 && hour >= 18 && Calendar.current.isDateInToday(date) {
            return ("🍚", L("Còn thiếu \(left) kcal để tăng cân. Thêm một bữa phụ giàu đạm trước khi ngủ nhé.", "\(left) kcal short of your gain target. Add a protein-rich snack before bed."))
        }
        let hit = onTarget(data, on: date)
        if hit && streak >= 7 { return ("🏆", L("Tuyệt vời! \(streak) ngày liên tiếp và hôm nay đúng mục tiêu. Giữ phong độ này nhé \(who)!", "Amazing! \(streak) days in a row and on target today. Keep it up, \(who)!")) }
        if hit && ratio > 1.02 { return ("⚖️", L("Vượt mục tiêu \(over) kcal — vẫn trong mức cho phép. Bữa sau ăn nhẹ hơn một chút là cân bằng.", "\(over) kcal over — still within range. A slightly lighter next meal balances it out.")) }
        if hit { return ("🎯", L("Đúng mục tiêu rồi! Bạn đang làm rất tốt.", "Right on target! You're doing great.")) }
        if ratio < 0.5 { return ("🌱", L("Khởi đầu tốt — còn \(left) kcal cho hôm nay.", "Good start — \(left) kcal left for today.")) }
        if ratio <= 1 { return ("🔥", L("Đã đi được \(Int((ratio * 100).rounded()))% chặng đường hôm nay. Cố lên \(who)!", "\(Int((ratio * 100).rounded()))% of the way through today. Keep going, \(who)!")) }
        return ("⚖️", L("Hơi vượt mục tiêu một chút. Bữa sau ưu tiên rau và đạm nạc nhé.", "Slightly over target. Favour vegetables and lean protein next meal."))
    }

    /// Rule-based advice that works offline; the AI coach builds on the same numbers.
    static func tips(_ data: AppData, on date: Date, activity: DayActivity?) -> [String] {
        let p = data.profile
        let todays = entries(data, on: date)
        let t = totals(todays)
        let target = Double(targetKcal(p))
        let macros = macroTargets(p)
        let today = Calendar.current.isDateInToday(date)
        let hour = Calendar.current.component(.hour, from: Date())
        var tips: [String] = []
        if today && hour >= 18 && t.kcal > 0 && t.protein < Double(macros.protein) * 0.6 {
            tips.append(L("Đạm hôm nay mới \(Int(t.protein))g / \(macros.protein)g. Bữa tối thêm cá, ức gà, trứng hoặc đậu hũ.", "Only \(Int(t.protein))g / \(macros.protein)g protein so far. Add fish, chicken breast, eggs or tofu at dinner."))
        }
        if today && hour >= 20 && t.kcal > 0 && t.kcal < bmr(p) * 0.7 {
            tips.append(L("Bạn đang ăn thấp hơn nhiều so với mức trao đổi chất cơ bản (\(Int(bmr(p))) kcal). Ăn quá ít dễ mất cơ và mệt — đừng bỏ bữa.", "You're eating far below your basal metabolic rate (\(Int(bmr(p))) kcal). Eating too little costs muscle and energy — don't skip meals."))
        }
        if t.kcal > target * 1.15 {
            let over = Int((t.kcal - target).rounded()), walk = Int(((t.kcal - target) / (3.5 * p.weightKg / 60)).rounded())
            tips.append(L("Đã vượt mục tiêu \(over) kcal. Khoảng \(walk) phút đi bộ nhanh sẽ bù lại.", "\(over) kcal over target. About \(walk) minutes of brisk walking makes up for it."))
        }
        if macros.carbs > 0 && t.carbs > Double(macros.carbs) * 1.2 {
            tips.append(L("Tinh bột hơi cao (\(Int(t.carbs))g). Thử đổi nửa chén cơm bằng rau hoặc khoai lang.", "Carbs are a bit high (\(Int(t.carbs))g). Try swapping half your rice or bread for vegetables or sweet potato."))
        }
        if today, let a = activity, a.steps > 0, a.steps < 5000, hour >= 15 {
            tips.append(L("Mới \(a.steps) bước. Đi bộ 15 phút sau bữa tối giúp tiêu hóa và ổn định đường huyết.", "Only \(a.steps) steps. A 15-minute walk after dinner helps digestion and blood sugar."))
        }
        if today, let sleep = activity?.sleepHours, sleep > 0, sleep < 6 {
            let text = String(format: "%.1f", sleep)
            tips.append(L("Đêm qua bạn ngủ \(text) giờ. Thiếu ngủ làm tăng cảm giác thèm ngọt và giảm phục hồi — hôm nay tập nhẹ, ưu tiên đạm và ngủ sớm hơn.", "You slept \(text) hours last night. Short sleep increases sugar cravings and slows recovery — train lightly today, favour protein and go to bed earlier."))
        }
        let sugary = todays.filter { $0.tags.contains("high_sugar") }.count
        if p.conditions.contains("diabetes") {
            if sugary > 0 { tips.append(L("Hôm nay có \(sugary) món nhiều đường. Với tiểu đường, nên thay bằng trái cây ít ngọt (ổi, táo) và đo đường huyết sau ăn 2 giờ.", "\(sugary) sugary item(s) today. With diabetes, swap them for low-sugar fruit (guava, apple) and check blood sugar 2 hours after eating.")) }
            tips.append(L("Tiểu đường: ăn rau → đạm → tinh bột, chia 3 bữa chính + 1–2 bữa phụ nhỏ, không bỏ bữa.", "Diabetes: eat vegetables → protein → starch, 3 main meals + 1–2 small snacks, never skip meals."))
        }
        if p.conditions.contains("hypertension") { tips.append(L("Huyết áp: giữ muối dưới 5g/ngày — hạn chế nước dùng phở/bún, đồ kho mặn, mì gói.", "Blood pressure: keep salt under 5 g a day — limit soups and broths, salty sauces, processed meats and instant noodles.")) }
        if p.conditions.contains("gout") { tips.append(L("Gout: uống 2–2,5 lít nước/ngày, hạn chế nội tạng, hải sản, bia rượu.", "Gout: drink 2–2.5 litres of water a day; limit organ meats, seafood and alcohol.")) }
        if p.conditions.contains("cholesterol") { tips.append(L("Mỡ máu: ưu tiên cá, yến mạch, các loại hạt; hạn chế đồ chiên và mỡ động vật.", "Cholesterol: favour fish, oats and nuts; limit fried food and animal fat.")) }
        if bmi(p) < 18.5 && p.goal == Goal.lose {
            let b = String(format: "%.1f", bmi(p))
            tips.append(L("BMI của bạn là \(b) (thiếu cân) — không nên giảm cân thêm. Hãy đổi mục tiêu sang giữ hoặc tăng cân.", "Your BMI is \(b) (underweight) — don't lose more weight. Switch your goal to maintain or gain."))
        }
        switch p.goal {
        case Goal.lose: tips.append(L("Giảm cân bền vững: thâm hụt 300–500 kcal/ngày, đạm 1,6g/kg để giữ cơ, ngủ đủ 7 giờ.", "Sustainable weight loss: a 300–500 kcal daily deficit, 1.6 g/kg protein to keep muscle, 7 hours of sleep."))
        case Goal.gain: tips.append(L("Tăng cân: ăn dư 300–500 kcal/ngày, thêm bữa phụ (sữa, chuối, hạt) và tập kháng lực để tăng cơ thay vì mỡ.", "Weight gain: a 300–500 kcal daily surplus, extra snacks (milk, bananas, nuts) and resistance training so it's muscle, not fat."))
        case Goal.gym: tips.append(L("Tăng cơ: đạm khoảng 2g/kg chia đều 4 bữa, nạp 20–30g đạm trong 2 giờ sau tập.", "Muscle gain: about 2 g/kg protein over 4 meals, and 20–30 g within 2 hours after training."))
        case Goal.run: tips.append(L("Chạy bộ: nạp tinh bột dễ tiêu 1–2 giờ trước khi chạy (chuối, bánh mì), bù nước và điện giải nếu chạy trên 60 phút.", "Running: easy carbs 1–2 hours before a run (banana, bread); water and electrolytes for runs over 60 minutes."))
        default: tips.append(L("Giữ cân: giữ nhịp ăn đều, mỗi bữa đủ rau – đạm – tinh bột theo tỉ lệ ½ – ¼ – ¼ đĩa.", "Maintenance: eat regularly; each plate ½ vegetables, ¼ protein, ¼ starch."))
        }
        return tips
    }

    static func goalGuides(_ goal: String) -> [String] {
        switch goal {
        case Goal.lose: return [L("Mỗi bữa: ½ đĩa rau, ¼ đạm nạc, ¼ tinh bột nguyên cám.", "Every meal: ½ plate vegetables, ¼ lean protein, ¼ whole grains."),
                                L("Uống nước trước bữa ăn 15 phút; hạn chế nước ngọt, trà sữa.", "Drink water 15 minutes before meals; limit soda and sweet coffee or tea drinks."),
                                L("Giảm 0,5 kg/tuần là an toàn; giảm nhanh hơn dễ mất cơ và tăng lại.", "0.5 kg a week is safe; faster loss costs muscle and tends to come back.")]
        case Goal.gain: return [L("Ăn 5–6 bữa nhỏ, thêm đồ uống giàu năng lượng (sinh tố bơ, sữa).", "Eat 5–6 small meals and add energy-dense drinks (smoothies, milk)."),
                                L("Tăng 0,25–0,5 kg/tuần; kết hợp tập tạ 3 buổi/tuần.", "Gain 0.25–0.5 kg a week, with weight training 3 times a week.")]
        case Goal.gym: return [L("Trước tập 60–90 phút: tinh bột + ít đạm (chuối + sữa chua).", "60–90 minutes before training: carbs + a little protein (banana + yogurt)."),
                               L("Sau tập trong 2 giờ: 20–30g đạm + tinh bột (cơm + ức gà, hoặc whey + chuối).", "Within 2 hours after training: 20–30 g protein + carbs (rice + chicken, or whey + banana)."),
                               L("Ngủ 7–8 giờ — cơ phát triển lúc nghỉ, không phải lúc tập.", "Sleep 7–8 hours — muscle grows while you rest, not while you train.")]
        case Goal.run: return [L("Ngày chạy dài: tăng tinh bột lên 6–8g/kg; ngày nghỉ giảm lại.", "Long-run days: raise carbs to 6–8 g/kg; lower them on rest days."),
                               L("Chạy trên 60 phút: bổ sung 30–60g tinh bột mỗi giờ và nước có điện giải.", "Runs over 60 minutes: 30–60 g carbs per hour plus electrolyte drinks."),
                               L("Sau chạy 30 phút: tinh bột + đạm tỉ lệ 3:1 để hồi phục glycogen.", "Within 30 minutes after a run: carbs and protein at 3:1 to restore glycogen.")]
        default: return [L("Ăn đa dạng, đủ 3 bữa chính; cân lại mỗi tuần vào cùng một buổi sáng.", "Eat a varied diet with 3 main meals; weigh yourself weekly on the same morning."),
                         L("Vận động vừa ít nhất 150 phút mỗi tuần.", "At least 150 minutes of moderate activity a week.")]
        }
    }

    // MARK: Eating order

    private static func rank(_ course: String, _ tags: [String]) -> Int {
        switch course {
        case "soup": return 0
        case "veg": return 1
        case "protein": return 2
        case "main": return 3
        case "fruit": return 4
        case "drink": return tags.contains("high_sugar") ? 6 : 4
        default: return 5
        }
    }

    private static func why(_ course: String, _ tags: [String], diabetic: Bool) -> String {
        switch course {
        case "soup": return L("Khai vị: làm ấm dạ dày, tạo cảm giác no sớm", "Starter: warms the stomach and makes you feel full sooner")
        case "veg": return diabetic ? L("Chất xơ đi trước giúp đường huyết sau ăn tăng chậm hơn", "Fibre first slows the rise in blood sugar after the meal") : L("Chất xơ trước giúp no lâu, hấp thu đường chậm lại", "Fibre first keeps you full longer and slows sugar absorption")
        case "protein": return L("Đạm giúp no bền và giữ cơ", "Protein keeps you full and preserves muscle")
        case "main": return diabetic ? L("Tinh bột ăn sau cùng trong bữa chính để hạn chế đường huyết tăng vọt", "Eat starches last to limit blood sugar spikes") : L("Tinh bột ăn sau rau và đạm để tránh ăn quá tay", "Eat starches after vegetables and protein to avoid overeating")
        case "fruit": return L("Trái cây sau bữa khoảng 30 phút, hoặc để dành bữa phụ", "Fruit about 30 minutes after the meal, or keep it for a snack")
        case "drink": return tags.contains("high_sugar") ? L("Đồ uống ngọt để sau cùng, uống ít", "Sweet drinks last, and only a little") : L("Uống kèm hoặc sau bữa", "With or after the meal")
        default: return L("Tráng miệng: ăn sau cùng, lượng nhỏ", "Dessert: last, in a small portion")
        }
    }

    /// Vegetables and soup first, then protein, then starch, sweets last. Course comes from the food table by id.
    static func eatingOrder(_ entries: [LogEntry], courseOf: (LogEntry) -> String, profile p: Profile) -> [(name: String, emoji: String, why: String)] {
        let diabetic = p.conditions.contains("diabetes")
        var seen = Set<String>()
        return entries.filter { seen.insert($0.foodId).inserted }
            .sorted { rank(courseOf($0), $0.tags) < rank(courseOf($1), $1.tags) }
            .map { ($0.label, $0.emoji, why(courseOf($0), $0.tags, diabetic: diabetic)) }
    }

    // MARK: Adaptive target

    /// Compares the real weight trend with the plan: after two or more weeks of data the scale beats the formula.
    static func adaptive(_ data: AppData) -> (delta: Int, actualPerWeek: Double, plannedPerWeek: Double, days: Int)? {
        let p = data.profile
        let from = max(dayKey(day(28)), p.kcalAdjustDate ?? "")
        let weights = (data.weights ?? []).filter { $0.date >= from }.sorted { $0.date < $1.date }
        guard let first = weights.first, let last = weights.last, weights.count >= 2 else { return nil }
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        guard let d1 = f.date(from: first.date), let d2 = f.date(from: last.date) else { return nil }
        let days = Calendar.current.dateComponents([.day], from: d1, to: d2).day ?? 0
        guard days >= 14 else { return nil }
        let logged = Set(data.entries.filter { $0.date >= first.date && $0.date <= last.date }.map { $0.date }).count
        guard Double(logged) >= Double(days) * 0.6 else { return nil }
        let actual = (last.kg - first.kg) / Double(days) * 7
        let planned = p.goal == Goal.lose ? -p.weeklyRateKg : p.goal == Goal.gain ? p.weeklyRateKg : 0
        let delta = Int((min(200, max(-200, (planned - actual) * 7700 / 7)) / 50).rounded()) * 50
        guard delta != 0, (-500...500).contains(p.adjust + delta) else { return nil }
        return (delta, actual, planned, days)
    }
}

/// One-day menus from meal templates of local or international dishes, filtered for the user's health and
/// scaled to the calorie target. Same templates as the Android app.
enum MenuPlanner {
    struct Item: Hashable { var food: Food; var qty: Double }
    struct PlannedMeal { var meal: String; var items: [Item]; var kcal: Int { Int(items.reduce(0) { $0 + Double($1.food.kcal) * $1.qty }.rounded()) } }

    private struct Combo { var meal: String; var region: String; var goals: Set<String>; var items: [(String, Double)] }

    private static let L_ = Goal.lose, M = Goal.maintain, G = Goal.gain, Y = Goal.gym, R = Goal.run
    private static func c(_ meal: String, _ region: String, _ goals: Set<String>, _ items: (String, Double)...) -> Combo { Combo(meal: meal, region: region, goals: goals, items: items) }

    private static let combos: [Combo] = [
        c("breakfast", "vn", [M, G, Y, R], ("pho_bo", 1)), c("breakfast", "vn", [], ("pho_ga", 1)),
        c("breakfast", "vn", [M, G, Y, R], ("banh_mi_trung", 1), ("sua_dau_nanh", 1)), c("breakfast", "all", [L_, R, M, Y], ("yen_mach", 1), ("chuoi", 1)),
        c("breakfast", "all", [L_, Y, M], ("trung_luoc", 2), ("khoai_lang", 1), ("sua_tuoi", 1)), c("breakfast", "vn", [L_, M], ("chao_ga", 1), ("trung_luoc", 1)),
        c("breakfast", "vn", [G, R], ("xoi_man", 1), ("sua_dau_nanh", 1)), c("breakfast", "vn", [M, G], ("banh_cuon", 1)),
        c("breakfast", "all", [], ("khoai_lang", 1), ("sua_dau_nanh", 1), ("chuoi", 1)), c("breakfast", "all", [], ("bo", 1), ("trung_luoc", 2), ("ca_phe_den", 1)),
        c("breakfast", "intl", [], ("banh_mi_nuong", 1), ("trung_chien", 2), ("sua_tuoi", 1)), c("breakfast", "intl", [L_, M, Y], ("sua_chua_hy_lap", 1), ("dau_tay", 1), ("hanh_nhan", 1)),
        c("breakfast", "intl", [G, R, M], ("banh_mi_nuong", 1), ("bo_dau_phong", 1), ("chuoi", 1)), c("breakfast", "intl", [L_, M, Y], ("trung_bac", 1), ("avocado_toast", 1), ("americano", 1)),
        c("breakfast", "intl", [L_, M, R], ("granola", 1), ("viet_quat", 1)), c("breakfast", "intl", [G, R], ("pancake", 1), ("trung_bac", 1)),
        c("breakfast", "intl", [Y, G], ("omelette", 1), ("banh_mi_nuong", 1), ("latte", 1)), c("breakfast", "intl", [M, G], ("bagel_cream_cheese", 1), ("cappuccino", 1)),
        c("lunch", "all", [L_, Y, M], ("canh_rau", 1), ("rau_luoc", 1), ("uc_ga", 1), ("com_gao_lut", 1)), c("lunch", "vn", [M, G, R], ("canh_chua", 1), ("rau_muong_xao", 1), ("ca_kho", 1), ("com_trang", 1)),
        c("lunch", "vn", [G, M], ("canh_bi", 1), ("rau_luoc", 1), ("thit_kho", 1), ("com_trang", 1)), c("lunch", "all", [Y, L_, M], ("salad", 1), ("bo_xao", 1), ("com_gao_lut", 1)),
        c("lunch", "vn", [G, Y, R], ("canh_rau", 1), ("com_ga", 1)), c("lunch", "vn", [R, G, M], ("bun_cha", 1)),
        c("lunch", "all", [], ("canh_rau", 1), ("rau_luoc", 1), ("dau_hu", 1), ("com_gao_lut", 1)), c("lunch", "all", [], ("salad", 1), ("ca_hoi", 1), ("bo", 1)),
        c("lunch", "vn", [G], ("canh_rau", 1), ("com_tam", 1)), c("lunch", "vn", [R, M], ("goi_cuon", 1), ("pho_ga", 1)),
        c("lunch", "intl", [M, Y, R], ("salad", 1), ("sandwich", 1)), c("lunch", "intl", [G, R, M], ("salad", 1), ("mi_y", 1)),
        c("lunch", "intl", [M, R, L_], ("salad", 1), ("sushi", 1)), c("lunch", "intl", [L_, M, Y], ("caesar_salad", 1)),
        c("lunch", "intl", [L_, Y, M], ("wrap_ga", 1), ("tao", 1)), c("lunch", "intl", [Y, R, M], ("poke", 1)),
        c("lunch", "intl", [G, R], ("teriyaki", 1), ("sup_ca_chua", 1)), c("lunch", "intl", [G, Y], ("burrito", 1)),
        c("lunch", "intl", [M, Y], ("tuna_sandwich", 1), ("salad", 1)), c("lunch", "intl", [M, R, G], ("bibimbap", 1), ("kimchi", 1)),
        c("dinner", "all", [L_, M], ("canh_rau", 1), ("rau_luoc", 1), ("ca_hap", 1), ("com_gao_lut", 0.5)), c("dinner", "all", [L_, Y], ("salad", 1), ("uc_ga", 1), ("khoai_lang", 1)),
        c("dinner", "vn", [M, Y, R], ("canh_chua", 1), ("rau_luoc", 1), ("tom_luoc", 1), ("com_trang", 1)), c("dinner", "vn", [G, M, Y], ("canh_bi", 1), ("rau_muong_xao", 1), ("thit_luoc", 1), ("com_trang", 1)),
        c("dinner", "all", [], ("canh_rau", 1), ("rau_muong_xao", 1), ("dau_hu", 1), ("com_trang", 1)), c("dinner", "all", [], ("rau_luoc", 1), ("trung_chien", 2), ("dau_hu_chien", 1)),
        c("dinner", "vn", [G, Y, R], ("rau_luoc", 1), ("bo_xao", 1), ("com_trang", 1.5)), c("dinner", "all", [], ("rau_luoc", 1), ("ga_luoc", 1), ("bo", 1)),
        c("dinner", "intl", [G, Y, M], ("salad", 1), ("steak", 1), ("khoai_lang", 1)), c("dinner", "intl", [], ("salad", 1), ("ca_hoi", 1), ("com_gao_lut", 1)),
        c("dinner", "intl", [L_, M, Y], ("roast_chicken", 1), ("bong_cai", 1), ("quinoa", 1)), c("dinner", "intl", [L_, Y], ("ga_tay", 1), ("bong_cai", 1), ("khoai_lang", 1)),
        c("dinner", "intl", [M, G, R], ("chicken_tikka", 1), ("com_trang", 1), ("bong_cai", 1)), c("dinner", "intl", [G, R], ("carbonara", 1), ("salad", 1)),
        c("dinner", "intl", [M, Y], ("chili_con_carne", 1), ("com_gao_lut", 1), ("salad", 1)), c("dinner", "intl", [M, R, L_], ("tom_yum", 1), ("sashimi", 1), ("com_trang", 0.5)),
        c("snack", "all", [L_, M], ("sua_chua", 1), ("dau_tay", 1)), c("snack", "all", [Y], ("chuoi", 1), ("whey", 1)),
        c("snack", "all", [R, G], ("chuoi", 1), ("dau_phong", 1)), c("snack", "vn", [G], ("sinh_to_bo", 1)),
        c("snack", "all", [L_, M, Y], ("tao", 1), ("hanh_nhan", 1)), c("snack", "all", [G], ("sua_tuoi", 1), ("banh_quy", 1)),
        c("snack", "all", [], ("cam", 1)), c("snack", "all", [], ("hat_dieu", 1)), c("snack", "vn", [], ("oi", 1)),
        c("snack", "all", [], ("dua_leo", 2), ("trung_luoc", 1)), c("snack", "intl", [Y, R], ("protein_bar", 1)),
        c("snack", "intl", [L_, M], ("hummus", 1), ("dua_leo", 1)), c("snack", "intl", [L_, M, Y], ("sua_chua_hy_lap", 1), ("viet_quat", 1)),
        c("snack", "intl", [G, M], ("pho_mai", 1), ("tao", 1)), c("snack", "intl", [], ("kiwi", 2)),
    ]

    private static func shares(_ goal: String) -> [String: Double] {
        goal == Goal.gain || goal == Goal.gym ? ["breakfast": 0.25, "lunch": 0.30, "dinner": 0.30, "snack": 0.15] : ["breakfast": 0.25, "lunch": 0.35, "dinner": 0.30, "snack": 0.10]
    }

    private static func pick(_ p: Profile, meal: String, want: Double, foods: [String: Food], rng: inout SeededRandom) -> PlannedMeal {
        let pool = combos.filter { $0.meal == meal && ($0.region == p.cuisine || $0.region == "all") }
        func items(_ c: Combo) -> [Item] { c.items.compactMap { id, q in foods[id].map { Item(food: $0, qty: q) } } }
        func clean(_ c: Combo) -> Bool { items(c).allSatisfy { Nutrition.warnings(tags: $0.food.tags, profile: p).isEmpty } }
        func safe(_ c: Combo) -> Bool { items(c).allSatisfy { !Nutrition.warnings(tags: $0.food.tags, profile: p).contains { $0.danger } } }
        func fits(_ c: Combo) -> Bool { c.goals.isEmpty || c.goals.contains(p.goal) }
        // Prefer templates with no warning at all; relax the goal first, never the allergy check.
        var candidates = pool.filter { clean($0) && fits($0) }
        if candidates.isEmpty { candidates = pool.filter(clean) }
        if candidates.isEmpty { candidates = pool.filter { safe($0) && fits($0) } }
        if candidates.isEmpty { candidates = pool.filter(safe) }
        guard !candidates.isEmpty else { return PlannedMeal(meal: meal, items: []) }
        let chosen = items(candidates[rng.next(candidates.count)])
        let base = chosen.reduce(0) { $0 + Double($1.food.kcal) * $1.qty }
        let scale = base > 0 ? min(2.5, max(0.5, want / base)) : 1
        return PlannedMeal(meal: meal, items: chosen.map { Item(food: $0.food, qty: max(0.5, (($0.qty * scale) * 2).rounded() / 2)) })
    }

    static func plan(_ p: Profile, foods: [Food], seed: Int) -> [PlannedMeal] {
        let byId = Dictionary(foods.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        var rng = SeededRandom(seed)
        let target = Double(Nutrition.targetKcal(p))
        return Meal.all.map { pick(p, meal: $0.id, want: target * (shares(p.goal)[$0.id] ?? 0.25), foods: byId, rng: &rng) }
    }

    /// "What should I eat next?": the next meal not yet logged today, sized from the calories still left.
    static func nextMeal(_ data: AppData, foods: [Food], seed: Int) -> (meal: PlannedMeal, note: String) {
        let p = data.profile
        let todays = Nutrition.entries(data, on: Date())
        let eaten = Nutrition.totals(todays)
        let remaining = Double(Nutrition.targetKcal(p)) - eaten.kcal
        let hour = Calendar.current.component(.hour, from: Date())
        let start = hour < 10 ? 0 : hour < 14 ? 1 : hour < 21 ? 2 : 3
        let open = ["breakfast", "lunch", "dinner"].dropFirst(start).filter { m in !todays.contains { $0.meal == m } }
        let byId = Dictionary(foods.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        var rng = SeededRandom(seed)
        if open.isEmpty || remaining < 200 {
            let note = remaining < 200 ? L("Bạn gần chạm mục tiêu hôm nay — nếu còn đói, chọn món nhẹ.", "You're close to today's target — if you're still hungry, pick something light.")
                : L("Các bữa chính đã xong. Còn \(Int(remaining)) kcal cho một bữa phụ.", "Main meals are done. \(Int(remaining)) kcal left for a snack.")
            return (pick(p, meal: "snack", want: min(250, max(80, remaining)), foods: byId, rng: &rng), note)
        }
        let share = shares(p.goal)
        let meal = open.first!
        let weight = share[meal] ?? 0.3
        let total = open.reduce(0) { $0 + (share[$1] ?? 0.3) }
        let budget = remaining * weight / total
        let macros = Nutrition.macroTargets(p)
        let note = eaten.protein < Double(macros.protein) * Double(3 - open.count) / 3 * 0.7
            ? L("Đạm đang thấp hơn tiến độ — bữa này đừng bỏ phần đạm.", "Protein is behind schedule — don't skip it this meal.")
            : L("Còn \(Int(remaining)) kcal cho \(open.count) bữa chính còn lại.", "\(Int(remaining)) kcal left for the \(open.count) remaining main meal(s).")
        return (pick(p, meal: meal, want: budget, foods: byId, rng: &rng), note)
    }
}

/// Small deterministic generator, so "Shuffle" gives a new menu and the same seed gives the same one.
struct SeededRandom {
    private var state: UInt64
    init(_ seed: Int) { state = UInt64(truncatingIfNeeded: seed &* 2654435761) | 1 }
    mutating func next(_ bound: Int) -> Int {
        state ^= state << 13; state ^= state >> 7; state ^= state << 17
        return Int(state % UInt64(max(bound, 1)))
    }
}

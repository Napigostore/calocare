import UIKit

/// Sample data for the App Store screenshots, taken on the simulator by the cloud build:
///   launch arguments `-demo home|food|scan|coach|profile`, plus `-demoPhoto <path>` for the scan screen.
/// Nothing is read from or written to the user's files while it is on, and nothing is sent to the server.
enum Demo {
    static let screen = UserDefaults.standard.string(forKey: "demo")
    static var active: Bool { screen != nil }

    static var tab: Int {
        switch screen {
        case "food", "scan": return 1
        case "menu": return 2
        case "progress": return 3
        case "coach": return 4
        default: return 0
        }
    }

    static let activity = DayActivity(activeKcal: 486, steps: 9_240, sleepHours: 7.3, exerciseMinutes: 42)

    static var history: [String: DayActivity] {
        var out: [String: DayActivity] = [:]
        for back in 0..<30 {
            let day = Calendar.current.date(byAdding: .day, value: -back, to: Date()) ?? Date()
            let wave = Double((back * 37) % 11) / 10
            out[dayKey(day)] = back == 0 ? activity : DayActivity(activeKcal: 320 + 260 * wave, steps: 6_000 + Int(5_500 * wave), sleepHours: 6.2 + 1.6 * wave, exerciseMinutes: 15 + Int(40 * wave))
        }
        return out
    }

    static var photo: UIImage? {
        UserDefaults.standard.string(forKey: "demoPhoto").flatMap { UIImage(contentsOfFile: $0) }
    }

    static var data: AppData {
        var d = AppData()
        d.profile = Profile(onboarded: true, name: Lang.en ? "Alex" : "Minh", male: true, birthYear: 1994, heightCm: 170, weightKg: 72, activity: 1.375,
                            goal: Goal.lose, weeklyRateKg: 0.5, conditions: [], allergies: [], diet: Diet.none)
        let foods = (Bundle.main.url(forResource: "seed", withExtension: "json").flatMap { try? Data(contentsOf: $0) })
            .flatMap { try? JSONDecoder().decode([Food].self, from: $0) } ?? []
        let byId = Dictionary(uniqueKeysWithValues: foods.map { ($0.id, $0) })
        // Today, then the six days before it: (meal, food id, servings).
        let days: [[(String, String, Double)]] = Lang.en ? [
            [("breakfast", "trung_bac", 1), ("breakfast", "avocado_toast", 1), ("breakfast", "latte", 1), ("lunch", "caesar_salad", 1), ("snack", "tao", 1)],
            [("breakfast", "granola", 1), ("lunch", "poke", 1), ("dinner", "roast_chicken", 1), ("dinner", "bong_cai", 1), ("dinner", "quinoa", 1)],
            [("breakfast", "bagel_cream_cheese", 1), ("lunch", "burger", 1), ("snack", "frappuccino", 1), ("dinner", "salad", 1), ("dinner", "ca_hoi", 1)],
            [("breakfast", "yen_mach", 1), ("lunch", "wrap_ga", 1), ("dinner", "chicken_tikka", 1), ("dinner", "com_trang", 1)],
            [("breakfast", "pancake", 1), ("lunch", "tuna_sandwich", 1), ("dinner", "carbonara", 1)],
            [("breakfast", "omelette", 1), ("lunch", "bibimbap", 1), ("dinner", "ga_tay", 1), ("dinner", "khoai_lang", 1)],
            [("breakfast", "ngu_coc", 1), ("lunch", "sushi", 1), ("dinner", "chili_con_carne", 1), ("dinner", "com_gao_lut", 1)],
        ] : [
            [("breakfast", "pho_bo", 1), ("breakfast", "ca_phe_sua", 1), ("lunch", "com_ga", 1), ("lunch", "canh_rau", 1), ("snack", "chuoi", 1)],
            [("breakfast", "banh_mi_trung", 1), ("lunch", "bun_cha", 1), ("dinner", "ca_kho", 1), ("dinner", "com_trang", 1), ("dinner", "rau_luoc", 1)],
            [("breakfast", "xoi_man", 1), ("lunch", "com_tam", 1), ("snack", "tra_sua", 1), ("dinner", "canh_chua", 1), ("dinner", "com_trang", 1)],
            [("breakfast", "yen_mach", 1), ("lunch", "bun_bo_hue", 1), ("dinner", "uc_ga", 1), ("dinner", "salad", 1), ("dinner", "com_gao_lut", 1)],
            [("breakfast", "banh_cuon", 1), ("lunch", "mi_quang", 1), ("dinner", "thit_kho", 1), ("dinner", "com_trang", 1.5)],
            [("breakfast", "pho_ga", 1), ("lunch", "com_ga", 1), ("dinner", "ca_hap", 1), ("dinner", "com_trang", 1), ("dinner", "rau_muong_xao", 1)],
            [("breakfast", "banh_mi_thit", 1), ("lunch", "hu_tieu", 1), ("dinner", "bo_xao", 1), ("dinner", "com_trang", 1)],
        ]
        for (back, meals) in days.enumerated() {
            let key = dayKey(Calendar.current.date(byAdding: .day, value: -back, to: Date()) ?? Date())
            for (meal, id, qty) in meals {
                guard let f = byId[id] else { continue }
                d.entries.append(LogEntry(id: UUID(), date: key, meal: meal, foodId: f.id, name: f.name, emoji: f.emoji, serving: f.serving,
                                          kcal: f.kcal, protein: f.protein, carbs: f.carbs, fat: f.fat, tags: f.tags, qty: qty))
            }
        }
        // A month of weigh-ins trending down, and a workout today, for the Progress screen.
        d.weights = (0..<9).reversed().map { i in
            WeightEntry(date: dayKey(Calendar.current.date(byAdding: .day, value: -i * 4, to: Date()) ?? Date()), kg: 72 + Double(i) * 0.3 - (i % 2 == 0 ? 0 : 0.2))
        }
        d.profile.targetWeightKg = 68
        d.exercises = [ExerciseEntry(id: UUID(), date: dayKey(Date()), name: Lang.en ? "Running" : "Chạy bộ", emoji: "🏃", minutes: 30, kcal: 353)]
        return d
    }

    /// What the AI answers for the sample photo (a plate of cơm tấm).
    static let analysis: MealAnalysis? = {
        let json = Lang.en ? """
        {"title":"Broken rice with grilled pork chop","score":58,
         "pros":["Plenty of protein from the pork chop","Cucumber adds some fibre"],
         "cons":["High in fat and salt from the grilled pork and fish sauce","Few vegetables for the amount of rice"],
         "items":[
          {"name":"Cơm tấm sườn bì chả","kcal":650,"qty":1,"size":"medium","emoji":"🍚","alternatives":["Cơm sườn nướng"],"confidence":0.9},
          {"name":"Dưa leo","kcal":16,"qty":1,"size":"medium","emoji":"🥒","alternatives":[],"confidence":0.8},
          {"name":"Fish sauce dip","kcal":35,"qty":1,"size":"small","emoji":"🥣","portion":"1 small bowl","protein_g":1,"carbs_g":8,"fat_g":0,"sodium_mg":900,"alternatives":[],"confidence":0.7}
         ]}
        """ : """
        {"title":"Cơm tấm sườn bì chả","score":58,
         "pros":["Đủ đạm từ sườn và chả","Có dưa leo, thêm chất xơ"],
         "cons":["Nhiều chất béo và muối từ sườn nướng, nước mắm","Ít rau so với lượng cơm"],
         "items":[
          {"name":"Cơm tấm sườn bì chả","kcal":650,"qty":1,"size":"medium","emoji":"🍚","alternatives":["Cơm sườn nướng","Cơm tấm sườn"],"confidence":0.9},
          {"name":"Dưa leo","kcal":16,"qty":1,"size":"medium","emoji":"🥒","alternatives":[],"confidence":0.8},
          {"name":"Nước mắm pha","kcal":35,"qty":1,"size":"small","emoji":"🥣","portion":"1 chén nhỏ","protein_g":1,"carbs_g":8,"fat_g":0,"sodium_mg":900,"alternatives":[],"confidence":0.7}
         ]}
        """
        return try? JSONDecoder().decode(MealAnalysis.self, from: Data(json.utf8))
    }()

    static var coachTurns: [(fromUser: Bool, text: String)] {
        guard screen == "coach" else { return [] }
        if Lang.en {
            return [
                (true, "What should I eat tonight?"),
                (false, """
                You've had about 1,190 kcal today, which leaves roughly 500 kcal for your 0.5 kg-a-week goal, so a light, protein-rich dinner fits best:

                • Grilled salmon fillet (~200 kcal, 22 g protein)
                • A big green salad with lemon dressing (~90 kcal)
                • Half a cup of quinoa (~110 kcal)
                • A small Greek yogurt with berries (~100 kcal)

                Eat the salad first, then the salmon, and the quinoa last: it keeps blood sugar steady and you full for longer.
                """),
            ]
        }
        return [
            (true, "Tối nay tôi nên ăn gì?"),
            (false, """
            Hôm nay bạn đã nạp khoảng 1.320 kcal, còn khoảng 360 kcal theo mục tiêu giảm 0,5 kg/tuần, nên bữa tối nhẹ và nhiều đạm là hợp nhất:

            • 1 phần cá hấp (~180 kcal, 25 g đạm)
            • Nửa chén cơm gạo lứt (~80 kcal)
            • 1 tô canh chua (~90 kcal) và rau luộc thoải mái

            Ăn rau và canh trước, rồi đến đạm, cơm sau cùng để đường huyết lên chậm và no lâu hơn.
            """),
        ]
    }
}

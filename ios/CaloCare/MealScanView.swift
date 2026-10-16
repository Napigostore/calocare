import SwiftUI

/// One dish in a photo result. `item` holds the numbers for ONE serving; qty and size are the user's to change.
struct ScanDish: Identifiable {
    let id = UUID()
    /// Exactly what the AI answered, kept so a correction can be reported.
    let ai: DetectedItem
    var item: DetectedItem
    /// Set when the dish is one the app knows; its calories then come from the app's table.
    var food: Food?
    var checked = true
    var qty: Double
    var size: String
    var alternatives: [String]
    var corrected = false
    var userBuilt = false

    static let sizes: [(id: String, label: String, factor: Double)] = [("small", L("Nhỏ", "Small"), 0.75), ("medium", L("Vừa", "Medium"), 1.0), ("large", L("To", "Large"), 1.3)]

    /// Servings to log: the count, scaled by the size.
    var amount: Double { qty * (ScanDish.sizes.first { $0.id == size }?.factor ?? 1) }
    var kcal: Int { Int((Double(item.kcal) * amount).rounded()) }
    var fromTable: Bool { food != nil }
}

/// A meal photo being analysed and reviewed.
@MainActor
final class MealScan: ObservableObject, Identifiable {
    let id = UUID()
    let image: UIImage
    @Published var loading = true
    @Published var error: String?
    @Published var dishes: [ScanDish] = []
    @Published var title = ""
    @Published var score = -1
    @Published var pros: [String] = []
    @Published var cons: [String] = []
    private var started = false

    init(image: UIImage) { self.image = image }

    func run(store: AppStore) async {
        guard !started else { return }
        started = true
        if Demo.active, let analysis = Demo.analysis {
            dishes = analysis.items.map { MealScan.ground($0, foods: store.foods) }
            title = analysis.title ?? ""
            score = analysis.score ?? -1
            pros = analysis.pros ?? []
            cons = analysis.cons ?? []
            loading = false
            return
        }
        guard let jpeg = image.jpegData(compressionQuality: 0.8) else { error = L("Không xử lý được ảnh.", "Couldn't process the photo."); loading = false; return }
        do {
            let analysis = try await API.shared.analyzePhoto(jpeg: jpeg, context: store.photoContext)
            dishes = analysis.items.map { MealScan.ground($0, foods: store.foods) }
            title = analysis.title ?? ""
            score = analysis.score ?? -1
            pros = analysis.pros ?? []
            cons = analysis.cons ?? []
            if dishes.isEmpty { error = L("AI không nhận ra món nào trong ảnh. Bạn có thể tự thêm món bên dưới.", "The AI found no food in the photo. You can add items below.") }
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    /// The AI says what the dish is and how much; the calories come from the app's table whenever the dish is in it.
    static func ground(_ ai: DetectedItem, foods: [Food]) -> ScanDish {
        let food = FoodMatcher.best(ai.name, in: foods)
        let qty = max(0.5, (min(max(ai.qty ?? 1, 0.5), 10) * 2).rounded() / 2)
        let size = ScanDish.sizes.contains { $0.id == ai.size } ? (ai.size ?? "medium") : "medium"
        return ScanDish(ai: ai, item: food.map { ai.like($0) } ?? ai, food: food, qty: qty, size: size, alternatives: ai.alternatives ?? [])
    }
}

extension DetectedItem {
    /// This dish with the name and numbers of a food the app knows.
    func like(_ food: Food) -> DetectedItem {
        var copy = self
        copy.name = food.label
        copy.emoji = food.emoji
        copy.portion = food.servingLabel
        copy.kcal = food.kcal
        copy.protein_g = food.protein
        copy.carbs_g = food.carbs
        copy.fat_g = food.fat
        copy.course = food.course
        copy.tags = food.tags
        return copy
    }
}

struct MealScanView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var scan: MealScan
    @State private var editing: EditTarget?

    /// Which dish the edit sheet is for; nil index means "add a dish the AI missed".
    struct EditTarget: Identifiable {
        let id = UUID()
        var index: Int?
    }

    var body: some View {
        let picked = scan.dishes.filter { $0.checked }
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Image(uiImage: scan.image).resizable().scaledToFill().frame(height: 200).frame(maxWidth: .infinity).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                    if scan.loading { HStack { ProgressView(); Text(L("AI đang nhận diện món ăn…", "The AI is identifying the food…")) } }
                    if let error = scan.error { Text(error).foregroundColor(.red) }
                    if !picked.isEmpty { summary(picked) }
                    ForEach(scan.dishes.indices, id: \.self) { index in dishCard(index) }
                    if !scan.loading {
                        // Always there: the AI can miss a dish, or recognise nothing at all.
                        Button { editing = EditTarget(index: nil) } label: {
                            Label(scan.dishes.isEmpty ? L("Tự thêm món", "Add items yourself") : L("Thêm món AI bỏ sót", "Add an item the AI missed"), systemImage: "plus").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 6)
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(L("📷 Bữa ăn của bạn", "📷 Your meal"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("Đóng", "Close")) { dismiss() } } }
            .safeAreaInset(edge: .bottom) {
                if !scan.loading && !scan.dishes.isEmpty {
                    VStack(spacing: 8) {
                        Picker(L("Bữa", "Meal"), selection: $store.meal) { ForEach(Meal.all, id: \.id) { Text($0.label).tag($0.id) } }.pickerStyle(.segmented)
                        Button { commit(picked) } label: {
                            Text(L("Thêm vào nhật ký · \(picked.reduce(0) { $0 + $1.kcal }) kcal", "Add to log · \(picked.reduce(0) { $0 + $1.kcal }) kcal")).frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent).disabled(picked.isEmpty)
                    }
                    .padding(16).background(.bar)
                }
            }
        }
        .task { await scan.run(store: store) }
        .sheet(item: $editing) { target in
            DishEditor(scan: scan, index: target.index)
        }
    }

    /// The whole tray at a glance: what it is, total energy, a health grade, the nutrients, good and bad points.
    private func summary(_ picked: [ScanDish]) -> some View {
        func total(_ value: (DetectedItem) -> Double?) -> Int { Int(picked.reduce(0) { $0 + (value($1.item) ?? 0) * $1.amount }.rounded()) }
        return Card(scan.title.isEmpty ? nil : scan.title) {
            HStack {
                VStack(alignment: .leading) {
                    Text("\(picked.reduce(0) { $0 + $1.kcal }) kcal").font(.system(size: 34, weight: .bold))
                    Text(L("\(picked.count) món", "\(picked.count) item(s)")).font(.caption).foregroundColor(.secondary)
                }
                Spacer()
                if (0...100).contains(scan.score) { GradeBadge(score: scan.score) }
            }
            HStack {
                nutrient(L("Đạm", "Protein"), "\(total { $0.protein_g })g"); nutrient(L("Tinh bột", "Carbs"), "\(total { $0.carbs_g })g"); nutrient(L("Chất béo", "Fat"), "\(total { $0.fat_g })g")
            }
            HStack {
                nutrient(L("Chất xơ", "Fibre"), "\(total { $0.fiber_g })g"); nutrient(L("Đường", "Sugar"), "\(total { $0.sugar_g })g"); nutrient(L("Natri", "Sodium"), "\(total { $0.sodium_mg })mg")
            }
            ForEach(scan.pros, id: \.self) { Text("＋ \($0)").font(.caption).foregroundColor(Palette.green) }
            ForEach(scan.cons, id: \.self) { Text("－ \($0)").font(.caption).foregroundColor(Palette.warn) }
        }
    }

    private func nutrient(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading) {
            Text(value).bold()
            Text(label).font(.caption2).foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func dishCard(_ index: Int) -> some View {
        let dish = scan.dishes[index]
        return Card {
            HStack(alignment: .top) {
                Toggle("", isOn: $scan.dishes[index].checked).labelsHidden()
                Text(dish.item.emoji ?? "🍽️").font(.title)
                VStack(alignment: .leading, spacing: 2) {
                    Text(dish.item.name).font(.headline)
                    Text("\(dish.item.kcal) kcal / \(dish.item.portion ?? L("1 phần", "1 serving"))" + ((dish.item.grams ?? 0) > 0 ? L(" · trong ảnh ≈ \(dish.item.grams ?? 0)g", " · in photo ≈ \(dish.item.grams ?? 0)g") : ""))
                        .font(.caption).foregroundColor(.secondary)
                    Text(dish.fromTable ? L("✓ Calo theo bảng món ăn của app", "✓ Calories from the app's food table") : L("≈ AI ước tính · chắc \(Int((dish.item.confidence ?? 0.5) * 100))%", "≈ AI estimate · \(Int((dish.item.confidence ?? 0.5) * 100))% sure"))
                        .font(.caption2).foregroundColor(dish.fromTable ? Palette.green : Palette.warn)
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text("\(dish.kcal) kcal").bold().foregroundColor(Palette.green)
                    // The AI can mistake one dish for another: a clear way to say what it really is.
                    Button { editing = EditTarget(index: index) } label: { Image(systemName: "pencil.circle.fill").font(.title) }
                }
            }
            HStack {
                // A large egg is not a small egg: the size scales the calories of one serving.
                Picker(L("Cỡ", "Size"), selection: $scan.dishes[index].size) { ForEach(ScanDish.sizes, id: \.id) { Text($0.label).tag($0.id) } }.pickerStyle(.segmented)
                Stepper("× \(formatQty(dish.qty))", value: $scan.dishes[index].qty, in: 0.5...20, step: 0.5).fixedSize()
            }
            if !dish.alternatives.isEmpty {
                // One tap instead of typing when the AI picked the wrong look-alike.
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        Text(L("Hay là:", "Or maybe:")).font(.caption).foregroundColor(.secondary)
                        ForEach(dish.alternatives, id: \.self) { name in
                            Button(name) { pickAlternative(index, name) }.buttonStyle(.bordered).font(.caption)
                        }
                    }
                }
            }
            ForEach(Nutrition.warnings(tags: dish.item.tags ?? [], profile: store.data.profile), id: \.self) { WarningLine(warning: $0) }
        }
    }

    private func pickAlternative(_ index: Int, _ name: String) {
        let previous = scan.dishes[index].item.name
        if let food = FoodMatcher.best(name, in: store.foods) {
            scan.dishes[index].item = scan.dishes[index].item.like(food)
            scan.dishes[index].food = food
        } else {
            scan.dishes[index].item.name = name
            scan.dishes[index].food = nil
        }
        scan.dishes[index].corrected = true
        // The dish it was becomes an alternative, so a mis-tap is one tap to undo.
        scan.dishes[index].alternatives = scan.dishes[index].alternatives.filter { $0 != name } + [previous]
    }

    private func commit(_ picked: [ScanDish]) {
        for dish in picked {
            let food = dish.food ?? dish.item.asFood(id: "ai_\(UUID().uuidString)")
            store.add(food, qty: dish.amount, quiet: true)
            // Share what the dish really was (names only) so the next person need not correct it.
            if dish.corrected && dish.item.name.lowercased() != dish.ai.name.lowercased() {
                Task { await API.shared.sendCorrection(ai: dish.ai, chosen: dish.item.name) }
            }
            if dish.userBuilt && dish.food == nil { Task { await API.shared.submitDish(food) } }
        }
        store.say(L("Đã thêm \(picked.count) món vào bữa \(Meal.label(store.meal).lowercased())", "Added \(picked.count) item(s) to \(Meal.label(store.meal).lowercased())"))
        dismiss()
    }
}

struct GradeBadge: View {
    var score: Int

    var body: some View {
        let grade: (letter: String, label: String, color: Color) =
            score >= 80 ? ("A", L("Rất tốt", "Very good"), Palette.green) : score >= 65 ? ("B", L("Tốt", "Good"), .green) : score >= 50 ? ("C", L("Trung bình", "Average"), Palette.carbs)
            : score >= 35 ? ("D", L("Chưa tốt", "Poor"), Palette.orange) : ("E", L("Kém", "Bad"), .red)
        VStack(spacing: 2) {
            Text(grade.letter).font(.title.bold()).foregroundColor(.white).frame(width: 52, height: 52).background(grade.color, in: Circle())
            Text("\(grade.label) · \(score)").font(.caption2)
        }
    }
}

/// Says what a dish is. With an index it corrects that dish; without, it adds one the AI missed.
/// Three ways in: pick a food the app knows, describe the dish for the AI, or adjust its ingredients by weight.
struct DishEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var scan: MealScan
    var index: Int?
    @State private var text = ""
    @State private var busy = false
    @State private var showIngredients = false

    var body: some View {
        let matches = FoodMatcher.ranked(text, in: store.foods).prefix(5).map { $0.food }
        NavigationStack {
            Form {
                Section {
                    // The keyboard's microphone key dictates in Vietnamese.
                    TextField(L("Tên món hoặc mô tả", "Name or description"), text: $text, axis: .vertical).lineLimit(1...4)
                } footer: {
                    Text(L("Ví dụ: “bánh khọt 5 cái” · “hủ tiếu: thịt 100g, rau một ít, bún 100g, nước xương 400ml” · “tương đương 1 tô phở”", "E.g. “5 chicken wings” · “salad: chicken 100g, half an avocado, dressing 2 tbsp” · “about the size of a burger”"))
                }
                if !matches.isEmpty {
                    Section(L("Món có sẵn (dùng calo của món đó)", "Known foods (uses their calories)")) {
                        ForEach(matches) { food in
                            Button { choose(food) } label: {
                                HStack {
                                    Text(food.emoji)
                                    VStack(alignment: .leading) {
                                        Text(food.label).foregroundColor(.primary)
                                        Text("\(food.kcal) kcal / \(food.servingLabel)").font(.caption).foregroundColor(.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
                Section {
                    Button { describe() } label: { HStack { Text(L("✨ AI tính calo theo mô tả này", "✨ AI calories from this description")); if busy { Spacer(); ProgressView() } } }
                        .disabled(busy || text.trimmingCharacters(in: .whitespaces).isEmpty)
                    if index != nil {
                        Button(L("🧮 Chỉnh theo thành phần (thịt, rau… bao nhiêu gam)", "🧮 Adjust by ingredients (grams of meat, veg…)")) { showIngredients = true }.disabled(busy)
                        Button(L("Chỉ đổi tên, giữ calo", "Rename only, keep calories")) { rename() }.disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            .navigationTitle(index == nil ? L("Thêm món", "Add item") : L("Đây là món gì?", "What is this?"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("Hủy", "Cancel")) { dismiss() } } }
            .onAppear { if let index, text.isEmpty { text = scan.dishes[index].item.name } }
            .sheet(isPresented: $showIngredients) {
                if let index {
                    let dish = scan.dishes[index]
                    IngredientEditor(title: dish.item.name, description: "\(dish.item.name): \(formatQty(dish.amount)) × \(dish.item.portion ?? L("1 phần", "1 serving"))") { totals in
                        scan.dishes[index].item.kcal = totals.kcal
                        scan.dishes[index].item.protein_g = totals.protein
                        scan.dishes[index].item.carbs_g = totals.carbs
                        scan.dishes[index].item.fat_g = totals.fat
                        scan.dishes[index].item.portion = totals.portion
                        scan.dishes[index].item.grams = 0
                        scan.dishes[index].food = nil
                        scan.dishes[index].qty = 1
                        scan.dishes[index].size = "medium"
                        scan.dishes[index].userBuilt = true
                        dismiss()
                    }
                }
            }
        }
    }

    private func choose(_ food: Food) {
        if let index {
            scan.dishes[index].item = scan.dishes[index].item.like(food)
            scan.dishes[index].food = food
            scan.dishes[index].corrected = true
        } else {
            let item = DetectedItem(name: food.name, kcal: food.kcal, confidence: 1).like(food)
            scan.dishes.append(ScanDish(ai: item, item: item, food: food, qty: 1, size: "medium", alternatives: []))
            scan.error = nil
        }
        dismiss()
    }

    private func rename() {
        guard let index else { return }
        scan.dishes[index].item.name = text.trimmingCharacters(in: .whitespaces)
        scan.dishes[index].food = nil
        scan.dishes[index].corrected = true
        dismiss()
    }

    private func describe() {
        busy = true
        Task {
            do {
                if var item = try await API.shared.estimate(text.trimmingCharacters(in: .whitespaces)) {
                    // The description already carries the amount and size, so the numbers are taken as they are.
                    item.qty = 1
                    item.size = "medium"
                    if let index {
                        scan.dishes[index].item = item
                        scan.dishes[index].food = nil
                        scan.dishes[index].qty = 1
                        scan.dishes[index].size = "medium"
                        scan.dishes[index].alternatives = []
                        scan.dishes[index].corrected = true
                    } else {
                        scan.dishes.append(ScanDish(ai: item, item: item, food: nil, qty: 1, size: "medium", alternatives: []))
                        scan.error = nil
                    }
                    dismiss()
                } else {
                    store.say(L("AI không nhận ra đây là món ăn. Hãy mô tả rõ hơn.", "The AI doesn't recognise this as a food. Please describe it more clearly."))
                }
            } catch {
                store.say(error.localizedDescription)
            }
            busy = false
        }
    }
}

struct DishTotals {
    var kcal: Int
    var protein: Double
    var carbs: Double
    var fat: Double
    var portion: String
}

/// Lets someone who knows roughly what went into a dish get its calories without knowing any nutrition:
/// the AI proposes the ingredients with weights, the user only moves the weights.
struct IngredientEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    var title: String
    var description: String
    var onApply: (DishTotals) -> Void
    @State private var rows: [Ingredient] = []
    @State private var loading = true
    @State private var extra = ""
    @State private var adding = false

    /// "Total: 520 kcal · 450g · Protein 30g · Carbs 60g · Fat 15g", built in steps the type checker handles quickly.
    private func totalsLine(_ kcal: Int) -> String {
        let grams: Int = rows.reduce(0) { $0 + $1.grams }
        let protein = Int(rows.reduce(0.0) { $0 + $1.protein })
        let carbs = Int(rows.reduce(0.0) { $0 + $1.carbs })
        let fat = Int(rows.reduce(0.0) { $0 + $1.fat })
        let parts: [String] = [
            "\(L("Tổng", "Total")): \(kcal) kcal", "\(grams)g", "\(L("Đạm", "Protein")) \(protein)g", "\(L("Bột", "Carbs")) \(carbs)g", "\(L("Béo", "Fat")) \(fat)g",
        ]
        return parts.joined(separator: " · ")
    }

    var body: some View {
        let kcal = Int(rows.reduce(0) { $0 + $1.kcal }.rounded())
        NavigationStack {
            Form {
                Section {
                    if loading { HStack { ProgressView(); Text(L("AI đang tách món thành các thành phần…", "The AI is splitting the dish into ingredients…")) } }
                    ForEach(rows.indices, id: \.self) { i in
                        VStack(alignment: .leading) {
                            HStack {
                                Text(rows[i].name).font(.headline)
                                Spacer()
                                Text("\(Int(rows[i].kcal.rounded())) kcal").foregroundColor(Palette.green).bold()
                            }
                            // Small amounts (herbs, oil) move in 10 g steps, the rest in half-lạng steps.
                            Stepper(weight(rows[i].grams), value: $rows[i].grams, in: 5...3000, step: rows[i].grams <= 50 ? 10 : 50)
                        }
                    }
                    .onDelete { rows.remove(atOffsets: $0) }
                } header: {
                    Text(L("Chỉnh số gam cho giống phần bạn ăn. Vuốt trái để bỏ một thành phần.", "Adjust the grams to match your portion. Swipe left to remove an ingredient."))
                } footer: {
                    if !rows.isEmpty {
                        Text(totalsLine(kcal))
                    }
                }
                Section(L("Thêm thành phần", "Add ingredient")) {
                    TextField(L("măng 2 lạng", "rice 150g"), text: $extra)
                    Button { add() } label: { HStack { Text(L("＋ Thêm", "＋ Add")); if adding { Spacer(); ProgressView() } } }
                        .disabled(adding || extra.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .navigationTitle(title.isEmpty ? L("Thành phần", "Ingredients") : title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Hủy", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Dùng \(kcal) kcal", "Use \(kcal) kcal")) {
                        onApply(DishTotals(kcal: kcal, protein: rows.reduce(0) { $0 + $1.protein }, carbs: rows.reduce(0) { $0 + $1.carbs },
                                           fat: rows.reduce(0) { $0 + $1.fat }, portion: rows.map { "\($0.name) \($0.grams)g" }.joined(separator: ", ")))
                        dismiss()
                    }
                    .disabled(rows.isEmpty || kcal <= 0)
                }
            }
            .task {
                guard loading else { return }
                do { rows = try await API.shared.breakdown(description) } catch { store.say(error.localizedDescription) }
                loading = false
            }
        }
    }

    private func weight(_ grams: Int) -> String {
        grams >= 100 && grams % 50 == 0 && !Lang.en ? "\(grams)g (\(formatQty(Double(grams) / 100)) lạng)" : "\(grams)g"
    }

    private func add() {
        adding = true
        Task {
            do {
                let more = try await API.shared.breakdown(extra.trimmingCharacters(in: .whitespaces))
                if more.isEmpty { store.say(L("AI không tách được thành phần. Hãy ghi rõ hơn, ví dụ “thịt vịt 300g”.", "The AI couldn't split this into ingredients. Be more specific, e.g. “chicken breast 200g”.")) } else { rows += more; extra = "" }
            } catch {
                store.say(error.localizedDescription)
            }
            adding = false
        }
    }
}

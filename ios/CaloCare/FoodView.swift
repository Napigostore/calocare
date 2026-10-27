import PhotosUI
import SwiftUI

struct FoodView: View {
    @EnvironmentObject var store: AppStore
    @State private var query = ""
    @State private var suggestion: Food?
    @State private var busy = false
    @State private var note = ""
    @State private var showCamera = false
    @State private var pickedPhoto: PhotosPickerItem?
    @State private var scan: MealScan?
    @State private var editing: Food?

    @State private var category = "all"
    @State private var online: [Food] = []
    @State private var onlineBusy = false

    private let columns = [GridItem(.adaptive(minimum: 100), spacing: 10)]

    private static var categories: [(id: String, label: String)] {
        [("all", L("Tất cả", "All")), ("main", L("Món chính", "Mains")), ("protein", L("Đạm", "Protein")), ("veg", L("Rau & canh", "Veg & soups")),
         ("fruit", L("Trái cây", "Fruit")), ("drink", L("Đồ uống", "Drinks")), ("snack", L("Ăn vặt", "Snacks")), ("mine", L("Của tôi", "Mine"))]
    }

    private func inCategory(_ food: Food) -> Bool {
        switch category {
        case "all": return true
        case "veg": return food.course == "veg" || food.course == "soup"
        case "snack": return food.course == "snack" || food.course == "dessert"
        case "mine": return food.custom
        default: return food.course == category
        }
    }

    var body: some View {
        let searching = !query.trimmingCharacters(in: .whitespaces).isEmpty
        let folded = FoodMatcher.fold(query)
        let matches = searching ? store.foods.filter { FoodMatcher.fold($0.name).contains(folded) || FoodMatcher.fold($0.label).contains(folded) } : store.foods.filter(inCategory)
        let usual = searching || category != "all" ? [] : store.frequentFoods(for: store.meal)
        let matchIds = Set(matches.map { $0.id })
        let similar = searching ? FoodMatcher.ranked(query, in: store.foods).filter { $0.score >= 0.3 && !matchIds.contains($0.food.id) }.prefix(6).map { $0.food } : []
        let mealEntries = store.todaysEntries.filter { $0.meal == store.meal }
        let counts: [String: Double] = Dictionary(grouping: mealEntries, by: { $0.foodId }).mapValues { $0.reduce(0) { $0 + $1.qty } }

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    DayPicker()
                    Picker(L("Bữa", "Meal"), selection: $store.meal) {
                        ForEach(Meal.all, id: \.id) { Text($0.label).tag($0.id) }
                    }
                    .pickerStyle(.segmented)

                    HStack {
                        // The iOS keyboard's own microphone key dictates dish names in Vietnamese.
                        TextField(L("Tìm hoặc nói tên món…", "Search or dictate a food…"), text: $query)
                            .textFieldStyle(.roundedBorder)
                            .onChange(of: query) { _ in suggestion = nil; note = "" }
                        Button { showCamera = true } label: { Image(systemName: "camera.fill").font(.title2) }
                            .buttonStyle(.borderedProminent)
                        PhotosPicker(selection: $pickedPhoto, matching: .images) { Image(systemName: "photo.on.rectangle").font(.title2) }
                            .buttonStyle(.bordered)
                    }

                    if !searching && !mealEntries.isEmpty { MealLog(entries: mealEntries) }

                    if !searching {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                ForEach(Self.categories, id: \.id) { c in
                                    Button(c.label) { category = c.id }
                                        .buttonStyle(.bordered).tint(category == c.id ? Palette.green : .secondary)
                                }
                            }
                        }
                    }
                    if !usual.isEmpty {
                        Text(L("⭐ Món quen của bạn", "⭐ Your usual foods")).font(.headline)
                        LazyVGrid(columns: columns, spacing: 10) {
                            ForEach(usual) { food in FoodTile(food: food, count: counts[food.id] ?? 0) { editing = $0 } }
                        }
                    }

                    Text(searching ? L("Món khớp tên", "Matching foods") : L("Đang chọn cho bữa \(Meal.label(store.meal).lowercased()) · chạm = +1 phần · giữ để sửa", "Adding to \(Meal.label(store.meal).lowercased()) · tap = +1 serving · hold to edit"))
                        .font(.caption).foregroundColor(.secondary)
                    LazyVGrid(columns: columns, spacing: 10) {
                        ForEach(matches) { food in FoodTile(food: food, count: counts[food.id] ?? 0) { editing = $0 } }
                    }

                    if searching {
                        if !similar.isEmpty {
                            Text(L("Món gần giống", "Similar foods")).font(.headline)
                            LazyVGrid(columns: columns, spacing: 10) {
                                ForEach(similar) { food in FoodTile(food: food, count: counts[food.id] ?? 0) { editing = $0 } }
                            }
                        }
                        newDishCard
                        if !online.isEmpty {
                            Text(L("🌐 Sản phẩm trên Open Food Facts", "🌐 Products on Open Food Facts")).font(.headline)
                            LazyVGrid(columns: columns, spacing: 10) {
                                ForEach(online) { food in FoodTile(food: food, count: counts[food.id] ?? 0) { editing = $0 } }
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(L("Nhật ký ăn uống", "Food log"))
            .navigationBarTitleDisplayMode(.inline)
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { image in start(image) }.ignoresSafeArea()
        }
        .onChange(of: pickedPhoto) { item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) { start(image) }
                pickedPhoto = nil
            }
        }
        .sheet(item: $scan) { scan in MealScanView(scan: scan) }
        .onAppear {
            if Demo.screen == "scan", scan == nil, let photo = Demo.photo { scan = MealScan(image: photo) }
        }
        .onChange(of: store.demoScan) { on in
            if on, let photo = Demo.photo { scan = MealScan(image: photo) } else if !on { scan = nil }
        }
        .sheet(item: $editing) { food in FoodEditor(food: food) }
        // A food that conflicts with a declared allergy is only added once the user confirms.
        .confirmationDialog(L("Cảnh báo dị ứng", "Allergy warning"), isPresented: Binding(get: { store.pendingDanger != nil }, set: { if !$0 { store.pendingDanger = nil } }),
                            titleVisibility: .visible, presenting: store.pendingDanger) { food in
            Button(L("Vẫn thêm", "Add anyway"), role: .destructive) { store.add(food); store.pendingDanger = nil }
            Button(L("Không thêm", "Don't add"), role: .cancel) { store.pendingDanger = nil }
        } message: { food in
            Text("\(food.emoji) \(food.label)\n" + Nutrition.warnings(tags: food.tags, profile: store.data.profile).filter { $0.danger }.map { $0.text }.joined(separator: "\n"))
        }
    }

    /// For a dish the app does not have yet: the AI suggests its calories, the user adds or adjusts it.
    private var newDishCard: some View {
        Card(L("Món mới: “\(query.trimmingCharacters(in: .whitespaces))”", "New food: “\(query.trimmingCharacters(in: .whitespaces))”")) {
            Text(L("AI gợi ý calo từ tên món. Bạn cũng có thể mô tả: “bún 100g, thịt 100g, rau một ít” hoặc “tương đương 1 tô phở”.", "The AI estimates calories from the name. You can also describe it: “pasta 100g, chicken 100g, some veg” or “about the size of a burger”."))
                .font(.caption).foregroundColor(.secondary)
            HStack {
                Button(L("✨ AI gợi ý calo", "✨ AI calorie estimate")) { suggest() }.buttonStyle(.borderedProminent).disabled(busy)
                Button(L("＋ Tự nhập", "＋ Enter manually")) {
                    editing = Food(id: "custom_\(UUID().uuidString)", name: query.trimmingCharacters(in: .whitespaces), emoji: "🍽️", serving: L("1 phần", "1 serving"), kcal: 0,
                                   protein: 0, carbs: 0, fat: 0, course: "main", tags: [], region: "all", custom: true)
                }
                .buttonStyle(.bordered)
                if busy || onlineBusy { ProgressView() }
            }
            Button(L("🌐 Tìm sản phẩm đóng gói", "🌐 Find packaged product")) { searchOnline() }.buttonStyle(.bordered).disabled(onlineBusy)
            if let food = suggestion {
                VStack(alignment: .leading, spacing: 6) {
                    Text("\(food.emoji) \(food.label)").font(.headline)
                    Text("\(food.kcal) kcal / \(food.servingLabel)").bold()
                    Text(L("Đạm \(Int(food.protein))g · Tinh bột \(Int(food.carbs))g · Béo \(Int(food.fat))g · AI ước tính", "Protein \(Int(food.protein))g · Carbs \(Int(food.carbs))g · Fat \(Int(food.fat))g · AI estimate")).font(.caption)
                    ForEach(Nutrition.warnings(tags: food.tags, profile: store.data.profile), id: \.self) { WarningLine(warning: $0) }
                    HStack {
                        Button(L("＋ Thêm vào bữa \(Meal.label(store.meal).lowercased())", "＋ Add to \(Meal.label(store.meal).lowercased())")) { store.add(food); store.saveCustom(food) }.buttonStyle(.borderedProminent)
                        Button(L("✏️ Sửa rồi lưu", "✏️ Edit, then save")) { editing = food }.buttonStyle(.bordered)
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Palette.green.opacity(0.15), in: RoundedRectangle(cornerRadius: 14))
            }
            if !note.isEmpty { Text(note).font(.caption).foregroundColor(.secondary) }
        }
    }

    private func searchOnline() {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        onlineBusy = true
        Task {
            do {
                online = try await FoodSearch.search(text)
                if online.isEmpty { note = L("Không thấy sản phẩm nào trên Open Food Facts. Thử “AI gợi ý calo”.", "Nothing found on Open Food Facts. Try “AI calorie estimate”.") }
            } catch {
                note = L("Không tìm được trên mạng. Kiểm tra kết nối.", "Online search failed. Check your connection.")
            }
            onlineBusy = false
        }
    }

    private func suggest() {
        let text = query.trimmingCharacters(in: .whitespaces)
        busy = true
        note = ""
        Task {
            do {
                if let item = try await API.shared.estimate(text) {
                    suggestion = item.asFood(id: "ai_\(UUID().uuidString)")
                } else {
                    note = L("AI không nhận ra đây là món ăn. Hãy thử mô tả rõ hơn.", "The AI doesn't recognise this as a food. Try describing it more clearly.")
                }
            } catch {
                note = error.localizedDescription
            }
            busy = false
        }
    }

    private func start(_ image: UIImage) {
        let wasCamera = showCamera
        showCamera = false
        let next = MealScan(image: image.squared(side: 768))
        // The result sheet cannot open while the camera is still closing.
        DispatchQueue.main.asyncAfter(deadline: .now() + (wasCamera ? 0.6 : 0)) { scan = next }
    }
}

extension DetectedItem {
    /// One serving of this dish as a food the diary can log.
    func asFood(id: String) -> Food {
        Food(id: id, name: name, emoji: (emoji ?? "").isEmpty ? "🍽️" : (emoji ?? "🍽️"), serving: portion ?? L("1 phần", "1 serving"), kcal: kcal, protein: protein_g ?? 0,
             carbs: carbs_g ?? 0, fat: fat_g ?? 0, course: course ?? "main", tags: tags ?? [], region: "all", custom: true)
    }
}

private struct FoodTile: View {
    @EnvironmentObject var store: AppStore
    var food: Food
    var count: Double
    var onEdit: (Food) -> Void = { _ in }

    var body: some View {
        Button { store.tap(food) } label: {
            VStack(spacing: 4) {
                FoodPhoto(id: food.id, emoji: food.emoji, size: 64)
                Text(food.label).font(.caption.weight(.medium)).multilineTextAlignment(.center).lineLimit(2, reservesSpace: true)
                Text("\(food.kcal) kcal").font(.caption2.weight(.semibold)).foregroundColor(Palette.green)
                Text(food.servingLabel).font(.caption2).foregroundColor(.secondary).lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10).padding(.horizontal, 4)
            .background(count > 0 ? Palette.green.opacity(0.18) : Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18))
            .overlay(alignment: .topTrailing) {
                if count > 0 {
                    Text("×\(formatQty(count))").font(.caption2.bold()).foregroundColor(.white)
                        .padding(.horizontal, 7).padding(.vertical, 2)
                        .background(Palette.green, in: Capsule()).padding(4)
                }
            }
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button { store.add(food) } label: { Label(L("Thêm 1 phần", "Add a serving"), systemImage: "plus") }
            Button { store.add(food, qty: 0.5) } label: { Label(L("Thêm nửa phần", "Add half a serving"), systemImage: "circle.lefthalf.filled") }
            Button { onEdit(food) } label: { Label(L("Sửa thông tin món", "Edit food"), systemImage: "pencil") }
        }
    }
}

/// What is already logged for the selected meal, with quantity steppers.
private struct MealLog: View {
    @EnvironmentObject var store: AppStore
    var entries: [LogEntry]
    @State private var showOrder = false

    var body: some View {
        Card(L("Bữa \(Meal.label(store.meal).lowercased()) · \(Int(store.totals(entries).kcal)) kcal", "\(Meal.label(store.meal)) · \(Int(store.totals(entries).kcal)) kcal")) {
            ForEach(entries) { entry in
                HStack {
                    FoodPhoto(id: entry.foodId, emoji: entry.emoji, size: 40)
                    VStack(alignment: .leading) {
                        Text(entry.label).font(.subheadline.weight(.medium)).lineLimit(1)
                        Text("\(Int(entry.totalKcal)) kcal · \(entry.servingLabel)").font(.caption).foregroundColor(.secondary).lineLimit(1)
                    }
                    Spacer()
                    Button { store.changeQty(entry, by: -0.5) } label: { Image(systemName: "minus.circle.fill") }.buttonStyle(.plain)
                    Text(formatQty(entry.qty)).bold().frame(width: 36)
                    Button { store.changeQty(entry, by: 0.5) } label: { Image(systemName: "plus.circle.fill") }.buttonStyle(.plain)
                }
                .foregroundColor(.primary)
            }
            if entries.count >= 2 {
                Button(showOrder ? L("Ẩn thứ tự", "Hide order") : L("🍽️ Nên ăn theo thứ tự", "🍽️ Best eating order")) { showOrder.toggle() }.font(.subheadline)
                if showOrder {
                    let steps = Nutrition.eatingOrder(entries, courseOf: store.course(of:), profile: store.data.profile)
                    ForEach(steps.indices, id: \.self) { i in
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(i + 1). \(steps[i].emoji) \(steps[i].name)").font(.subheadline.weight(.medium))
                            Text(steps[i].why).font(.caption).foregroundColor(.secondary)
                        }
                    }
                }
            }
        }
    }
}

/// Create or adjust one of the user's own dishes. Calories follow the macros, and the ingredient
/// editor works the numbers out for someone who does not know them.
struct FoodEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State var food: Food
    @State private var showIngredients = false
    @State private var substitution: Substitution?
    @State private var loadingSubstitution = false

    var body: some View {
        NavigationStack {
            Form {
                Section(L("Món ăn", "Food")) {
                    TextField(L("Tên món", "Name"), text: $food.name)
                    TextField(L("Khẩu phần", "Serving"), text: $food.serving)
                }
                Section(L("Dinh dưỡng mỗi phần", "Nutrition per serving")) {
                    number("Calo (kcal)", Binding(get: { Double(food.kcal) }, set: { food.kcal = Int($0) }))
                    number(L("Đạm (g)", "Protein (g)"), macro(\.protein))
                    number(L("Tinh bột (g)", "Carbs (g)"), macro(\.carbs))
                    number(L("Chất béo (g)", "Fat (g)"), macro(\.fat))
                    Text(L("Đổi đạm, bột, béo thì calo tự tính lại.", "Change protein, carbs or fat and the calories update.")).font(.caption).foregroundColor(.secondary)
                    Button(L("🧮 Tính theo thành phần (thịt, rau… bao nhiêu gam)", "🧮 Work it out from ingredients (grams of meat, veg…)")) { showIngredients = true }
                        .disabled(food.name.trimmingCharacters(in: .whitespaces).isEmpty)
                }

                if !food.custom {
                    Section(L("💡 Gợi ý thay thế", "💡 Healthier swap")) {
                        if let sub = substitution {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(sub.name).font(.headline)
                                        Text(sub.reason).font(.caption).foregroundColor(.secondary)
                                    }
                                    Spacer()
                                }
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(L("Calo", "Kcal")).font(.caption2)
                                        Text("\(sub.kcal)").font(.headline.monospacedDigit())
                                            .foregroundColor(sub.kcal < food.kcal ? Palette.green : .secondary)
                                    }
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(L("Đạm", "Protein")).font(.caption2)
                                        Text("\(String(format: "%.1f", sub.protein_g))g").font(.caption.monospacedDigit())
                                            .foregroundColor(sub.protein_g > food.protein ? Palette.protein : .secondary)
                                    }
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(L("Chất xơ", "Fiber")).font(.caption2)
                                        Text("✓").font(.headline).foregroundColor(Palette.green)
                                    }
                                    Spacer()
                                }
                                ForEach(sub.benefits, id: \.self) { b in
                                    Text("• " + b).font(.caption).foregroundColor(.secondary)
                                }
                                Button(L("Chọn món này", "Use this swap")) {
                                    food.name = sub.name
                                    food.kcal = sub.kcal
                                    food.protein = sub.protein_g
                                    food.carbs = sub.carbs_g
                                    food.fat = sub.fat_g
                                    substitution = nil
                                }
                                .buttonStyle(.bordered)
                                .frame(maxWidth: .infinity, alignment: .center)
                            }
                            .padding(.vertical, 8)
                        } else if loadingSubstitution {
                            ProgressView().frame(maxWidth: .infinity, alignment: .center)
                        } else {
                            Button(L("🔄 Tìm thay thế lành mạnh", "🔄 Find healthier swap")) {
                                loadSubstitution()
                            }
                            .buttonStyle(.bordered)
                            .disabled(food.name.trimmingCharacters(in: .whitespaces).isEmpty || food.kcal <= 0)
                        }
                    }
                }
            }
            .navigationTitle(L("Món của tôi", "My food"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Hủy", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Lưu", "Save")) {
                        var saved = food
                        saved.custom = true
                        store.saveCustom(saved)
                        store.say(L("Đã lưu \(saved.name)", "Saved \(saved.name)"))
                        dismiss()
                    }
                    .disabled(food.name.trimmingCharacters(in: .whitespaces).isEmpty || food.kcal <= 0)
                }
                if store.isCustom(food) {
                    ToolbarItem(placement: .bottomBar) {
                        Button(store.library.contains { $0.id == food.id } ? L("Khôi phục món gốc", "Restore original") : L("Xóa món này", "Delete this food"), role: .destructive) {
                            store.removeCustom(food)
                            dismiss()
                        }
                    }
                }
            }
            // A built-in dish opens under the name the user reads.
            .onAppear { if !food.custom && Lang.en { food.name = food.label; food.serving = food.servingLabel } }
            .sheet(isPresented: $showIngredients) {
                IngredientEditor(title: food.name, description: "\(food.name): \(food.serving)") { totals in
                    food.kcal = totals.kcal
                    food.protein = totals.protein
                    food.carbs = totals.carbs
                    food.fat = totals.fat
                    food.serving = totals.portion
                }
            }
        }
    }

    /// Energy is 4 kcal per gram of protein and carbohydrate and 9 per gram of fat, so a changed macro updates the calories.
    private func macro(_ path: WritableKeyPath<Food, Double>) -> Binding<Double> {
        let read: () -> Double = { food[keyPath: path] }
        let write: (Double) -> Void = { value in
            food[keyPath: path] = value
            let fromProtein: Double = 4.0 * food.protein
            let fromCarbs: Double = 4.0 * food.carbs
            let fromFat: Double = 9.0 * food.fat
            let total: Int = Int((fromProtein + fromCarbs + fromFat).rounded())
            if total > 0 && !food.tags.contains("alcohol") { food.kcal = total }
        }
        return Binding<Double>(get: read, set: write)
    }

    private func number(_ label: String, _ value: Binding<Double>) -> some View {
        HStack {
            Text(label)
            Spacer()
            TextField("0", value: value, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 100)
        }
    }

    private func loadSubstitution() {
        loadingSubstitution = true
        Task {
            do {
                let detectedFood = DetectedFood(
                    name: food.name,
                    kcal: food.kcal,
                    protein_g: food.protein,
                    carbs_g: food.carbs,
                    fat_g: food.fat,
                    fiber_g: 0,
                    tags: food.tags.map { $0 }
                )
                let result = try await API.shared.substitute(food: detectedFood)
                await MainActor.run {
                    substitution = result
                    loadingSubstitution = false
                }
            } catch {
                await MainActor.run {
                    loadingSubstitution = false
                }
            }
        }
    }
}

extension UIImage {
    /// Centre-cropped square scaled to `side` pixels: the same shape the Android camera sends.
    func squared(side: CGFloat) -> UIImage {
        let edge = min(size.width, size.height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { _ in
            let scale = side / edge
            let drawn = CGSize(width: size.width * scale, height: size.height * scale)
            draw(in: CGRect(x: (side - drawn.width) / 2, y: (side - drawn.height) / 2, width: drawn.width, height: drawn.height))
        }
    }
}

/// The system camera. The photo is cropped to its centre square, so frame the meal in the middle.
/// A green circle over the camera preview, as in the Android app: only the centre of the frame is analysed.
final class GuideOverlay: UIView {
    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        backgroundColor = .clear
        let label = UILabel()
        label.text = L("Đưa món ăn vào trong vòng tròn xanh", "Fit the food inside the green circle")
        label.textColor = .white
        label.font = .boldSystemFont(ofSize: 17)
        label.textAlignment = .center
        label.shadowColor = .black
        label.frame = CGRect(x: 16, y: frame.height * 0.12, width: frame.width - 32, height: 24)
        addSubview(label)
    }

    required init?(coder: NSCoder) { nil }

    override func draw(_ rect: CGRect) {
        // The camera preview sits above the shutter controls; the circle is centred on it.
        let side = rect.width * 0.88
        let centre = CGPoint(x: rect.midX, y: (rect.height - 150) / 2 + 20)
        let circle = UIBezierPath(ovalIn: CGRect(x: centre.x - side / 2, y: centre.y - side / 2, width: side, height: side))
        UIColor(red: 0.2, green: 0.85, blue: 0.5, alpha: 1).setStroke()
        circle.lineWidth = 4
        circle.stroke()
    }
}

struct CameraPicker: UIViewControllerRepresentable {
    var onImage: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.delegate = context.coordinator
        if picker.sourceType == .camera { picker.cameraOverlayView = GuideOverlay(frame: UIScreen.main.bounds) }
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage { parent.onImage(image) } else { parent.dismiss() }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}

/// A real photo of the dish when the app ships one (FoodPhotos, with credits in CREDITS.tsv), else its emoji.
struct FoodPhoto: View {
    var id: String
    var emoji: String
    var size: CGFloat

    var body: some View {
        // Xcode copies the photos to the bundle root; a folder reference keeps the folder, so both are tried.
        let path = Bundle.main.path(forResource: id, ofType: "jpg") ?? Bundle.main.path(forResource: id, ofType: "jpg", inDirectory: "FoodPhotos")
        if let path, let image = UIImage(contentsOfFile: path) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: size / 4))
        } else {
            Text(emoji).font(.system(size: size * 0.55)).frame(width: size, height: size)
        }
    }
}

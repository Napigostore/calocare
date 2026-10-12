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

    private let columns = [GridItem(.adaptive(minimum: 100), spacing: 10)]

    var body: some View {
        let searching = !query.trimmingCharacters(in: .whitespaces).isEmpty
        let folded = FoodMatcher.fold(query)
        let matches = searching ? store.foods.filter { FoodMatcher.fold($0.name).contains(folded) } : store.foods
        let matchIds = Set(matches.map { $0.id })
        let similar = searching ? FoodMatcher.ranked(query, in: store.foods).filter { $0.score >= 0.3 && !matchIds.contains($0.food.id) }.prefix(6).map { $0.food } : []
        let mealEntries = store.todaysEntries.filter { $0.meal == store.meal }
        let counts = Dictionary(grouping: mealEntries, by: { $0.foodId }).mapValues { $0.reduce(0) { $0 + $1.qty } }

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    DayPicker()
                    Picker("Bữa", selection: $store.meal) {
                        ForEach(Meal.all, id: \.id) { Text($0.label).tag($0.id) }
                    }
                    .pickerStyle(.segmented)

                    HStack {
                        // The iOS keyboard's own microphone key dictates dish names in Vietnamese.
                        TextField("Tìm hoặc nói tên món…", text: $query)
                            .textFieldStyle(.roundedBorder)
                            .onChange(of: query) { _ in suggestion = nil; note = "" }
                        Button { showCamera = true } label: { Image(systemName: "camera.fill").font(.title2) }
                            .buttonStyle(.borderedProminent)
                        PhotosPicker(selection: $pickedPhoto, matching: .images) { Image(systemName: "photo.on.rectangle").font(.title2) }
                            .buttonStyle(.bordered)
                    }

                    if !searching && !mealEntries.isEmpty { MealLog(entries: mealEntries) }

                    Text(searching ? "Món khớp tên" : "Đang chọn cho bữa \(Meal.label(store.meal).lowercased()) · chạm = +1 phần")
                        .font(.caption).foregroundColor(.secondary)
                    LazyVGrid(columns: columns, spacing: 10) {
                        ForEach(matches) { food in FoodTile(food: food, count: counts[food.id] ?? 0) }
                    }

                    if searching {
                        if !similar.isEmpty {
                            Text("Món gần giống").font(.headline)
                            LazyVGrid(columns: columns, spacing: 10) {
                                ForEach(similar) { food in FoodTile(food: food, count: counts[food.id] ?? 0) }
                            }
                        }
                        newDishCard
                    }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Nhật ký ăn uống")
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
        .sheet(item: $editing) { food in FoodEditor(food: food) }
    }

    /// For a dish the app does not have yet: the AI suggests its calories, the user adds or adjusts it.
    private var newDishCard: some View {
        Card("Món mới: “\(query.trimmingCharacters(in: .whitespaces))”") {
            Text("AI gợi ý calo từ tên món. Bạn cũng có thể mô tả: “bún 100g, thịt 100g, rau một ít” hoặc “tương đương 1 tô phở”.")
                .font(.caption).foregroundColor(.secondary)
            HStack {
                Button("✨ AI gợi ý calo") { suggest() }.buttonStyle(.borderedProminent).disabled(busy)
                Button("＋ Tự nhập") {
                    editing = Food(id: "custom_\(UUID().uuidString)", name: query.trimmingCharacters(in: .whitespaces), emoji: "🍽️", serving: "1 phần", kcal: 0,
                                   protein: 0, carbs: 0, fat: 0, course: "main", tags: [], region: "all", custom: true)
                }
                .buttonStyle(.bordered)
                if busy { ProgressView() }
            }
            if let food = suggestion {
                VStack(alignment: .leading, spacing: 6) {
                    Text("\(food.emoji) \(food.name)").font(.headline)
                    Text("\(food.kcal) kcal / \(food.serving)").bold()
                    Text("Đạm \(Int(food.protein))g · Tinh bột \(Int(food.carbs))g · Béo \(Int(food.fat))g · AI ước tính").font(.caption)
                    ForEach(Nutrition.warnings(tags: food.tags, profile: store.data.profile), id: \.self) { WarningLine(warning: $0) }
                    HStack {
                        Button("＋ Thêm vào bữa \(Meal.label(store.meal).lowercased())") { store.add(food); store.saveCustom(food) }.buttonStyle(.borderedProminent)
                        Button("✏️ Sửa rồi lưu") { editing = food }.buttonStyle(.bordered)
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Palette.green.opacity(0.15), in: RoundedRectangle(cornerRadius: 14))
            }
            if !note.isEmpty { Text(note).font(.caption).foregroundColor(.secondary) }
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
                    note = "AI không nhận ra đây là món ăn. Hãy thử mô tả rõ hơn."
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
        Food(id: id, name: name, emoji: (emoji ?? "").isEmpty ? "🍽️" : (emoji ?? "🍽️"), serving: portion ?? "1 phần", kcal: kcal, protein: protein_g ?? 0,
             carbs: carbs_g ?? 0, fat: fat_g ?? 0, course: course ?? "main", tags: tags ?? [], region: "all", custom: true)
    }
}

private struct FoodTile: View {
    @EnvironmentObject var store: AppStore
    var food: Food
    var count: Double

    var body: some View {
        Button { store.add(food) } label: {
            VStack(spacing: 4) {
                Text(food.emoji).font(.system(size: 34))
                Text(food.name).font(.caption.weight(.medium)).multilineTextAlignment(.center).lineLimit(2, reservesSpace: true)
                Text("\(food.kcal) kcal").font(.caption2.weight(.semibold)).foregroundColor(Palette.green)
                Text(food.serving).font(.caption2).foregroundColor(.secondary).lineLimit(1)
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
    }
}

/// What is already logged for the selected meal, with quantity steppers.
private struct MealLog: View {
    @EnvironmentObject var store: AppStore
    var entries: [LogEntry]

    var body: some View {
        Card("Bữa \(Meal.label(store.meal).lowercased()) · \(Int(store.totals(entries).kcal)) kcal") {
            ForEach(entries) { entry in
                HStack {
                    Text(entry.emoji).font(.title3)
                    VStack(alignment: .leading) {
                        Text(entry.name).font(.subheadline.weight(.medium)).lineLimit(1)
                        Text("\(Int(entry.totalKcal)) kcal · \(entry.serving)").font(.caption).foregroundColor(.secondary).lineLimit(1)
                    }
                    Spacer()
                    Button { store.changeQty(entry, by: -0.5) } label: { Image(systemName: "minus.circle.fill") }.buttonStyle(.plain)
                    Text(formatQty(entry.qty)).bold().frame(width: 36)
                    Button { store.changeQty(entry, by: 0.5) } label: { Image(systemName: "plus.circle.fill") }.buttonStyle(.plain)
                }
                .foregroundColor(.primary)
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

    var body: some View {
        NavigationStack {
            Form {
                Section("Món ăn") {
                    TextField("Tên món", text: $food.name)
                    TextField("Khẩu phần", text: $food.serving)
                }
                Section("Dinh dưỡng mỗi phần") {
                    number("Calo (kcal)", Binding(get: { Double(food.kcal) }, set: { food.kcal = Int($0) }))
                    number("Đạm (g)", macro(\.protein))
                    number("Tinh bột (g)", macro(\.carbs))
                    number("Chất béo (g)", macro(\.fat))
                    Text("Đổi đạm, bột, béo thì calo tự tính lại.").font(.caption).foregroundColor(.secondary)
                    Button("🧮 Tính theo thành phần (thịt, rau… bao nhiêu gam)") { showIngredients = true }
                        .disabled(food.name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .navigationTitle("Món của tôi")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Hủy") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Lưu") {
                        var saved = food
                        saved.custom = true
                        store.saveCustom(saved)
                        store.say("Đã lưu \(saved.name)")
                        dismiss()
                    }
                    .disabled(food.name.trimmingCharacters(in: .whitespaces).isEmpty || food.kcal <= 0)
                }
            }
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
        Binding(get: { food[keyPath: path] }, set: { value in
            food[keyPath: path] = value
            let total = Int((4 * food.protein + 4 * food.carbs + 9 * food.fat).rounded())
            if total > 0 && !food.tags.contains("alcohol") { food.kcal = total }
        })
    }

    private func number(_ label: String, _ value: Binding<Double>) -> some View {
        HStack {
            Text(label)
            Spacer()
            TextField("0", value: value, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 100)
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
struct CameraPicker: UIViewControllerRepresentable {
    var onImage: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.delegate = context.coordinator
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

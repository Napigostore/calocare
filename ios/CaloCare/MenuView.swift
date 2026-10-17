import SwiftUI

/// A suggested day of meals for the user's goal, cuisine and health, as on Android.
struct MenuView: View {
    @EnvironmentObject var store: AppStore
    @State private var seed = Int(Date().timeIntervalSince1970 / 86400)

    var body: some View {
        let p = store.data.profile
        let plan = MenuPlanner.plan(p, foods: store.foods, seed: seed)
        let total = plan.reduce(0) { $0 + $1.kcal }
        let macros = Nutrition.macroTargets(p)
        let filters = HealthTags.conditions.filter { p.conditions.contains($0.id) }.map { $0.label }
            + (p.allergies.isEmpty ? [] : [L("dị ứng", "allergies")])
            + (p.diet == Diet.none ? [] : Diet.all.filter { $0.id == p.diet }.map { $0.label })

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text((Region.all.first { $0.id == p.cuisine }?.label ?? "") + L(" · đã lọc theo sức khỏe của bạn", " · filtered for your health"))
                        .font(.subheadline).foregroundColor(.secondary)
                    Card(L("≈ \(total) kcal · mục tiêu \(Nutrition.targetKcal(p))", "≈ \(total) kcal · target \(Nutrition.targetKcal(p))")) {
                        Text(L("Đạm \(macros.protein)g · Tinh bột \(macros.carbs)g · Béo \(macros.fat)g mỗi ngày", "Protein \(macros.protein)g · Carbs \(macros.carbs)g · Fat \(macros.fat)g a day"))
                            .font(.subheadline)
                        if !filters.isEmpty {
                            Text(L("🛡️ Đã loại món không hợp: ", "🛡️ Excluded unsuitable dishes: ") + filters.joined(separator: ", ")).font(.caption).foregroundColor(Palette.green)
                        }
                        Button { seed += 1 } label: { Label(L("Đổi thực đơn", "New menu"), systemImage: "arrow.clockwise") }.buttonStyle(.bordered)
                    }
                    ForEach(plan.indices, id: \.self) { i in mealCard(plan[i]) }
                    Card(L("Nguyên tắc cho mục tiêu của bạn", "Rules for your goal") + " · " + Goal.label(p.goal)) {
                        ForEach(Nutrition.goalGuides(p.goal), id: \.self) { Text("• " + $0).font(.subheadline) }
                        Button(L("✨ Nhờ AI lập thực đơn riêng cho tôi", "✨ Ask AI for a menu just for me")) { store.tab = 4 }.buttonStyle(.borderedProminent)
                    }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(L("Thực đơn gợi ý", "Suggested menu"))
        }
    }

    private func mealCard(_ meal: MenuPlanner.PlannedMeal) -> some View {
        Card("\(Meal.label(meal.meal)) · \(meal.kcal) kcal") {
            if meal.items.isEmpty {
                Text(L("Chưa có món phù hợp với các giới hạn của bạn. Hãy hỏi chuyên gia AI để có gợi ý riêng.", "No dishes fit your restrictions yet. Ask the AI expert for personal suggestions."))
                    .font(.subheadline).foregroundColor(.secondary)
            }
            ForEach(meal.items, id: \.self) { item in
                HStack {
                    Text(item.food.emoji)
                    VStack(alignment: .leading) {
                        Text("\(item.food.label) × \(formatQty(item.qty))").font(.subheadline.weight(.medium))
                        Text(item.food.servingLabel).font(.caption).foregroundColor(.secondary)
                    }
                    Spacer()
                    Text("\(Int((Double(item.food.kcal) * item.qty).rounded())) kcal").font(.subheadline)
                }
            }
            if !meal.items.isEmpty {
                Button(L("＋ Ghi vào nhật ký", "＋ Add to log")) { store.addPlanned(meal) }.buttonStyle(.bordered)
            }
        }
    }
}

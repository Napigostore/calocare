import SwiftUI

/// The fourth tab: the suggested menu, meal logging, the profile, and the credits for photos and data.
struct MoreView: View {
    @EnvironmentObject var store: AppStore
    @State private var showMenu = false
    @State private var showFood = false

    var body: some View {
        NavigationStack {
            List {
                Section(L("Điểm khác biệt của CaloGold", "What sets CaloGold apart")) {
                    Label(L("Món Việt đi đầu, có khẩu phần và calo", "Vietnamese dishes first, with servings and calories"), systemImage: "fork.knife.circle")
                    Label(L("Mâm chung: chia một món cho 1 đến 4 người", "Shared tray: split a dish between 1 to 4 people"), systemImage: "person.3.fill")
                    Label(L("Thẻ tuần để chia sẻ với bạn bè", "A weekly card to share with friends"), systemImage: "rectangle.stack.person.crop")
                }
                Section {
                    Button { showMenu = true } label: { Label(L("Thực đơn gợi ý", "Suggested menu"), systemImage: "list.bullet.rectangle") }
                    Button { showFood = true } label: { Label(L("Ghi bữa ăn", "Log a meal"), systemImage: "fork.knife") }
                }
                Section {
                    Button { store.showProfile = true } label: { Label(L("Hồ sơ và mục tiêu", "Profile and goals"), systemImage: "person.text.rectangle") }
                    NavigationLink { CreditsView() } label: { Label(L("Nguồn ảnh và dữ liệu", "Photo and data credits"), systemImage: "info.circle") }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Palette.paper)
            .navigationTitle(L("Cá nhân", "Me"))
            .sheet(isPresented: $showMenu) { MenuView() }
            .sheet(isPresented: $showFood) { FoodView() }
        }
    }
}

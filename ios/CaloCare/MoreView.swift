import SwiftUI

/// The fourth tab: the suggested menu, meal logging, the profile, and the credits for photos and data.
struct MoreView: View {
    @EnvironmentObject var store: AppStore
    @State private var showMenu = false
    @State private var showFood = false

    var body: some View {
        NavigationStack {
            List {
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

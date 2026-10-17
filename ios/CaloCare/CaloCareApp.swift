import SwiftUI

@main
struct CaloCareApp: App {
    @StateObject private var store = AppStore()
    @StateObject private var pro = ProStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(pro)
                .tint(Palette.green)
                .task { await store.syncLibrary() }
                .task { if !Demo.active { await pro.refresh() } }
        }
    }
}

enum Palette {
    static let green = Color(red: 0.12, green: 0.62, blue: 0.38)
    static let orange = Color(red: 0.95, green: 0.55, blue: 0.22)
    static let protein = Color(red: 0.88, green: 0.36, blue: 0.44)
    static let carbs = Color(red: 0.90, green: 0.65, blue: 0.0)
    static let fat = Color(red: 0.23, green: 0.51, blue: 0.77)
    static let warn = Color(red: 0.70, green: 0.42, blue: 0.0)
}

struct RootView: View {
    @EnvironmentObject var store: AppStore
    @EnvironmentObject var pro: ProStore

    var body: some View {
        ZStack(alignment: .bottom) {
            if store.data.profile.onboarded {
                // Same five tabs as the Android app; the profile opens from the Today screen.
                TabView(selection: $store.tab) {
                    HomeView().tabItem { Label(L("Hôm nay", "Today"), systemImage: "house.fill") }.tag(0)
                    FoodView().tabItem { Label(L("Ăn uống", "Food"), systemImage: "fork.knife") }.tag(1)
                    MenuView().tabItem { Label(L("Thực đơn", "Menu"), systemImage: "menucard.fill") }.tag(2)
                    ProgressScreen().tabItem { Label(L("Tiến độ", "Progress"), systemImage: "chart.xyaxis.line") }.tag(3)
                    CoachView().tabItem { Label("Coach", systemImage: "bubble.left.and.bubble.right.fill") }.tag(4)
                }
                .sheet(isPresented: $store.showProfile) { ProfileView() }
            } else {
                NavigationStack {
                    ProfileForm(title: "🥗 CaloCare", saveLabel: L("Bắt đầu", "Get started"))
                }
            }
            if let toast = store.toast {
                Text(toast)
                    .font(.subheadline)
                    .foregroundColor(.white)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Color.black.opacity(0.85), in: RoundedRectangle(cornerRadius: 14))
                    .padding(.bottom, 64)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: store.toast)
        .sheet(isPresented: $pro.showPaywall) { ProView() }
    }
}

/// White rounded card used across the screens.
struct Card<Content: View>: View {
    var title: String?
    var content: Content

    init(_ title: String? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title { Text(title).font(.headline) }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
    }
}

struct WarningLine: View {
    var warning: HealthWarning

    var body: some View {
        Text((warning.danger ? "⛔ " : "⚠️ ") + warning.text)
            .font(.caption)
            .foregroundColor(warning.danger ? .red : Palette.warn)
    }
}

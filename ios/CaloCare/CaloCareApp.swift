import SwiftUI
import UIKit

@main
struct CaloCareApp: App {
    @StateObject private var store = AppStore()
    @StateObject private var pro = ProStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(pro)
                .tint(Palette.red)
                .task { await store.syncLibrary() }
                .task { if !Demo.active { await pro.refresh() } }
        }
    }
}

/// A guide-book palette: deep red and black ink on ivory paper, with a muted gold accent. Dark mode keeps the same roles.
enum Palette {
    static let red = Color(red: 0.72, green: 0.07, blue: 0.13)
    static let ink = Color(red: 0.10, green: 0.10, blue: 0.10)
    static let gold = Color(red: 0.72, green: 0.58, blue: 0.33)
    static let paper = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.07, alpha: 1) : UIColor(red: 0.97, green: 0.95, blue: 0.91, alpha: 1) })
    static let surface = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.13, alpha: 1) : UIColor.white })
    static let green = Color(red: 0.14, green: 0.40, blue: 0.27)
    static let orange = gold
    static let protein = red
    static let carbs = gold
    static let fat = Color(red: 0.33, green: 0.36, blue: 0.42)
    static let warn = Color(red: 0.62, green: 0.32, blue: 0.05)
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
        .task { if Demo.screen == "tour" { await tour() } }
    }
}

extension RootView {
    /// Demo tour for the walkthrough video: one session moving through the app's screens on its own.
    @MainActor func tour() async {
        func wait(_ seconds: Double) async { try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000)) }
        await wait(4)
        store.tab = 1; await wait(4)
        store.demoScan = true; await wait(7)
        store.demoScan = false; await wait(1.5)
        store.tab = 2; await wait(5)
        store.tab = 3; await wait(5)
        store.tab = 4; await wait(5)
        store.tab = 0; store.showProfile = true; await wait(5)
        store.showProfile = false; await wait(1.5)
        pro.showPaywall = true; await wait(6)
        pro.showPaywall = false
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
            if let title { Text(title).font(.system(.headline, design: .serif)) }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Palette.ink.opacity(0.12), lineWidth: 0.5))
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

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
                .tint(Palette.gold)
                .preferredColorScheme(.dark)
                .task { await store.syncLibrary() }
                .task { if !Demo.active { await pro.refresh() } }
        }
    }
}

/// CaloGold's palette: a dark screen with a warm gold accent, as in the app's design set.
/// The app always uses the dark appearance, so these colours are fixed rather than adaptive.
enum Palette {
    static let gold = Color(red: 0.84, green: 0.66, blue: 0.33)
    static let red = gold
    static let ink = Color.white
    static let paper = Color(red: 0.04, green: 0.04, blue: 0.05)
    static let surface = Color(red: 0.11, green: 0.11, blue: 0.12)
    static let green = Color(red: 0.38, green: 0.80, blue: 0.52)
    static let orange = gold
    static let protein = Color(red: 0.93, green: 0.45, blue: 0.42)
    static let carbs = gold
    static let fat = Color(red: 0.45, green: 0.62, blue: 0.90)
    static let warn = Color(red: 0.96, green: 0.70, blue: 0.30)
}

struct RootView: View {
    @EnvironmentObject var store: AppStore
    @EnvironmentObject var pro: ProStore

    var body: some View {
        ZStack(alignment: .bottom) {
            if store.data.profile.onboarded {
                // Four tabs, as in the CaloGold design. Logging a meal opens from the Today screen.
                TabView(selection: $store.tab) {
                    HomeView().tabItem { Label(L("Hôm nay", "Today"), systemImage: "house.fill") }.tag(0)
                    CoachView().tabItem { Label("Coach", systemImage: "bubble.left.and.bubble.right.fill") }.tag(1)
                    ProgressScreen().tabItem { Label(L("Tiến độ", "Progress"), systemImage: "chart.xyaxis.line") }.tag(2)
                    MoreView().tabItem { Label(L("Cá nhân", "Me"), systemImage: "person.crop.circle.fill") }.tag(3)
                }
                .sheet(isPresented: $store.showProfile) { ProfileView() }
            } else {
                NavigationStack {
                    ProfileForm(title: "CaloGold", saveLabel: L("Bắt đầu", "Get started"))
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
        store.tab = 0; await wait(4)
        store.demoScan = true; await wait(7)
        store.demoScan = false; await wait(1.5)
        store.tab = 2; await wait(5)
        store.tab = 1; await wait(5)
        store.tab = 3; await wait(5)
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
            if let title { Text(title).font(.headline) }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 18))
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

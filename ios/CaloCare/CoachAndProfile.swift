import SwiftUI

struct CoachView: View {
    @EnvironmentObject var store: AppStore
    @State private var turns: [(fromUser: Bool, text: String)] = Demo.coachTurns
    @State private var input = ""
    @State private var busy = false

    private let starters = [L("Tối nay tôi nên ăn gì?", "What should I eat tonight?"), L("Nhận xét tuần ăn uống của tôi", "Review my eating this week"), L("Gợi ý thực đơn ngày mai cho tôi", "Suggest a menu for tomorrow"), L("Ăn gì trước và sau khi tập?", "What to eat before and after training?")]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            if turns.isEmpty {
                                Text(L("Mình đã xem hồ sơ, mục tiêu và nhật ký 7 ngày của bạn. Hỏi bất cứ điều gì về ăn uống và tập luyện nhé.", "I've looked at your profile, goal and last 7 days of logs. Ask me anything about food and training."))
                                    .foregroundColor(.secondary)
                                ForEach(starters, id: \.self) { s in Button(s) { send(s) }.buttonStyle(.bordered) }
                            }
                            ForEach(turns.indices, id: \.self) { i in
                                Text(turns[i].text)
                                    .padding(.horizontal, 14).padding(.vertical, 10)
                                    .foregroundColor(turns[i].fromUser ? .white : .primary)
                                    .background(turns[i].fromUser ? Palette.green : Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18))
                                    .frame(maxWidth: .infinity, alignment: turns[i].fromUser ? .trailing : .leading)
                                    .id(i)
                            }
                            if busy { HStack { ProgressView(); Text(L("Đang soạn câu trả lời…", "Writing a reply…")).foregroundColor(.secondary) }.id(-1) }
                        }
                        .padding(16)
                    }
                    .onChange(of: turns.count) { _ in withAnimation { proxy.scrollTo(turns.count - 1, anchor: .bottom) } }
                }
                HStack {
                    TextField(L("Hỏi coach…", "Ask the coach…"), text: $input, axis: .vertical).lineLimit(1...4).textFieldStyle(.roundedBorder)
                    Button { send(input) } label: { Image(systemName: "arrow.up.circle.fill").font(.title) }
                        .disabled(busy || input.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(12)
                Text(L("Thông tin mang tính tham khảo, không thay thế tư vấn của bác sĩ.", "For information only; not a substitute for your doctor's advice.")).font(.caption2).foregroundColor(.secondary).padding(.bottom, 6)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Coach")
        }
    }

    private func send(_ text: String) {
        let message = text.trimmingCharacters(in: .whitespaces)
        guard !message.isEmpty, !busy else { return }
        turns.append((true, message))
        input = ""
        busy = true
        Task {
            do {
                // Older turns are resent, and paid for, with every new message; the last few carry the thread.
                let reply = try await API.shared.chat(context: store.coachContext, history: Array(turns.suffix(10)))
                turns.append((false, reply))
            } catch {
                turns.removeLast()
                store.say(error.localizedDescription)
            }
            busy = false
        }
    }
}

struct ProfileView: View {
    @EnvironmentObject var store: AppStore
    @State private var quota: Quota?
    @State private var confirmWipe = false

    var body: some View {
        let p = store.data.profile
        let macros = Nutrition.macroTargets(p)
        NavigationStack {
            List {
                Section(L("Chỉ số của bạn", "Your numbers")) {
                    Text(L("🎯 Mục tiêu nạp: \(Nutrition.targetKcal(p)) kcal/ngày", "🎯 Daily target: \(Nutrition.targetKcal(p)) kcal")).bold()
                    Text("BMR \(Int(Nutrition.bmr(p))) kcal · TDEE \(Int(Nutrition.tdee(p))) kcal · BMI \(String(format: "%.1f", Nutrition.bmi(p)))")
                    Text(L("Đạm \(macros.protein)g · Tinh bột \(macros.carbs)g · Béo \(macros.fat)g", "Protein \(macros.protein)g · Carbs \(macros.carbs)g · Fat \(macros.fat)g")).foregroundColor(.secondary)
                }
                Section {
                    NavigationLink(L("Sửa hồ sơ, mục tiêu và sức khỏe", "Edit profile, goal and health")) { ProfileForm(title: L("Hồ sơ", "Profile"), saveLabel: L("Lưu thay đổi", "Save changes")) }
                }
                Section {
                    if store.healthLinked {
                        Label(L("Đã kết nối Apple Health", "Apple Health connected"), systemImage: "checkmark.circle.fill").foregroundColor(Palette.green)
                        Text(L("CaloCare chỉ đọc calo vận động, bước chân, phút tập, giấc ngủ, cân nặng và chiều cao. Đổi quyền trong Cài đặt → Sức khỏe → Truy cập dữ liệu & thiết bị → CaloCare.", "CaloCare only reads active calories, steps, exercise minutes, sleep, weight and height. Change access in Settings → Health → Data Access & Devices → CaloCare."))
                            .font(.caption).foregroundColor(.secondary)
                        Button(L("Ngừng dùng dữ liệu Apple Health", "Stop using Apple Health data"), role: .destructive) { store.unlinkHealth() }
                    } else {
                        Button { Task { await store.linkHealth() } } label: { Label(L("Kết nối Apple Health", "Connect Apple Health"), systemImage: "heart.fill") }
                        Text(L("Apple Watch ghi thẳng vào Apple Health. Garmin Connect, Mi Fitness, Zepp, Huawei Health, Samsung Health… bật đồng bộ sang Apple Health trong cài đặt của app đồng hồ.", "Apple Watch records straight into Apple Health. Garmin Connect, Fitbit, Mi Fitness, Zepp, Huawei Health… turn on syncing to Apple Health in their own app's settings."))
                            .font(.caption).foregroundColor(.secondary)
                    }
                } header: { Text(L("⌚ Đồng hồ & Apple Health", "⌚ Watch & Apple Health")) }
                Section(L("✨ Trợ lý AI", "✨ AI assistant")) {
                    if let quota {
                        Text(L("📷 Còn \(quota.photos_left_today) ảnh hôm nay", "📷 \(quota.photos_left_today) photos left today") + (quota.trial_photos_left > 0 ? L(" · \(quota.trial_photos_left) ảnh dùng thử", " · \(quota.trial_photos_left) trial photos") : ""))
                        Text(L("💬 Còn \(quota.chats_left_today) câu hỏi", "💬 \(quota.chats_left_today) questions left") + (quota.trial_chats_left > 0 ? L(" dùng thử", " (trial)") : L(" hôm nay", " today")))
                    } else if !API.shared.configured {
                        Text(L("Bản này chưa được cấu hình máy chủ AI.", "This build has no AI server configured.")).foregroundColor(.secondary)
                    }
                    Text(L("Dùng thử 100 ảnh (tối đa 30 ảnh/ngày) và 100 câu hỏi. Sau đó mỗi ngày có 6 ảnh và 10 câu hỏi.", "Trial: 100 photos (up to 30 a day) and 100 questions. After that, 6 photos and 10 questions a day.")).font(.caption).foregroundColor(.secondary)
                }
                Section(L("Quyền riêng tư & dữ liệu", "Privacy & data")) {
                    Text(L("Hồ sơ và nhật ký chỉ nằm trên thiết bị này. Ảnh, câu hỏi và tên món bạn tạo được gửi tới máy chủ CaloCare khi bạn dùng tính năng AI. Dữ liệu Apple Health chỉ được đọc và dùng trên máy này, không gửi đi đâu.", "Your profile and log stay on this device. Photos, questions and names of foods you create go to CaloCare's server when you use AI features. Apple Health data is only read and used on this device and is never sent anywhere.")).font(.caption)
                    Button(L("Xóa toàn bộ dữ liệu", "Delete all data"), role: .destructive) { confirmWipe = true }
                }
                Section {
                    Text(L("CaloCare đưa ra ước tính và gợi ý tham khảo, không thay thế chẩn đoán hay chỉ định của bác sĩ.", "CaloCare gives estimates and general suggestions; it does not replace your doctor's diagnosis or advice.")).font(.caption).foregroundColor(.secondary)
                }
            }
            .navigationTitle(L("Hồ sơ", "Profile"))
            .task { if !Demo.active { quota = await API.shared.refreshQuota() } }
            .confirmationDialog(L("Xóa hồ sơ, nhật ký và món tự tạo khỏi thiết bị? Không thể khôi phục.", "Delete your profile, log and custom foods from this device? This can't be undone."), isPresented: $confirmWipe, titleVisibility: .visible) {
                Button(L("Xóa", "Delete"), role: .destructive) { store.wipe() }
                Button(L("Giữ lại", "Keep"), role: .cancel) {}
            }
        }
    }
}

/// Body numbers, goal and health declarations; shared by the first-run screen and the profile tab.
struct ProfileForm: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    var title: String
    var saveLabel: String
    @State private var draft = Profile()
    @State private var loaded = false

    private let activityLevels: [(value: Double, label: String)] = [
        (1.2, L("Ít vận động (ngồi nhiều)", "Sedentary (mostly sitting)")), (1.375, L("Nhẹ (tập 1–3 buổi/tuần)", "Light (1–3 workouts a week)")), (1.55, L("Vừa (tập 3–5 buổi/tuần)", "Moderate (3–5 workouts a week)")), (1.725, L("Nặng (tập 6–7 buổi/tuần)", "Heavy (6–7 workouts a week)")),
    ]

    var body: some View {
        let thisYear = Calendar.current.component(.year, from: Date())
        let valid = (1925...(thisYear - 10)).contains(draft.birthYear) && (100...230).contains(draft.heightCm) && (25...300).contains(draft.weightKg)
        Form {
            Section(L("Về bạn", "About you")) {
                TextField(L("Tên gọi", "Name"), text: $draft.name)
                Picker(L("Giới tính", "Sex"), selection: $draft.male) { Text(L("Nam", "Male")).tag(true); Text(L("Nữ", "Female")).tag(false) }.pickerStyle(.segmented)
                numberRow(L("Năm sinh", "Birth year"), Binding(get: { Double(draft.birthYear) }, set: { draft.birthYear = Int($0) }))
                numberRow(L("Chiều cao (cm)", "Height (cm)"), $draft.heightCm)
                numberRow(L("Cân nặng (kg)", "Weight (kg)"), $draft.weightKg)
                Picker(L("Mức vận động", "Activity level"), selection: $draft.activity) { ForEach(activityLevels, id: \.value) { Text($0.label).tag($0.value) } }
            }
            Section(L("Mục tiêu", "Goal")) {
                Picker(L("Mục tiêu", "Goal"), selection: $draft.goal) { ForEach(Goal.all, id: \.id) { Text($0.label).tag($0.id) } }
                if draft.goal == Goal.lose || draft.goal == Goal.gain {
                    Picker(L("Tốc độ mỗi tuần", "Rate per week"), selection: $draft.weeklyRateKg) { Text(L("0,25 kg", "0.25 kg")).tag(0.25); Text(L("0,5 kg", "0.5 kg")).tag(0.5); Text(L("0,75 kg", "0.75 kg")).tag(0.75) }.pickerStyle(.segmented)
                }
            }
            Section(L("Bệnh lý cần lưu ý", "Health conditions")) {
                ForEach(HealthTags.conditions, id: \.id) { c in Toggle(c.label, isOn: membership(\.conditions, c.id)) }
            }
            Section(L("Dị ứng thực phẩm", "Food allergies")) {
                ForEach(HealthTags.allergens, id: \.id) { a in Toggle(a.label, isOn: membership(\.allergies, a.id)) }
            }
            Section(L("Chế độ ăn", "Diet")) {
                Picker(L("Chế độ ăn", "Diet"), selection: $draft.diet) { ForEach(Diet.all, id: \.id) { Text($0.label).tag($0.id) } }
            }
            Section {
                Button(saveLabel) {
                    var saved = draft
                    saved.onboarded = true
                    store.update { $0.profile = saved }
                    store.say(L("Đã lưu hồ sơ", "Profile saved"))
                    dismiss()
                }
                .disabled(!valid)
            }
        }
        .navigationTitle(title)
        .onAppear {
            if !loaded { draft = store.data.profile; loaded = true }
        }
    }

    private func membership(_ path: WritableKeyPath<Profile, [String]>, _ id: String) -> Binding<Bool> {
        Binding(get: { draft[keyPath: path].contains(id) }, set: { on in
            draft[keyPath: path].removeAll { $0 == id }
            if on { draft[keyPath: path].append(id) }
        })
    }

    private func numberRow(_ label: String, _ value: Binding<Double>) -> some View {
        HStack {
            Text(label)
            Spacer()
            TextField("0", value: value, format: .number.grouping(.never)).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 100)
        }
    }
}

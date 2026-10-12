import SwiftUI

struct CoachView: View {
    @EnvironmentObject var store: AppStore
    @State private var turns: [(fromUser: Bool, text: String)] = []
    @State private var input = ""
    @State private var busy = false

    private let starters = ["Tối nay tôi nên ăn gì?", "Nhận xét tuần ăn uống của tôi", "Gợi ý thực đơn ngày mai cho tôi", "Ăn gì trước và sau khi tập?"]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            if turns.isEmpty {
                                Text("Mình đã xem hồ sơ, mục tiêu và nhật ký 7 ngày của bạn. Hỏi bất cứ điều gì về ăn uống và tập luyện nhé.")
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
                            if busy { HStack { ProgressView(); Text("Đang soạn câu trả lời…").foregroundColor(.secondary) }.id(-1) }
                        }
                        .padding(16)
                    }
                    .onChange(of: turns.count) { _ in withAnimation { proxy.scrollTo(turns.count - 1, anchor: .bottom) } }
                }
                HStack {
                    TextField("Hỏi coach…", text: $input, axis: .vertical).lineLimit(1...4).textFieldStyle(.roundedBorder)
                    Button { send(input) } label: { Image(systemName: "arrow.up.circle.fill").font(.title) }
                        .disabled(busy || input.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(12)
                Text("Thông tin mang tính tham khảo, không thay thế tư vấn của bác sĩ.").font(.caption2).foregroundColor(.secondary).padding(.bottom, 6)
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
                Section("Chỉ số của bạn") {
                    Text("🎯 Mục tiêu nạp: \(Nutrition.targetKcal(p)) kcal/ngày").bold()
                    Text("BMR \(Int(Nutrition.bmr(p))) kcal · TDEE \(Int(Nutrition.tdee(p))) kcal · BMI \(String(format: "%.1f", Nutrition.bmi(p)))")
                    Text("Đạm \(macros.protein)g · Tinh bột \(macros.carbs)g · Béo \(macros.fat)g").foregroundColor(.secondary)
                }
                Section {
                    NavigationLink("Sửa hồ sơ, mục tiêu và sức khỏe") { ProfileForm(title: "Hồ sơ", saveLabel: "Lưu thay đổi") }
                }
                Section("✨ Trợ lý AI") {
                    if let quota {
                        Text("📷 Còn \(quota.photos_left_today) ảnh hôm nay" + (quota.trial_photos_left > 0 ? " · \(quota.trial_photos_left) ảnh dùng thử" : ""))
                        Text("💬 Còn \(quota.chats_left_today) câu hỏi" + (quota.trial_chats_left > 0 ? " dùng thử" : " hôm nay"))
                    } else if !API.shared.configured {
                        Text("Bản này chưa được cấu hình máy chủ AI.").foregroundColor(.secondary)
                    }
                    Text("Dùng thử 100 ảnh (tối đa 30 ảnh/ngày) và 100 câu hỏi. Sau đó mỗi ngày có 6 ảnh và 10 câu hỏi.").font(.caption).foregroundColor(.secondary)
                }
                Section("Quyền riêng tư & dữ liệu") {
                    Text("Hồ sơ và nhật ký chỉ nằm trên thiết bị này. Ảnh, câu hỏi và tên món bạn tạo được gửi tới máy chủ CaloCare khi bạn dùng tính năng AI.").font(.caption)
                    Button("Xóa toàn bộ dữ liệu", role: .destructive) { confirmWipe = true }
                }
                Section {
                    Text("CaloCare đưa ra ước tính và gợi ý tham khảo, không thay thế chẩn đoán hay chỉ định của bác sĩ.").font(.caption).foregroundColor(.secondary)
                }
            }
            .navigationTitle("Hồ sơ")
            .task { quota = await API.shared.refreshQuota() }
            .confirmationDialog("Xóa hồ sơ, nhật ký và món tự tạo khỏi thiết bị? Không thể khôi phục.", isPresented: $confirmWipe, titleVisibility: .visible) {
                Button("Xóa", role: .destructive) { store.wipe() }
                Button("Giữ lại", role: .cancel) {}
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
        (1.2, "Ít vận động (ngồi nhiều)"), (1.375, "Nhẹ (tập 1–3 buổi/tuần)"), (1.55, "Vừa (tập 3–5 buổi/tuần)"), (1.725, "Nặng (tập 6–7 buổi/tuần)"),
    ]

    var body: some View {
        let thisYear = Calendar.current.component(.year, from: Date())
        let valid = (1925...(thisYear - 10)).contains(draft.birthYear) && (100...230).contains(draft.heightCm) && (25...300).contains(draft.weightKg)
        Form {
            Section("Về bạn") {
                TextField("Tên gọi", text: $draft.name)
                Picker("Giới tính", selection: $draft.male) { Text("Nam").tag(true); Text("Nữ").tag(false) }.pickerStyle(.segmented)
                numberRow("Năm sinh", Binding(get: { Double(draft.birthYear) }, set: { draft.birthYear = Int($0) }))
                numberRow("Chiều cao (cm)", $draft.heightCm)
                numberRow("Cân nặng (kg)", $draft.weightKg)
                Picker("Mức vận động", selection: $draft.activity) { ForEach(activityLevels, id: \.value) { Text($0.label).tag($0.value) } }
            }
            Section("Mục tiêu") {
                Picker("Mục tiêu", selection: $draft.goal) { ForEach(Goal.all, id: \.id) { Text($0.label).tag($0.id) } }
                if draft.goal == Goal.lose || draft.goal == Goal.gain {
                    Picker("Tốc độ mỗi tuần", selection: $draft.weeklyRateKg) { Text("0,25 kg").tag(0.25); Text("0,5 kg").tag(0.5); Text("0,75 kg").tag(0.75) }.pickerStyle(.segmented)
                }
            }
            Section("Bệnh lý cần lưu ý") {
                ForEach(HealthTags.conditions, id: \.id) { c in Toggle(c.label, isOn: membership(\.conditions, c.id)) }
            }
            Section("Dị ứng thực phẩm") {
                ForEach(HealthTags.allergens, id: \.id) { a in Toggle(a.label, isOn: membership(\.allergies, a.id)) }
            }
            Section("Chế độ ăn") {
                Picker("Chế độ ăn", selection: $draft.diet) { ForEach(Diet.all, id: \.id) { Text($0.label).tag($0.id) } }
            }
            Section {
                Button(saveLabel) {
                    var saved = draft
                    saved.onboarded = true
                    store.update { $0.profile = saved }
                    store.say("Đã lưu hồ sơ")
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

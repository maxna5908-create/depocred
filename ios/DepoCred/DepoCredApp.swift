import SwiftUI

@main struct DepoCredApp: App {
    @StateObject private var model: AppModel
    init() {
        #if DEBUG
        let isolated = ProcessInfo.processInfo.arguments.contains("--ui-testing")
        let directory = isolated ? FileManager.default.temporaryDirectory.appendingPathComponent("ui-tests") : nil
        if isolated && ProcessInfo.processInfo.arguments.contains("--reset-state"), let directory { try? FileManager.default.removeItem(at: directory) }
        _model = StateObject(wrappedValue: AppModel(store: LocalStore(directory: directory)))
        #else
        _model = StateObject(wrappedValue: AppModel())
        #endif
    }
    var body: some Scene { WindowGroup { AppRoot(model: model) } }
}

struct AppRoot: View {
    @ObservedObject var model: AppModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var splash = true
    @State private var keyboard = false
    @State private var schedule: SavedCalculation?
    @State private var exported: ExportedFile?
    private var palette: Palette { Palette(dark: model.settings.theme == "dark", primary: skins.first { $0.id == model.settings.skin }?.color ?? skins[2].color) }
    var body: some View {
        NavigationStack {
            ZStack {
                palette.background.ignoresSafeArea()
                VStack(spacing: 0) {
                    AppHeader(tool: model.tool)
                    ToolPage(model: model, keyboard: keyboard, onSchedule: { schedule = $0 }, onExport: export).id(model.tool)
                }.frame(maxWidth: 800)
                if !keyboard {
                    VStack { Spacer(); BottomMenu(selected: model.tool) { model.select($0) }.frame(maxWidth: 752).padding(.horizontal, 12).padding(.bottom, 20) }
                }
                if let notice = model.notice {
                    VStack {
                        HStack(spacing: 12) {
                            Glyph(name: "info").foregroundStyle(palette.primary)
                            Text(notice).font(.inter(13)).foregroundStyle(palette.text)
                        }.padding(16).background(palette.solid, in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(palette.primary, lineWidth: 1))
                            .padding(16).frame(maxWidth: 520).onTapGesture { model.notice = nil }.accessibilityIdentifier("notice")
                        Spacer()
                    }
                }
                if splash { SplashView { withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.6)) { splash = false } }.transition(.opacity).zIndex(2) }
            }.toolbar(.hidden, for: .navigationBar)
                .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in keyboard = true }
                .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidHideNotification)) { _ in keyboard = false }
                .fullScreenCover(item: $schedule) { entry in ScheduleView(entry: entry, onExport: export).environment(\.palette, palette) }
                .sheet(item: $exported) { file in
                    if file.save { DocumentExporter(url: file.url) }
                    else { ShareSheet(items: [file.url]) }
                }
        }.environment(\.palette, palette).preferredColorScheme(splash || palette.dark ? .dark : .light).tint(palette.primary)
    }
    private func export(_ entry: SavedCalculation, _ save: Bool) {
        do { exported = ExportedFile(url: try PDFReport.make(entry, skin: model.settings.skin), save: save) }
        catch { model.notify("Не удалось создать PDF. \(error.localizedDescription)") }
    }
}

struct AppHeader: View {
    @Environment(\.palette) private var p
    let tool: Tool
    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text("ВАШ ФИНАНСОВЫЙ АССИСТЕНТ").font(.inter(11, .medium, relativeTo: .caption)).tracking(1.1).foregroundStyle(p.muted)
                    .padding(.bottom, 4).overlay(alignment: .bottom) { Capsule().fill(p.primary).frame(height: 1.5) }
                Text(tool.heading).font(.inter(20, .bold, relativeTo: .title3)).foregroundStyle(p.primary).fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
            }.frame(maxWidth: .infinity, alignment: .leading)
            Glyph(name: "coins", size: 32).foregroundStyle(p.primary)
        }.padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 16)
    }
}
struct ToolPage: View {
    @ObservedObject var model: AppModel
    let keyboard: Bool
    let onSchedule: (SavedCalculation) -> Void
    let onExport: (SavedCalculation, Bool) -> Void
    @FocusState private var focused: String?
    @State private var pendingResult = false
    @Environment(\.palette) private var p
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private var order: [String] { model.form.order(tool: model.tool, reverse: model.reverse) }
    private var next: String? {
        guard let focused, let index = order.firstIndex(of: focused), order.indices.contains(index + 1) else { return nil }
        return order[index + 1]
    }
    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Color.clear.frame(height: 0).id("top")
                    switch model.tool {
                    case .deposit, .loan, .leasing:
                        CalculatorForm(model: model, focus: $focused)
                        if let entry = model.current {
                            ResultView(entry: entry, onType: model.paymentType, onSave: model.saveCurrent, onSchedule: { onSchedule(entry) }, onExport: onExport).id("result")
                            Button { withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.3)) { proxy.scrollTo("top", anchor: .top) } } label: {
                                VStack(spacing: 8) { Glyph(name: "arrow_up", size: 32); Text("К ПАРАМЕТРАМ \(model.tool == .deposit ? "ДЕПОЗИТА" : model.tool == .loan ? "КРЕДИТА" : "ЛИЗИНГА")").font(.inter(10, .medium)) }.foregroundStyle(p.muted).frame(maxWidth: .infinity).padding(12)
                            }.buttonStyle(.plain)
                        }
                    case .history: HistoryView(model: model, onSchedule: onSchedule, onExport: onExport)
                    case .info: InfoView(model: model)
                    }
                }.padding(.horizontal, 20).padding(.top, 8).padding(.bottom, keyboard ? 28 : 144)
            }.accessibilityIdentifier("content").scrollDismissesKeyboard(.interactively)
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button(next == nil ? "Применить" : "Далее") { focused = next }.font(.inter(16, .semibold)).accessibilityIdentifier("keyboard_next")
                }
            }
            .onChange(of: focused) { field in
                if let field { withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) { proxy.scrollTo("input_" + field, anchor: .center) } }
            }
            .onChange(of: model.focusRevision) { _ in
                guard let field = model.requestedField else { return }
                focused = field == "paymentType" ? nil : field
                withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) { proxy.scrollTo("input_" + field, anchor: .center) }
                model.requestedField = nil
            }
            .onChange(of: model.resultRevision) { _ in
                pendingResult = true
                if !keyboard { revealResult(proxy) }
            }
            .onChange(of: keyboard) { visible in
                if !visible && pendingResult { revealResult(proxy) }
                else if visible, let focused { withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo("input_" + focused, anchor: .center) } }
            }
        }
    }
    private func revealResult(_ proxy: ScrollViewProxy) {
        pendingResult = false
        Task { @MainActor in
            await Task.yield()
            guard model.current != nil else { return }
            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.3)) { proxy.scrollTo("result", anchor: .top) }
        }
    }
}
struct BottomMenu: View {
    @Environment(\.palette) private var p
    @ScaledMetric(relativeTo: .caption) private var height = 80.0
    let selected: Tool
    let onSelect: (Tool) -> Void
    var body: some View {
        HStack(spacing: 0) {
            ForEach(Tool.allCases) { tool in MenuItem(tool: tool, active: selected == tool) { onSelect(tool) } }
        }.padding(4).frame(height: max(80, height)).background(.ultraThinMaterial, in: Capsule())
            .background(Color(hex: p.dark ? 0x374151 : 0xE9EBEE).opacity(0.9), in: Capsule())
            .overlay(Capsule().strokeBorder(.white.opacity(p.dark ? 0.14 : 0.4), lineWidth: 1))
            .shadow(color: .black.opacity(0.16), radius: 16, x: 0, y: 8)
    }
}
struct MenuItem: View {
    @Environment(\.palette) private var p
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var flash = false
    let tool: Tool
    let active: Bool
    let action: () -> Void
    var body: some View {
        Button {
            flash = true; action()
            Task { try? await Task.sleep(nanoseconds: 160_000_000); flash = false }
        } label: {
            VStack(spacing: 5) {
                Glyph(name: tool.icon, size: 20)
                Text(tool.title.uppercased()).font(.inter(10, .bold, relativeTo: .caption2)).tracking(0.4).lineLimit(1).minimumScaleFactor(0.65)
            }.foregroundStyle(active ? p.primary : p.text.opacity(0.4)).frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(p.text.opacity(flash ? (p.dark ? 0.16 : 0.11) : active ? (p.dark ? 0.08 : 0.05) : 0), in: Capsule())
                .scaleEffect(flash && !reduceMotion ? 0.985 : 1).contentShape(Capsule())
        }.buttonStyle(PressStyle()).animation(reduceMotion ? nil : .easeInOut(duration: 0.22), value: active).animation(reduceMotion ? nil : .easeOut(duration: 0.16), value: flash)
            .accessibilityIdentifier("tab_" + tool.rawValue).accessibilityLabel(tool.title).accessibilityAddTraits(active ? .isSelected : [])
    }
}
struct SplashView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var scale = 0.6
    let ready: () -> Void
    var body: some View {
        ZStack {
            Color(hex: 0xF59E0B).ignoresSafeArea()
            VStack(spacing: 0) {
                Glyph(name: "coins", size: 96)
                Text("DepoCred").font(.inter(30, .black)).padding(.top, 28)
                Text("ВАШ ФИНАНСОВЫЙ АССИСТЕНТ").font(.inter(11)).tracking(1).opacity(0.8).padding(.top, 8)
            }.foregroundStyle(.white).scaleEffect(reduceMotion ? 1 : scale)
            VStack { Spacer(); ProgressView().tint(.white).padding(.bottom, 50) }
        }.accessibilityIdentifier("startup").task {
            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.9)) { scale = 1 }
            do { try await Task.sleep(nanoseconds: 2_400_000_000); ready() } catch { }
        }
    }
}

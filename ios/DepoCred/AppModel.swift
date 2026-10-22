import SwiftUI

@MainActor final class AppModel: ObservableObject {
    @Published private(set) var tool: Tool = .deposit
    @Published private(set) var settings: Settings
    @Published private(set) var history: [SavedCalculation]
    @Published private(set) var forms: [String: Form] = [:]
    @Published private(set) var modes: [Tool: Bool] = [:]
    @Published private(set) var current: SavedCalculation?
    @Published private(set) var calculating = false
    @Published private(set) var resultRevision = 0
    @Published var notice: String?
    @Published var requestedField: String?
    @Published private(set) var focusRevision = 0
    private let store: LocalStore
    private var calculationJob: Task<Void, Never>?
    private var noticeJob: Task<Void, Never>?
    private var revision = 0
    var reverse: Bool { modes[tool] ?? false }
    var formKey: String { tool.rawValue + (reverse ? "Reverse" : "") }
    var form: Form { forms[formKey] ?? .empty(tool, reverse: reverse) }

    init(store: LocalStore = LocalStore()) {
        self.store = store
        let saved = store.read()
        settings = saved.settings; history = saved.history
        notice = store.recoveryNotice
    }
    func notify(_ text: String) {
        noticeJob?.cancel(); notice = text
        noticeJob = Task { try? await Task.sleep(nanoseconds: 3_500_000_000); if !Task.isCancelled { notice = nil } }
    }
    private func cancelCalculation() { revision += 1; calculationJob?.cancel(); calculating = false }
    func select(_ destination: Tool) {
        guard tool != destination else { return }
        cancelCalculation(); requestedField = nil; current = nil; tool = destination
    }
    func mode(_ value: Bool) {
        guard value != reverse else { return }
        cancelCalculation(); requestedField = nil; current = nil; modes[tool] = value
    }
    func updateSettings(_ update: (inout Settings) -> Void) {
        update(&settings); persist()
    }
    private func persist() {
        do { try store.write(StoredState(settings: settings, history: history)) }
        catch { notify("Не удалось записать настройки и историю. Проверьте доступное место и повторите сохранение.") }
    }
    func edit(_ key: String, _ value: String) {
        cancelCalculation()
        var next = form.with(key, value)
        if tool == .leasing && key == "advanceUnit" && value != form.text(key) && !form.text("advance").isEmpty {
            let rubles = form.leasing(reverse: reverse).advanceAmount
            next.fields["advance"] = numberText(leaseRound(value == "rub" ? rubles : next.number("amount") > 0 ? rubles / next.number("amount") * 100 : 0))
        }
        if tool == .leasing && key == "groupCount" {
            let count = next.groupCount
            for index in count..<12 { next.fields.removeValue(forKey: "count\(index)"); next.fields.removeValue(forKey: "payment\(index)") }
        }
        if reverse && tool == .loan {
            if key == "down" && next.number("down") > next.number("amount") { next.fields["down"] = next.text("amount"); notify("Взнос не может быть больше суммы кредита/покупки") }
            next = ReverseForms.loan(next)
        } else if reverse && tool == .deposit { next = ReverseForms.deposit(next, editedKey: key) }
        forms[formKey] = next; current = nil
    }
    func reset() { cancelCalculation(); forms[formKey] = .empty(tool, reverse: reverse); current = nil; requestedField = nil }
    func requestFocus(_ field: String, message: String) { requestedField = field; focusRevision += 1; notify(message) }
    @discardableResult func calculate() -> Bool {
        cancelCalculation()
        if let (field, message) = form.required(tool: tool, reverse: reverse) {
            let editable = tool == .loan && reverse && field == form.target ? ["term","payment","total"].first { $0 != field }! : field
            requestFocus(editable, message: message); return false
        }
        let snapshot = form, destination = tool, isReverse = reverse, key = formKey, token = revision
        calculating = true
        calculationJob = Task {
            let result = await Task.detached(priority: .userInitiated) { Result { try snapshot.calculated(tool: destination, reverse: isReverse) } }.value
            guard !Task.isCancelled && token == revision else { return }
            calculating = false
            switch result {
            case .success(let (form, value)):
                forms[key] = form; current = SavedCalculation(mode: isReverse ? "reverse" : "direct", result: value); resultRevision += 1
            case .failure(let error): notify(error.localizedDescription)
            }
        }
        return true
    }
    func paymentType(_ type: String) {
        if reverse && form.number("amount") <= 0 { requestFocus("amount", message: "Сначала укажите сумму кредита"); return }
        guard form.text("paymentType") != type else { return }
        guard tool == .loan && !reverse, let existing = current else { edit("paymentType", type); return }
        // Keep the result identity and scroll position when switching result tabs.
        cancelCalculation()
        let next = form.with("paymentType", type)
        do { let value = try Finance.loan(next.loan); forms[formKey] = next; current = SavedCalculation(id: existing.id, timestamp: existing.timestamp, mode: existing.mode, result: value) }
        catch { notify(error.localizedDescription) }
    }
    func saveCurrent() {
        guard var saved = current else { return }
        saved.id = UUID(); saved.timestamp = Date()
        history = Array(([saved] + history).prefix(10))
        do { try store.write(StoredState(settings: settings, history: history)); notify("Текущий расчёт сохранён в историю расчётов") }
        catch { notify("Не удалось записать историю. Проверьте доступное место и повторите сохранение.") }
    }
    func delete(_ id: UUID) { history.removeAll { $0.id == id }; persist() }
}

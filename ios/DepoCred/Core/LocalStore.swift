import Foundation

// Only intentional saves and settings persist. Drafts and scroll positions are session state.
struct StoredState: Codable, Equatable {
    var schema = 1
    var settings = Settings()
    var history: [SavedCalculation] = []
}

final class LocalStore {
    let directory: URL
    var file: URL { directory.appendingPathComponent("depocred-state.json") }
    var backup: URL { directory.appendingPathComponent("depocred-state.backup.json") }
    private(set) var recoveryNotice: String?
    init(directory: URL? = nil) {
        self.directory = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("DepoCred", isDirectory: true)
    }
    private func decode(_ data: Data) throws -> StoredState {
        let state = try JSONDecoder().decode(StoredState.self, from: data)
        try require(state.schema == 1, "Неподдерживаемая версия сохранённых данных")
        try require(state.history.count <= 10)
        for entry in state.history {
            let c = entry.result
            try require([c.loan != nil, c.deposit != nil, c.leasing != nil].filter { $0 }.count == 1 && (1...999).contains(c.rows.count))
        }
        return state
    }
    func read() -> StoredState {
        guard FileManager.default.fileExists(atPath: file.path) else { return (try? decode(Data(contentsOf: backup))) ?? StoredState() }
        do { return try decode(Data(contentsOf: file)) }
        catch {
            // Preserve damaged bytes for recovery. Never silently destroy saved history.
            try? FileManager.default.copyItem(at: file, to: directory.appendingPathComponent("recovery-\(UUID().uuidString).json"))
            if let recovered = try? decode(Data(contentsOf: backup)) {
                recoveryNotice = "История и настройки восстановлены из резервной копии на устройстве."
                return recovered
            }
            recoveryNotice = "Не удалось прочитать данные. Копия сохранена на устройстве для восстановления."
            return StoredState()
        }
    }
    func write(_ state: StoredState) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let data = try JSONEncoder().encode(state)
        // Back up only a valid old snapshot. Atomic rename prevents half-written JSON.
        if let previous = try? Data(contentsOf: file), (try? decode(previous)) != nil { try previous.write(to: backup, options: .atomic) }
        try data.write(to: file, options: .atomic)
    }
}

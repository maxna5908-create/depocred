import XCTest
import PDFKit
@testable import DepoCred

final class StorageAndExportTests: XCTestCase {
    func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url
    }
    func samples() throws -> [SavedCalculation] {
        [SavedCalculation(mode: "direct",result: try Finance.deposit(DepositInput(initialAmount: 100000,rate: 12,term: 12))),
         SavedCalculation(mode: "direct",result: try Finance.loan(LoanInput(amount: 100000,rate: 12,term: 12))),
         SavedCalculation(mode: "reverse",result: try LeasingFinance.reverse(LeasingInput(amount: 100000,groups: [LeasePaymentGroup(count: 12,payment: 10000)])))]
    }
    func testAtomicStoreRoundTripPreservesAllThreeTools() throws {
        let store = LocalStore(directory: try directory())
        let state = StoredState(settings: Settings(theme: "dark",skin: "violet",hints: false),history: try samples())
        try store.write(state); XCTAssertEqual(store.read(),state)
        let raw = try JSONSerialization.jsonObject(with: Data(contentsOf: store.file)) as! [String: Any]
        XCTAssertEqual(Set(raw.keys),Set(["schema","settings","history"]))
    }
    func testCorruptionRecoversLastValidBackupAndRetainsDamagedFile() throws {
        let store = LocalStore(directory: try directory())
        let state = StoredState(history: try samples())
        try store.write(state); try store.write(state)
        try Data("damaged".utf8).write(to: store.file)
        XCTAssertEqual(store.read(),state); XCTAssertNotNil(store.recoveryNotice)
        XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: store.directory.path).contains { $0.hasPrefix("recovery-") })
    }
    @MainActor func testFreshLaunchClearsDraftsAndKeepsPreferencesAndHistory() throws {
        let store = LocalStore(directory: try directory())
        let model = AppModel(store: store)
        XCTAssertEqual(model.tool,.deposit); XCTAssertEqual(model.settings.theme,"light"); XCTAssertEqual(model.settings.skin,"amber")
        model.edit("amount","100000"); model.edit("rate","12"); model.edit("term","12")
        model.updateSettings { $0.theme = "dark"; $0.skin = "blue" }
        let relaunched = AppModel(store: store)
        XCTAssertEqual(relaunched.form.text("amount"),""); XCTAssertNil(relaunched.current); XCTAssertEqual(relaunched.tool,.deposit)
        XCTAssertEqual(relaunched.settings.theme,"dark"); XCTAssertEqual(relaunched.settings.skin,"blue")
        model.select(.info); model.select(.deposit); XCTAssertEqual(model.form.text("amount"),"100000")
    }
    @MainActor func testAdvanceUnitConversionAndRemovedGroupsDoNotReturn() {
        let model = AppModel(store: LocalStore(directory: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)))
        model.select(.leasing); model.edit("amount","100000"); model.edit("advance","20"); model.edit("advanceUnit","rub")
        XCTAssertEqual(model.form.number("advance"),20000); model.edit("advanceUnit","percent"); XCTAssertEqual(model.form.number("advance"),20)
        model.mode(true); model.edit("groupCount","2"); model.edit("payment1","50000"); model.edit("groupCount","1"); model.edit("groupCount","2")
        XCTAssertEqual(model.form.text("payment1"),"")
    }
    @MainActor func testAllPDFTypesContainCyrillicAndCompleteSchedules() throws {
        var entries = try samples()
        entries.append(SavedCalculation(mode: "direct",result: try Finance.loan(LoanInput(amount: 1000000,rate: 12,term: 999))))
        for entry in entries {
            let url = try PDFReport.make(entry,skin: "amber")
            defer { try? FileManager.default.removeItem(at: url) }
            let pdf = try XCTUnwrap(PDFDocument(url: url))
            let text = (0..<pdf.pageCount).compactMap { pdf.page(at: $0)?.string }.joined(separator: "\n")
            XCTAssertTrue(text.contains("DepoCred")); XCTAssertTrue(text.contains("РЕЗУЛЬТАТ")); XCTAssertTrue(text.contains(entry.result.type.title))
            XCTAssertTrue(text.contains(String(entry.result.rows.count)))
            if entry.result.rows.count == 999 { XCTAssertGreaterThan(pdf.pageCount,30); XCTAssertTrue(pdf.page(at: pdf.pageCount - 1)!.string!.contains("999")) }
        }
    }
}

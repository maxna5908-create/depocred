import XCTest
@testable import DepoCred

final class FinanceTests: XCTestCase {
    func fixtures(_ name: String) throws -> [String: Any] {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: "json"))
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    }
    func close(_ expected: Double, _ actual: Double, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(expected, actual, accuracy: max(0.01, abs(expected) * 1e-9), file: file, line: line)
    }
    func testThirtyLoanFixturesAndEveryScheduleRowMatchAndroidAndPWA() throws {
        let cases = try XCTUnwrap(fixtures("pwa-parity")["loans"] as? [[String: Any]])
        XCTAssertEqual(cases.count, 30)
        for fixture in cases {
            let i = fixture["input"] as! [String: Any], e = fixture["result"] as! [String: Any]
            let input = LoanInput(amount: i.n("amount"), rate: i.n("rate"), term: Int(i.n("term")), fixedCommission: i.n("fixedCommission"), percentCommission: i.n("percentCommission"), paymentType: i.s("paymentType"), downPayment: i.n("downPayment"))
            let actual = try Finance.loan(input)
            let rows = e["schedule"] as! [[String: Any]]
            let unpaid = rows.last!.n("remainingBalance")
            close(e.n("totalRepayment"), actual.totalRepayment)
            if unpaid < 0.01 { close(e.n("totalInterest"), actual.totalInterest) }
            else { XCTAssertEqual(input.term, 999); XCTAssertEqual(input.paymentType, "annuity"); close(actual.totalRepayment - input.amount + input.downPayment + actual.totalCommission, actual.totalInterest) }
            close(e.n("totalCommission"), actual.totalCommission); close(e.n("amountReceived"), actual.amountReceived)
            XCTAssertEqual(rows.count, actual.rows.count)
            for (expected, row) in zip(rows, actual.rows) {
                close(expected.n("payment"), row.payment)
                if unpaid < 0.01 { close(expected.n("principal"), row.principal); close(expected.n("interest"), row.interest); close(expected.n("remainingBalance"), row.balance) }
            }
            XCTAssertTrue(actual.effectiveRate.isFinite && actual.effectiveRate >= 0)
            close(input.amount - input.downPayment, actual.rows.reduce(0) { $0 + $1.principal })
            close(0, actual.rows.last!.balance)
            let monthly = expm1(log1p(actual.effectiveRate / 100) / 12)
            close(actual.amountReceived, actual.rows.reduce(0) { $0 + $1.payment * exp(-Double($1.month) * log1p(monthly)) })
        }
    }
    func testOneHundredFortyDepositFixturesAndEveryScheduleRowMatch() throws {
        let cases = try XCTUnwrap(fixtures("pwa-parity")["deposits"] as? [[String: Any]])
        XCTAssertEqual(cases.count, 140)
        for fixture in cases {
            let i = fixture["input"] as! [String: Any], e = fixture["result"] as! [String: Any]
            let actual = try Finance.deposit(DepositInput(initialAmount: i.n("initialAmount"), rate: i.n("rate"), term: Int(i.n("term")), monthlyAddition: i.n("monthlyAddition"), capitalization: i.s("capitalization"), additionPeriod: i.s("additionPeriod")))
            close(e.n("finalAmount"), actual.finalAmount); close(e.n("totalInterest"), actual.totalInterest); close(e.n("totalAdditions"), actual.totalAdditions)
            let rows = e["schedule"] as! [[String: Any]]
            XCTAssertEqual(rows.count, actual.rows.count)
            for (expected, row) in zip(rows, actual.rows) { close(expected.n("interest"), row.interest); close(expected.n("addition"), row.addition); close(expected.n("balance"), row.balance) }
        }
    }
    func testReverseLoanRecoversRatesAndTerms() throws {
        for type in ["annuity","differentiated"] { for rate in [0.0,0.01,12.5,100.0,999.0] { for term in [1,12,360,999] {
            let c = try Finance.loan(LoanInput(amount: 1_000_000, rate: rate, term: term, paymentType: type))
            XCTAssertEqual(rate, try Finance.loanRate(principal: 1_000_000, term: term, payment: c.firstPayment, type: type), accuracy: 0.000001)
        } } }
        XCTAssertEqual(13, Finance.loanMissing(principal: 100_000, term: 0, payment: 10_000, total: 125_000, type: "annuity", field: "term"))
    }
    func testReverseDepositRecoversAllFourParametersForEveryPeriod() throws {
        for cap in capOptions { for period in addOptions {
            let input = DepositInput(initialAmount: 250_000, rate: 13.5, term: 37, monthlyAddition: 7500, capitalization: cap.id, additionPeriod: period.id)
            let target = try Finance.deposit(input).finalAmount
            close(target.rounded(.toNearestOrEven), try Finance.depositMissing(input, target: 0, field: "final"))
            XCTAssertEqual(13.5, try Finance.depositMissing(input, target: target, field: "rate"), accuracy: 0.01)
            XCTAssertEqual(250_000, try Finance.depositMissing(input, target: target, field: "amount"), accuracy: 1)
            XCTAssertEqual(37, try Finance.depositMissing(input, target: target, field: "term"))
        } }
    }
    func testReverseFieldOwnershipMatchesEveryPWAEditSequence() throws {
        let sequences = try XCTUnwrap(fixtures("pwa-reverse-forms")["fixtures"] as? [[[String: Any]]])
        for sequence in sequences {
            var form = Form.empty(.deposit, reverse: true)
            for step in sequence {
                let key = step.s("key")
                form = ReverseForms.deposit(form.with(key, step.s("text")), editedKey: key)
                let expected = step["expected"] as! [String: Any]
                for field in ["amount","rate","term","final"] { XCTAssertEqual(expected.n(field), form.number(field), accuracy: field == "rate" ? 0.011 : field == "term" ? 0 : 1, "field \(field), edit \(key)") }
                XCTAssertEqual(step["manual"] as! [String], form.manual)
            }
        }
    }
    func testClearingReverseDepositDoesNotEraseOrRefillAnotherInput() {
        var original = Form.empty(.deposit, reverse: true)
        for (key,value) in [("amount","100000"),("rate","12"),("term","12")] { original = ReverseForms.deposit(original.with(key,value), editedKey: key) }
        XCTAssertEqual(original.number("final"),112682)
        for key in ["amount","final","rate","term"] {
            let cleared = ReverseForms.deposit(original.with(key,""), editedKey: key)
            XCTAssertEqual(cleared.text(key), "")
            for other in ["amount","final","rate","term"] where other != key { XCTAssertEqual(original.text(other), cleared.text(other)) }
        }
        let changed = ReverseForms.deposit(original.with("final","120000"), editedKey: "final")
        XCTAssertEqual(changed.target,"amount"); XCTAssertEqual(changed.manual,["rate","term","final"])
    }
    func testReverseLoanKeepsUnknownUntilASourceIsCleared() {
        var form = Form(fields: ["amount":"100000","down":"20000","paymentType":"annuity","term":"12","payment":"8000"])
        form = ReverseForms.loan(form); XCTAssertEqual(form.target,"total"); XCTAssertEqual(form.text("total"),"96000")
        form = ReverseForms.loan(form.with("paymentType","differentiated")); XCTAssertEqual(form.text("total"),"88667")
        form = ReverseForms.loan(form.with("down","10000")); XCTAssertEqual(form.text("total"),"93250")
        form = ReverseForms.loan(form.with("payment","")); XCTAssertNil(form.target); XCTAssertEqual(form.text("total"),""); XCTAssertEqual(form.text("term"),"12")
    }
    func testZeroAndInvalidInputs() throws {
        let c = try Finance.loan(LoanInput(amount: 120000, rate: 0, term: 12))
        close(10000,c.firstPayment); close(0,c.totalInterest); close(0,c.effectiveRate)
        XCTAssertThrowsError(try Finance.loan(LoanInput(amount: 0, rate: 12, term: 12)))
        XCTAssertThrowsError(try Finance.loan(LoanInput(amount: 1000, rate: 12, term: 0)))
        XCTAssertThrowsError(try Finance.loan(LoanInput(amount: 1000, rate: 12, term: 12, fixedCommission: 600, percentCommission: 50)))
        XCTAssertThrowsError(try Finance.deposit(DepositInput(initialAmount: .nan, rate: 12, term: 12)))
        XCTAssertThrowsError(try Finance.loanRate(principal: 100000, term: 12, payment: 1, type: "annuity"))
    }
}
extension Dictionary where Key == String, Value == Any {
    func n(_ key: String) -> Double { (self[key] as? NSNumber)?.doubleValue ?? 0 }
    func s(_ key: String) -> String { self[key] as? String ?? "" }
}

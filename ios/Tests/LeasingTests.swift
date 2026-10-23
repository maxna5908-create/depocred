import XCTest
@testable import DepoCred

final class LeasingTests: XCTestCase {
    func reconcile(_ c: Calculation) {
        let x = c.leasing!, rate = c.nominalRate / 1200
        let pv = c.rows.reduce(0) { $0 + $1.payment / pow(1 + rate, Double($1.month)) }
        XCTAssertEqual(c.amountReceived,pv,accuracy: max(0.0001,x.amount * 1e-10))
        XCTAssertEqual(x.financed - x.expenses,c.rows.reduce(0) { $0 + $1.principal },accuracy: 0.01)
        XCTAssertEqual(c.rows.last!.balance,0)
        XCTAssertEqual(x.advanceAmount + x.expenses + c.rows.reduce(0) { $0 + $1.payment },c.totalRepayment,accuracy: 0.01)
        XCTAssertEqual(c.totalRepayment - x.amount,c.totalInterest,accuracy: 0.01)
    }
    func testCustomerExamplesAgainstIndependentReferenceValues() throws {
        let cases: [(LeasingInput, Double, Double, Double)] = [
            (LeasingInput(amount: 10_300_000, advance: 10, groups: [LeasePaymentGroup(count: 34,payment: 359210.80)], buyout: 20000),20.060638595837016,22.011858372624758,13263167.20),
            (LeasingInput(amount: 10_300_000, advance: 10, groups: [LeasePaymentGroup(count: 34,payment: 359210.80)], buyout: 20000, expenses: 51000),20.48352330511784,22.520312690431197,13314167.20),
            (LeasingInput(amount: 8500000, advance: 1700000, advanceUnit: "rub", groups: [LeasePaymentGroup(count: 18,payment: 456912.86)], buyout: 1999.99),25.029762346759114,28.110500722781584,9926431.47),
            (LeasingInput(amount: 8500000, advance: 1700000, advanceUnit: "rub", groups: [LeasePaymentGroup(count: 18,payment: 456912.86)], buyout: 1999.99, expenses: 22000),25.473749409272685,28.66878518273075,9948431.47)
        ]
        for (input, nominal, effective, total) in cases {
            let c = try LeasingFinance.reverse(input)
            XCTAssertEqual(nominal,c.nominalRate,accuracy: 1e-9); XCTAssertEqual(effective,c.effectiveRate,accuracy: 1e-9); XCTAssertEqual(total,c.totalRepayment,accuracy: 0.001); reconcile(c)
        }
    }
    func testDirectReverseParityWithBuyoutDeferralAndRounding() throws {
        for delay in [1,3] { for timing in ["next","last"] {
            var x = LeasingInput(amount: 1000000, advance: 20, rate: 18, term: 36, buyout: 100000, buyoutTiming: timing, firstMonth: delay)
            let c = try LeasingFinance.direct(x)
            XCTAssertEqual(18,c.nominalRate,accuracy: 0.00001)
            var groups: [LeasePaymentGroup] = []
            for row in c.rows where row.month >= delay && row.month < delay + x.term {
                let payment = leaseRound(row.payment - (timing == "last" && row.month == x.maturity ? x.buyout : 0))
                if groups.last?.payment == payment { groups[groups.count - 1].count += 1 }
                else { groups.append(LeasePaymentGroup(count: 1,payment: payment)) }
            }
            x.groups = groups
            let reverse = try LeasingFinance.reverse(x)
            XCTAssertEqual(c.rows,reverse.rows); reconcile(c)
        } }
        XCTAssertEqual(1.01,leaseRound(1.005),accuracy: 1e-10)
        XCTAssertEqual(1234.57,leaseRound(1234.565),accuracy: 1e-10)
    }
    func testUniformAndDecreasingApplyVATOnceToInterest() throws {
        for type in ["uniform","differentiated"] {
            let c = try LeasingFinance.direct(LeasingInput(amount: 7300000, advance: 40, rate: 14, term: 28, paymentType: type, vatRate: 18))
            XCTAssertEqual(7300000 + 740950 * 1.18,c.totalRepayment,accuracy: 0.001)
            XCTAssertEqual(type == "uniform" ? 187654.32 : 216726.57,c.firstPayment,accuracy: 0.001); reconcile(c)
        }
    }
    func testSteppedPaymentsHolidaysAndNegativeRateArePreserved() throws {
        let groups = [LeasePaymentGroup(count: 2,payment: 0),LeasePaymentGroup(count: 6,payment: 40000),LeasePaymentGroup(count: 12,payment: 60000)]
        let c = try LeasingFinance.reverse(LeasingInput(amount: 1000000, advance: 20, groups: groups, buyout: 30000, firstMonth: 2))
        XCTAssertEqual(c.rows.prefix(4).map(\.payment),[0,0,0,40000]); XCTAssertEqual(c.rows.count,22); XCTAssertEqual(c.rows.last!.payment,30000); reconcile(c)
        let negative = try LeasingFinance.reverse(LeasingInput(amount: 100000, groups: [LeasePaymentGroup(count: 12,payment: 8000)]))
        XCTAssertLessThan(negative.nominalRate,0); XCTAssertEqual(negative.totalInterest,-4000); reconcile(negative)
    }
    func testZeroRateAndAdvanceUnitsAgree() throws {
        let a = try LeasingFinance.direct(LeasingInput(amount: 120000, advance: 20, rate: 0, term: 12))
        let b = try LeasingFinance.direct(LeasingInput(amount: 120000, advance: 24000, advanceUnit: "rub", rate: 0, term: 12))
        XCTAssertEqual(a.rows,b.rows); XCTAssertEqual(a.firstPayment,8000); XCTAssertEqual(a.nominalRate,0); reconcile(a)
    }
    func testRejectsInvalidAndHandlesLongSchedules() throws {
        XCTAssertThrowsError(try LeasingFinance.direct(LeasingInput(amount: 100000, advance: 100, term: 12)))
        XCTAssertThrowsError(try LeasingFinance.direct(LeasingInput(amount: 100000, term: 12, firstMonth: 999)))
        XCTAssertThrowsError(try LeasingFinance.reverse(LeasingInput(amount: 100000, groups: [LeasePaymentGroup(count: 0,payment: 100)])))
        XCTAssertThrowsError(try LeasingFinance.reverse(LeasingInput(amount: 100000, groups: [LeasePaymentGroup(count: 12,payment: .nan)])))
        let c = try LeasingFinance.reverse(LeasingInput(amount: 100000, groups: [LeasePaymentGroup(count: 999,payment: 1000)]))
        XCTAssertTrue(c.nominalRate.isFinite); reconcile(c)
    }
}

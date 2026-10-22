import Foundation

enum Tool: String, CaseIterable, Codable, Identifiable {
    case deposit, loan, leasing, history, info
    var id: String { rawValue }
    var title: String {
        switch self { case .deposit: return "Депозит"; case .loan: return "Кредит"; case .leasing: return "Лизинг"; case .history: return "История"; case .info: return "Инфо" }
    }
    var heading: String {
        switch self { case .deposit: return "Депозитный калькулятор"; case .loan: return "Кредитный калькулятор"; case .leasing: return "Лизинг"; case .history: return "Сохраненные расчеты"; case .info: return "О приложении" }
    }
    var icon: String {
        switch self { case .deposit: return "piggy_bank"; case .loan: return "credit_card"; case .leasing: return "car_front"; case .history: return "bookmark"; case .info: return "info" }
    }
}

struct Settings: Codable, Equatable {
    var theme = "light"
    var skin = "amber"
    var hints = true
}

struct LoanInput: Codable, Equatable {
    var amount: Double
    var rate: Double
    var term: Int
    var fixedCommission = 0.0
    var percentCommission = 0.0
    var paymentType = "annuity"
    var downPayment = 0.0
}

struct DepositInput: Codable, Equatable {
    var initialAmount: Double
    var rate: Double
    var term: Int
    var monthlyAddition = 0.0
    var capitalization = "monthly"
    var additionPeriod = "none"
}

struct LeasePaymentGroup: Codable, Equatable {
    var count: Int
    var payment: Double
}

struct LeasingInput: Codable, Equatable {
    var amount: Double
    var advance = 0.0
    var advanceUnit = "percent"
    var rate = 0.0
    var term = 0
    var paymentType = "annuity"
    var groups: [LeasePaymentGroup] = []
    var buyout = 0.0
    var buyoutTiming = "next"
    var firstMonth = 1
    var expenses = 0.0
    var vatRate = 0.0
    var advanceAmount: Double { leaseRound(advanceUnit == "percent" ? amount * advance / 100 : advance) }
    var financed: Double { amount - advanceAmount }
    var count: Int { groups.isEmpty ? term : groups.reduce(0) { $0 + $1.count } }
    var maturity: Int { firstMonth + count - 1 + (buyout > 0 && buyoutTiming == "next" ? 1 : 0) }
}

struct ScheduleRow: Codable, Equatable, Identifiable {
    var month: Int
    var payment = 0.0
    var principal = 0.0
    var interest: Double
    var balance: Double
    var addition = 0.0
    var id: Int { month }
}

struct Calculation: Codable, Equatable {
    var loan: LoanInput? = nil
    var deposit: DepositInput? = nil
    var leasing: LeasingInput? = nil
    var rows: [ScheduleRow]
    var totalInterest: Double
    var totalCommission = 0.0
    var totalRepayment = 0.0
    var amountReceived = 0.0
    var effectiveRate = 0.0
    var finalAmount = 0.0
    var totalAdditions = 0.0
    var nominalRate = 0.0
    var type: Tool { loan != nil ? .loan : leasing != nil ? .leasing : .deposit }
    var firstPayment: Double { rows.first?.payment ?? 0 }
}

struct SavedCalculation: Codable, Equatable, Identifiable {
    var id = UUID()
    var timestamp = Date()
    var mode: String
    var result: Calculation
}

struct FinanceError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

func require(_ condition: Bool, _ message: String = "Проверьте параметры расчёта") throws {
    if !condition { throw FinanceError(message: message) }
}

struct Choice: Identifiable {
    var id: String
    var title: String
    var detail: String = ""
    init(_ id: String, _ title: String, _ detail: String = "") { self.id = id; self.title = title; self.detail = detail }
}

let loanDescriptions = [
    "annuity": "Равные регулярные платежи. Проценты начисляются на остаток долга: доля процентов уменьшается, а доля погашения долга растёт.",
    "differentiated": "Долг погашается равными частями. Проценты начисляются на уменьшающийся остаток, поэтому при положительной ставке платежи постепенно снижаются."
]
let leaseDescriptions = [
    "annuity": "Равные регулярные платежи, рассчитанные по ставке и сроку с учётом выкупа. Доля процентов уменьшается, а доля погашения долга растёт.",
    "uniform": "Сначала рассчитываются проценты при погашении долга равными частями. Затем сумма погашаемого долга и процентов делится поровну между платежами. Поэтому платёж отличается от аннуитетного.",
    "differentiated": loanDescriptions["differentiated"]!
]
let leaseTypes = [Choice("annuity", "Аннуитетный", leaseDescriptions["annuity"]!), Choice("uniform", "Равномерный", leaseDescriptions["uniform"]!), Choice("differentiated", "Убывающий", leaseDescriptions["differentiated"]!)]
let capOptions = [Choice("none", "В конце срока"), Choice("daily", "Ежедневно"), Choice("weekly", "Еженедельно"), Choice("monthly", "Ежемесячно"), Choice("quarterly", "Ежеквартально"), Choice("semi-yearly", "Раз в полгода"), Choice("yearly", "Ежегодно")]
let addOptions = [Choice("none", "Нет"), Choice("monthly", "Раз в месяц"), Choice("quarterly", "Раз в квартал"), Choice("semi-yearly", "Раз в полгода"), Choice("yearly", "Раз в год")]
let leaseTimings = [Choice("next", "Через месяц после платежей"), Choice("last", "Вместе с последним платежом")]

func numberText(_ value: Double) -> String {
    guard value.isFinite else { return "" }
    return NSDecimalNumber(value: value).stringValue
}
func money(_ value: Double, cents: Bool = false) -> String {
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "ru_RU")
    formatter.numberStyle = .decimal
    formatter.minimumFractionDigits = cents ? 2 : 0
    formatter.maximumFractionDigits = cents ? 2 : 0
    return (formatter.string(from: NSNumber(value: value)) ?? "—") + " ₽"
}
func percent(_ value: Double, digits: Int = 2) -> String {
    String(format: "%.*f%%", locale: Locale(identifier: "ru_RU"), digits, value)
}
// Decimal half-up rounding agrees with the Android contract, including .005 ties.
func leaseRound(_ value: Double) -> Double {
    guard value.isFinite else { return value }
    var decimal = Decimal(string: String(value), locale: Locale(identifier: "en_US_POSIX")) ?? Decimal(value)
    var rounded = Decimal()
    NSDecimalRound(&rounded, &decimal, 2, .plain)
    return NSDecimalNumber(decimal: rounded).doubleValue
}

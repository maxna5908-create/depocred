import Foundation

enum Finance {
    private static func valid(_ values: Double...) throws {
        try require(values.allSatisfy { $0.isFinite && $0 >= 0 }, "Параметры должны быть конечными неотрицательными числами")
    }
    static func loan(_ input: LoanInput) throws -> Calculation {
        let x = input
        try valid(x.amount, x.rate, x.fixedCommission, x.percentCommission, x.downPayment)
        try require((1...999).contains(x.term), "Срок должен быть от 1 до 999 месяцев")
        let p = x.amount - x.downPayment
        try require(p > 0, "Первоначальный взнос должен быть меньше суммы кредита")
        try require(["annuity", "differentiated"].contains(x.paymentType), "Выберите тип платежа")
        let commission = x.fixedCommission + p * x.percentCommission / 100
        try require(commission < p, "Общая комиссия должна быть меньше выдаваемой суммы кредита")
        let i = x.rate / 1200
        let monthly = i == 0 ? p / Double(x.term) : p * i / -expm1(-Double(x.term) * log1p(i))
        var balance = p
        let rows: [ScheduleRow] = (1...x.term).map { month in
            let interest = balance * i
            let next = x.paymentType == "annuity" && i > 0
                ? monthly / i * -expm1(-Double(x.term - month) * log1p(i))
                : p * Double(x.term - month) / Double(x.term)
            let principal = max(0, balance - next)
            balance = max(0, next)
            return ScheduleRow(month: month, payment: principal + interest, principal: principal, interest: interest, balance: balance)
        }
        let result = Calculation(loan: x, rows: rows, totalInterest: rows.reduce(0) { $0 + $1.interest } + commission,
            totalCommission: commission, totalRepayment: rows.reduce(0) { $0 + $1.payment }, amountReceived: p - commission,
            effectiveRate: effectiveRate(received: p - commission, rows: rows))
        try require(result.totalInterest.isFinite && result.effectiveRate.isFinite, "Параметры превышают допустимый диапазон")
        return result
    }
    private static func effectiveRate(received: Double, rows: [ScheduleRow]) -> Double {
        func pv(_ rate: Double) -> Double { rows.reduce(0) { $0 + $1.payment * exp(-Double($1.month) * log1p(rate)) } }
        if abs(pv(0) - received) < 1e-7 { return 0 }
        var low = 0.0, high = 1.0
        while pv(high) > received && high < 1e6 { high *= 2 }
        for _ in 0..<100 {
            let mid = (low + high) / 2
            if pv(mid) > received { low = mid } else { high = mid }
        }
        return expm1(12 * log1p((low + high) / 2)) * 100
    }
    private static func period(_ key: String) -> Int {
        ["monthly": 1, "quarterly": 3, "semi-yearly": 6, "yearly": 12][key] ?? 0
    }
    // Same calendar as Android: January start, fixed 365-day year, additions at the start of the selected month.
    static func deposit(_ input: DepositInput) throws -> Calculation {
        let x = input
        try valid(x.initialAmount, x.rate, x.monthlyAddition)
        try require((1...999).contains(x.term), "Срок должен быть от 1 до 999 месяцев")
        try require(capOptions.contains { $0.id == x.capitalization } && addOptions.contains { $0.id == x.additionPeriod })
        var balance = x.initialAmount, additions = 0.0, accumulated = 0.0
        var day = 0
        let days = [31,28,31,30,31,30,31,31,30,31,30,31]
        let addStep = period(x.additionPeriod), capMonths = period(x.capitalization)
        let capDays = x.capitalization == "daily" ? 1 : x.capitalization == "weekly" ? 7 : 0
        var rows: [ScheduleRow] = []
        for month in 1...x.term {
            let addition = addStep > 0 && month % addStep == 0 ? x.monthlyAddition : 0
            balance += addition; additions += addition
            var capitalized = 0.0
            if capDays > 0 {
                for _ in 0..<days[(month - 1) % 12] {
                    day += 1; accumulated += balance * x.rate / 100 / 365
                    if day % capDays == 0 { balance += accumulated; capitalized += accumulated; accumulated = 0 }
                }
            } else {
                accumulated += balance * x.rate / 100 * Double(days[(month - 1) % 12]) / 365
                if capMonths > 0 && month % capMonths == 0 { balance += accumulated; capitalized = accumulated; accumulated = 0 }
            }
            rows.append(ScheduleRow(month: month, interest: capitalized, balance: balance, addition: addition))
        }
        if accumulated > 0 { balance += accumulated; rows[rows.count - 1].interest += accumulated; rows[rows.count - 1].balance = balance }
        try require(balance.isFinite, "Параметры превышают допустимый диапазон")
        let n = Double(["daily":365, "weekly":52, "monthly":12, "quarterly":4, "semi-yearly":2, "yearly":1][x.capitalization] ?? 0)
        let apy = n == 0 ? x.rate : expm1(n * log1p(x.rate / 100 / n)) * 100
        return Calculation(deposit: x, rows: rows, totalInterest: balance - x.initialAmount - additions,
            effectiveRate: apy, finalAmount: balance, totalAdditions: additions)
    }
    static func loanRate(principal: Double, term: Int, payment: Double, type: String) throws -> Double {
        try require(principal.isFinite && payment.isFinite && principal > 0 && (1...999).contains(term) && payment > 0)
        try require(payment * Double(term) >= principal - 0.01, "Платёж и срок недостаточны для возврата суммы кредита")
        if type == "differentiated" { return max(0, (payment - principal / Double(term)) / principal * 1200) }
        if abs(payment * Double(term) - principal) < 0.01 { return 0 }
        func amount(_ rate: Double) -> Double { rate == 0 ? principal / Double(term) : principal * rate / -expm1(-Double(term) * log1p(rate)) }
        var low = 0.0, high = 1.0
        while amount(high) < payment { high *= 2 }
        for _ in 0..<100 { let mid = (low + high) / 2; if amount(mid) < payment { low = mid } else { high = mid } }
        return (low + high) / 2 * 1200
    }
    static func loanMissing(principal: Double, term: Double, payment: Double, total: Double, type: String, field: String) -> Double {
        let diff = type == "differentiated"
        switch field {
        case "total": return diff ? floor(0.5 + principal * (term - 1) / (2 * term) + payment * (term + 1) / 2) : term * payment
        case "payment": return floor(0.5 + (diff ? (2 * total - principal * (term - 1) / term) / (term + 1) : total / term))
        case "term":
            let b = principal + payment - 2 * total
            return floor(0.5 + (diff ? (-b + sqrt(b * b + 4 * payment * principal)) / (2 * payment) : total / payment))
        default: return 0
        }
    }
    static func depositMissing(_ input: DepositInput, target: Double, field: String) throws -> Double {
        if field == "final" { return try deposit(input).finalAmount.rounded(.toNearestOrEven) }
        try require(target > 0 && target.isFinite, "Укажите итоговую сумму")
        if field == "term" {
            var sample = input
            sample.term = 999
            try require(try deposit(sample).finalAmount >= target, "Для цели требуется срок более 999 месяцев")
            sample.term = 1
            try require(try deposit(sample).finalAmount <= target + 1, "Цель достигается раньше одного месяца")
            var low = 1, high = 999
            while low < high { let mid = (low + high) / 2; sample.term = mid; if try deposit(sample).finalAmount < target { low = mid + 1 } else { high = mid } }
            return Double(low)
        }
        func value(_ v: Double) throws -> Double { var x = input; if field == "rate" { x.rate = v } else { x.initialAmount = v }; return try deposit(x).finalAmount }
        var low = 0.0, high = field == "rate" ? 1000.0 : target
        try require(try value(low) <= target + 0.5, "Итоговая сумма меньше внесённых средств")
        try require(try value(high) >= target - 0.5, "Итоговая сумма недостижима при указанных параметрах")
        for _ in 0..<70 { let mid = (low + high) / 2; if try value(mid) < target { low = mid } else { high = mid } }
        let result = (low + high) / 2
        return field == "rate" ? (result * 100).rounded(.toNearestOrEven) / 100 : result.rounded(.toNearestOrEven)
    }
}

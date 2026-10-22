import Foundation

enum LeasingFinance {
    private static func validate(_ x: LeasingInput, reverse: Bool) throws {
        try require([x.amount,x.advance,x.rate,x.buyout,x.expenses,x.vatRate].allSatisfy { $0.isFinite && (0...1e12).contains($0) }, "Введите конечные неотрицательные суммы и ставки")
        try require(x.amount > 0 && ["rub", "percent"].contains(x.advanceUnit), "Укажите стоимость предмета лизинга")
        try require(x.advanceUnit != "percent" || x.advance < 100, "Аванс должен быть меньше 100%")
        try require(x.financed > 0, "Аванс должен быть меньше стоимости предмета")
        try require(x.expenses < x.financed, "Расходы в начале должны быть меньше суммы финансирования")
        try require((1...999).contains(x.firstMonth) && (1...999).contains(x.count) && (1...999).contains(x.maturity), "График должен укладываться в 999 месяцев")
        try require(["next", "last"].contains(x.buyoutTiming), "Выберите момент выкупного платежа")
        if reverse {
            try require((1...12).contains(x.groups.count) && x.groups.allSatisfy { (1...999).contains($0.count) && $0.payment.isFinite && (0...1e12).contains($0.payment) }, "Проверьте количество и суммы групп платежей")
            try require(x.groups.contains { $0.payment > 0 } || x.buyout > 0, "В графике должен быть хотя бы один положительный платёж")
        } else {
            try require(x.groups.isEmpty && leaseTypes.contains { $0.id == x.paymentType }, "Выберите тип графика")
            try require(x.buyout < x.financed, "Выкуп должен быть меньше суммы финансирования")
            try require(x.vatRate <= 100, "НДС должен быть от 0 до 100%")
        }
    }
    static func direct(_ x: LeasingInput) throws -> Calculation {
        try validate(x, reverse: false)
        let i = x.rate / 1200, count = x.term, p = x.financed
        var payments = Array(repeating: 0.0, count: x.maturity)
        if x.paymentType == "annuity" {
            let discount = log1p(i)
            let factor = (0..<count).reduce(0.0) { $0 + exp(-Double(x.firstMonth + $1) * discount) }
            let payment = (p - x.buyout * exp(-Double(x.maturity) * discount)) / factor
            try require(payment.isFinite && payment > 0, "Параметры превышают допустимый диапазон")
            for index in 0..<count { payments[x.firstMonth - 1 + index] = leaseRound(payment) }
            let pv = payments.enumerated().reduce(0.0) { $0 + $1.element * exp(-Double($1.offset + 1) * discount) } + x.buyout * exp(-Double(x.maturity) * discount)
            let last = x.firstMonth + count - 2
            payments[last] = leaseRound(payments[last] + (p - pv) * exp(Double(last + 1) * discount))
        } else {
            let principal = (p - x.buyout) / Double(count), grossRate = i * (1 + x.vatRate / 100)
            var unrounded = (0..<count).map { principal + (p - principal * Double($0)) * grossRate * Double($0 == 0 ? x.firstMonth : 1) }
            if x.buyout > 0 && x.buyoutTiming == "next" { unrounded[count - 1] += x.buyout * grossRate }
            let total = leaseRound(unrounded.reduce(0, +))
            for index in 0..<count { payments[x.firstMonth - 1 + index] = leaseRound(x.paymentType == "uniform" ? total / Double(count) : unrounded[index]) }
            let last = x.firstMonth + count - 2
            payments[last] = leaseRound(payments[last] + total - payments.reduce(0, +))
        }
        payments[payments.count - 1] = leaseRound(payments[payments.count - 1] + x.buyout)
        try require(payments.allSatisfy { $0.isFinite && $0 >= 0 }, "Проверьте ставку, срок и выкупной платёж")
        return try result(x, payments: payments)
    }
    static func reverse(_ x: LeasingInput) throws -> Calculation {
        try validate(x, reverse: true)
        var payments = Array(repeating: 0.0, count: x.maturity), month = x.firstMonth - 1
        for group in x.groups { for _ in 0..<group.count { payments[month] = leaseRound(group.payment); month += 1 } }
        payments[payments.count - 1] = leaseRound(payments[payments.count - 1] + x.buyout)
        return try result(x, payments: payments)
    }
    private static func monthlyRate(received: Double, payments: [Double]) -> Double {
        func pv(_ rate: Double) -> Double { payments.enumerated().reduce(0.0) { $0 + ($1.element == 0 ? 0 : $1.element * exp(-Double($1.offset + 1) * rate)) } }
        if abs(payments.reduce(0, +) - received) <= 1e-8 { return 0 }
        var low = -36.0, high = 1.0
        while pv(high) > received && high < 100 { high *= 2 }
        for _ in 0..<180 { let mid = (low + high) / 2; if pv(mid) > received { low = mid } else { high = mid } }
        return expm1((low + high) / 2)
    }
    private static func result(_ x: LeasingInput, payments: [Double]) throws -> Calculation {
        let received = x.financed - x.expenses, monthly = monthlyRate(received: received, payments: payments)
        let nominal = monthly * 1200, effective = expm1(12 * log1p(monthly)) * 100
        try require(nominal.isFinite && effective.isFinite, "Ставка выходит за допустимый диапазон")
        var balances = Array(repeating: 0.0, count: payments.count + 1)
        for index in payments.indices.reversed() { balances[index] = (payments[index] + balances[index + 1]) / (1 + monthly) }
        let rows = payments.enumerated().map { index, value in
            ScheduleRow(month: index + 1, payment: value, principal: value - balances[index] * monthly, interest: balances[index] * monthly, balance: balances[index + 1])
        }
        let total = leaseRound(x.advanceAmount + x.expenses + payments.reduce(0, +))
        return Calculation(leasing: x, rows: rows, totalInterest: leaseRound(total - x.amount), totalCommission: x.expenses,
            totalRepayment: total, amountReceived: received, effectiveRate: effective, nominalRate: nominal)
    }
}

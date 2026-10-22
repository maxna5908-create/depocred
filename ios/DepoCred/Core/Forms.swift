import Foundation

struct Form: Equatable {
    var fields: [String: String] = [:]
    var target: String? = nil
    var manual: [String] = []
    func text(_ key: String) -> String { fields[key] ?? "" }
    func number(_ key: String) -> Double { Double(text(key).replacingOccurrences(of: ",", with: ".")) ?? 0 }
    func integer(_ key: String) -> Int { let n = number(key); return n.isFinite && abs(n) < 1e8 ? Int(n) : 0 }
    func with(_ key: String, _ value: String) -> Form { var copy = self; copy.fields[key] = value; return copy }
    static func empty(_ tool: Tool, reverse: Bool) -> Form {
        switch tool {
        case .loan: return Form(fields: ["paymentType": reverse ? "" : "annuity"])
        case .leasing: return Form(fields: ["advanceUnit":"percent", "paymentType":"annuity", "buyoutTiming":"next", "firstMonth":"1", "groupCount":"1"])
        default: return Form(fields: ["cap":"monthly", "period":"none"])
        }
    }
    var loan: LoanInput { LoanInput(amount: number("amount"), rate: number("rate"), term: integer("term"), fixedCommission: number("fixed"), percentCommission: number("commission"), paymentType: text("paymentType"), downPayment: number("down")) }
    var deposit: DepositInput { DepositInput(initialAmount: number("amount"), rate: number("rate"), term: integer("term"), monthlyAddition: number("addition"), capitalization: text("cap"), additionPeriod: text("period")) }
    var groupCount: Int { min(12, max(1, integer("groupCount"))) }
    func leasing(reverse: Bool) -> LeasingInput {
        LeasingInput(amount: number("amount"), advance: number("advance"), advanceUnit: text("advanceUnit"), rate: number("rate"), term: integer("term"), paymentType: text("paymentType"),
            groups: reverse ? (0..<groupCount).map { LeasePaymentGroup(count: integer("count\($0)"), payment: number("payment\($0)")) } : [],
            buyout: number("buyout"), buyoutTiming: text("buyoutTiming"), firstMonth: integer("firstMonth"), expenses: number("expenses"),
            vatRate: reverse || text("paymentType") == "annuity" ? 0 : number("vat"))
    }
    func order(tool: Tool, reverse: Bool) -> [String] {
        switch tool {
        case .loan:
            if reverse { return (text("paymentType").isEmpty ? ["amount"] : ["amount","term","payment","total","down"]).filter { $0 != target } }
            return ["amount","rate","term","fixed","commission"]
        case .deposit: return ["amount"] + (reverse ? ["final"] : []) + ["rate","term"] + (text("period") != "none" ? ["addition"] : [])
        case .leasing:
            let middle = reverse ? (0..<groupCount).flatMap { ["count\($0)","payment\($0)"] } : ["rate","term"] + (text("paymentType") == "annuity" ? [] : ["vat"])
            return ["amount","advance"] + middle + ["buyout","firstMonth","expenses"]
        default: return []
        }
    }
    func required(tool: Tool, reverse: Bool) -> (String, String)? {
        if number("amount") <= 0 { return ("amount", tool == .leasing ? "Укажите стоимость предмета лизинга" : "Укажите сумму для расчёта") }
        if tool == .leasing {
            let x = leasing(reverse: reverse)
            if text("advanceUnit") == "percent" && number("advance") >= 100 || x.advanceAmount >= number("amount") { return ("advance", "Аванс должен быть меньше стоимости предмета") }
            if reverse {
                for index in 0..<groupCount {
                    if !(1...999).contains(integer("count\(index)")) { return ("count\(index)", "Укажите количество платежей в группе \(index + 1)") }
                    if text("payment\(index)").isEmpty { return ("payment\(index)", "Укажите платёж группы \(index + 1) (можно 0)") }
                }
            } else {
                if text("rate").isEmpty { return ("rate", "Укажите ставку финансирования (можно 0%)") }
                if !(1...999).contains(integer("term")) { return ("term", "Укажите количество регулярных платежей") }
                if number("buyout") >= x.financed { return ("buyout", "Выкуп должен быть меньше суммы финансирования") }
            }
            if !(1...999).contains(integer("firstMonth")) { return ("firstMonth", "Укажите месяц первого платежа от 1 до 999") }
            if x.maturity > 999 { return (reverse ? "count0" : "term", "График должен укладываться в 999 месяцев") }
            if x.expenses >= x.financed { return ("expenses", "Расходы в начале должны быть меньше суммы финансирования") }
            if !reverse && x.vatRate > 100 { return ("vat", "НДС должен быть от 0 до 100%") }
            return nil
        }
        if tool == .loan && reverse {
            if text("paymentType").isEmpty { return ("paymentType", "Выберите тип платежа") }
            if !(1...999).contains(integer("term")) { return ("term", "Укажите срок от 1 до 999 месяцев") }
            if number("payment") <= 0 { return ("payment", "Заполните любые два значения из трёх") }
            if number("total") <= 0 { return ("total", "Заполните любые два значения из трёх") }
        } else {
            if tool == .deposit && reverse {
                if number("final") <= 0 { return ("final", "Заполните три параметра депозита") }
                if number("final") <= number("amount") { return ("final", "Итоговая сумма должна быть больше суммы депозита") }
            }
            if !reverse && text("rate").isEmpty { return ("rate", "Укажите процентную ставку (в том числе 0%)") }
            if !(1...999).contains(integer("term")) { return ("term", "Укажите срок от 1 до 999 месяцев") }
        }
        return nil
    }
    func calculated(tool: Tool, reverse: Bool) throws -> (Form, Calculation) {
        if tool == .leasing { return (self, try reverse ? LeasingFinance.reverse(leasing(reverse: true)) : LeasingFinance.direct(leasing(reverse: false))) }
        var form = self
        if reverse && tool == .loan {
            let p = number("amount") - number("down")
            try require(number("total") >= p, "Сумма возврата не может быть меньше суммы кредита")
            let rate = try number("payment") * number("term") <= p ? 0 : Finance.loanRate(principal: p, term: integer("term"), payment: number("payment"), type: text("paymentType"))
            form.fields["rate"] = numberText(rate)
        } else if reverse && tool == .deposit {
            try require(number("final") > number("amount"), "Итоговая сумма должна быть больше суммы депозита")
            form.fields["rate"] = numberText(try Finance.depositMissing(deposit, target: number("final"), field: "rate"))
        }
        return (form, try tool == .loan ? Finance.loan(form.loan) : Finance.deposit(form.deposit))
    }
}

enum ReverseForms {
    static func loan(_ form: Form) -> Form {
        let keys = ["term","payment","total"]
        var next = form
        if let target = next.target, keys.filter({ $0 != target }).contains(where: { next.number($0) <= 0 }) { next.fields[target] = ""; next.target = nil }
        if next.target == nil && keys.filter({ next.number($0) > 0 }).count == 2 { next.target = keys.first { next.number($0) <= 0 } }
        guard let target = next.target, next.number("amount") > next.number("down"), !next.text("paymentType").isEmpty else { return next }
        let value = Finance.loanMissing(principal: next.number("amount") - next.number("down"), term: next.number("term"), payment: next.number("payment"), total: next.number("total"), type: next.text("paymentType"), field: target)
        return next.with(target, value.isFinite && value > 0 ? numberText(value) : "")
    }
    static func deposit(_ form: Form, editedKey: String) -> Form {
        let keys = ["amount","rate","term","final"]
        var next = form
        if keys.contains(editedKey) { next.manual = Array((form.manual.filter { $0 != editedKey } + [editedKey]).suffix(3)) }
        next.target = nil
        let filled = keys.filter { next.number($0) > 0 }
        let target = filled.count == 3 ? keys.first { !filled.contains($0) } : filled.count == 4 ? keys.first { !next.manual.contains($0) } : nil
        guard let target, !next.manual.contains(target) else { return next }
        next.target = target
        if ["rate","term"].contains(target) && next.number("final") <= next.number("amount") { return next }
        guard let value = try? depositParameter(next, field: target) else { return next }
        let tolerance = target == "rate" ? 0.005 : 0.5
        return value > 0 && abs(value - next.number(target)) > tolerance ? next.with(target, numberText(value)) : next
    }
    private static func depositParameter(_ form: Form, field: String) throws -> Double {
        let input = form.deposit, target = form.number("final")
        if field == "final" { return floor(try Finance.deposit(input).finalAmount + 0.5) }
        var low = field == "term" ? 1.0 : 0, high = field == "term" ? 600.0 : field == "rate" ? 1000 : target, mid = 0.0
        for _ in 0..<40 {
            mid = (low + high) / 2
            var sample = input
            switch field { case "term": sample.term = Int(floor(mid + 0.5)); case "rate": sample.rate = mid; default: sample.initialAmount = mid }
            if try Finance.deposit(sample).finalAmount < target { low = mid } else { high = mid }
        }
        return field == "rate" ? floor(mid * 100 + 0.5) / 100 : floor(mid + 0.5)
    }
}

import SwiftUI

struct ResultView: View {
    @Environment(\.palette) private var p
    @State private var explanation: String?
    let entry: SavedCalculation
    var onType: ((String) -> Void)? = nil
    var onSave: (() -> Void)? = nil
    let onSchedule: () -> Void
    let onExport: (SavedCalculation, Bool) -> Void
    private var c: Calculation { entry.result }
    var body: some View {
        VStack(spacing: 12) {
            Text("\(entry.mode == "reverse" ? "Обратный" : "Прямой") расчет по \(c.type == .loan ? "кредиту" : c.type == .deposit ? "депозиту" : "лизингу")")
                .font(.inter(13, .bold)).foregroundStyle(p.primary).padding(.horizontal, 20).padding(.vertical, 8)
                .background(p.solid, in: Capsule()).overlay(Capsule().strokeBorder(p.border, lineWidth: 1))
            if let loan = c.loan, entry.mode != "reverse", let onType {
                AttachedPanel(titles: ["Аннуитетный","Дифференцированный"], selected: loan.paymentType == "annuity" ? 0 : 1, onSelect: { onType($0 == 0 ? "annuity" : "differentiated") }) { details; actions }
            } else { Glass { details; actions } }
        }.accessibilityIdentifier("calculation_result")
            .sheet(isPresented: Binding(get: { explanation != nil }, set: { if !$0 { explanation = nil } })) {
                RateExplanation(type: explanation ?? "deposit", calculation: c).environment(\.palette, p)
            }
    }
    @ViewBuilder private var details: some View {
        if let lease = c.leasing { leaseDetails(lease) }
        else {
            if let loan = c.loan { Text(loanDescriptions[loan.paymentType] ?? "").font(.inter(11)).foregroundStyle(p.muted).lineSpacing(4) }
            HStack {
                Text(c.type == .loan ? "Ежемесячный платеж" : "Итоговая сумма").font(.inter(13)).foregroundStyle(p.muted)
                Spacer()
                Text(c.type == .deposit ? "Депозит" : c.loan?.paymentType == "annuity" ? "Фиксированный" : "Уменьшаемый")
                    .font(.inter(9, .medium, relativeTo: .caption2)).foregroundStyle(p.primary).padding(6).background(p.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            }
            Text(money(c.type == .loan ? c.firstPayment : c.finalAmount)).font(.inter(34, .bold, relativeTo: .largeTitle)).foregroundStyle(p.text).minimumScaleFactor(0.6).lineLimit(1)
            if c.loan?.paymentType == "differentiated" { Text("до \(money(c.rows.last?.payment ?? 0))").font(.inter(11)).foregroundStyle(p.muted) }
            Divider().overlay(p.border)
            if let loan = c.loan {
                MetricPair(left: ("Переплата", money(c.totalInterest)), right: ("Сумма возврата", money(c.totalRepayment)), accent: true)
                MetricPair(left: ("Комиссия", money(c.totalCommission)), right: ("Срок кредита", "\(loan.term) мес."))
                MetricPair(left: ("Сумма кредита", money(loan.amount)), right: ("Получено на руки", money(c.amountReceived)))
                if loan.downPayment > 0 { Metric(title: "Первоначальный взнос", value: money(loan.downPayment)) }
                TwoFields {
                    infoMetric("Номинальная ставка", percent(loan.rate), type: "nominal")
                    infoMetric("Эффективная ставка", percent(c.effectiveRate), type: "effective")
                }
                Divider().overlay(p.border)
                Text("Переплата по кредиту составит \(percent(c.totalInterest / (loan.amount - loan.downPayment) * 100)) от суммы кредита.").font(.inter(11)).italic().foregroundStyle(p.primary)
                InfoLink(title: "Как рассчитана номинальная ставка") { explanation = "nominal" }
                InfoLink(title: "Как рассчитана эффективная ставка") { explanation = "effective" }
            } else if let deposit = c.deposit {
                MetricPair(left: ("Депозит", money(deposit.initialAmount)), right: ("Ставка / Срок", "\(percent(deposit.rate)) | \(deposit.term) мес."))
                MetricPair(left: ("Пополнения", money(c.totalAdditions)), right: ("Доход", money(c.totalInterest)), accent: true)
                infoMetric("Эффективная годовая ставка", percent(c.effectiveRate), type: "deposit")
                Divider().overlay(p.border)
                Text("Доходность с учетом сложного процента составит \(percent(c.totalInterest / max(1, deposit.initialAmount + c.totalAdditions) * 100)) за весь период.").font(.inter(11)).italic().foregroundStyle(p.primary)
                InfoLink(title: "Как рассчитана ставка депозита") { explanation = "deposit" }
            }
        }
    }
    private func infoMetric(_ title: String, _ value: String, type: String) -> some View {
        Button { explanation = type } label: {
            HStack(alignment: .top, spacing: 4) { Metric(title: title, value: value, accent: true); Glyph(name: "info", size: 14).foregroundStyle(p.primary) }.padding(.vertical, 4).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityLabel("Информация: " + title)
    }
    @ViewBuilder private func leaseDetails(_ x: LeasingInput) -> some View {
        Text("Эквивалент кредита, годовых").font(.inter(13)).foregroundStyle(p.muted)
        Text(percent(c.nominalRate)).font(.inter(36, .bold, relativeTo: .largeTitle)).foregroundStyle(p.text)
        infoMetric("Эффективная годовая ставка", percent(c.effectiveRate), type: "leasing")
        InfoLink(title: "Как рассчитана ставка") { explanation = "leasing" }
        Divider().overlay(p.border)
        if entry.mode != "reverse" {
            let lastRegular = x.firstMonth + x.count - 1
            let last = c.rows[lastRegular - 1].payment - (x.buyoutTiming == "last" ? x.buyout : 0)
            let first = c.rows[x.firstMonth - 1].payment - (x.count == 1 && x.buyoutTiming == "last" ? x.buyout : 0)
            MetricPair(left: ("Первый платёж", money(first, cents: true)), right: ("Последний регулярный", money(last, cents: true)), accent: true)
            Text(leaseTypes.first { $0.id == x.paymentType }?.title ?? "").font(.inter(11, .semibold)).foregroundStyle(p.muted)
            Text(leaseDescriptions[x.paymentType] ?? "").font(.inter(11)).foregroundStyle(p.muted).lineSpacing(4)
        }
        MetricPair(left: ("Стоимость предмета", money(x.amount, cents: true)), right: ("Аванс", money(x.advanceAmount, cents: true)))
        MetricPair(left: ("Финансирование", money(x.financed, cents: true)), right: ("До последнего платежа", "\(x.maturity) мес."))
        MetricPair(left: ("Переплата с расходами", money(c.totalInterest, cents: true)), right: ("Всего с авансом", money(c.totalRepayment, cents: true)), accent: true)
        MetricPair(left: ("Выкуп", money(x.buyout, cents: true)), right: ("Расходы в начале", money(x.expenses, cents: true)))
        Text("Ставки рассчитаны по платежам с НДС, без налоговых вычетов. Месяц 0 — получение финансирования и оплата аванса и отдельных расходов.").font(.inter(11)).foregroundStyle(p.muted).lineSpacing(4)
    }
    private var actions: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                ActionButton(title: c.type == .leasing ? "Платежи" : c.type == .loan ? "График платежей" : "График начислений", action: onSchedule).accessibilityIdentifier("open_schedule")
                if let onSave { IconButton(icon: "save", label: "Сохранить расчёт", action: onSave).accessibilityIdentifier("save_calculation") }
                IconButton(icon: "share_2", label: "Отправить PDF") { onExport(entry, false) }
            }
            ActionButton(title: "Сохранить PDF", icon: "download", primary: false) { onExport(entry, true) }.accessibilityIdentifier("save_pdf")
        }
    }
}

struct RateExplanation: View {
    @Environment(\.palette) private var p
    let type: String
    let calculation: Calculation
    private var c: Calculation { calculation }
    var body: some View {
        DialogPage(title: type == "nominal" ? "Номинальная ставка" : type == "leasing" ? "Ставка лизинга" : "Эффективная ставка") {
            if let x = c.leasing {
                section("Что означает ставка", "Это ставка кредита с тем же денежным потоком. Она учитывает размер и время каждого платежа, выкуп и отдельно оплаченные расходы.")
                MetricPair(left: ("Финансирование", money(x.financed, cents: true)), right: ("За вычетом расходов", money(c.amountReceived, cents: true)))
                MetricPair(left: ("Месячная ставка", percent(c.nominalRate / 12)), right: ("Номинальная годовая", percent(c.nominalRate)), accent: true)
                Metric(title: "Эффективная годовая", value: percent(c.effectiveRate), accent: true)
                formula("Σ Платёжₘ / (1 + r)ᵐ = финансирование − расходы\nНоминальная = r × 12\nЭффективная = (1 + r)¹² − 1")
                section("Периоды и аванс", "Периоды — месяцы от момента финансирования. Аванс уменьшает финансирование и не включается в платежи повторно. Если фактическая дата выдачи отличается от аванса, задайте соответствующую отсрочку первого платежа. Удорожание за срок не является годовой ставкой кредита.")
            } else if let x = c.loan, type == "nominal" {
                section("Экскурс", "Номинальная процентная ставка — это годовая ставка, указанная в договоре кредитования. Она служит базой для начисления процентов за каждый расчетный период, но не учитывает капитализацию и дополнительные расходы (комиссии).")
                section("Как рассчитывается", "Ставка за один период равна годовой ставке, делённой на количество таких периодов в году.")
                formula("J = j / m\nj — годовая ставка, m — число периодов в году")
                section("Шаг 1: Определение базовой ставки", "Годовая ставка в вашем расчёте: \(percent(x.rate)).")
                section("Шаг 2: Расчет ставки за период", "Кредит рассчитывается помесячно. Эта ставка применяется к остатку долга в каждом из \(x.term) месяцев.")
                formula("\(percent(x.rate)) / 12 = \(percent(x.rate / 12, digits: 4)) в месяц")
            } else if let x = c.loan {
                section("Экскурс", "Эффективная процентная ставка отражает годовую стоимость кредита с учётом времени платежей, сложных процентов и комиссий.")
                section("Как рассчитывается", "Сумма полученного кредита за вычетом комиссий равна приведенной стоимости всех будущих платежей. Месячная ставка находится численно из уравнения денежных потоков.")
                formula("Prec = Σ Mk / (1 + r)ᵏ\nЭПС = ((1 + r)¹² − 1) × 100%")
                section("Шаг 1: Чистая сумма кредита", "Вычитаем первоначальный взнос и комиссии:")
                formula("\(money(x.amount)) − \(money(x.downPayment)) − \(money(c.totalCommission)) = \(money(c.amountReceived))")
                section("Шаг 2: Поиск доходности (IRR)", "Берутся все \(x.term) платежей: от \(money(c.firstPayment)) до \(money(c.rows.last?.payment ?? 0)). Находим месячную ставку r, при которой их дисконтированная сумма равна полученным средствам.")
                let monthly = expm1(log1p(c.effectiveRate / 100) / 12)
                section("Шаг 3: Годовая эффективная ставка", "Найденная месячная ставка: \(percent(monthly * 100, digits: 4)).")
                formula("((1 + \(numberText(monthly)))¹² − 1) × 100% = \(percent(c.effectiveRate))")
            } else if let x = c.deposit {
                section("Экскурс", "Эффективная годовая ставка показывает доходность с учетом частоты капитализации процентов. Срок и пополнения учитываются отдельно в графике начислений.")
                section("Как рассчитывается", "j — годовая ставка в долях единицы, m — число капитализаций в году. Без капитализации эффективная ставка равна номинальной.")
                formula("ЭПС = ((1 + j / m)ᵐ − 1) × 100%")
                section("Параметры депозита", "Ставка: \(percent(x.rate)). Капитализация: \(capOptions.first { $0.id == x.capitalization }?.title.lowercased() ?? ""). Срок: \(x.term) мес.")
                Metric(title: "Эффективная годовая ставка", value: percent(c.effectiveRate), accent: true)
                MetricPair(left: ("Вклад", money(x.initialAmount)), right: ("Пополнения", money(c.totalAdditions)))
                MetricPair(left: ("Доход", money(c.totalInterest)), right: ("Итоговая сумма", money(c.finalAmount)), accent: true)
            }
        }.accessibilityIdentifier("rate_info")
    }
    private func section(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.uppercased()).font(.inter(10, .bold, relativeTo: .caption)).tracking(1).foregroundStyle(p.primary)
            Text(text).font(.inter(14)).foregroundStyle(p.muted).lineSpacing(6)
        }
    }
    private func formula(_ text: String) -> some View {
        Text(text).font(.system(size: 12, weight: .semibold, design: .monospaced)).foregroundStyle(p.primary).lineSpacing(6).frame(maxWidth: .infinity, alignment: .leading).padding(12).background(p.field, in: RoundedRectangle(cornerRadius: 8))
    }
}

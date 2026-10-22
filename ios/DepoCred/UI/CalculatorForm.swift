import SwiftUI

struct CalculatorForm: View {
    @ObservedObject var model: AppModel
    let focus: FocusState<String?>.Binding
    @Environment(\.palette) private var p
    private var form: Form { model.form }
    private var order: [String] { form.order(tool: model.tool, reverse: model.reverse) }
    private var loan: Bool { model.tool == .loan }
    private func field(_ title: String, _ key: String, suffix: String = "₽", integer: Bool = false, enabled: Bool = true) -> some View {
        let calculated = model.reverse && form.target == key
        let index = order.firstIndex(of: key)
        let next = index.flatMap { order.indices.contains($0 + 1) ? order[$0 + 1] : nil }
        return NumericField(title: title, key: key, form: form, suffix: suffix, integer: integer, enabled: enabled,
            calculated: calculated, locked: loan && calculated, focus: focus, next: next, edit: model.edit)
    }
    var body: some View {
        AttachedPanel(titles: ["Прямой","Обратный"], selected: model.reverse ? 1 : 0, onSelect: { focus.wrappedValue = nil; model.mode($0 == 1) }) {
            if model.tool == .leasing { leasingFields }
            else { standardFields }
            HStack(spacing: 8) {
                ActionButton(title: "Рассчитать", icon: "calculator", busy: model.calculating) { if model.calculate() { focus.wrappedValue = nil } }.accessibilityIdentifier("calculate")
                IconButton(icon: "trash_2", label: "Сбросить параметры") { focus.wrappedValue = nil; model.reset() }
            }
        }.id(model.formKey)
    }
    @ViewBuilder private var standardFields: some View {
        if model.reverse && model.settings.hints {
            Button {
                model.updateSettings { $0.hints = false }; model.notify("Подсказки можно включить в меню «Инфо»")
            } label: {
                Hint(text: (loan
                    ? "Обратный расчет позволяет вычислить номинальную и эффективную ставку кредита. Укажите сумму и тип платежа, затем любые два из трёх параметров: срок, платёж, сумма возврата."
                    : "Обратный расчет позволяет вычислить параметры депозита. Заполните любые три поля: сумма, итоговая сумма, ставка, срок. Четвёртое рассчитается автоматически.") + "\n\nНАЖМИТЕ, ЧТОБЫ СКРЫТЬ ПОДСКАЗКУ")
            }.buttonStyle(.plain)
        }
        field(loan ? "Сумма кредита (₽)" : "Сумма депозита (₽)", "amount")
        if loan && model.reverse {
            VStack(alignment: .leading, spacing: 12) {
                FieldLabel(text: "Тип ежемесячного платежа")
                ForEach([Choice("annuity", "Аннуитетный — фиксированный", loanDescriptions["annuity"]!), Choice("differentiated", "Дифференцированный — уменьшаемый", loanDescriptions["differentiated"]!)]) { option in
                    Button { focus.wrappedValue = nil; model.paymentType(option.id) } label: {
                        HStack(alignment: .top, spacing: 10) {
                            Circle().strokeBorder(form.text("paymentType") == option.id ? p.primary : p.muted.opacity(0.4), lineWidth: 2).frame(width: 16, height: 16)
                                .overlay(Circle().fill(form.text("paymentType") == option.id ? p.primary : .clear).frame(width: 8, height: 8))
                            VStack(alignment: .leading, spacing: 5) {
                                Text(option.title).foregroundStyle(p.text)
                                Text(option.detail).foregroundStyle(p.muted).lineSpacing(3)
                            }.font(.inter(11)).frame(maxWidth: .infinity, alignment: .leading)
                        }.padding(12).overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(p.border, lineWidth: 1)).contentShape(RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(PressStyle()).accessibilityAddTraits(form.text("paymentType") == option.id ? .isSelected : [])
                }
            }.id("input_paymentType")
            if !form.text("paymentType").isEmpty {
                TwoFields {
                    field("Срок (мес.)", "term", suffix: "мес.", integer: true)
                    field(form.text("paymentType") == "differentiated" ? "Первый платёж (₽)" : "Платёж (₽)", "payment")
                }
                field("Сумма возврата (₽)", "total")
                field("Первоначальный взнос (₽)", "down")
            }
        } else {
            if !loan && model.reverse { field("Итоговая сумма (₽)", "final") }
            TwoFields { field("Ставка (%)", "rate", suffix: "%"); field("Срок (мес.)", "term", suffix: "мес.", integer: true) }
            if loan {
                TwoFields { field("Комиссия (₽)", "fixed"); field("Комиссия (%)", "commission", suffix: "%") }
                Text("*Расчет производится для обоих типов платежей.").font(.inter(13)).italic().foregroundStyle(p.primary)
            } else {
                TwoFields {
                    OptionField(title: "Пополнение", value: form.text("period"), options: addOptions) { model.edit("period", $0) }
                    field("Сумма взноса (₽)", "addition", enabled: form.text("period") != "none")
                }
                OptionField(title: "Капитализация процентов", value: form.text("cap"), options: capOptions) { model.edit("cap", $0) }
            }
        }
    }
    @ViewBuilder private var leasingFields: some View {
        if model.settings.hints {
            Hint(text: model.reverse
                ? "Укажите стоимость, аванс и платежи из предложения. Для ступенчатого графика добавьте группы в порядке оплаты. Выкуп укажите отдельно, не включайте его повторно в группу."
                : "Укажите стоимость, аванс, ставку и срок, чтобы рассчитать платежи. В выборе графика описано, как рассчитывается каждый вариант. Выкуп и отдельные расходы учитываются дополнительно.")
        }
        field("Стоимость предмета с НДС (₽)", "amount")
        TwoFields {
            field(form.text("advanceUnit") == "percent" ? "Аванс (%)" : "Аванс (₽)", "advance", suffix: form.text("advanceUnit") == "percent" ? "%" : "₽")
            OptionField(title: "Единица аванса", value: form.text("advanceUnit"), options: [Choice("percent","%"), Choice("rub","₽")]) { model.edit("advanceUnit", $0) }
        }
        if form.number("amount") > 0 {
            let input = form.leasing(reverse: model.reverse)
            Text("Аванс: \(money(input.advanceAmount, cents: true)) · Финансирование: \(money(input.financed, cents: true))").font(.inter(11)).foregroundStyle(p.primary)
        }
        if model.reverse {
            ForEach(0..<form.groupCount, id: \.self) { index in
                VStack(alignment: .leading, spacing: 8) {
                    FieldLabel(text: "ГРУППА ПЛАТЕЖЕЙ \(index + 1)")
                    TwoFields { field("Количество", "count\(index)", suffix: "мес.", integer: true); field("Платёж (₽)", "payment\(index)") }
                }
            }
            HStack(spacing: 8) {
                if form.groupCount < 12 { ActionButton(title: "Добавить группу", primary: false) { focus.wrappedValue = nil; model.edit("groupCount", String(form.groupCount + 1)) } }
                if form.groupCount > 1 { IconButton(icon: "trash_2", label: "Удалить последнюю группу") { focus.wrappedValue = nil; model.edit("groupCount", String(form.groupCount - 1)) } }
            }
        } else {
            OptionField(title: "График платежей", value: form.text("paymentType"), options: leaseTypes) { model.edit("paymentType", $0) }
            field("Ставка финансирования (% годовых)", "rate", suffix: "%")
            field("Количество регулярных платежей", "term", suffix: "мес.", integer: true)
            if form.text("paymentType") != "annuity" {
                field("НДС на финансирование (%)", "vat", suffix: "%")
                Text("Стоимость и аванс уже включают НДС. Здесь задаётся НДС только на проценты финансирования; пустое поле — 0%.").font(.inter(11)).foregroundStyle(p.muted)
            }
        }
        field("Выкупной платёж (₽)", "buyout")
        if form.number("buyout") > 0 { OptionField(title: "Момент выкупа", value: form.text("buyoutTiming"), options: leaseTimings) { model.edit("buyoutTiming", $0) } }
        field("Первый платёж через (мес.)", "firstMonth", suffix: "мес.", integer: true)
        field("Расходы в начале договора (₽)", "expenses")
        Text("Комиссии и услуги, оплачиваемые отдельно сразу. Расходы, уже включённые в платежи, повторно не добавляйте. Расчёт по месячным периодам, без налоговых вычетов.").font(.inter(11)).foregroundStyle(p.muted).lineSpacing(4)
        if form.leasing(reverse: model.reverse).count > 0 { Text("До последнего платежа: \(form.leasing(reverse: model.reverse).maturity) мес. Аванс — в момент 0.").font(.inter(11)).foregroundStyle(p.primary) }
    }
}

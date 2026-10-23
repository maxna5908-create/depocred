import SwiftUI

struct HistoryView: View {
    @ObservedObject var model: AppModel
    let onSchedule: (SavedCalculation) -> Void
    let onExport: (SavedCalculation, Bool) -> Void
    @Environment(\.palette) private var p
    @State private var filter: Tool?
    private var entries: [SavedCalculation] { model.history.filter { filter == nil || $0.result.type == filter }.sorted { $0.timestamp > $1.timestamp } }
    var body: some View {
        VStack(spacing: 16) {
            HStack(spacing: 4) {
                ForEach([Tool.deposit, .loan, .leasing]) { tool in
                    let count = model.history.filter { $0.result.type == tool }.count
                    Button { filter = filter == tool ? nil : tool } label: {
                        HStack(spacing: 4) {
                            Text(tool.title).font(.inter(14, .semibold))
                            if count > 0 { Text(String(count)).font(.inter(10, .bold)).padding(4).background(p.primary.opacity(0.1), in: RoundedRectangle(cornerRadius: 8)) }
                        }.foregroundStyle(filter == tool ? Color.white : p.muted).frame(maxWidth: .infinity, minHeight: 44).background(filter == tool ? p.primary : .clear, in: RoundedRectangle(cornerRadius: 8))
                    }.buttonStyle(.plain).accessibilityIdentifier("history_filter_" + tool.rawValue).accessibilityAddTraits(filter == tool ? .isSelected : [])
                }
            }.padding(4).background(p.field, in: RoundedRectangle(cornerRadius: 12)).overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(p.border, lineWidth: 1))
            if entries.isEmpty {
                VStack(spacing: 16) {
                    Glyph(name: "bookmark", size: 40).foregroundStyle(p.muted.opacity(0.4)).frame(width: 80, height: 80).background(p.field, in: Circle())
                    Text("История расчетов пуста").font(.inter(14, .bold)).foregroundStyle(p.muted)
                }.frame(maxWidth: .infinity).padding(.vertical, 80)
            }
            ForEach(entries) { entry in
                HistoryCard(entry: entry, onSchedule: { onSchedule(entry) }, onExport: onExport, onDelete: { model.delete(entry.id) })
            }
        }
    }
}
struct HistoryCard: View {
    @Environment(\.palette) private var p
    let entry: SavedCalculation
    let onSchedule: () -> Void
    let onExport: (SavedCalculation, Bool) -> Void
    let onDelete: () -> Void
    private var c: Calculation { entry.result }
    private var accent: Color { c.type == .loan ? Color(hex: 0xF43F5E) : c.type == .deposit ? Color(hex: 0x10B981) : p.primary }
    var body: some View {
        Glass(padding: 20) {
            HStack {
                Text(entry.timestamp.formatted(.dateTime.day().month(.abbreviated).year().hour().minute().locale(Locale(identifier: "ru_RU"))))
                    .font(.inter(10, .semibold, relativeTo: .caption2)).foregroundStyle(p.muted)
                Spacer()
                Text(c.type.title.uppercased()).font(.inter(10, .bold, relativeTo: .caption2)).foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 4).background(accent, in: Capsule())
            }
            VStack(alignment: .leading, spacing: 4) {
                FieldLabel(text: "Итоговая сумма")
                Text(money(c.type == .deposit ? c.finalAmount : c.totalRepayment, cents: c.type == .leasing)).font(.inter(30, .black, relativeTo: .title)).foregroundStyle(p.text).lineLimit(1).minimumScaleFactor(0.6)
            }
            Divider().overlay(accent.opacity(0.2))
            if let x = c.loan {
                MetricPair(left: ("Кредит", money(x.amount)), right: ("Ставка / Срок", "\(percent(x.rate)) | \(x.term) мес."))
                MetricPair(left: (x.paymentType == "annuity" ? "Платёж" : "Первый платёж", money(c.firstPayment)), right: ("Переплата", money(c.totalInterest)))
            } else if let x = c.deposit {
                MetricPair(left: ("Депозит", money(x.initialAmount)), right: ("Ставка / Срок", "\(percent(x.rate)) | \(x.term) мес."))
                MetricPair(left: ("Пополнения", money(c.totalAdditions)), right: ("Доход", money(c.totalInterest)))
            } else if let x = c.leasing {
                MetricPair(left: ("Предмет лизинга", money(x.amount, cents: true)), right: ("Аванс", money(x.advanceAmount, cents: true)))
                MetricPair(left: ("Эквивалент кредита", percent(c.nominalRate)), right: ("Эффективная ставка", percent(c.effectiveRate)), accent: true)
                MetricPair(left: ("Срок / Выкуп", "\(x.maturity) мес. / \(money(x.buyout, cents: true))"), right: ("Переплата", money(c.totalInterest, cents: true)))
            }
            Divider().overlay(accent.opacity(0.2))
            HStack(spacing: 8) {
                ActionButton(title: c.type == .leasing ? "Платежи" : "График", primary: false, action: onSchedule)
                IconButton(icon: "share_2", label: "Отправить PDF") { onExport(entry, false) }
                IconButton(icon: "trash_2", label: "Удалить расчёт", action: onDelete)
            }
        }.overlay(alignment: .leading) { Rectangle().fill(accent).frame(width: 4) }.clipShape(RoundedRectangle(cornerRadius: 16))
            .accessibilityElement(children: .contain).accessibilityIdentifier("history_entry_" + c.type.rawValue)
    }
}

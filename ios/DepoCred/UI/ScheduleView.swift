import SwiftUI

struct ScheduleView: View {
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    @State private var exported: ExportedFile?
    @State private var exportError: String?
    let entry: SavedCalculation
    let onExport: (SavedCalculation, Bool) -> Void
    private var c: Calculation { entry.result }
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                IconButton(icon: "arrow_left", label: "Назад") { dismiss() }
                Text(c.type == .leasing ? "Платежи лизинга" : c.type == .loan ? "График платежей" : "График начислений")
                    .font(.inter(18, .bold, relativeTo: .headline)).foregroundStyle(p.text).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                IconButton(icon: "share_2", label: "Отправить PDF") { export(false) }
            }.padding(16)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                    VStack(spacing: 12) {
                        if let x = c.loan {
                            MetricPair(left: ("Сумма кредита", money(x.amount)), right: ("Ставка / Срок", "\(percent(x.rate)) | \(x.term) мес."))
                            MetricPair(left: ("Переплата", money(c.totalInterest)), right: ("Сумма возврата", money(c.totalRepayment)), accent: true)
                            if c.totalCommission > 0 { MetricPair(left: ("Комиссия", money(c.totalCommission)), right: ("Проценты", money(c.totalInterest - c.totalCommission))) }
                        } else if let x = c.deposit {
                            MetricPair(left: ("Сумма вклада", money(x.initialAmount)), right: ("Пополнения", money(c.totalAdditions)))
                            MetricPair(left: ("Ставка / Срок", "\(percent(x.rate)) | \(x.term) мес."), right: ("Доход", money(c.totalInterest)))
                            Metric(title: "Итоговая сумма", value: money(c.finalAmount), accent: true)
                        } else if let x = c.leasing {
                            MetricPair(left: ("Номинальная ставка", percent(c.nominalRate)), right: ("Эффективная ставка", percent(c.effectiveRate)), accent: true)
                            Text("Месяц 0: аванс \(money(x.advanceAmount, cents: true)), расходы \(money(x.expenses, cents: true))").font(.inter(12)).foregroundStyle(p.muted)
                        }
                    }.padding(.vertical, 20)
                    Section {
                        ForEach(c.rows) { row in scheduleRow(row) }
                    } header: {
                        HStack(spacing: 10) {
                            Text("№").frame(width: 28)
                            Text(c.type == .deposit ? "ПРОЦЕНТ / ВЗНОС" : "ПЛАТЕЖ / ОСТАТОК").frame(maxWidth: .infinity, alignment: .leading)
                            if c.type != .leasing { Text(c.type == .deposit ? "БАЛАНС" : "ПРОЦ. / ОСН.").frame(maxWidth: .infinity, alignment: .leading) }
                        }.font(.inter(9, .bold, relativeTo: .caption2)).foregroundStyle(p.primary).padding(.vertical, 14).padding(.horizontal, 8).background(p.solid)
                    }
                }.padding(.horizontal, 16)
            }.accessibilityIdentifier("schedule")
            ActionButton(title: "Сохранить PDF", icon: "download") { export(true) }.padding(16)
        }.frame(maxWidth: 800).frame(maxWidth: .infinity).background(p.solid.ignoresSafeArea())
            .sheet(item: $exported) { file in if file.save { DocumentExporter(url: file.url) } else { ShareSheet(items: [file.url]) } }
            .alert("Не удалось создать PDF", isPresented: Binding(get: { exportError != nil }, set: { if !$0 { exportError = nil } })) { Button("Закрыть", role: .cancel) {} } message: { Text(exportError ?? "") }
    }
    private func export(_ save: Bool) {
        do { exported = ExportedFile(url: try PDFReport.make(entry, accent: UIColor(p.primary)), save: save) }
        catch { exportError = error.localizedDescription }
    }
    private func scheduleRow(_ row: ScheduleRow) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(String(row.month)).font(.inter(11)).foregroundStyle(p.muted).frame(width: 28)
            VStack(alignment: .leading, spacing: 4) {
                Text(money(c.type == .deposit ? row.interest : row.payment, cents: c.type == .leasing)).font(.inter(13, .semibold)).foregroundStyle(p.text)
                if c.type == .deposit { Text("+ \(money(row.addition))").font(.inter(11)).foregroundStyle(p.muted) }
                else if c.type == .loan { Text(money(row.balance)).font(.inter(11)).foregroundStyle(p.muted) }
                if let x = c.leasing, x.buyout > 0, row.month == x.maturity { Text("В том числе выкуп \(money(x.buyout, cents: true))").font(.inter(11)).foregroundStyle(p.primary) }
            }.frame(maxWidth: .infinity, alignment: .leading)
            if c.type != .leasing {
                VStack(alignment: .leading, spacing: 4) {
                    Text(money(c.type == .deposit ? row.balance : row.interest)).font(.inter(13, .semibold)).foregroundStyle(p.text)
                    if c.type == .loan { Text(money(row.principal)).font(.inter(11)).foregroundStyle(p.muted) }
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
        }.padding(.vertical, 14).padding(.horizontal, 8).background(row.month % 2 == 0 ? p.field : .clear)
    }
}

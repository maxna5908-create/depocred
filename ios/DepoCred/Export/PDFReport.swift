import UIKit
import SwiftUI

struct ExportedFile: Identifiable {
    let id = UUID()
    let url: URL
    let save: Bool
}
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
struct DocumentExporter: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController { UIDocumentPickerViewController(forExporting: [url], asCopy: true) }
    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}
}

enum PDFReport {
    static func make(_ entry: SavedCalculation, skin: String) throws -> URL {
        try make(entry, accent: UIColor(skins.first { $0.id == skin }?.color ?? skins[2].color))
    }
    static func make(_ entry: SavedCalculation, accent: UIColor) throws -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("DepoCredReports", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("DepoCred-\(entry.result.type.rawValue)-\(UUID().uuidString.prefix(8)).pdf")
        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = [kCGPDFContextCreator as String: "DepoCred", kCGPDFContextTitle as String: "DepoCred — \(entry.result.type.title)", kCGPDFContextAuthor as String: "DepoCred"]
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 595, height: 842), format: format)
        let data = renderer.pdfData { context in PDFWriter(context: context, entry: entry, accent: accent).render() }
        try data.write(to: file, options: .atomic)
        return file
    }
}

private final class PDFWriter {
    let context: UIGraphicsPDFRendererContext
    let entry: SavedCalculation
    let accent: UIColor
    var y: CGFloat = 0
    var page = 0
    let margin: CGFloat = 36
    let width: CGFloat = 523
    var c: Calculation { entry.result }
    let ink = UIColor(red: 15/255, green: 23/255, blue: 42/255, alpha: 1)
    let muted = UIColor(red: 100/255, green: 116/255, blue: 139/255, alpha: 1)
    init(context: UIGraphicsPDFRendererContext, entry: SavedCalculation, accent: UIColor) { self.context = context; self.entry = entry; self.accent = accent }
    func font(_ size: CGFloat, bold: Bool = false) -> UIFont {
        let base = UIFont(name: "Inter-Regular", size: size) ?? UIFont.systemFont(ofSize: size)
        guard bold else { return base }
        return UIFont(descriptor: base.fontDescriptor.addingAttributes([.traits: [UIFontDescriptor.TraitKey.weight: UIFont.Weight.bold.rawValue]]), size: size)
    }
    func attributes(_ size: CGFloat, bold: Bool = false, color: UIColor? = nil) -> [NSAttributedString.Key: Any] {
        let paragraph = NSMutableParagraphStyle(); paragraph.lineSpacing = 3
        return [.font: font(size, bold: bold), .foregroundColor: color ?? ink, .paragraphStyle: paragraph]
    }
    @discardableResult func draw(_ text: String, x: CGFloat, top: CGFloat, width: CGFloat, size: CGFloat = 10, bold: Bool = false, color: UIColor? = nil) -> CGFloat {
        let attrs = attributes(size, bold: bold, color: color)
        let measured = (text as NSString).boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attrs, context: nil)
        (text as NSString).draw(in: CGRect(x: x, y: top, width: width, height: ceil(measured.height) + 2), withAttributes: attrs)
        return ceil(measured.height)
    }
    func footer() {
        draw("DepoCred · \(c.type.title) · Страница \(page)", x: margin, top: 806, width: width, size: 8, color: muted)
    }
    func newPage() {
        if page > 0 { footer() }
        context.beginPage(); page += 1
        accent.setFill(); UIBezierPath(rect: CGRect(x: margin, y: 32, width: width, height: 3)).fill()
        draw("DepoCred", x: margin, top: 46, width: width, size: 23, bold: true, color: accent)
        let mode = entry.mode == "reverse" ? "Обратный" : "Прямой"
        draw("\(c.type.title) · \(mode) расчёт", x: margin, top: 78, width: width, size: 12, bold: true)
        let formatter = DateFormatter(); formatter.locale = Locale(identifier: "ru_RU"); formatter.dateFormat = "dd.MM.yyyy HH:mm"
        draw(formatter.string(from: entry.timestamp), x: margin, top: 99, width: width, size: 9, color: muted)
        y = 125
    }
    func ensure(_ height: CGFloat) { if y + height > 782 { newPage() } }
    func paragraph(_ text: String, accentColor: Bool = false) {
        let attrs = attributes(10)
        let height = (text as NSString).boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude), options: .usesLineFragmentOrigin, attributes: attrs, context: nil).height + 8
        ensure(height); y += draw(text, x: margin, top: y, width: width, color: accentColor ? accent : muted) + 12
    }
    func heading(_ title: String) { ensure(32); y += 6; y += draw(title.uppercased(), x: margin, top: y, width: width, size: 11, bold: true, color: accent) + 12 }
    func metric(_ title: String, _ value: String) {
        let a = (title as NSString).boundingRect(with: CGSize(width: 245, height: .greatestFiniteMagnitude), options: .usesLineFragmentOrigin, attributes: attributes(10), context: nil).height
        let b = (value as NSString).boundingRect(with: CGSize(width: 262, height: .greatestFiniteMagnitude), options: .usesLineFragmentOrigin, attributes: attributes(10, bold: true), context: nil).height
        let height = max(a, b) + 12
        ensure(height)
        draw(title, x: margin, top: y, width: 245, color: muted)
        draw(value, x: margin + 261, top: y, width: 262, bold: true)
        y += height
    }
    func render() {
        newPage(); heading("Параметры расчёта")
        if let x = c.loan {
            metric("Сумма кредита / покупки", money(x.amount)); metric("Первоначальный взнос", money(x.downPayment))
            metric("Номинальная ставка / срок", "\(percent(x.rate)) / \(x.term) мес.")
            metric("Тип платежей", x.paymentType == "annuity" ? "Аннуитетный" : "Дифференцированный")
            metric("Фиксированная комиссия", money(x.fixedCommission)); metric("Комиссия от финансирования", percent(x.percentCommission))
            paragraph(loanDescriptions[x.paymentType] ?? "")
            heading("Результат")
            metric("Первый / последний платёж", "\(money(c.firstPayment)) / \(money(c.rows.last?.payment ?? 0))")
            metric("Комиссия / получено на руки", "\(money(c.totalCommission)) / \(money(c.amountReceived))")
            metric("Переплата с комиссией", money(c.totalInterest)); metric("Сумма платежей по графику", money(c.totalRepayment))
            metric("Эффективная годовая ставка", percent(c.effectiveRate))
            paragraph("Эффективная ставка учитывает комиссии и время платежей. Находим r из уравнения Получено = Σ Платёжₘ / (1+r)ᵐ. ЭПС = ((1+r)¹²−1) × 100%.")
        } else if let x = c.deposit {
            metric("Начальный вклад", money(x.initialAmount)); metric("Ставка / срок", "\(percent(x.rate)) / \(x.term) мес.")
            metric("Капитализация", capOptions.first { $0.id == x.capitalization }?.title ?? "")
            metric("Период пополнения", addOptions.first { $0.id == x.additionPeriod }?.title ?? "")
            metric("Сумма пополнения", money(x.additionPeriod == "none" ? 0 : x.monthlyAddition))
            heading("Результат")
            metric("Всего пополнений", money(c.totalAdditions)); metric("Доход", money(c.totalInterest)); metric("Итоговая сумма", money(c.finalAmount))
            metric("Эффективная годовая ставка", percent(c.effectiveRate))
            paragraph("ЭПС = ((1+j/m)ᵐ−1) × 100%, где j — годовая ставка в долях, m — число капитализаций в году. Без капитализации ЭПС равна номинальной ставке. Расчёт начинается с января; год — 365 дней, пополнение в начале выбранного месяца.")
        } else if let x = c.leasing {
            metric("Стоимость предмета с НДС", money(x.amount, cents: true)); metric("Аванс", "\(money(x.advanceAmount, cents: true))\(x.advanceUnit == "percent" ? " (\(percent(x.advance)))" : "")")
            metric("Финансирование", money(x.financed, cents: true)); metric("Расходы в начале", money(x.expenses, cents: true))
            metric("Первый платёж / до последнего", "\(x.firstMonth) / \(x.maturity) мес.")
            metric("Выкупной платёж", money(x.buyout, cents: true)); metric("Момент выкупа", leaseTimings.first { $0.id == x.buyoutTiming }?.title ?? "")
            if x.groups.isEmpty {
                metric("График / регулярных платежей", "\(leaseTypes.first { $0.id == x.paymentType }?.title ?? "") / \(x.term)")
                metric("Ставка финансирования", percent(x.rate))
                if x.paymentType != "annuity" { metric("НДС на финансирование", percent(x.vatRate)) }
                paragraph(leaseDescriptions[x.paymentType] ?? "")
            } else { for (index, group) in x.groups.enumerated() { metric("Группа \(index + 1)", "\(group.count) × \(money(group.payment, cents: true))") } }
            heading("Результат")
            metric("Эквивалент кредита, годовых", percent(c.nominalRate)); metric("Эффективная годовая ставка", percent(c.effectiveRate))
            metric("Переплата с расходами", money(c.totalInterest, cents: true)); metric("Всего с авансом", money(c.totalRepayment, cents: true))
            paragraph("Σ Платёжₘ / (1+r)ᵐ = Финансирование − расходы в начале. Номинальная ставка = r × 12 × 100%; эффективная = ((1+r)¹²−1) × 100%. Аванс — в момент 0. Суммы с НДС, без налоговых вычетов. Выкуп включён в последний месяц графика.")
        }
        paragraph("Расчёт носит информационный характер. Фактические условия зависят от договора.")
        heading(c.type == .deposit ? "График начислений" : "График платежей")
        drawTableHeader()
        for row in c.rows {
            if y + 26 > 782 { newPage(); drawTableHeader() }
            let values: [String]
            if c.type == .deposit { values = [String(row.month), money(row.interest, cents: true), money(row.addition, cents: true), money(row.balance, cents: true)] }
            else if c.type == .leasing { values = [String(row.month), money(row.payment, cents: true), row.month == c.leasing?.maturity && (c.leasing?.buyout ?? 0) > 0 ? "в т.ч. выкуп \(money(c.leasing!.buyout, cents: true))" : ""] }
            else { values = [String(row.month), money(row.payment, cents: true), money(row.principal, cents: true), money(row.interest, cents: true), money(row.balance, cents: true)] }
            if row.month % 2 == 0 { UIColor(white: 0.965, alpha: 1).setFill(); UIBezierPath(rect: CGRect(x: margin,y: y-3,width: width,height: 26)).fill() }
            drawCells(values, header: false); y += 26
        }
        footer()
    }
    func drawTableHeader() {
        ensure(34)
        let headers = c.type == .deposit ? ["Мес.","Проценты","Пополнение","Баланс"] : c.type == .leasing ? ["Мес.","Платёж","Примечание"] : ["Мес.","Платёж","Основной долг","Проценты","Остаток"]
        drawCells(headers, header: true); y += 28
    }
    func drawCells(_ values: [String], header: Bool) {
        let widths: [CGFloat] = values.count == 5 ? [31,123,123,123,123] : values.count == 4 ? [31,164,164,164] : [31,210,282]
        var x = margin
        for (index, value) in values.enumerated() { draw(value, x: x+3, top: y, width: widths[index]-6, size: header ? 8 : 8.5, bold: header, color: header ? accent : ink); x += widths[index] }
    }
}

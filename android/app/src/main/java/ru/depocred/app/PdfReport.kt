package ru.depocred.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native vector/text PDF, with Cyrillic, repeating table headers and A4 pagination. */
object PdfReport {
    fun create(context: Context, entry: SavedCalculation, skin: String): File {
        val directory = File(context.cacheDir, "reports").apply { check(mkdirs() || isDirectory) }
        val now = System.currentTimeMillis()
        directory.listFiles()?.filter { now - it.lastModified() > 7 * 86_400_000L }?.forEach { it.delete() }
        val type = if (entry.result.isLoan) "Loan" else "Deposit"
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(now))
        val file = File(directory, "${type}_${entry.mode}_${date}_${entry.id.take(8)}.pdf")
        val temporary = File(directory, "${file.name}.${System.nanoTime()}.tmp")
        val font = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.create("sans-serif", Typeface.NORMAL)
        val color = when (skin) { "blue" -> Color.rgb(59,130,246); "amber" -> Color.rgb(245,158,11); "rose" -> Color.rgb(244,63,94); "violet" -> Color.rgb(139,92,246); else -> Color.rgb(16,185,129) }
        try {
            Writer(font, color).write(entry.result, temporary)
            check(temporary.renameTo(file)) { "Не удалось завершить PDF" }
            return file
        } catch (e: Exception) { temporary.delete(); throw e }
    }

    private class Writer(private val font: Typeface, private val primary: Int) {
        private val document = PdfDocument()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = 0f
        private var pageNumber = 0
        private val margin = 36f
        private val width = 595f
        private val bottom = 792f
        private val textColor = Color.rgb(15, 23, 42)
        private val muted = Color.rgb(100, 116, 139)
        private val usable get() = width - 2 * margin
        private fun newPage() {
            page?.let(::finishPage)
            pageNumber++
            val next = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            page = next
            canvas = next.canvas
            y = 38f
        }
        private fun finishPage(p: PdfDocument.Page) {
            text("DepoCred — финансовый калькулятор кредитов и вкладов", margin, 812f, 8f, muted)
            text("Стр. $pageNumber", width - margin, 812f, 8f, muted, right = true)
            document.finishPage(p)
        }
        private fun text(s: String, x: Float, y: Float, size: Float = 10f, color: Int = textColor, bold: Boolean = false, right: Boolean = false) {
            paint.color = color; paint.textSize = size
            paint.typeface = if (bold) Typeface.create(font, Typeface.BOLD) else font
            paint.style = Paint.Style.FILL
            paint.textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
            canvas.drawText(s.replace('\u202f', ' ').replace('\u00a0', ' '), x, y, paint)
        }
        private fun lines(value: String, max: Float, size: Float): List<String> {
            paint.textSize = size; paint.typeface = font
            val words = value.replace('\u00a0', ' ').replace('\u202f', ' ').split(' ')
            val result = mutableListOf<String>()
            var line = ""
            words.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && paint.measureText(candidate) > max) { result += line; line = word } else line = candidate
            }
            if (line.isNotEmpty()) result += line
            return result.ifEmpty { listOf("") }
        }
        private fun ensure(height: Float) { if (y + height > bottom) newPage() }
        private fun heading(value: String) { ensure(48f); text(value, margin, y, 14f, primary, true); y += 22f }
        private fun pair(label: String, value: String) {
            val left = lines(label, usable * .51f, 10f)
            val right = lines(value, usable * .45f, 10f)
            val height = maxOf(left.size, right.size) * 14f + 7f
            ensure(height)
            left.forEachIndexed { i, s -> text(s, margin, y + i * 14f, 10f, muted) }
            right.forEachIndexed { i, s -> text(s, width - margin, y + i * 14f, 10f, right = true) }
            y += height
        }
        fun write(c: Calculation, file: File) {
            try {
                newPage()
                text(if (c.isLoan) "Расчет по кредиту" else "Расчет по депозиту", margin, y + 8f, 22f, bold = true)
                y += 28f
                paint.color = primary; paint.strokeWidth = 2f
                canvas.drawLine(margin, y, width - margin, y, paint)
                y += 26f
                heading(if (c.isLoan) "Параметры кредита" else "Параметры депозита")
                if (c.isLoan) {
                    val l = c.loan!!
                    pair("Сумма кредита / покупки", money(l.amount))
                    if (l.downPayment > 0) {
                        pair("Первоначальный взнос", money(l.downPayment))
                        pair("Сумма финансирования", money(l.amount - l.downPayment))
                    }
                    pair("Ставка и срок", "${percent(l.rate)} | ${l.term} мес.")
                    pair("Тип платежа", if (l.paymentType == "annuity") "Аннуитетный" else "Дифференцированный")
                    if (c.totalCommission > 0) pair("Комиссия", "${money(l.fixedCommission)} + ${percent(l.percentCommission)} = ${money(c.totalCommission)}")
                    pair("Сумма платежа", if (l.paymentType == "annuity") money(c.firstPayment) else "от ${money(c.firstPayment)} до ${money(c.rows.last().payment)}")
                } else {
                    val d = c.deposit!!
                    pair("Сумма вклада", money(d.initialAmount))
                    pair("Ставка и срок", "${percent(d.rate)} | ${d.term} мес.")
                    pair("Пополнения", if (d.additionPeriod == "none") "Нет" else "${money(d.monthlyAddition)} — ${ADD_OPTIONS.first { it.first == d.additionPeriod }.second}")
                    pair("Капитализация процентов", CAP_OPTIONS.first { it.first == d.capitalization }.second)
                }
                y += 12f
                heading("Результаты расчета")
                if (c.isLoan) {
                    pair("Переплата с комиссиями", money(c.totalInterest))
                    pair("Сумма возврата по графику", money(c.totalRepayment))
                    pair("Получено на руки", money(c.amountReceived))
                    pair("Номинальная ставка", percent(c.loan!!.rate))
                    pair("Эффективная годовая ставка", percent(c.effectiveRate))
                } else {
                    pair("Итоговая сумма", money(c.finalAmount))
                    pair("Доход", money(c.totalInterest))
                    pair("Пополнения за весь срок", money(c.totalAdditions))
                    pair("Эффективная годовая ставка", percent(c.effectiveRate))
                }
                y += 12f
                ensure(80f)
                heading(if (c.isLoan) "График платежей" else "График начислений")
                val headers = if (c.isLoan) listOf("Месяц", "Платёж", "Долг", "Проценты", "Остаток") else listOf("Месяц", "Проценты", "Взнос", "Баланс")
                val widths = if (c.isLoan) listOf(40f, 121f, 121f, 121f, 120f) else listOf(46f, 159f, 159f, 159f)
                fun tableHeader() {
                    paint.color = primary; paint.style = Paint.Style.FILL
                    canvas.drawRect(margin, y, width - margin, y + 26f, paint)
                    var x = margin
                    headers.forEachIndexed { i, s -> text(s, x + 7f, y + 17f, 8f, Color.WHITE, true); x += widths[i] }
                    y += 26f
                }
                tableHeader()
                c.rows.forEachIndexed { index, row ->
                    if (y + 25f > bottom) { newPage(); tableHeader() }
                    if (index % 2 == 0) { paint.color = Color.rgb(241,245,249); canvas.drawRect(margin, y, width - margin, y + 24f, paint) }
                    val values = if (c.isLoan) listOf(row.month.toString(), money(row.payment), money(row.principal), money(row.interest), money(row.balance)) else listOf(row.month.toString(), money(row.interest), money(row.addition), money(row.balance))
                    var x = margin
                    values.forEachIndexed { i, s ->
                        // Fit very large monetary values without truncation or overlap.
                        var size = 8f
                        paint.typeface = font; paint.textSize = size
                        while (paint.measureText(s) > widths[i] - 12 && size > 5f) { size -= .25f; paint.textSize = size }
                        text(s, if (i == 0) x + 7 else x + widths[i] - 7, y + 16f, size, right = i > 0)
                        x += widths[i]
                    }
                    y += 24f
                }
                y += 18f
                val note = "Информационный расчет. Принята база 365 дней; пополнения — в начале выбранного месяца. Условия, календарь и округление банка могут отличаться."
                lines(note, usable, 8f).forEach { line -> ensure(12f); text(line, margin, y, 8f, muted); y += 12f }
                page?.let(::finishPage); page = null
                FileOutputStream(file).use { output -> document.writeTo(output); output.fd.sync() }
            } finally { document.close() }
        }
    }
}

package ru.depocred.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.util.Locale
import kotlin.math.pow

/** Deposit selectors have their own 24px card, as in DepositCalculator.tsx. */
@Composable internal fun ChoiceDialog(title: String, selected: String, options: List<Pair<String, String>>, onClose: () -> Unit, descriptions: Map<String, String> = emptyMap(), onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(.6f) }
        Surface(Modifier.padding(24.dp).widthIn(max = 384.dp).fillMaxWidth().heightIn(max = height * .9f), shape = RoundedCornerShape(24.dp), color = if (p.dark) p.solid else Color.White, border = BorderStroke(1.dp, if (p.dark) Color.White.copy(alpha = .1f) else Color(0xFFE2E8F0))) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Column(Modifier.width(IntrinsicSize.Max)) {
                            Text(title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = p.text)
                            Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp).background(p.primary))
                        }
                    }
                    IconButton(onClick = onClose) { Glyph(R.drawable.lucide_x, "Закрыть окно", p.muted, 20) }
                }
                Column(Modifier.weight(1f, fill = false).heightIn(max = height * .6f).clipToBounds().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (key, label) ->
                        val active = key == selected
                        Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(key) }, shape = RoundedCornerShape(12.dp), color = if (active) p.primary else if (p.dark) Color.White.copy(alpha = .05f) else p.field) {
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(label, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else p.text)
                                    descriptions[key]?.let { description -> Text(description, fontSize = 12.sp, lineHeight = 18.sp, color = if (active) Color.White.copy(alpha = .9f) else p.muted) }
                                }
                                if (active) Box(Modifier.size(8.dp).background(Color.White, RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shared original PWA dialog: fixed header/footer, bounded scrolling body. */
@Composable internal fun AppDialog(
    title: String, onClose: () -> Unit, tag: String = "app_dialog",
    confirm: String? = null, content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPalette.current
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } * .9f
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).widthIn(max = 512.dp).fillMaxWidth().heightIn(max = height).testTag(tag),
            shape = RoundedCornerShape(16.dp), color = if (p.dark) Color(0xFF0F172A) else Color.White,
            border = BorderStroke(1.dp, if (p.dark) Color.White.copy(alpha = .1f) else Color(0xFFE2E8F0).copy(alpha = .5f))
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = p.text, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Glyph(R.drawable.lucide_x, "Закрыть окно", p.muted, 20) }
                }
                HorizontalDivider(color = if (p.dark) Color.White.copy(alpha = .05f) else Color(0xFFF1F5F9))
                Column(Modifier.weight(1f, fill = false).clipToBounds().verticalScroll(rememberScrollState()).testTag("${tag}_body").padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
                if (confirm != null) {
                    HorizontalDivider(color = p.border.copy(alpha = .05f))
                    Box(Modifier.fillMaxWidth().background(if (p.dark) Color.White.copy(alpha = .05f) else Color(0xFFF8FAFC)).padding(16.dp)) {
                        Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClose), shape = RoundedCornerShape(12.dp), color = p.primary) {
                            Text(confirm, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}


private fun decimal(value: Double, digits: Int) = String.format(Locale.forLanguageTag("ru-RU"), "%.${digits}f", value)

@Composable private fun Explanation(text: String, small: Boolean = false, italic: Boolean = false) {
    Text(text, color = LocalPalette.current.muted, fontSize = if (small) 11.sp else 14.sp,
        lineHeight = if (small) 16.sp else 22.sp, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)
}
@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), color = LocalPalette.current.primary, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        content()
    }
}
@Composable private fun Formula(text: String, note: String? = null, accent: Boolean = false) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(if (p.dark) Color.White.copy(alpha = .05f) else Color(0xFFF8FAFC), RoundedCornerShape(8.dp)).border(1.dp, if (accent) p.primary.copy(alpha = .1f) else p.border.copy(alpha = .06f), RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = if (accent) p.text else p.primary)
        note?.let { Explanation(it, small = true, italic = true) }
    }
}
@Composable private fun Steps(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.primary.copy(alpha = .05f), RoundedCornerShape(12.dp)).border(1.dp, p.primary.copy(alpha = .2f), RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable private fun Step(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), color = LocalPalette.current.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = .5.sp)
        content()
    }
}

@Composable internal fun RateInfoDialog(type: String, calculation: Calculation, onClose: () -> Unit) {
    val c = calculation
    val loan = c.loan
    val nominal = type == "nominal"
    AppDialog(if (nominal) "Номинальная ставка" else "Эффективная ставка", onClose, "rate_info_$type", "ПОНЯТНО") {
        if (loan != null && nominal) {
            Section("Экскурс") {
                Explanation("Номинальная процентная ставка — это годовая процентная ставка, указанная в договоре кредитования. Она служит базой для начисления процентов за каждый расчетный период, но не учитывает капитализацию и дополнительные расходы (комиссии).")
            }
            Section("Как рассчитывается") {
                Explanation("Рассчитывается как произведение ставки за один период начисления на количество таких периодов в году. Например, если ставка в месяц составляет 1%, номинальная годовая ставка будет 12%.")
                Formula("J = j / m", "где J — ставка за период, j — номинальная ставка, m — число периодов в году.")
            }
            Section("Ваш расчет") {
                Steps {
                    Step("Шаг 1: Определение базовой ставки") { Explanation("Вы указали годовую ставку ${percent(loan.rate)}.") }
                    Step("Шаг 2: Расчет ставки за период") {
                        Explanation("Кредит рассчитывается помесячно, поэтому годовая ставка делится на 12 месяцев:")
                        Formula("${percent(loan.rate)} / 12 = ${decimal(loan.rate / 12, 4)}% в месяц", accent = true)
                    }
                    HorizontalDivider(color = LocalPalette.current.primary.copy(alpha = .1f))
                    Explanation("Эта ставка используется как константа для начисления процентов на остаток долга в каждом из ${loan.term} месяцев.", small = true, italic = true)
                }
            }
        } else if (loan != null) {
            Section("Экскурс") {
                Explanation("Эффективная процентная ставка (ЭПС) — это показатель, отражающий реальную годовую стоимость кредита. Она учитывает не только номинальный процент, но и эффект капитализации (сложные проценты), а также все выплаченные комиссии и сборы.")
            }
            Section("Как рассчитывается") {
                Explanation("Эффективная ставка определяется на основе уравнения денежных потоков (аналог IRR). Она учитывает временную стоимость денег: сумма полученного кредита за вычетом комиссий должна быть равна приведенной стоимости всех будущих платежей.")
                Formula("Prec = Σ [Mk / (1+r)^k]", "Эффективная ставка = ((1+r)^12 − 1) × 100%")
            }
            Section("Ваш расчет по шагам") {
                Steps {
                    Step("Шаг 1: Чистая сумма кредита") {
                        Explanation(if (loan.downPayment > 0) "Вычитаем первоначальный взнос и комиссии из суммы кредита:" else "Вычитаем комиссии из суммы кредита (${money(loan.amount)}):")
                        val down = if (loan.downPayment > 0) " − ${money(loan.downPayment)}" else ""
                        Formula("${money(loan.amount)}$down − ${money(c.totalCommission)} = ${money(c.amountReceived)}", accent = true)
                    }
                    Step("Шаг 2: Поиск доходности (IRR)") {
                        Explanation(if (loan.paymentType == "annuity") "• Берутся все ${loan.term} платежей по ${money(c.firstPayment)}." else "• Берутся все ${loan.term} дифференцированных платежей (от ${money(c.firstPayment)} до ${money(c.rows.last().payment)}).", small = true)
                        Explanation("• Методом итераций находится ставка r, при которой сумма всех этих платежей, дисконтированных по ставке r, в точности равна чистой сумме из Шага 1.", small = true)
                    }
                    Step("Шаг 3: Годовая эффективная ставка") {
                        val r = decimal((1 + c.effectiveRate / 100).pow(1.0 / 12) - 1, 6)
                        Explanation("Найденная месячная ставка r = $r переводится в годовую с учетом капитализации:")
                        Formula("((1 + $r)^12 − 1) × 100% = ${percent(c.effectiveRate)}", accent = true)
                    }
                }
            }
        } else {
            val d = c.deposit!!
            Section("Экскурс") { Explanation("Эффективная годовая ставка показывает доходность с учетом частоты капитализации процентов. Срок и пополнения учитываются отдельно в графике начислений.") }
            Section("Как рассчитывается") { Formula("ЭПС = ((1 + j / m)^m − 1) × 100%", "j — годовая ставка в долях единицы, m — число капитализаций в году. Без капитализации ЭПС равна номинальной ставке.") }
            Section("Ваш расчет") {
                Steps {
                    Step("Параметры депозита") { Explanation("Ставка: ${percent(d.rate)}. Капитализация: ${CAP_OPTIONS.first { it.first == d.capitalization }.second.lowercase()}. Срок: ${d.term} мес.") }
                    Step("Результат") {
                        Formula("Эффективная годовая ставка: ${percent(c.effectiveRate)}", accent = true)
                        Explanation("Вклад: ${money(d.initialAmount)}. Пополнения: ${money(c.totalAdditions)}. Доход: ${money(c.totalInterest)}. Итоговая сумма: ${money(c.finalAmount)}.")
                    }
                }
            }
        }
    }
}

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ru.depocred.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable internal fun LeasingForm(state: UiState, model: AppViewModel) {
    val p = LocalPalette.current
    val f = state.form
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val groupCount = f.number("groupCount").toInt().coerceIn(1, 12)
    val order = listOf("amount", "advance") + if (state.reverse) {
        (0 until groupCount).flatMap { listOf("count$it", "payment$it") }
    } else listOf("rate", "term") + if (f.text("paymentType") != "annuity") listOf("vat") else emptyList()
    val fields = order + listOf("buyout", "firstMonth", "expenses")
    val requesters = remember(state.formKey, fields) { fields.associateWith { FocusRequester() } }
    val bring = remember(state.formKey, fields) { fields.associateWith { BringIntoViewRequester() } }
    val navigation = FormNavigation(fields, requesters, bring, scope, keyboard)
    val requested = model.inputFocusRequest
    LaunchedEffect(requested, state.formKey) {
        if (requested != null && requested.formKey == state.formKey) {
            try { navigation.focusAndReveal(requested.field) }
            finally { model.consumeInputFocus(requested.sequence) }
        }
    }
    CompositionLocalProvider(LocalFormNavigation provides navigation) {
        AttachedPanel(listOf("Прямой", "Обратный"), if (state.reverse) 1 else 0, { focus.clearFocus(); model.mode(it == 1) }) {
            if (state.settings.hints) LeaseHint(if (state.reverse)
                "Укажите стоимость, аванс и платежи из предложения. Для ступенчатого графика добавьте группы в порядке оплаты. Выкуп укажите отдельно, не включайте его повторно в группу."
                else "Укажите стоимость, аванс, ставку и срок, чтобы рассчитать платежи. В выборе графика описано, как рассчитывается каждый вариант. Выкуп и отдельные расходы учитываются дополнительно.")
            Numeric("Стоимость предмета с НДС (₽)", "amount", f, model::edit)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                Numeric(if (f.text("advanceUnit") == "percent") "Аванс (%)" else "Аванс (₽)", "advance", f, model::edit, Modifier.weight(1f), if (f.text("advanceUnit") == "percent") "%" else "₽")
                LeaseOption("Единица аванса", f.text("advanceUnit"), listOf("percent" to "%", "rub" to "₽"), Modifier.width(100.dp)) { model.edit("advanceUnit", it) }
            }
            if (f.number("amount") > 0) {
                val input = f.leasing(state.reverse)
                Text("Аванс: ${leaseMoney(input.advanceAmount)} · Финансирование: ${leaseMoney(input.financed)}", color = p.primary, fontSize = 11.sp, lineHeight = 16.sp)
            }
            if (state.reverse) {
                for (index in 0 until groupCount) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ГРУППА ПЛАТЕЖЕЙ ${index + 1}", color = p.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Numeric("Количество", "count$index", f, model::edit, Modifier.weight(1f), "мес.", integer = true)
                            Numeric("Платёж (₽)", "payment$index", f, model::edit, Modifier.weight(1.4f))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (groupCount < 12) Action("Добавить группу", Modifier.weight(1f).testTag("lease_add_group"), primary = false) { focus.clearFocus(); model.edit("groupCount", (groupCount + 1).toString()) }
                    if (groupCount > 1) SmallButton(R.drawable.lucide_trash_2, "Удалить последнюю группу") { focus.clearFocus(); model.edit("groupCount", (groupCount - 1).toString()) }
                }
            } else {
                LeaseOption("График платежей", f.text("paymentType"), LEASE_TYPES) { model.edit("paymentType", it) }
                Numeric("Ставка финансирования (% годовых)", "rate", f, model::edit, suffix = "%")
                Numeric("Количество регулярных платежей", "term", f, model::edit, suffix = "мес.", integer = true)
                if (f.text("paymentType") != "annuity") {
                    Numeric("НДС на финансирование (%)", "vat", f, model::edit, suffix = "%")
                    Text("Стоимость и аванс уже включают НДС. Здесь задаётся НДС только на проценты финансирования; пустое поле — 0%.", color = p.muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            Numeric("Выкупной платёж (₽)", "buyout", f, model::edit)
            if (f.number("buyout") > 0) LeaseOption("Момент выкупа", f.text("buyoutTiming"), LEASE_TIMINGS) { model.edit("buyoutTiming", it) }
            Numeric("Первый платёж через (мес.)", "firstMonth", f, model::edit, suffix = "мес.", integer = true)
            Numeric("Расходы в начале договора (₽)", "expenses", f, model::edit)
            Text("Комиссии и услуги, оплачиваемые отдельно сразу. Расходы, уже включённые в платежи, повторно не добавляйте. Расчёт по месячным периодам, без налоговых вычетов.", color = p.muted, fontSize = 11.sp, lineHeight = 16.sp)
            if (f.leasing(state.reverse).count > 0) Text("До последнего платежа: ${f.leasing(state.reverse).maturity} мес. Аванс — в момент 0.", color = p.primary, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Рассчитать", Modifier.weight(1f).testTag("calculate"), R.drawable.lucide_calculator, busy = state.calculating) { if (model.calculate()) { focus.clearFocus(); keyboard?.hide() } }
                SmallButton(R.drawable.lucide_trash_2, "Сбросить параметры") { focus.clearFocus(); model.reset() }
            }
        }
    }
}

@Composable private fun LeaseHint(value: String) {
    val p = LocalPalette.current
    Surface(color = p.primary.copy(alpha = .06f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, p.primary.copy(alpha = .2f))) {
        Text(value, Modifier.padding(12.dp), color = p.primary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable private fun LeaseOption(label: String, value: String, options: List<Pair<String, String>>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = p.muted)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, p.border), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            Text(options.firstOrNull { it.first == value }?.second ?: "Выбрать", color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
    }
    if (open) ChoiceDialog(label, value, options, { open = false }, descriptions = if (label == "График платежей") LEASE_TYPE_DESCRIPTIONS else emptyMap()) { onSelect(it); open = false }
}

@Composable internal fun LeasingResult(entry: SavedCalculation, onSave: (() -> Unit)?, onSchedule: () -> Unit, onExport: (SavedCalculation, Boolean) -> Unit) {
    val p = LocalPalette.current
    val c = entry.result
    val i = c.leasing!!
    var info by rememberSaveable { mutableStateOf(false) }
    Glass(Modifier.testTag("lease_result")) {
        Text("${if (entry.mode == "reverse") "Обратный" else "Прямой"} расчёт лизинга", color = p.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Эквивалент кредита, годовых", color = p.muted, fontSize = 13.sp)
        Text(percent(c.nominalRate), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = p.text)
        Metric("Эффективная годовая ставка", percent(c.effectiveRate), accent = true) { info = true }
        Action("Как рассчитана ставка", primary = false) { info = true }
        Line()
        if (entry.mode != "reverse") {
            val lastRegular = i.firstMonth + i.count - 1
            val last = c.rows[lastRegular - 1].payment - if (i.buyoutTiming == "last") i.buyout else 0.0
            MetricPair("Первый платёж" to leaseMoney(c.rows[i.firstMonth - 1].payment - if (i.count == 1 && i.buyoutTiming == "last") i.buyout else 0.0), "Последний регулярный" to leaseMoney(last), true)
            Text(LEASE_TYPES.first { it.first == i.paymentType }.second, color = p.muted, fontSize = 11.sp)
            Text(LEASE_TYPE_DESCRIPTIONS.getValue(i.paymentType), color = p.muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        MetricPair("Стоимость предмета" to leaseMoney(i.amount), "Аванс" to leaseMoney(i.advanceAmount))
        MetricPair("Финансирование" to leaseMoney(i.financed), "До последнего платежа" to "${i.maturity} мес.")
        MetricPair("Переплата с расходами" to leaseMoney(c.totalInterest), "Всего с авансом" to leaseMoney(c.totalRepayment), true)
        MetricPair("Выкуп" to leaseMoney(i.buyout), "Расходы в начале" to leaseMoney(i.expenses))
        Text("Ставки рассчитаны по платежам с НДС, без налоговых вычетов. Месяц 0 — получение финансирования и оплата аванса и отдельных расходов.", color = p.muted, fontSize = 11.sp, lineHeight = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Платежи", Modifier.weight(1f), onClick = onSchedule)
            if (onSave != null) SmallButton(R.drawable.lucide_save, "Сохранить расчёт", onSave)
            SmallButton(R.drawable.lucide_share_2, "Отправить PDF") { onExport(entry, false) }
        }
        Action("Сохранить PDF", icon = R.drawable.lucide_download, primary = false) { onExport(entry, true) }
    }
    if (info) AppDialog("Ставка лизинга", { info = false }, confirm = "ЗАКРЫТЬ") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LeaseHint("Это ставка кредита с тем же денежным потоком. Она учитывает размер и время каждого платежа, выкуп и отдельно оплаченные расходы.")
            MetricPair("Финансирование" to leaseMoney(i.financed), "За вычетом расходов" to leaseMoney(c.amountReceived))
            MetricPair("Месячная ставка" to percent(c.nominalRate / 12), "Номинальная годовая" to percent(c.nominalRate), true)
            Metric("Эффективная годовая", percent(c.effectiveRate), accent = true)
            Text("Находим месячную ставку r: сумма Платёжₘ / (1 + r)ᵐ равна финансированию за вычетом расходов в начале. Номинальная годовая = r × 12, эффективная = (1 + r)¹² − 1. Аванс уменьшает финансирование и не включается в платежи повторно.", color = p.muted, fontSize = 13.sp, lineHeight = 20.sp)
            Text("Периоды — месяцы от момента финансирования. Если фактическая дата выдачи отличается от аванса, задайте соответствующую отсрочку первого платежа. Удорожание за срок не является годовой ставкой кредита.", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable internal fun LeasingSchedule(entry: SavedCalculation, onClose: () -> Unit, onExport: (SavedCalculation, Boolean) -> Unit) {
    val p = LocalPalette.current
    val c = entry.result
    val i = c.leasing!!
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let { WindowCompat.getInsetsController(it, view).apply { isAppearanceLightStatusBars = !p.dark; isAppearanceLightNavigationBars = !p.dark } } }
        Column(Modifier.fillMaxSize().background(p.solid).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallButton(R.drawable.lucide_arrow_left, "Назад", onClose)
                Text("Платежи лизинга", Modifier.weight(1f).padding(horizontal = 12.dp), color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                SmallButton(R.drawable.lucide_share_2, "Отправить PDF") { onExport(entry, false) }
            }
            MetricPair("Номинальная ставка" to percent(c.nominalRate), "Эффективная ставка" to percent(c.effectiveRate), true)
            Text("Месяц 0: аванс ${leaseMoney(i.advanceAmount)}, расходы ${leaseMoney(i.expenses)}", color = p.muted, fontSize = 12.sp)
            LazyColumn(Modifier.weight(1f).testTag("schedule"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(c.rows, key = { it.month }) { row ->
                    Row(Modifier.fillMaxWidth().background(p.field, RoundedCornerShape(10.dp)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${row.month} мес.", color = p.muted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                        Column(Modifier.weight(1f)) {
                            Text(leaseMoney(row.payment), color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (i.buyout > 0 && row.month == i.maturity) Text("В том числе выкуп ${leaseMoney(i.buyout)}", color = p.primary, fontSize = 11.sp)
                        }
                    }
                }
            }
            Action("Сохранить PDF", icon = R.drawable.lucide_download) { onExport(entry, true) }
        }
    }
}

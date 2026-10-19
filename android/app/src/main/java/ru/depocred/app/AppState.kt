package ru.depocred.app

import android.app.Application
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Settings(val theme: String = "light", val skin: String = "emerald", val hints: Boolean = true)
data class Form(
    val fields: Map<String, String> = emptyMap(), val target: String? = null,
    val manual: List<String> = emptyList(), val autoError: String? = null
) {
    fun text(key: String) = fields[key].orEmpty()
    fun number(key: String) = text(key).replace(',', '.').toDoubleOrNull() ?: 0.0
    fun loan() = LoanInput(number("amount"), number("rate"), number("term").toInt(), number("fixed"), number("commission"), text("paymentType"), number("down"))
    fun deposit() = DepositInput(number("amount"), number("rate"), number("term").toInt(), number("addition"), text("cap"), text("period"))
    fun with(key: String, value: String) = copy(fields = fields + (key to value))
}
fun emptyForm(loan: Boolean, reverse: Boolean) = Form(if (loan) mapOf("paymentType" to if (reverse) "" else "annuity") else mapOf("cap" to "monthly", "period" to "none"))
data class SavedCalculation(val id: String = UUID.randomUUID().toString(), val timestamp: Long = System.currentTimeMillis(), val mode: String, val result: Calculation)
data class InputFocusRequest(val formKey: String, val field: String, val sequence: Long)
data class UiState(
    val tab: String = "loan", val settings: Settings = Settings(),
    val loanReverse: Boolean = false, val depositReverse: Boolean = false,
    val forms: Map<String, Form> = mapOf("loan" to emptyForm(true, false), "loanReverse" to emptyForm(true, true), "deposit" to emptyForm(false, false), "depositReverse" to emptyForm(false, true)),
    val current: SavedCalculation? = null, val history: List<SavedCalculation> = emptyList(),
    val calculating: Boolean = false, val resultRevision: Int = 0
) {
    val reverse get() = if (tab == "loan") loanReverse else depositReverse
    val formKey get() = tab + if (reverse) "Reverse" else ""
    val form get() = forms[formKey] ?: emptyForm(tab == "loan", reverse)
}

object StateJson {
    fun calculation(c: Calculation): JSONObject = JSONObject().apply {
        c.loan?.let { l -> put("loan", JSONObject().apply {
            put("amount", l.amount); put("rate", l.rate); put("term", l.term); put("fixed", l.fixedCommission)
            put("commission", l.percentCommission); put("paymentType", l.paymentType); put("down", l.downPayment)
        }) }
        c.deposit?.let { d -> put("deposit", JSONObject().apply {
            put("amount", d.initialAmount); put("rate", d.rate); put("term", d.term); put("addition", d.monthlyAddition)
            put("cap", d.capitalization); put("period", d.additionPeriod)
        }) }
        put("interest", c.totalInterest); put("commission", c.totalCommission); put("repayment", c.totalRepayment)
        put("received", c.amountReceived); put("effective", c.effectiveRate); put("final", c.finalAmount); put("additions", c.totalAdditions)
        put("rows", JSONArray().apply { c.rows.forEach { r -> put(JSONArray(listOf(r.month, r.payment, r.principal, r.interest, r.balance, r.addition))) } })
    }
    fun calculation(j: JSONObject): Calculation {
        val l = j.optJSONObject("loan")?.let { LoanInput(it.getDouble("amount"), it.getDouble("rate"), it.getInt("term"), it.getDouble("fixed"), it.getDouble("commission"), it.getString("paymentType"), it.getDouble("down")) }
        val d = j.optJSONObject("deposit")?.let { DepositInput(it.getDouble("amount"), it.getDouble("rate"), it.getInt("term"), it.getDouble("addition"), it.getString("cap"), it.getString("period")) }
        val a = j.getJSONArray("rows")
        require((l != null) xor (d != null))
        require(a.length() in 1..999)
        val rows = (0 until a.length()).map { i -> a.getJSONArray(i).let {
            ScheduleRow(it.getInt(0), it.getDouble(1), it.getDouble(2), it.getDouble(3), it.getDouble(4), it.getDouble(5))
        } }
        return Calculation(l, d, rows, j.getDouble("interest"), j.getDouble("commission"), j.getDouble("repayment"), j.getDouble("received"), j.getDouble("effective"), j.getDouble("final"), j.getDouble("additions"))
    }
    private fun entry(e: SavedCalculation) = JSONObject().apply {
        put("id", e.id); put("timestamp", e.timestamp); put("mode", e.mode); put("result", calculation(e.result))
    }
    private fun entry(j: JSONObject) = SavedCalculation(j.getString("id"), j.getLong("timestamp"), j.getString("mode"), calculation(j.getJSONObject("result")))
    fun encode(state: UiState): String = JSONObject().apply {
        put("schema", 1); put("tab", state.tab); put("loanReverse", state.loanReverse); put("depositReverse", state.depositReverse)
        put("settings", JSONObject().apply { put("theme", state.settings.theme); put("skin", state.settings.skin); put("hints", state.settings.hints) })
        put("forms", JSONObject().apply { state.forms.forEach { (key, form) -> put(key, JSONObject().apply {
            put("fields", JSONObject(form.fields)); put("manual", JSONArray(form.manual)); form.target?.let { put("target", it) }
        }) } })
        put("history", JSONArray().apply { state.history.take(10).forEach { put(entry(it)) } })
        state.current?.let { put("current", entry(it)) }
    }.toString()
    fun decode(raw: String): UiState {
        val j = JSONObject(raw)
        require(j.getInt("schema") == 1) { "Неподдерживаемая версия данных" }
        val s = j.getJSONObject("settings")
        val forms = UiState().forms.toMutableMap()
        val f = j.getJSONObject("forms")
        forms.keys.toList().forEach { key -> f.optJSONObject(key)?.let { x ->
            val fields = x.getJSONObject("fields")
            val manual = x.optJSONArray("manual") ?: JSONArray()
            forms[key] = Form(fields.keys().asSequence().associateWith { fields.getString(it) }, x.optString("target").takeIf { it.isNotBlank() }, (0 until manual.length()).map { manual.getString(it) })
        } }
        val h = j.getJSONArray("history")
        val entries = (0 until minOf(h.length(), 10)).map { entry(h.getJSONObject(it)) }
        return UiState(tab = j.optString("tab").takeIf { it in listOf("loan", "deposit", "history", "info") } ?: "loan",
            settings = Settings(s.optString("theme", "light"), s.optString("skin", "emerald"), s.optBoolean("hints", true)),
            loanReverse = j.optBoolean("loanReverse"), depositReverse = j.optBoolean("depositReverse"), forms = forms,
            current = j.optJSONObject("current")?.let(::entry), history = entries)
    }
}

class LocalStore(directory: File) {
    private val file = AtomicFile(File(directory, "depocred-state.json"))
    var recoveryNotice: String? = null
        private set
    @Synchronized fun read(): UiState {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return UiState()
        return try { StateJson.decode(String(file.readFully(), Charsets.UTF_8)) } catch (_: Exception) {
            // Keep unreadable bytes for recovery instead of deleting user history.
            runCatching { file.baseFile.copyTo(File(file.baseFile.parentFile, "depocred-recovery-${System.currentTimeMillis()}.json")) }
            recoveryNotice = "Не удалось прочитать данные. Резервная копия сохранена на устройстве."
            UiState()
        }
    }
    @Synchronized fun write(raw: String) {
        val stream = file.startWrite()
        try { stream.write(raw.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app.filesDir)
    var state by mutableStateOf(store.read())
        private set
    var notice by mutableStateOf(store.recoveryNotice)
        private set
    var inputFocusRequest by mutableStateOf<InputFocusRequest?>(null)
        private set
    private var focusSequence = 0L
    fun consumeInputFocus(sequence: Long) {
        if (inputFocusRequest?.sequence == sequence) inputFocusRequest = null
    }
    private var saveJob: Job? = null
    private var calculationJob: Job? = null
    fun notify(text: String) { notice = text }
    fun clearNotice() { notice = null }
    private fun persist(immediate: Boolean = false) {
        saveJob?.cancel()
        val raw = StateJson.encode(state)
        saveJob = viewModelScope.launch {
            if (!immediate) delay(250)
            try { withContext(Dispatchers.IO) { store.write(raw) } }
            catch (_: Exception) { notify("Не удалось сохранить данные. Проверьте свободное место.") }
        }
    }
    fun flush() {
        // A short, atomic final write also protects the latest edit on process termination.
        saveJob?.cancel()
        runCatching { store.write(StateJson.encode(state)) }.onFailure { notify("Не удалось сохранить последние изменения") }
    }
    fun tab(value: String) {
        calculationJob?.cancel()
        state = state.copy(tab = value, current = null, calculating = false)
        persist()
    }
    fun mode(reverse: Boolean) {
        calculationJob?.cancel()
        state = if (state.tab == "loan") state.copy(loanReverse = reverse, current = null, calculating = false)
            else state.copy(depositReverse = reverse, current = null, calculating = false)
        persist()
    }
    fun settings(value: Settings) { state = state.copy(settings = value); persist() }
    fun edit(key: String, value: String) {
        calculationJob?.cancel()
        var form = state.form.with(key, value).copy(autoError = null)
        if (state.reverse) {
            if (state.tab == "loan") {
                val keys = listOf("term", "payment", "total")
                if (form.target != null && keys.filter { it != form.target }.any { form.number(it) <= 0 }) {
                    form = form.with(form.target!!, "").copy(target = null)
                }
                if (form.target == null && keys.count { form.number(it) > 0 } == 2) form = form.copy(target = keys.first { form.number(it) <= 0 })
                if (form.target != null && form.number("amount") > form.number("down") && form.text("paymentType").isNotBlank()) {
                    val solved = Finance.loanMissing(form.number("amount") - form.number("down"), form.number("term"), form.number("payment"), form.number("total"), form.text("paymentType"), form.target!!)
                    if (solved.isFinite() && solved > 0) form = form.with(form.target!!, numberText(solved))
                }
            } else {
                val keys = listOf("amount", "rate", "term", "final")
                if (key in keys) {
                    if (value.isBlank() && form.target != null && key != form.target) form = form.with(form.target!!, "").copy(target = null)
                    form = form.copy(manual = if (value.isBlank()) form.manual.filter { it != key } else (form.manual.filter { it != key } + key).takeLast(3))
                }
                val filled = keys.filter { form.text(it).isNotBlank() && (form.number(it) > 0 || it == "rate") }
                val target = if (filled.size == 3) keys.first { it !in filled } else if (filled.size == 4) keys.firstOrNull { it !in form.manual } else null
                form = form.copy(target = target)
                if (target != null) {
                    try {
                        val solved = Finance.depositMissing(form.deposit(), form.number("final"), target)
                        form = form.with(target, numberText(solved))
                    } catch (e: IllegalArgumentException) { form = form.copy(autoError = e.message) }
                }
            }
        }
        state = state.copy(forms = state.forms + (state.formKey to form), current = null, calculating = false)
        persist()
    }
    fun reset() {
        calculationJob?.cancel()
        state = state.copy(forms = state.forms + (state.formKey to emptyForm(state.tab == "loan", state.reverse)), current = null, calculating = false)
        persist()
    }
    fun calculate(): Boolean {
        calculationJob?.cancel()
        val snapshot = state
        snapshot.requiredInput()?.let { (field, message) ->
            state = state.copy(calculating = false)
            // A failed automatic reverse solve must focus an editable source field.
            val editable = if (snapshot.tab == "loan" && snapshot.reverse && field == snapshot.form.target)
                listOf("term", "payment", "total").first { it != field } else field
            inputFocusRequest = InputFocusRequest(snapshot.formKey, editable, ++focusSequence)
            notify(message)
            return false
        }
        state = state.copy(calculating = true)
        calculationJob = viewModelScope.launch {
            try {
                var form = snapshot.form
                require(form.text("amount").isNotBlank() && form.number("amount") > 0) { "Укажите сумму для расчёта" }
                require(form.number("term") in 1.0..999.0) { "Укажите срок от 1 до 999 месяцев" }
                if (snapshot.reverse && snapshot.tab == "loan") {
                    require(form.text("paymentType").isNotBlank()) { "Выберите тип платежа" }
                    require(form.number("payment") > 0 && form.number("total") > 0) { "Заполните любые два значения из трёх" }
                    require(form.number("total") >= form.number("amount") - form.number("down")) { "Сумма возврата не может быть меньше суммы кредита" }
                    val rate = Finance.loanRate(form.number("amount") - form.number("down"), form.number("term").toInt(), form.number("payment"), form.text("paymentType"))
                    form = form.with("rate", numberText(rate))
                } else if (snapshot.reverse && snapshot.tab == "deposit") {
                    form.autoError?.let { throw IllegalArgumentException(it) }
                    require(form.number("final") >= form.number("amount")) { "Итоговая сумма не может быть меньше суммы депозита" }
                    // Respect the parameter chosen by the 3-of-4 solver; never silently replace a manually entered rate.
                    require(form.text("rate").isNotBlank()) { "Заполните три параметра депозита" }
                } else require(form.text("rate").isNotBlank()) { "Укажите процентную ставку (в том числе 0%)" }
                val result = withContext(Dispatchers.Default) {
                    if (snapshot.tab == "loan") Finance.loan(form.loan()) else Finance.deposit(form.deposit())
                }
                state = state.copy(forms = state.forms + (snapshot.formKey to form), current = SavedCalculation(mode = if (snapshot.reverse) "reverse" else "direct", result = result), calculating = false, resultRevision = state.resultRevision + 1)
                persist()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state = state.copy(calculating = false); notify(e.message ?: "Проверьте параметры расчёта") }
        }
        return true
    }
    fun paymentType(type: String) {
        val recalculate = state.current != null
        edit("paymentType", type)
        if (recalculate) calculate()
    }
    fun saveCurrent() {
        val current = state.current ?: return
        state = state.copy(history = (listOf(current.copy(id = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis())) + state.history).take(10))
        persist(true)
        notify("Текущий расчёт сохранён в историю расчётов")
    }
    fun delete(id: String) { state = state.copy(history = state.history.filter { it.id != id }); persist(true) }
}
// Required inputs follow the visible form order; an explicitly entered zero rate is valid.
private fun UiState.requiredInput(): Pair<String, String>? {
    val f = form
    if (f.text("amount").isBlank() || f.number("amount") <= 0) return "amount" to "Укажите сумму для расчёта"
    if (tab == "loan" && reverse) {
        if (f.text("paymentType").isBlank()) return "paymentType" to "Выберите тип платежа"
        if (f.number("term") !in 1.0..999.0) return "term" to "Укажите срок от 1 до 999 месяцев"
        if (f.number("payment") <= 0) return "payment" to "Заполните любые два значения из трёх"
        if (f.number("total") <= 0) return "total" to "Заполните любые два значения из трёх"
    } else {
        if (tab == "deposit" && reverse) {
            f.autoError?.let { return (f.target ?: "final") to it }
            if (f.number("final") <= 0) return "final" to "Заполните три параметра депозита"
        }
        if (f.text("rate").isBlank()) return "rate" to if (reverse) "Заполните три параметра депозита" else "Укажите процентную ставку (в том числе 0%)"
        if (f.number("term") !in 1.0..999.0) return "term" to "Укажите срок от 1 до 999 месяцев"
    }
    return null
}
fun numberText(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

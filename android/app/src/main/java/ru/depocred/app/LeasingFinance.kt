package ru.depocred.app

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

data class LeasePaymentGroup(val count: Int, val payment: Double)
data class LeasingInput(
    val amount: Double, val advance: Double = 0.0, val advanceUnit: String = "percent",
    val rate: Double = 0.0, val term: Int = 0, val paymentType: String = "annuity",
    val groups: List<LeasePaymentGroup> = emptyList(), val buyout: Double = 0.0,
    val buyoutTiming: String = "next", val firstMonth: Int = 1,
    val expenses: Double = 0.0, val vatRate: Double = 0.0
) {
    val advanceAmount get() = leaseRound(if (advanceUnit == "percent") amount * advance / 100 else advance)
    val financed get() = amount - advanceAmount
    val count get() = if (groups.isEmpty()) term else groups.sumOf { it.count }
    val maturity get() = firstMonth + count - 1 + if (buyout > 0 && buyoutTiming == "next") 1 else 0
}
fun leaseRound(value: Double): Double = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
fun leaseMoney(value: Double): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("ru-RU")).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format(value) + " ₽"

val LEASE_TYPES = listOf("annuity" to "Аннуитетный", "uniform" to "Равномерный", "differentiated" to "Убывающий")
val LEASE_TYPE_DESCRIPTIONS = mapOf(
    "annuity" to "Равные регулярные платежи, рассчитанные по ставке и сроку с учётом выкупа. Доля процентов уменьшается, а доля погашения долга растёт.",
    "uniform" to "Сначала рассчитываются проценты при погашении долга равными частями. Затем сумма погашаемого долга и процентов делится поровну между платежами. Поэтому платёж отличается от аннуитетного.",
    "differentiated" to LOAN_PAYMENT_DESCRIPTIONS.getValue("differentiated")
)
val LEASE_TIMINGS = listOf("next" to "Через месяц после платежей", "last" to "Вместе с последним платежом")

/** Borrower's gross cash flows. No tax deductions or assumed supplier funding dates. */
object LeasingFinance {
    private fun validate(i: LeasingInput, reverse: Boolean) = with(i) {
        require(listOf(amount, advance, rate, buyout, expenses, vatRate).all { it.isFinite() && it in 0.0..1e12 }) { "Введите конечные неотрицательные суммы и ставки" }
        require(amount > 0 && advanceUnit in listOf("rub", "percent")) { "Укажите стоимость предмета лизинга" }
        require(advanceUnit != "percent" || advance < 100) { "Аванс должен быть меньше 100%" }
        require(financed > 0) { "Аванс должен быть меньше стоимости предмета" }
        require(expenses < financed) { "Расходы в начале должны быть меньше суммы финансирования" }
        require(firstMonth in 1..999 && count in 1..999 && maturity in 1..999) { "График должен укладываться в 999 месяцев" }
        require(buyoutTiming in listOf("next", "last")) { "Выберите момент выкупного платежа" }
        if (reverse) {
            require(groups.size in 1..12 && groups.all { it.count in 1..999 && it.payment.isFinite() && it.payment in 0.0..1e12 }) { "Проверьте количество и суммы групп платежей" }
            require(groups.any { it.payment > 0 } || buyout > 0) { "В графике должен быть хотя бы один положительный платёж" }
        } else {
            require(groups.isEmpty() && paymentType in LEASE_TYPES.map { it.first }) { "Выберите тип графика" }
            require(buyout < financed) { "Выкуп должен быть меньше суммы финансирования" }
            require(vatRate <= 100) { "НДС должен быть от 0 до 100%" }
        }
    }

    fun direct(input: LeasingInput): Calculation {
        validate(input, false)
        val i = input.rate / 1200
        val count = input.term
        val p = input.financed
        val payments = DoubleArray(input.maturity)
        if (input.paymentType == "annuity") {
            val discount = ln1p(i)
            val factor = (0 until count).sumOf { exp(-(input.firstMonth + it) * discount) }
            val payment = (p - input.buyout * exp(-input.maturity * discount)) / factor
            require(payment.isFinite() && payment > 0) { "Параметры превышают допустимый диапазон" }
            repeat(count) { payments[input.firstMonth - 1 + it] = leaseRound(payment) }
            // Correct only the final regular payment for accumulated kopeck rounding.
            val pv = payments.mapIndexed { index, v -> v * exp(-(index + 1) * discount) }.sum() + input.buyout * exp(-input.maturity * discount)
            val last = input.firstMonth + count - 2
            payments[last] = leaseRound(payments[last] + (p - pv) * exp((last + 1) * discount))
        } else {
            // Equal principal, interest on the declining gross funding balance.
            // Apply a single VAT multiplier only to financing interest.
            val principal = (p - input.buyout) / count
            val grossRate = i * (1 + input.vatRate / 100)
            val unrounded = (0 until count).map { index ->
                principal + (p - principal * index) * grossRate * if (index == 0) input.firstMonth else 1
            }.toMutableList()
            if (input.buyout > 0 && input.buyoutTiming == "next") unrounded[unrounded.lastIndex] += input.buyout * grossRate
            val total = leaseRound(unrounded.sum())
            repeat(count) { index ->
                payments[input.firstMonth - 1 + index] = leaseRound(if (input.paymentType == "uniform") total / count else unrounded[index])
            }
            val last = input.firstMonth + count - 2
            payments[last] = leaseRound(payments[last] + total - payments.sum())
        }
        payments[payments.lastIndex] = leaseRound(payments.last() + input.buyout)
        require(payments.all { it.isFinite() && it >= 0 }) { "Проверьте ставку, срок и выкупной платёж" }
        return result(input, payments.toList())
    }

    fun reverse(input: LeasingInput): Calculation {
        validate(input, true)
        val payments = MutableList(input.maturity) { 0.0 }
        var month = input.firstMonth - 1
        input.groups.forEach { group -> repeat(group.count) { payments[month++] = leaseRound(group.payment) } }
        payments[payments.lastIndex] = leaseRound(payments.last() + input.buyout)
        return result(input, payments)
    }

    private fun monthlyRate(received: Double, payments: List<Double>): Double {
        // Positive receipt followed by nonnegative outflows has a unique root in (-1,+∞).
        // Solve log(1+r) to avoid overflow at negative rates and long deferrals.
        fun pv(logRate: Double) = payments.mapIndexed { month, value ->
            if (value == 0.0) 0.0 else value * exp(-(month + 1) * logRate)
        }.sum()
        if (abs(payments.sum() - received) <= 1e-8) return 0.0
        var low = -36.0
        var high = 1.0
        while (pv(high) > received && high < 100) high *= 2
        repeat(180) {
            val mid = (low + high) / 2
            if (pv(mid) > received) low = mid else high = mid
        }
        return expm1((low + high) / 2)
    }

    private fun result(input: LeasingInput, payments: List<Double>): Calculation {
        val received = input.financed - input.expenses
        val monthly = monthlyRate(received, payments)
        val nominal = monthly * 1200
        val effective = expm1(12 * ln1p(monthly)) * 100
        require(nominal.isFinite() && effective.isFinite()) { "Ставка выходит за допустимый диапазон" }
        // Remaining discounted outflows provide a stable credit-equivalent schedule,
        // including negative amortization in stepped/deferred payment plans.
        val balances = DoubleArray(payments.size + 1)
        for (index in payments.lastIndex downTo 0) balances[index] = (payments[index] + balances[index + 1]) / (1 + monthly)
        val rows = payments.mapIndexed { index, value ->
            val interest = balances[index] * monthly
            ScheduleRow(index + 1, value, value - interest, interest, balances[index + 1])
        }
        val total = leaseRound(input.advanceAmount + input.expenses + payments.sum())
        return Calculation(leasing = input, rows = rows, totalInterest = leaseRound(total - input.amount),
            totalRepayment = total, totalCommission = input.expenses, amountReceived = received,
            nominalRate = nominal, effectiveRate = effective)
    }
}

fun emptyLeasingForm() = Form(mapOf("advanceUnit" to "percent", "paymentType" to "annuity", "buyoutTiming" to "next", "firstMonth" to "1", "groupCount" to "1"))
fun Form.leasing(reverse: Boolean) = LeasingInput(
    amount = number("amount"), advance = number("advance"), advanceUnit = text("advanceUnit"),
    rate = number("rate"), term = number("term").toInt(), paymentType = text("paymentType"),
    groups = if (reverse) (0 until number("groupCount").toInt().coerceIn(1,12)).map { LeasePaymentGroup(number("count$it").toInt(), number("payment$it")) } else emptyList(),
    buyout = number("buyout"), buyoutTiming = text("buyoutTiming"), firstMonth = number("firstMonth").toInt(), expenses = number("expenses"),
    vatRate = if (reverse || text("paymentType") == "annuity") 0.0 else number("vat"))

fun Form.leasingRequired(reverse: Boolean): Pair<String, String>? {
    if (number("amount") <= 0) return "amount" to "Укажите стоимость предмета лизинга"
    if ((text("advanceUnit") == "percent" && number("advance") >= 100) || leasing(reverse).advanceAmount >= number("amount")) return "advance" to "Аванс должен быть меньше стоимости предмета"
    if (reverse) {
        for (index in 0 until number("groupCount").toInt().coerceIn(1,12)) {
            if (number("count$index") !in 1.0..999.0) return "count$index" to "Укажите количество платежей в группе ${index + 1}"
            if (text("payment$index").isBlank()) return "payment$index" to "Укажите платёж группы ${index + 1} (можно 0)"
        }
    } else {
        if (text("rate").isBlank()) return "rate" to "Укажите ставку финансирования (можно 0%)"
        if (number("term") !in 1.0..999.0) return "term" to "Укажите количество регулярных платежей"
        if (number("buyout") >= leasing(false).financed) return "buyout" to "Выкуп должен быть меньше суммы финансирования"
    }
    if (number("firstMonth") !in 1.0..999.0) return "firstMonth" to "Укажите месяц первого платежа от 1 до 999"
    if (leasing(reverse).maturity > 999) return (if (reverse) "count0" else "term") to "График должен укладываться в 999 месяцев"
    if (number("expenses") >= leasing(reverse).financed) return "expenses" to "Расходы в начале должны быть меньше суммы финансирования"
    if (!reverse && text("paymentType") != "annuity" && number("vat") > 100) return "vat" to "НДС должен быть от 0 до 100%"
    return null
}

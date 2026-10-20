package ru.depocred.app

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

// Model/calendar conventions match src/lib/calculators.ts: January start,
// fixed 365-day year, additions at the beginning of the selected month.
data class LoanInput(
    val amount: Double, val rate: Double, val term: Int,
    val fixedCommission: Double = 0.0, val percentCommission: Double = 0.0,
    val paymentType: String = "annuity", val downPayment: Double = 0.0
)
data class DepositInput(
    val initialAmount: Double, val rate: Double, val term: Int,
    val monthlyAddition: Double = 0.0, val capitalization: String = "monthly",
    val additionPeriod: String = "none"
)
data class ScheduleRow(
    val month: Int, val payment: Double = 0.0, val principal: Double = 0.0,
    val interest: Double, val balance: Double, val addition: Double = 0.0
)
data class Calculation(
    val loan: LoanInput? = null, val deposit: DepositInput? = null,
    val rows: List<ScheduleRow>, val totalInterest: Double,
    val totalCommission: Double = 0.0, val totalRepayment: Double = 0.0,
    val amountReceived: Double = 0.0, val effectiveRate: Double = 0.0,
    val finalAmount: Double = 0.0, val totalAdditions: Double = 0.0,
    val leasing: LeasingInput? = null, val nominalRate: Double = 0.0
) {
    val isLoan get() = loan != null
    val isLeasing get() = leasing != null
    val type get() = if (isLoan) "loan" else if (isLeasing) "leasing" else "deposit"
    val firstPayment get() = rows.firstOrNull()?.payment ?: 0.0
}

object Finance {
    private fun valid(vararg values: Double) = require(values.all { it.isFinite() && it >= 0.0 }) {
        "Параметры должны быть конечными неотрицательными числами"
    }
    fun loan(input: LoanInput): Calculation {
        with(input) {
            valid(amount, rate, fixedCommission, percentCommission, downPayment)
            require(term in 1..999) { "Срок должен быть от 1 до 999 месяцев" }
            val p = amount - downPayment
            require(p > 0) { "Первоначальный взнос должен быть меньше суммы кредита" }
            require(paymentType in listOf("annuity", "differentiated")) { "Выберите тип платежа" }
            val commission = fixedCommission + p * percentCommission / 100
            require(commission < p) { "Общая комиссия должна быть меньше выдаваемой суммы кредита" }
            val i = rate / 1200
            // Numerically stable equivalent of the PWA annuity formula.
            val monthly = if (i == 0.0) p / term else p * i / -expm1(-term * ln1p(i))
            var balance = p
            val rows = (1..term).map { month ->
                val interest = balance * i
                // Closed-form outstanding balance prevents compounding floating-point
                // drift (the PWA can leave the whole principal unpaid at long/high rates).
                val next = if (paymentType == "annuity" && i > 0.0)
                    monthly / i * -expm1(-(term - month) * ln1p(i))
                    else p * (term - month).toDouble() / term
                val principal = max(0.0, balance - next)
                balance = max(0.0, next)
                ScheduleRow(month, principal + interest, principal, interest, balance)
            }
            val interest = rows.sumOf { it.interest }
            val result = Calculation(
                loan = input, rows = rows, totalInterest = interest + commission,
                totalCommission = commission, totalRepayment = rows.sumOf { it.payment },
                amountReceived = p - commission, effectiveRate = effectiveRate(p - commission, rows)
            )
            require(result.totalInterest.isFinite() && result.effectiveRate.isFinite()) { "Параметры превышают допустимый диапазон" }
            return result
        }
    }
    // Bisection avoids Newton divergence at zero interest, long terms and high commissions.
    private fun effectiveRate(received: Double, rows: List<ScheduleRow>): Double {
        fun pv(rate: Double): Double = rows.sumOf { it.payment * exp(-it.month * ln1p(rate)) }
        if (abs(pv(0.0) - received) < 1e-7) return 0.0
        var low = 0.0
        var high = 1.0
        while (pv(high) > received && high < 1e6) high *= 2
        repeat(100) {
            val mid = (low + high) / 2
            if (pv(mid) > received) low = mid else high = mid
        }
        return expm1(12 * ln1p((low + high) / 2)) * 100
    }

    private fun period(key: String) = when (key) {
        "monthly" -> 1; "quarterly" -> 3; "semi-yearly" -> 6; "yearly" -> 12; else -> 0
    }
    fun deposit(input: DepositInput): Calculation {
        with(input) {
            valid(initialAmount, rate, monthlyAddition)
            require(term in 1..999) { "Срок должен быть от 1 до 999 месяцев" }
            require(capitalization in CAP_OPTIONS.map { it.first } && additionPeriod in ADD_OPTIONS.map { it.first })
            var balance = initialAmount
            var additions = 0.0
            var accumulated = 0.0
            var currentDay = 0
            val days = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            val addStep = period(additionPeriod)
            val capMonths = period(capitalization)
            val capDays = when (capitalization) { "daily" -> 1; "weekly" -> 7; else -> 0 }
            val rows = mutableListOf<ScheduleRow>()
            for (month in 1..term) {
                val addition = if (addStep > 0 && month % addStep == 0) monthlyAddition else 0.0
                balance += addition
                additions += addition
                var capitalized = 0.0
                if (capDays > 0) {
                    repeat(days[(month - 1) % 12]) {
                        currentDay++
                        accumulated += balance * rate / 100 / 365
                        if (currentDay % capDays == 0) {
                            balance += accumulated
                            capitalized += accumulated
                            accumulated = 0.0
                        }
                    }
                } else {
                    accumulated += balance * rate / 100 * days[(month - 1) % 12] / 365
                    if (capMonths > 0 && month % capMonths == 0) {
                        balance += accumulated
                        capitalized = accumulated
                        accumulated = 0.0
                    }
                }
                rows += ScheduleRow(month, interest = capitalized, balance = balance, addition = addition)
            }
            if (accumulated > 0) {
                balance += accumulated
                rows[rows.lastIndex] = rows.last().copy(interest = rows.last().interest + accumulated, balance = balance)
            }
            require(balance.isFinite()) { "Параметры превышают допустимый диапазон" }
            val n = when (capitalization) {
                "daily" -> 365; "weekly" -> 52; "monthly" -> 12; "quarterly" -> 4; "semi-yearly" -> 2; "yearly" -> 1; else -> 0
            }
            val apy = if (n == 0) rate else expm1(n * ln1p(rate / 100 / n)) * 100
            return Calculation(deposit = input, rows = rows, totalInterest = balance - initialAmount - additions,
                finalAmount = balance, totalAdditions = additions, effectiveRate = apy)
        }
    }

    fun loanRate(principal: Double, term: Int, payment: Double, type: String): Double {
        require(principal > 0 && term in 1..999 && payment > 0)
        require(payment * term >= principal - 0.01) { "Платёж и срок недостаточны для возврата суммы кредита" }
        if (type == "differentiated") return max(0.0, (payment - principal / term) / principal * 1200)
        if (abs(payment * term - principal) < 0.01) return 0.0
        fun amount(rate: Double) = if (rate == 0.0) principal / term else principal * rate / -expm1(-term * ln1p(rate))
        var low = 0.0
        var high = 1.0
        while (amount(high) < payment) high *= 2
        repeat(100) {
            val mid = (low + high) / 2
            if (amount(mid) < payment) low = mid else high = mid
        }
        return (low + high) / 2 * 1200
    }

    fun loanMissing(principal: Double, term: Double, payment: Double, total: Double, type: String, field: String): Double {
        val diff = type == "differentiated"
        return when (field) {
            "total" -> if (diff) floor(.5 + principal * (term - 1) / (2 * term) + payment * (term + 1) / 2) else term * payment
            "payment" -> if (diff) floor(.5 + (2 * total - principal * (term - 1) / term) / (term + 1)) else floor(.5 + total / term)
            "term" -> if (diff) {
                val b = principal + payment - 2 * total
                floor(.5 + (-b + sqrt(b * b + 4 * payment * principal)) / (2 * payment))
            } else floor(.5 + total / payment)
            else -> 0.0
        }
    }

    fun depositMissing(input: DepositInput, target: Double, field: String): Double {
        if (field == "final") return round(deposit(input).finalAmount)
        require(target > 0) { "Укажите итоговую сумму" }
        if (field == "term") {
            var low = 1; var high = 999
            require(deposit(input.copy(term = high)).finalAmount >= target) { "Для цели требуется срок более 999 месяцев" }
            require(deposit(input.copy(term = 1)).finalAmount <= target + 1) { "Цель достигается раньше одного месяца" }
            while (low < high) {
                val mid = (low + high) / 2
                if (deposit(input.copy(term = mid)).finalAmount < target) low = mid + 1 else high = mid
            }
            return low.toDouble()
        }
        var low = 0.0
        var high = if (field == "rate") 1000.0 else target
        fun value(v: Double) = deposit(if (field == "rate") input.copy(rate = v) else input.copy(initialAmount = v)).finalAmount
        require(value(low) <= target + 0.5) { "Итоговая сумма меньше внесённых средств" }
        require(value(high) >= target - 0.5) { "Итоговая сумма недостижима при указанных параметрах" }
        repeat(70) {
            val mid = (low + high) / 2
            if (value(mid) < target) low = mid else high = mid
        }
        val result = (low + high) / 2
        return if (field == "rate") round(result * 100) / 100 else round(result)
    }
}

val CAP_OPTIONS = listOf("none" to "В конце срока", "daily" to "Ежедневно", "weekly" to "Еженедельно", "monthly" to "Ежемесячно", "quarterly" to "Ежеквартально", "semi-yearly" to "Раз в полгода", "yearly" to "Ежегодно")
val ADD_OPTIONS = listOf("none" to "Нет", "monthly" to "Раз в месяц", "quarterly" to "Раз в квартал", "semi-yearly" to "Раз в полгода", "yearly" to "Раз в год")
fun money(value: Double): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).apply { maximumFractionDigits = 0 }.format(value) + " ₽"
fun percent(value: Double): String = String.format(Locale.forLanguageTag("ru-RU"), "%.2f%%", value)

package ru.depocred.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class FinanceTest {
    private val fixtures by lazy { JSONObject(javaClass.getResource("/pwa-parity.json")!!.readText()) }
    private fun close(expected: Double, actual: Double) = assertEquals(expected, actual, maxOf(0.01, abs(expected) * 1e-9))
    @Test fun allLoanSchedulesMatchUnchangedPwa() {
        val cases = fixtures.getJSONArray("loans")
        for (index in 0 until cases.length()) {
            val item = cases.getJSONObject(index); val i = item.getJSONObject("input"); val e = item.getJSONObject("result")
            val actual = Finance.loan(LoanInput(i.getDouble("amount"), i.getDouble("rate"), i.getInt("term"), i.getDouble("fixedCommission"), i.getDouble("percentCommission"), i.getString("paymentType"), i.getDouble("downPayment")))
            val sourceRows = e.getJSONArray("schedule")
            val sourceUnpaid = sourceRows.getJSONObject(sourceRows.length() - 1).getDouble("remainingBalance")
            close(e.getDouble("totalRepayment"), actual.totalRepayment)
            if (sourceUnpaid < .01) close(e.getDouble("totalInterest"), actual.totalInterest)
            else {
                assertEquals("annuity", i.getString("paymentType"))
                assertEquals(999, i.getInt("term"))
                // Independent accounting invariants for the known unstable PWA edge case.
                close(actual.totalRepayment - i.getDouble("amount") + i.getDouble("downPayment") + actual.totalCommission, actual.totalInterest)
            }
            close(e.getDouble("totalCommission"), actual.totalCommission); close(e.getDouble("amountReceived"), actual.amountReceived)
            val rows = e.getJSONArray("schedule")
            assertEquals(rows.length(), actual.rows.size)
            actual.rows.forEachIndexed { rowIndex, row -> val expected = rows.getJSONObject(rowIndex)
                close(expected.getDouble("payment"), row.payment)
                if (sourceUnpaid < .01) {
                    close(expected.getDouble("principal"), row.principal)
                    close(expected.getDouble("interest"), row.interest); close(expected.getDouble("remainingBalance"), row.balance)
                }
            }
            // PWA's Newton solver can diverge; the native bounded solver must always remain finite.
            assertTrue(actual.effectiveRate.isFinite() && actual.effectiveRate >= 0)
            close(i.getDouble("amount") - i.getDouble("downPayment"), actual.rows.sumOf { it.principal })
            close(0.0, actual.rows.last().balance)
            val monthlyRate = kotlin.math.expm1(kotlin.math.ln1p(actual.effectiveRate / 100) / 12)
            val recoveredPrincipal = actual.rows.sumOf { it.payment * kotlin.math.exp(-it.month * kotlin.math.ln1p(monthlyRate)) }
            assertEquals(actual.amountReceived, recoveredPrincipal, .01)
        }
    }
    @Test fun allDepositModesAndSchedulesMatchUnchangedPwa() {
        val cases = fixtures.getJSONArray("deposits")
        for (index in 0 until cases.length()) {
            val item = cases.getJSONObject(index); val i = item.getJSONObject("input"); val e = item.getJSONObject("result")
            val actual = Finance.deposit(DepositInput(i.getDouble("initialAmount"), i.getDouble("rate"), i.getInt("term"), i.getDouble("monthlyAddition"), i.getString("capitalization"), i.getString("additionPeriod")))
            close(e.getDouble("finalAmount"), actual.finalAmount); close(e.getDouble("totalInterest"), actual.totalInterest); close(e.getDouble("totalAdditions"), actual.totalAdditions)
            val rows = e.getJSONArray("schedule")
            assertEquals(rows.length(), actual.rows.size)
            actual.rows.forEachIndexed { rowIndex, row -> val expected = rows.getJSONObject(rowIndex)
                close(expected.getDouble("interest"), row.interest); close(expected.getDouble("addition"), row.addition); close(expected.getDouble("balance"), row.balance)
            }
        }
    }
    @Test fun reverseLoanRecoversAnnuityAndDifferentiatedRate() {
        for (type in listOf("annuity", "differentiated")) for (rate in listOf(0.0, .01, 12.5, 100.0, 999.0)) for (term in listOf(1, 12, 360, 999)) {
            val result = Finance.loan(LoanInput(1_000_000.0, rate, term, paymentType = type))
            assertEquals(rate, Finance.loanRate(1_000_000.0, term, result.firstPayment, type), .000001)
        }
    }
    @Test fun reverseLoanTwoOfThreeAndDownPayment() {
        for (type in listOf("annuity", "differentiated")) {
            val input = LoanInput(1_000_000.0, 12.0, 36, paymentType = type, downPayment = 200_000.0)
            val c = Finance.loan(input)
            val p = 800_000.0
            close(if (type == "differentiated") kotlin.math.round(c.totalRepayment) else c.totalRepayment, Finance.loanMissing(p, 36.0, c.firstPayment, 0.0, type, "total"))
            assertEquals(kotlin.math.round(c.firstPayment), Finance.loanMissing(p, 36.0, 0.0, c.totalRepayment, type, "payment"), 1.0)
            close(36.0, Finance.loanMissing(p, 0.0, c.firstPayment, c.totalRepayment, type, "term"))
        }
    }
    @Test fun reverseDepositRecoversAllFourParameters() {
        for (cap in CAP_OPTIONS.map { it.first }) for (period in ADD_OPTIONS.map { it.first }) {
            val input = DepositInput(250000.0, 13.5, 37, 7500.0, cap, period)
            val target = Finance.deposit(input).finalAmount
            close(kotlin.math.round(target), Finance.depositMissing(input, 0.0, "final"))
            assertEquals(13.5, Finance.depositMissing(input.copy(rate = 0.0), target, "rate"), .01)
            assertEquals(250000.0, Finance.depositMissing(input.copy(initialAmount = 0.0), target, "amount"), 1.0)
            assertEquals(37.0, Finance.depositMissing(input.copy(term = 1), target, "term"), 0.0)
        }
    }
    @Test fun zeroRateHasNoInterestAndNoNan() {
        val l = Finance.loan(LoanInput(120000.0, 0.0, 12))
        close(10000.0, l.firstPayment); close(0.0, l.totalInterest); close(0.0, l.effectiveRate)
        val d = Finance.deposit(DepositInput(100000.0, 0.0, 12, 1000.0, additionPeriod = "monthly"))
        close(112000.0, d.finalAmount); close(0.0, d.totalInterest)
    }
    @Test fun impossibleInputsAreRejected() {
        listOf<() -> Any>(
            { Finance.loan(LoanInput(0.0, 12.0, 12)) },
            { Finance.loan(LoanInput(1000.0, 12.0, 0)) },
            { Finance.loan(LoanInput(1000.0, 12.0, 12, downPayment = 1000.0)) },
            { Finance.loan(LoanInput(1000.0, 12.0, 12, fixedCommission = 600.0, percentCommission = 50.0)) },
            { Finance.deposit(DepositInput(Double.NaN, 12.0, 12)) },
            { Finance.loanRate(100000.0, 12, 1.0, "annuity") },
            { Finance.depositMissing(DepositInput(100000.0, 0.0, 12, 10000.0, additionPeriod = "monthly"), 100001.0, "rate") }
        ).forEach { calculation -> assertThrows(IllegalArgumentException::class.java) { calculation() } }
    }
    @Test fun jsonRoundTripPreservesHistoryAndPreferencesButNotDrafts() {
        val c = Finance.loan(LoanInput(500000.0, 15.0, 36, 500.0, 1.0))
        val entry = SavedCalculation(mode = "direct", result = c)
        val state = UiState(settings = Settings("dark", "rose", false), current = entry, history = listOf(entry), forms = UiState().forms + ("loan" to Form(mapOf("amount" to "12345.67", "rate" to "0", "paymentType" to "annuity"))))
        val raw = StateJson.encode(state)
        assertEquals(UiState(settings = state.settings, history = state.history), StateJson.decode(raw))
        val json = JSONObject(raw)
        listOf("forms", "current", "tab", "loanReverse", "depositReverse", "leasingReverse").forEach { assertFalse(json.has(it)) }
        val encoded = StateJson.calculation(Finance.deposit(DepositInput(50000.0, 8.0, 12)))
        assertEquals(encoded.toString(), StateJson.calculation(StateJson.calculation(encoded)).toString())
    }
    @Test fun upgradeDiscardsEvenInvalidLegacyDraftsWithoutLosingSavedHistory() {
        val entry = SavedCalculation(mode = "direct", result = Finance.deposit(DepositInput(100000.0, 10.0, 12)))
        val old = JSONObject(StateJson.encode(UiState(history = listOf(entry), settings = Settings("dark", "violet")))).apply {
            put("schema", 1); put("tab", "leasing"); put("leasingReverse", true)
            put("forms", JSONObject().put("deposit", JSONObject().put("fields", JSONObject().put("amount", "999999"))))
            put("current", JSONObject().put("incomplete", true))
        }
        assertEquals(UiState(settings = Settings("dark", "violet"), history = listOf(entry)), StateJson.decode(old.toString()))
    }
}

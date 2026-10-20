package ru.depocred.app

import org.junit.Assert.*
import org.junit.Test

class ReverseFormsTest {
    private fun edit(form: Form, key: String, value: String) = ReverseForms.deposit(form.with(key, value), key)
    private fun deposit() = edit(edit(edit(emptyForm(false, true), "amount", "100000"), "rate", "12"), "term", "12")

    @Test fun clearingDepositInputsPreservesOtherValuesAndAllowsTyping() {
        val original = deposit()
        assertEquals("final", original.target)
        assertEquals(112683.0, original.number("final"), 0.0)
        for (key in listOf("amount", "final", "rate", "term")) {
            val cleared = edit(original, key, "")
            assertEquals("", cleared.text(key))
            for (other in listOf("amount", "final", "rate", "term").filter { it != key }) assertEquals(original.text(other), cleared.text(other))
            assertTrue(cleared.manual.contains(key))
            assertNull(cleared.autoError)
        }
    }
    @Test fun depositOwnershipMovesToTheFieldOutsideLastThreeManualEdits() {
        val original = deposit()
        val changedFinal = edit(original, "final", "120000")
        assertEquals(listOf("rate", "term", "final"), changedFinal.manual)
        assertEquals("amount", changedFinal.target)
        assertEquals("12", changedFinal.text("rate")); assertEquals("12", changedFinal.text("term"))
        assertEquals("120000", changedFinal.text("final")); assertTrue(changedFinal.number("amount") > 106000)
        val changedAmount = edit(changedFinal, "amount", "100000")
        assertEquals("rate", changedAmount.target)
        assertEquals(18.37, changedAmount.number("rate"), .1)
    }
    @Test fun allDepositUnknownsAndOrderChangesRemainEditable() {
        val values = linkedMapOf("amount" to "100000", "final" to "112682.5", "rate" to "12", "term" to "12")
        for (unknown in values.keys) {
            var form = emptyForm(false, true)
            values.filterKeys { it != unknown }.forEach { (key, value) -> form = edit(form, key, value) }
            assertEquals(unknown, form.target)
            assertEquals(values.getValue(unknown).toDouble(), form.number(unknown), if (unknown == "final") 1.0 else .02)
            form = edit(form, unknown, values.getValue(unknown))
            assertEquals(values.getValue(unknown), form.text(unknown))
            assertNotEquals(unknown, form.target)
        }
    }
    @Test fun additionsAndCapitalizationRecalculateOnlyTheOwnedDepositField() {
        val original = deposit()
        val addition = edit(edit(original, "period", "monthly"), "addition", "1000")
        assertEquals(original.manual, addition.manual)
        assertEquals("final", addition.target)
        assertTrue(addition.number("final") > original.number("final") + 12000)
        val daily = edit(addition, "cap", "daily")
        assertEquals(addition.manual, daily.manual)
        assertTrue(daily.number("final") > addition.number("final"))
        val paused = edit(daily, "period", "none")
        assertEquals("1000", paused.text("addition"))
        assertTrue(paused.number("final") < daily.number("final"))
    }
    @Test fun partialDepositTargetsDoNotEraseSourcesOrShowIntermediateErrors() {
        var form = edit(edit(edit(emptyForm(false, true), "amount", "100000"), "final", "120000"), "term", "12")
        assertEquals("rate", form.target)
        form = edit(form, "final", "1")
        assertEquals("1", form.text("final")); assertEquals("100000", form.text("amount"))
        assertNull(form.autoError)
    }
    @Test fun reverseLoanKeepsTheSameUnknownUntilASourceIsCleared() {
        var form = Form(mapOf("amount" to "100000", "down" to "20000", "paymentType" to "annuity", "term" to "12", "payment" to "8000"))
        form = ReverseForms.loan(form)
        assertEquals("total", form.target); assertEquals("96000", form.text("total"))
        form = ReverseForms.loan(form.with("paymentType", "differentiated"))
        assertEquals("total", form.target); assertEquals("88667", form.text("total"))
        form = ReverseForms.loan(form.with("down", "10000"))
        assertEquals("93250", form.text("total"))
        form = ReverseForms.loan(form.with("payment", ""))
        assertNull(form.target); assertEquals("", form.text("total")); assertEquals("12", form.text("term"))
        // JavaScript Math.round rounds halves up, unlike Kotlin's bankers' round.
        assertEquals(13.0, Finance.loanMissing(100000.0, 0.0, 10000.0, 125000.0, "annuity", "term"), 0.0)
    }
}

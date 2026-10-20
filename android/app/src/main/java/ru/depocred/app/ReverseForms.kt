package ru.depocred.app

import kotlin.math.abs

/** Field ownership follows the PWA: loan 2-of-3, deposit the last 3 manual edits. */
internal object ReverseForms {
    fun loan(form: Form): Form {
        val keys = listOf("term", "payment", "total")
        var next = form
        if (next.target != null && keys.filter { it != next.target }.any { next.number(it) <= 0 }) {
            next = next.with(next.target!!, "").copy(target = null)
        }
        if (next.target == null && keys.count { next.number(it) > 0 } == 2) {
            next = next.copy(target = keys.first { next.number(it) <= 0 })
        }
        val target = next.target ?: return next
        if (next.number("amount") <= next.number("down") || next.text("paymentType").isBlank()) return next
        val value = Finance.loanMissing(next.number("amount") - next.number("down"), next.number("term"), next.number("payment"), next.number("total"), next.text("paymentType"), target)
        return next.with(target, if (value.isFinite() && value > 0) numberText(value) else "")
    }

    fun deposit(form: Form, editedKey: String): Form {
        val keys = listOf("amount", "rate", "term", "final")
        // Clearing a field is a manual edit too. Never fill it back while the user types,
        // and never erase a different field as a side effect of clearing this one.
        val manual = if (editedKey in keys) (form.manual.filter { it != editedKey } + editedKey).takeLast(3) else form.manual
        var next = form.copy(manual = manual, target = null, autoError = null)
        val filled = keys.filter { next.number(it) > 0 }
        val target = when (filled.size) {
            3 -> keys.first { it !in filled }
            4 -> keys.firstOrNull { it !in manual }
            else -> null
        }?.takeUnless { it in manual } ?: return next
        next = next.copy(target = target)
        // Partial edits may temporarily describe an impossible deposit. Preserve them;
        // final validation belongs to Calculate, rather than replacing another input.
        if ((target == "rate" || target == "term") && next.number("final") <= next.number("amount")) return next
        val value = runCatching { Finance.depositMissing(next.deposit(), next.number("final"), target) }.getOrNull() ?: return next
        val tolerance = if (target == "rate") .005 else .5
        return if (value > 0 && abs(value - next.number(target)) > tolerance) next.with(target, numberText(value)) else next
    }
}

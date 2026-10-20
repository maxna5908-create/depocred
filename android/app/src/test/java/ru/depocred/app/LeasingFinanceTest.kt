package ru.depocred.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class LeasingFinanceTest {
    private fun chat(expenses: Double = 0.0) = LeasingInput(10_300_000.0, 10.0,
        groups = listOf(LeasePaymentGroup(34,359_210.80)), buyout = 20_000.0, expenses = expenses)
    private fun contract(expenses: Double = 0.0) = LeasingInput(8_500_000.0, 1_700_000.0, "rub",
        groups = listOf(LeasePaymentGroup(18,456_912.86)), buyout = 1_999.99, expenses = expenses)
    private fun reconciles(c: Calculation) {
        val i = c.leasing!!
        val monthly = c.nominalRate / 1200
        // Independent forward discounted-cash-flow and accounting checks.
        val pv = c.rows.sumOf { it.payment / (1 + monthly).pow(it.month) }
        assertEquals(c.amountReceived, pv, max(.0001, i.amount * 1e-10))
        assertEquals(i.financed - i.expenses, c.rows.sumOf { it.principal }, .01)
        assertEquals(0.0, c.rows.last().balance, 0.0)
        assertEquals(i.advanceAmount + i.expenses + c.rows.sumOf { it.payment }, c.totalRepayment, .01)
        assertEquals(c.totalRepayment - i.amount, c.totalInterest, .01)
        assertEquals(((1 + monthly).pow(12) - 1) * 100, c.effectiveRate, 1e-8)
    }
    @Test fun correspondenceExampleMatchesIndependentScipyRootAndCustomerRoundedRates() {
        val c = LeasingFinance.reverse(chat())
        assertEquals(20.060638595837016, c.nominalRate, 1e-9)
        assertEquals(22.011858372624758, c.effectiveRate, 1e-9)
        assertEquals(13_263_167.20, c.totalRepayment, .001)
        assertEquals(2_963_167.20, c.totalInterest, .001)
        assertEquals(35,c.rows.size)
        assertEquals(20_000.0,c.rows.last().payment,0.0)
        reconciles(c)
    }
    @Test fun separateUpfrontServiceIncreasesRateWithoutDoubleCounting() {
        val c = LeasingFinance.reverse(chat(51_000.0))
        assertEquals(20.48352330511784,c.nominalRate,1e-9)
        assertEquals(22.520312690431197,c.effectiveRate,1e-9)
        assertEquals(13_314_167.20,c.totalRepayment,.001)
        reconciles(c)
    }
    @Test fun suppliedContractHasEighteenRegularPaymentsAndSeparateNineteenthMonthBuyout() {
        val c = LeasingFinance.reverse(contract())
        assertEquals(9_926_431.47,c.totalRepayment,.001)
        assertEquals(25.029762346759114,c.nominalRate,1e-9)
        assertEquals(28.110500722781584,c.effectiveRate,1e-9)
        assertEquals(19,c.rows.size)
        val fee = LeasingFinance.reverse(contract(22_000.0))
        assertEquals(25.473749409272685,fee.nominalRate,1e-9)
        assertEquals(28.66878518273075,fee.effectiveRate,1e-9)
        assertEquals(9_948_431.47,fee.totalRepayment,.001)
        reconciles(c); reconciles(fee)
    }
    @Test fun annuityDirectAndReverseRecoverRateWithBuyoutAdvanceAndDeferral() {
        for (delay in listOf(1,3)) for (timing in listOf("next","last")) {
            val input = LeasingInput(1_000_000.0,20.0,rate=18.0,term=36,buyout=100_000.0,buyoutTiming=timing,firstMonth=delay)
            val c = LeasingFinance.direct(input)
            assertEquals(18.0,c.nominalRate,.00001)
            val regular = c.rows.filter { it.month >= delay && it.month < delay + input.term }.map {
                LeasePaymentGroup(1,leaseRound(it.payment - if (timing=="last" && it.month==input.maturity) input.buyout else 0.0))
            }
            // Group identical regular payments, including last-payment kopeck adjustment.
            val groups = mutableListOf<LeasePaymentGroup>()
            regular.forEach { g -> if (groups.lastOrNull()?.payment == g.payment) groups[groups.lastIndex] = groups.last().copy(count=groups.last().count+1) else groups += g }
            val reverse = LeasingFinance.reverse(input.copy(groups=groups))
            assertEquals(c.rows,reverse.rows)
            assertEquals(c.nominalRate,reverse.nominalRate,1e-10)
            reconciles(c)
        }
    }
    @Test fun zeroRateAndAllAdvanceUnitsUseTheSamePrincipal() {
        val a = LeasingFinance.direct(LeasingInput(120_000.0,20.0,rate=0.0,term=12))
        val b = LeasingFinance.direct(LeasingInput(120_000.0,24_000.0,"rub",rate=0.0,term=12))
        assertEquals(a.rows,b.rows)
        assertEquals(8_000.0,a.firstPayment,0.0)
        assertEquals(0.0,a.nominalRate,0.0)
        assertEquals(120_000.0,a.totalRepayment,0.0)
        reconciles(a)
    }
    @Test fun correctedXlsSchedulesUseOneVatRateAndDoNotInflatePrincipal() {
        // Source: 7.3m price, 40% advance, 28 months, 14% finance rate.
        // Source interest column sums to 740950. Mixed 18% and 1.2 caused extra principal.
        for (type in listOf("uniform","differentiated")) {
            val c = LeasingFinance.direct(LeasingInput(7_300_000.0,40.0,rate=14.0,term=28,paymentType=type,vatRate=18.0))
            assertEquals(7_300_000.0 + 740_950.0 * 1.18,c.totalRepayment,.001)
            if (type=="uniform") assertEquals(187_654.32,c.firstPayment,.001)
            else {
                assertEquals(216_726.57,c.firstPayment,.001)
                assertTrue(c.rows.zipWithNext().all { (a,b) -> a.payment > b.payment })
            }
            reconciles(c)
        }
    }
    @Test fun steppedPaymentsTimingAndZeroPaymentHolidaysArePreserved() {
        val groups = listOf(LeasePaymentGroup(2,0.0),LeasePaymentGroup(6,40_000.0),LeasePaymentGroup(12,60_000.0))
        val c = LeasingFinance.reverse(LeasingInput(1_000_000.0,20.0,groups=groups,buyout=30_000.0,firstMonth=2))
        assertEquals(listOf(0.0,0.0,0.0,40_000.0),c.rows.take(4).map { it.payment })
        assertEquals(22,c.rows.size)
        assertEquals(30_000.0,c.rows.last().payment,0.0)
        val sameMonth = LeasingFinance.reverse(c.leasing!!.copy(buyoutTiming="last"))
        assertEquals(21,sameMonth.rows.size)
        assertEquals(90_000.0,sameMonth.rows.last().payment,0.0)
        assertTrue(sameMonth.nominalRate > c.nominalRate)
        reconciles(c); reconciles(sameMonth)
    }
    @Test fun negativeRateForDiscountedOfferIsNotClampedToZero() {
        val c = LeasingFinance.reverse(LeasingInput(100_000.0,groups=listOf(LeasePaymentGroup(12,8_000.0))))
        assertTrue(c.nominalRate < 0 && c.effectiveRate < 0)
        assertEquals(-4_000.0,c.totalInterest,0.0)
        reconciles(c)
    }
    @Test fun invalidInputsFailAndLongValidSchedulesRemainFinite() {
        for (input in listOf(chat().copy(advance=100.0),chat().copy(expenses=9_270_000.0),chat().copy(firstMonth=999),chat().copy(groups=listOf(LeasePaymentGroup(0,100.0))),chat().copy(groups=listOf(LeasePaymentGroup(1,Double.NaN))),chat().copy(groups=listOf(LeasePaymentGroup(1,0.0)),buyout=0.0))) {
            assertThrows(IllegalArgumentException::class.java) { LeasingFinance.reverse(input) }
        }
        val c = LeasingFinance.reverse(LeasingInput(100_000.0,groups=listOf(LeasePaymentGroup(999,1_000.0))))
        assertTrue(c.nominalRate.isFinite() && c.effectiveRate.isFinite())
        reconciles(c)
    }
    @Test fun historyRoundTripKeepsEveryLeaseInputAndIsBackwardCompatible() {
        val lease = SavedCalculation(mode="reverse",result=LeasingFinance.reverse(contract(22_000.0)))
        val loan = SavedCalculation(mode="direct",result=Finance.loan(LoanInput(100_000.0,12.0,12)))
        val deposit = SavedCalculation(mode="direct",result=Finance.deposit(DepositInput(100_000.0,12.0,12)))
        val state = UiState(tab="leasing",leasingReverse=true,current=lease,history=listOf(lease,loan,deposit),
            forms=UiState().forms + ("leasingReverse" to emptyLeasingForm().with("count0","18").with("payment0","456912.86")))
        assertEquals(state,StateJson.decode(StateJson.encode(state)))
        assertEquals(listOf("leasing","loan","deposit"),state.history.map { it.type })
        val legacy = JSONObject(StateJson.encode(UiState(history=listOf(loan,deposit))))
        legacy.remove("leasingReverse"); legacy.getJSONObject("forms").remove("leasing"); legacy.getJSONObject("forms").remove("leasingReverse")
        val restored = StateJson.decode(legacy.toString())
        assertEquals(listOf(loan,deposit),restored.history)
        assertEquals(emptyLeasingForm(),restored.forms["leasing"])
        assertFalse(restored.leasingReverse)
    }
    @Test fun requiredLeaseFieldsFollowVisibleOrderAndAcceptExplicitZeroPayments() {
        var f = emptyLeasingForm()
        assertEquals("amount",f.leasingRequired(true)!!.first)
        f=f.with("amount","100000")
        assertEquals("count0",f.leasingRequired(true)!!.first)
        f=f.with("count0","12")
        assertEquals("payment0",f.leasingRequired(true)!!.first)
        f=f.with("payment0","0").with("buyout","100000")
        assertNull(f.leasingRequired(true))
        assertEquals("rate",emptyLeasingForm().with("amount","100000").leasingRequired(false)!!.first)
    }
}

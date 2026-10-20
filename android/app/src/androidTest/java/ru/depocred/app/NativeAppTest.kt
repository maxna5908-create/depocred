package ru.depocred.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.text.input.ImeAction
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeAppTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    @Before fun setup() {
        rule.waitUntil(120000) { rule.onAllNodesWithText("DepoCred", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        rule.runOnIdle {
            // Each scenario starts with all four forms empty, including period selectors.
            for (tab in listOf("loan", "deposit")) {
                model.tab(tab)
                for (reverse in listOf(false, true)) { model.mode(reverse); model.reset() }
                model.mode(false)
            }
            model.tab("loan"); model.settings(Settings())
            model.state.history.forEach { model.delete(it.id) }; model.clearNotice()
        }
    }
    private fun field(key: String, value: String) {
        rule.onNodeWithTag("field_$key").performScrollTo().performTextReplacement(value)
    }
    private fun calculate() {
        rule.onNodeWithTag("calculate").performScrollTo().performClick()
        rule.waitUntil(120000) { model.state.current != null }
    }
    private fun keyboard(visible: Boolean) {
        rule.waitUntil(15000) { ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == visible }
    }
    private fun nativeKeyboardAction(action: Int) {
        // Check the EditorInfo served to the real API-35 keyboard, in addition to Compose semantics.
        var lastDump = ""
        try {
            rule.waitUntil(10000) {
                val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys input_method")
                lastDump = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                // Android 15 exposes the editor currently received by the IME here.
                val flags = Regex("mInputEditorInfo:[\\s\\S]*?imeOptions=0x([0-9a-fA-F]+)").find(lastDump)?.groupValues?.get(1)?.toLongOrNull(16)
                flags != null && (flags.toInt() and android.view.inputmethod.EditorInfo.IME_MASK_ACTION) == action
            }
        } finally {
            val directory = File(rule.activity.filesDir, "qa").apply { mkdirs() }
            File(directory, "ime-${System.nanoTime()}.txt").writeText(lastDump)
        }
    }

    private fun next(from: String, to: String) {
        rule.onNodeWithTag("field_$from").assert(hasImeAction(ImeAction.Next)).performImeAction()
        rule.onNodeWithTag("field_$to").assertIsFocused().assertIsDisplayed()
        keyboard(true)
        rule.waitUntil(5000) {
            val node = rule.onNodeWithTag("field_$to").fetchSemanticsNode()
            node.boundsInRoot.height >= node.size.height - 1
        }
    }
    @Test fun keyboardFollowsEditableFieldsAndKeepsLastFieldVisible() {
        rule.onNodeWithText("Прямой").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(200)
        screenshot("17-panel-pressed")
        rule.onNodeWithText("Прямой").performTouchInput { up() }
        rule.onNodeWithTag("field_amount").performScrollTo().performClick().performTextReplacement("1000000")
        keyboard(true)
        next("amount", "rate"); rule.onNodeWithTag("field_rate").performTextReplacement("12")
        next("rate", "term"); rule.onNodeWithTag("field_term").performTextReplacement("12")
        next("term", "fixed"); next("fixed", "commission")
        nativeKeyboardAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
        rule.onNodeWithTag("field_commission").assert(hasImeAction(ImeAction.Done)).performImeAction()
        keyboard(false)
        rule.onNodeWithText("Обратный").performScrollTo().performClick()
        rule.onNodeWithTag("field_term").assertDoesNotExist()
        rule.onNodeWithTag("field_amount").performScrollTo().performClick().performTextReplacement("1000000")
        rule.onNodeWithText("Аннуитетный — фиксированный").performScrollTo().performClick()
        rule.onNodeWithTag("field_amount").performScrollTo().performClick()
        next("amount", "term"); rule.onNodeWithTag("field_term").performTextReplacement("12")
        next("term", "payment"); rule.onNodeWithTag("field_payment").performTextReplacement("90000")
        rule.runOnIdle { assertEquals("total", model.state.form.target) }
        rule.onNodeWithTag("field_total").assertIsNotEnabled()
        next("payment", "down")
        nativeKeyboardAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
        screenshot("18-long-form-next")
        rule.onNodeWithTag("field_down").assert(hasImeAction(ImeAction.Done)).performImeAction()
        keyboard(false)
        rule.onNodeWithTag("tab_deposit").performClick()
        rule.onNodeWithTag("field_term").performScrollTo().performClick().assert(hasImeAction(ImeAction.Done)).performImeAction()
        keyboard(false)
        rule.onNodeWithContentDescription("Пополнение").performScrollTo().performClick()
        rule.onNodeWithText("Раз в месяц").performClick()
        rule.onNodeWithTag("field_term").performScrollTo().performClick()
        next("term", "addition")
        nativeKeyboardAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
        screenshot("19-deposit-next")
        rule.onNodeWithTag("field_addition").assert(hasImeAction(ImeAction.Done)).performImeAction()
        keyboard(false)
    }
    @Test fun missingInputsShowNoticeAndFocusTheRequiredField() {
        rule.onNodeWithTag("calculate").performScrollTo().performClick()
        rule.onNodeWithText("Укажите сумму для расчёта").assertExists()
        rule.onNodeWithTag("field_amount").assertIsFocused().assertIsDisplayed()
        keyboard(true)
        screenshot("20-required-amount")
        rule.onNodeWithTag("field_amount").performTextReplacement("1000000")
        rule.onNodeWithTag("calculate").performScrollTo().performClick()
        rule.onNodeWithTag("field_rate").assertIsFocused().assertIsDisplayed()
        keyboard(true)
        rule.onNodeWithTag("field_rate").performTextReplacement("0")
        rule.onNodeWithTag("calculate").performScrollTo().performClick()
        rule.onNodeWithTag("field_term").assertIsFocused().assertIsDisplayed()
        keyboard(true)
        rule.onNodeWithTag("field_term").performTextReplacement("12")
        calculate(); keyboard(false)
        rule.runOnIdle { assertEquals(0.0, model.state.current!!.result.totalInterest, .001) }
        rule.onNodeWithTag("tab_deposit").performClick()
        rule.onNodeWithText("Обратный").performScrollTo().performClick()
        rule.onNodeWithTag("calculate").performScrollTo().performClick()
        rule.onNodeWithTag("field_amount").assertIsFocused().assertIsDisplayed()
        keyboard(true)
    }
    private fun screenshot(name: String) {
        rule.runOnIdle { model.clearNotice() }
        rule.waitForIdle()
        // InputConnection/IME and Surface transactions finish outside Compose's test clock.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(400)
        val directory = File(rule.activity.filesDir, "qa").apply { mkdirs() }
        // Capture the physical display so Dialog windows, their dimming and fixed
        // headers are included; a Compose root can belong to the underlying Activity.
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun loanCalculationHistoryScheduleAndRecreation() {
        screenshot("01-loan-light")
        field("amount", "1000000"); field("rate", "12"); field("term", "12"); field("fixed", "2500"); field("commission", "1.5"); calculate()
        rule.runOnIdle { assertEquals(88848.788678, model.state.current!!.result.firstPayment, .01) }
        screenshot("02-loan-results")
        rule.onNodeWithContentDescription("Информация: Номинальная ставка").performScrollTo().performClick()
        rule.onNodeWithText("ЭКСКУРС").assertIsDisplayed()
        rule.onNodeWithText("J = j / m").assertExists()
        screenshot("07-nominal-info")
        rule.onNodeWithText("12,00% / 12 = 1,0000% в месяц").performScrollTo().assertIsDisplayed()
        screenshot("08-nominal-steps")
        rule.onNodeWithText("ПОНЯТНО").performClick()
        rule.onNodeWithContentDescription("Информация: Эффективная ставка").performScrollTo().performClick()
        rule.onNodeWithText("Prec = Σ [Mk / (1+r)^k]").assertExists()
        screenshot("09-effective-info")
        rule.onNodeWithText("${money(1000000.0)} − ${money(17500.0)} = ${money(982500.0)}").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("× 100% = ", substring = true).performScrollTo().assertIsDisplayed()
        screenshot("10-effective-steps")
        rule.onNodeWithContentDescription("Закрыть окно").performClick()
        rule.onNodeWithContentDescription("Сохранить расчёт").performScrollTo().performClick()
        rule.onNodeWithTag("tab_history").performClick()
        rule.runOnIdle { assertEquals(1, model.state.history.size) }
        rule.onNodeWithText("График платежей").performScrollTo().performClick()
        rule.onNodeWithTag("schedule").assertExists()
        screenshot("11-loan-schedule")
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, model.state.history.size) }
        screenshot("03-history")
    }
    @Test fun depositOptionsAndReverseParameters() {
        rule.onNodeWithTag("tab_deposit").performClick()
        screenshot("12-deposit-form")
        field("amount", "250000"); field("rate", "13.5"); field("term", "37")
        rule.onNodeWithContentDescription("Пополнение").performScrollTo().performClick()
        screenshot("15-deposit-options")
        rule.onNodeWithText("Раз в месяц").performClick()
        rule.onNodeWithContentDescription("Капитализация процентов").performScrollTo().performClick()
        screenshot("16-capitalization-options")
        rule.onNode(hasText("Ежемесячно") and hasAnyAncestor(isDialog())).performClick()
        field("addition", "7500")
        calculate()
        rule.runOnIdle { assertTrue(model.state.current!!.result.finalAmount > 600000) }
        screenshot("04-deposit-results")
        rule.onNodeWithText("График начислений").performScrollTo().performClick()
        rule.onNode(hasText("ПОПОЛНЕНИЯ") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.onNodeWithText("% ЗА ПЕРИОД").assertExists()
        screenshot("13-deposit-schedule")
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Обратный").performScrollTo().performClick()
        field("amount", "100000"); field("rate", "12"); field("term", "12")
        rule.runOnIdle { assertTrue(model.state.form.number("final") > 112000); assertEquals("final", model.state.form.target) }
        field("rate", "")
        rule.runOnIdle { assertTrue(model.state.form.number("final") > 112000); assertEquals("", model.state.form.text("rate")) }
        rule.runOnIdle { model.reset() }
        field("amount", "100000"); field("final", "120000"); field("term", "12")
        rule.runOnIdle { assertEquals("rate", model.state.form.target); assertTrue(model.state.form.number("rate") > 18) }
        calculate()
        rule.runOnIdle { assertEquals(120000.0, model.state.current!!.result.finalAmount, 10.0) }
    }
    @Test fun resultTabsKeepTheCardMountedAndTheScrollPositionStable() {
        field("amount", "150000"); field("rate", "16.8"); field("term", "28")
        calculate()
        rule.onNodeWithText("Аннуитетный").performScrollTo().assertIsDisplayed()
        rule.waitForIdle()
        fun scrollValue() = rule.onNodeWithTag("content").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        val revision = model.state.resultRevision
        for (type in listOf("differentiated", "annuity", "differentiated", "annuity")) {
            val before = scrollValue()
            rule.onNodeWithText(if (type == "annuity") "Аннуитетный" else "Дифференцированный").performClick()
            rule.runOnIdle { assertNotNull(model.state.current) }
            rule.waitUntil(10000) { !model.state.calculating && model.state.current?.result?.loan?.paymentType == type }
            rule.onNodeWithTag("calculation_result").assertExists()
            assertEquals(revision, model.state.resultRevision)
            assertEquals(before, scrollValue(), 1f)
        }
        screenshot("21-floating-menu-results")
        // Content viewport continues behind the menu and its outside margins.
        val content = rule.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        val menu = rule.onNodeWithTag("bottom_menu").fetchSemanticsNode().boundsInRoot
        assertTrue(content.bottom >= menu.bottom)
        rule.runOnIdle { model.settings(model.state.settings.copy(theme = "dark")) }
        screenshot("22-floating-menu-dark")
    }
    @Test fun reverseDepositManualEditsAndLoanPrerequisitesMatchThePwa() {
        rule.onNodeWithText("Обратный").performScrollTo().performClick()
        rule.onNodeWithText("Аннуитетный — фиксированный").performScrollTo().performClick()
        rule.onNodeWithTag("field_term").assertDoesNotExist()
        rule.onNodeWithTag("field_amount").assertIsFocused()
        field("amount", "100000")
        rule.onNodeWithText("Аннуитетный — фиксированный").performScrollTo().performClick()
        field("term", "12"); field("payment", "10000")
        rule.onNodeWithTag("field_total").assertIsNotEnabled()
        rule.runOnIdle { assertEquals("120000", model.state.form.text("total")) }
        field("payment", "")
        rule.onNodeWithTag("field_total").assertIsEnabled()
        rule.runOnIdle { assertEquals("", model.state.form.text("total")) }
        rule.onNodeWithTag("field_down").performScrollTo().performClick().performImeAction()
        keyboard(false)
        rule.onNodeWithTag("tab_deposit").performClick()
        rule.onNodeWithText("Обратный").performScrollTo().performClick()
        field("amount", "100000"); field("rate", "12"); field("term", "12")
        field("final", "")
        rule.runOnIdle {
            assertEquals("", model.state.form.text("final"))
            assertEquals("100000", model.state.form.text("amount")); assertEquals("12", model.state.form.text("rate"))
        }
        rule.runOnIdle { model.reset() }
        field("amount", "100000"); field("final", "120000"); field("rate", "12")
        rule.runOnIdle { assertEquals("term", model.state.form.target) }
        field("rate", "")
        rule.runOnIdle { assertEquals("", model.state.form.text("rate")); assertTrue(model.state.form.number("term") > 0) }
        calculate()
        rule.runOnIdle {
            val result = model.state.current!!.result
            assertEquals(120000.0, result.finalAmount, 20.0)
            assertEquals(result.deposit!!.rate, model.state.form.number("rate"), 0.0)
        }
        screenshot("23-reverse-deposit-result")
    }
    @Test fun nativePdfHasAllPagesAndReadableShareUri() {
        val result = Finance.loan(LoanInput(1000000.0, 12.0, 999, 500.0, 1.0))
        val entry = SavedCalculation(mode = "direct", result = result)
        val file = PdfReport.create(rule.activity, entry, "blue")
        assertTrue(file.length() > 10000)
        assertEquals("%PDF", file.inputStream().use { String(it.readNBytes(4)) })
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount > 30)
            renderer.openPage(0).use { page ->
                assertEquals(595, page.width); assertEquals(842, page.height)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(rule.activity.filesDir, "qa").mkdirs()
                File(rule.activity.filesDir, "qa/pdf-first-page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(rule.activity, "${rule.activity.packageName}.fileprovider", file)
        assertEquals("content", uri.scheme)
        rule.activity.contentResolver.openInputStream(uri).use { assertTrue(it!!.read() >= 0) }
        val intent = pdfShareIntent(uri)
        assertEquals("application/pdf", intent.type)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(intent.hasExtra(android.content.Intent.EXTRA_TEXT))
        // One realistic PDF of each calculator is retained for external extraction/render QA.
        PdfReport.create(rule.activity, SavedCalculation(mode = "direct", result = Finance.loan(LoanInput(750000.0, 12.0, 60, 2500.0, 1.5))), "emerald").copyTo(File(rule.activity.filesDir, "qa/loan-sample.pdf"), overwrite = true)
        PdfReport.create(rule.activity, SavedCalculation(mode = "reverse", result = Finance.deposit(DepositInput(250000.0, 13.5, 37, 7500.0, "weekly", "quarterly"))), "violet").copyTo(File(rule.activity.filesDir, "qa/deposit-sample.pdf"), overwrite = true)
    }
    @Test fun settingsAndLocalStoreSurviveReload() {
        rule.onNodeWithTag("tab_info").performClick()
        screenshot("14-info-light")
        rule.onNodeWithText("Темная").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Фиолетовая").performScrollTo().performClick()
        screenshot("05-info-dark")
        rule.onNodeWithTag("tab_loan").performClick()
        screenshot("06-loan-dark")
        rule.runOnIdle {
            model.edit("amount", "123456"); model.flush()
            val restored = LocalStore(rule.activity.filesDir).read()
            assertEquals("dark", restored.settings.theme); assertEquals("violet", restored.settings.skin)
            assertEquals("123456", restored.forms.getValue("loan").text("amount"))
        }
        val temp = File(rule.activity.cacheDir, "store-test").apply { mkdirs() }
        val corrupt = File(temp, "depocred-state.json"); corrupt.writeText("{incomplete")
        val store = LocalStore(temp)
        store.read()
        assertNotNull(store.recoveryNotice)
        assertTrue(temp.listFiles()!!.any { it.name.startsWith("depocred-recovery-") && it.readText() == "{incomplete" })
    }
}

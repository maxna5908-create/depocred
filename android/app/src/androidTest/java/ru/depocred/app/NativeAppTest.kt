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
            // Each scenario starts with all six forms empty, including period selectors.
            for (tab in listOf("loan", "deposit", "leasing")) {
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
    private fun SemanticsNodeInteraction.revealAndClick(): SemanticsNodeInteraction {
        performScrollTo()
        val menu = rule.onAllNodesWithTag("bottom_menu").fetchSemanticsNodes().firstOrNull()
        if (menu != null) {
            // The content viewport extends under the floating menu. A normal scrollTo
            // can leave a target behind it; move the target into the tappable area.
            val delta = fetchSemanticsNode().boundsInRoot.bottom - menu.boundsInRoot.top + 12 * rule.activity.resources.displayMetrics.density
            if (delta > 0) {
                rule.onNodeWithTag("content").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, delta) }
                rule.waitForIdle()
            }
        }
        return performClick()
    }
    private fun calculate() {
        rule.onNodeWithTag("calculate").revealAndClick()
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

    private fun focused(key: String) {
        rule.waitUntil(10000) {
            rule.onAllNodesWithTag("field_$key").fetchSemanticsNodes().any { it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false } }
        }
        rule.onNodeWithTag("field_$key").assertIsFocused().assertIsDisplayed()
    }
    private fun next(from: String, to: String) {
        rule.onNodeWithTag("field_$from").assert(hasImeAction(ImeAction.Next)).performImeAction()
        focused(to)
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
        rule.onNodeWithTag("field_amount").revealAndClick().performTextReplacement("1000000")
        keyboard(true)
        next("amount", "rate"); rule.onNodeWithTag("field_rate").performTextReplacement("12")
        next("rate", "term"); rule.onNodeWithTag("field_term").performTextReplacement("12")
        next("term", "fixed"); next("fixed", "commission")
        nativeKeyboardAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
        rule.onNodeWithTag("field_commission").assert(hasImeAction(ImeAction.Done)).performImeAction()
        keyboard(false)
        rule.onNodeWithText("Обратный").revealAndClick()
        rule.onNodeWithTag("field_term").assertDoesNotExist()
        rule.onNodeWithTag("field_amount").revealAndClick().performTextReplacement("1000000")
        rule.onNodeWithText("Аннуитетный — фиксированный").revealAndClick()
        rule.onNodeWithTag("field_amount").revealAndClick()
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
        rule.onNodeWithTag("field_term").revealAndClick().assert(hasImeAction(ImeAction.Done)).performImeAction()
        keyboard(false)
        rule.onNodeWithContentDescription("Пополнение").revealAndClick()
        rule.onNodeWithText("Раз в месяц").performClick()
        rule.onNodeWithTag("field_term").revealAndClick()
        next("term", "addition")
        nativeKeyboardAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
        screenshot("19-deposit-next")
        rule.onNodeWithTag("field_addition").assert(hasImeAction(ImeAction.Done)).performImeAction()
        keyboard(false)
    }
    @Test fun missingInputsShowNoticeAndFocusTheRequiredField() {
        rule.onNodeWithTag("calculate").revealAndClick()
        rule.onNodeWithText("Укажите сумму для расчёта").assertExists()
        focused("amount")
        keyboard(true)
        screenshot("20-required-amount")
        rule.onNodeWithTag("field_amount").performTextReplacement("1000000")
        rule.onNodeWithTag("calculate").revealAndClick()
        focused("rate")
        keyboard(true)
        rule.onNodeWithTag("field_rate").performTextReplacement("0")
        rule.onNodeWithTag("calculate").revealAndClick()
        focused("term")
        keyboard(true)
        rule.onNodeWithTag("field_term").performTextReplacement("12")
        calculate(); keyboard(false)
        rule.runOnIdle { assertEquals(0.0, model.state.current!!.result.totalInterest, .001) }
        rule.onNodeWithTag("tab_deposit").performClick()
        rule.onNodeWithText("Обратный").revealAndClick()
        rule.onNodeWithTag("calculate").revealAndClick()
        rule.onNodeWithText("Укажите сумму для расчёта").assertExists()
        focused("amount")
        keyboard(true)
    }
    private fun screenshot(name: String) {
        rule.runOnIdle { model.clearNotice() }
        rule.waitForIdle()
        // InputConnection/IME and Surface transactions finish outside Compose's test clock.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(400)
        // Tap-feedback timers can schedule a final Compose frame during the wait.
        rule.waitForIdle()
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
        rule.onNodeWithText("Как рассчитана номинальная ставка").revealAndClick()
        rule.onNodeWithText("ЭКСКУРС").assertIsDisplayed()
        rule.onNodeWithText("J = j / m").assertExists()
        screenshot("07-nominal-info")
        rule.onNodeWithText("12,00% / 12 = 1,0000% в месяц").performScrollTo().assertIsDisplayed()
        screenshot("08-nominal-steps")
        rule.onNodeWithText("ПОНЯТНО").performClick()
        rule.onNodeWithText("Как рассчитана эффективная ставка").revealAndClick()
        rule.onNodeWithText("Prec = Σ [Mk / (1+r)^k]").assertExists()
        screenshot("09-effective-info")
        rule.onNodeWithText("${money(1000000.0)} − ${money(17500.0)} = ${money(982500.0)}").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("× 100% = ", substring = true).performScrollTo().assertIsDisplayed()
        screenshot("10-effective-steps")
        rule.onNodeWithContentDescription("Закрыть окно").performClick()
        rule.onNodeWithContentDescription("Сохранить расчёт").revealAndClick()
        rule.onNodeWithTag("tab_history").performClick()
        rule.runOnIdle { assertEquals(1, model.state.history.size) }
        rule.onNodeWithText("График платежей").revealAndClick()
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
        rule.onNodeWithContentDescription("Пополнение").revealAndClick()
        screenshot("15-deposit-options")
        rule.onNodeWithText("Раз в месяц").performClick()
        rule.onNodeWithContentDescription("Капитализация процентов").revealAndClick()
        screenshot("16-capitalization-options")
        rule.onNode(hasText("Ежемесячно") and hasAnyAncestor(isDialog())).performClick()
        field("addition", "7500")
        calculate()
        rule.runOnIdle { assertTrue(model.state.current!!.result.finalAmount > 600000) }
        screenshot("04-deposit-results")
        rule.onNodeWithText("Как рассчитана ставка депозита").revealAndClick()
        rule.onNodeWithTag("rate_info_deposit").assertExists()
        rule.onNodeWithText("ПОНЯТНО").performClick()
        rule.onNodeWithText("График начислений").revealAndClick()
        rule.onNode(hasText("ПОПОЛНЕНИЯ") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.onNodeWithText("% ЗА ПЕРИОД").assertExists()
        screenshot("13-deposit-schedule")
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Обратный").revealAndClick()
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
        fun scrollValue() = rule.onNodeWithTag("content").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        // Finish Calculate's initial navigation before measuring tab-switch movement.
        rule.waitUntil(10000) { scrollValue() > 0 }
        rule.waitForIdle()
        rule.onNodeWithText("Аннуитетный").assertIsDisplayed()
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
        rule.onNodeWithText("Обратный").revealAndClick()
        rule.onNodeWithText("Аннуитетный — фиксированный").revealAndClick()
        rule.onNodeWithTag("field_term").assertDoesNotExist()
        focused("amount")
        field("amount", "100000")
        rule.onNodeWithText("Аннуитетный — фиксированный").revealAndClick()
        field("term", "12"); field("payment", "10000")
        rule.onNodeWithTag("field_total").assertIsNotEnabled()
        rule.runOnIdle { assertEquals("120000", model.state.form.text("total")) }
        field("payment", "")
        rule.onNodeWithTag("field_total").assertIsEnabled()
        rule.runOnIdle { assertEquals("", model.state.form.text("total")) }
        field("total", "100000")
        rule.runOnIdle { assertEquals("8333", model.state.form.text("payment")) }
        calculate(); keyboard(false)
        rule.runOnIdle {
            assertEquals(0.0, model.state.current!!.result.loan!!.rate, 0.0)
            assertEquals(100000.0, model.state.current!!.result.totalRepayment, .01)
        }
        rule.onNodeWithTag("tab_deposit").performClick()
        rule.onNodeWithText("Обратный").revealAndClick()
        field("amount", "100000"); field("rate", "12"); field("term", "12")
        rule.onNodeWithContentDescription("Очистить Итоговая сумма (₽)", useUnmergedTree = true).revealAndClick()
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
    private fun workingSystemBars(dark: Boolean) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)
        rule.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        // On tablets the bottom taskbar belongs to Launcher/System UI and follows
        // the device theme, independently of our in-app theme. Verify our requested
        // icon appearance and sample only system-bar backgrounds drawn by the app.
        rule.runOnIdle {
            val controller = androidx.core.view.WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
            assertEquals(!dark, controller.isAppearanceLightStatusBars)
            assertEquals(!dark, controller.isAppearanceLightNavigationBars)
        }
        val samples = mutableListOf(bitmap.width / 2 to 6)
        if (rule.activity.resources.configuration.smallestScreenWidthDp < 600) {
            samples += bitmap.width / 4 to bitmap.height - 6
        }
        for ((x, y) in samples) {
            val pixel = bitmap.getPixel(x, y)
            val channels = listOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel))
            assertTrue("System bar must follow ${if (dark) "dark" else "light"} theme: $channels", if (dark) channels.max() < 140 else channels.min() > 180)
        }
        bitmap.recycle()
    }
    @Test fun fiveMenuTabsAndThreeHistoryFiltersPreserveCalculationsAndPreferences() {
        val freshDirectory = File(rule.activity.cacheDir, "fresh-defaults-${System.nanoTime()}").apply { mkdirs() }
        val fresh = LocalStore(freshDirectory).read()
        assertEquals("deposit", fresh.tab)
        assertEquals("light", fresh.settings.theme)
        assertEquals("amber", fresh.settings.skin)
        val legacy = StateJson.decode(StateJson.encode(UiState(settings = Settings("dark", "emerald"))))
        assertEquals("dark", legacy.settings.theme)
        assertEquals("emerald", legacy.settings.skin)
        val sections = listOf("deposit", "loan", "leasing", "history", "info")
        val menuBounds = sections.map { rule.onNodeWithTag("tab_$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        assertTrue(menuBounds.zipWithNext().all { (left, right) -> left.right <= right.left + 1 })
        screenshot("31-five-section-menu-orange")
        rule.onNodeWithTag("tab_deposit").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(100)
        screenshot("32-menu-pressed")
        rule.onNodeWithTag("tab_deposit").performTouchInput { up() }
        rule.onNodeWithTag("tab_deposit").assertIsSelected()
        rule.onNodeWithTag("tab_loan").performClick()
        field("amount", "100000"); field("rate", "10"); field("term", "12"); calculate()
        val resultId = model.state.current!!.id
        rule.onNodeWithTag("tab_loan").performClick()
        rule.runOnIdle { assertEquals(resultId, model.state.current?.id); model.saveCurrent(); model.clearNotice() }
        rule.onNodeWithTag("tab_deposit").performClick()
        field("amount", "200000"); field("rate", "12"); field("term", "12"); calculate()
        rule.runOnIdle { model.saveCurrent(); model.clearNotice() }
        rule.onNodeWithTag("tab_leasing").performClick()
        rule.onNodeWithContentDescription("График платежей").revealAndClick()
        LEASE_TYPES.forEach { (key, label) ->
            rule.onNode(hasText(label) and hasAnyAncestor(isDialog())).assertExists()
            rule.onNode(hasText(LEASE_TYPE_DESCRIPTIONS.getValue(key)) and hasAnyAncestor(isDialog())).assertExists()
        }
        screenshot("44-leasing-payment-descriptions")
        rule.onNodeWithContentDescription("Закрыть окно").performClick()
        field("amount", "1500000"); field("advance", "20"); field("rate", "18"); field("term", "36"); calculate()
        rule.runOnIdle { model.saveCurrent(); model.clearNotice() }
        rule.onNodeWithTag("lease_result").assertExists()
        screenshot("33-leasing-direct")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("tab_leasing").assertIsSelected()
        rule.onNodeWithTag("lease_result").assertExists()
        workingSystemBars(false)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("tab_history").performClick()
        rule.mainClock.advanceTimeBy(96)
        screenshot("38-menu-transition-100ms")
        rule.mainClock.advanceTimeBy(320)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag("tab_history").assertIsSelected()
        rule.runOnIdle { assertEquals(3, model.state.history.size) }
        rule.onNodeWithTag("history_entry_deposit").assertExists()
        rule.onNodeWithTag("history_entry_loan").assertExists()
        val filters = listOf("deposit", "loan", "leasing").map { rule.onNodeWithTag("history_filter_$it").fetchSemanticsNode().boundsInRoot }
        assertTrue(filters.zipWithNext().all { (left, right) -> left.right <= right.left + 1 })
        rule.onNodeWithTag("history_filter_deposit").performClick().assertIsSelected()
        rule.onNodeWithTag("history_entry_deposit").assertExists()
        rule.onNodeWithTag("history_entry_loan").assertDoesNotExist()
        screenshot("34-history-deposit")
        rule.onNodeWithTag("history_filter_loan").performClick().assertIsSelected()
        rule.onNodeWithTag("history_entry_loan").assertExists()
        rule.onNodeWithTag("history_entry_deposit").assertDoesNotExist()
        screenshot("35-history-loan")
        rule.onNodeWithTag("history_filter_leasing").performClick().assertIsSelected()
        rule.onNodeWithTag("history_entry_loan").assertDoesNotExist()
        rule.onNodeWithTag("history_entry_deposit").assertDoesNotExist()
        rule.onNodeWithTag("history_entry_leasing").assertExists()
        rule.onNodeWithText("История расчетов пуста").assertDoesNotExist()
        screenshot("36-history-leasing")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("history_filter_leasing").assertIsSelected()
        rule.onNodeWithTag("history_filter_leasing").performClick()
        rule.onNodeWithTag("history_entry_deposit").assertExists()
        rule.onNodeWithTag("history_entry_loan").assertExists()
        rule.onNodeWithTag("history_entry_leasing").assertExists()
        rule.runOnIdle { model.settings(Settings("dark", "violet")) }
        workingSystemBars(true)
        screenshot("37-menu-dark-violet")
        rule.activityRule.scenario.recreate()
        workingSystemBars(true)
        screenshot("39-dark-theme-after-recreation")
    }
    @Test fun leasingReverseFormsValidateGroupsAndPreserveCashFlowsAcrossReload() {
        rule.onNodeWithTag("tab_leasing").performClick()
        rule.onNodeWithText("Обратный").revealAndClick()
        rule.onNodeWithTag("calculate").revealAndClick()
        focused("amount"); keyboard(true)
        rule.runOnIdle { model.clearNotice() }
        rule.onNodeWithTag("field_amount").performTextReplacement("10300000")
        next("amount", "advance"); rule.onNodeWithTag("field_advance").performTextReplacement("10")
        next("advance", "count0"); rule.onNodeWithTag("field_count0").performTextReplacement("34")
        next("count0", "payment0"); rule.onNodeWithTag("field_payment0").performTextReplacement("359210.80")
        next("payment0", "buyout"); rule.onNodeWithTag("field_buyout").performTextReplacement("20000")
        next("buyout", "firstMonth"); next("firstMonth", "expenses")
        rule.onNodeWithTag("field_expenses").performTextReplacement("51000")
        rule.onNodeWithTag("field_expenses").assert(hasImeAction(ImeAction.Done)).performImeAction()
        keyboard(false)
        calculate()
        rule.runOnIdle {
            assertEquals(20.48352330511784,model.state.current!!.result.nominalRate,1e-8)
            model.saveCurrent(); model.clearNotice(); model.flush()
            val restored = LocalStore(rule.activity.filesDir).read()
            assertNull(restored.current)
            assertEquals(model.state.current!!.result,restored.history.first().result)
            assertEquals(UiState().forms,restored.forms)
            assertEquals("leasing",restored.history.first().type)
        }
        screenshot("40-leasing-reverse-result")
        rule.onNodeWithText("Сохранить PDF").performScrollTo()
        val pdfButton = rule.onNodeWithText("Сохранить PDF").fetchSemanticsNode().boundsInRoot
        val resultCard = rule.onNodeWithTag("lease_result").fetchSemanticsNode().boundsInRoot
        assertEquals(resultCard.width - 48 * rule.activity.resources.displayMetrics.density, pdfButton.width, 2f)
        screenshot("44-leasing-full-width-pdf")
        rule.onNodeWithText("Как рассчитана ставка").revealAndClick()
        rule.onNodeWithText("Ставка лизинга").assertIsDisplayed()
        screenshot("41-leasing-rate-info")
        rule.onNodeWithText("ЗАКРЫТЬ").performClick()
        rule.onNodeWithText("Платежи").revealAndClick()
        rule.onNodeWithTag("schedule").assertIsDisplayed()
        screenshot("42-leasing-payments")
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Обратный").revealAndClick()
        rule.onNodeWithContentDescription("Единица аванса").revealAndClick()
        rule.onNode(hasText("₽") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.runOnIdle { assertEquals(1030000.0,model.state.form.number("advance"),0.0) }
        rule.onNodeWithTag("lease_add_group").revealAndClick()
        field("count0","12"); field("payment0","300000"); field("count1","22"); field("payment1","391507.60")
        screenshot("43-leasing-step-form")
        calculate()
        rule.runOnIdle { assertEquals(2,model.state.current!!.result.leasing!!.groups.size) }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("lease_result").assertExists()
        rule.runOnIdle { assertEquals("391507.60",model.state.form.text("payment1")) }
        rule.onNodeWithTag("tab_history").performClick()
        rule.onNodeWithTag("history_filter_leasing").performClick()
        rule.onNodeWithTag("history_entry_leasing").assertExists()
        rule.onNodeWithContentDescription("Удалить расчёт").revealAndClick()
        rule.runOnIdle { assertTrue(model.state.history.isEmpty()) }
    }
    @Test fun leasingPdfContainsFullScheduleAndUsesTheExistingSecureShareMechanism() {
        val input = LeasingInput(8_500_000.0,1_700_000.0,"rub",groups=listOf(LeasePaymentGroup(18,456_912.86)),buyout=1_999.99,expenses=22_000.0)
        val entry = SavedCalculation(mode="reverse",result=LeasingFinance.reverse(input))
        val directory = File(rule.activity.filesDir,"qa").apply { mkdirs() }
        val file = PdfReport.create(rule.activity,entry,"amber")
        file.copyTo(File(directory,"leasing-sample.pdf"),overwrite=true)
        PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount >= 2)
            for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(directory,"leasing-pdf-${index+1}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(rule.activity,"${rule.activity.packageName}.fileprovider",file)
        val intent = pdfShareIntent(uri)
        assertEquals("application/pdf",intent.type)
        assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
        rule.activity.contentResolver.openInputStream(uri).use { assertTrue(it!!.read() >= 0) }
        val long = PdfReport.create(rule.activity,SavedCalculation(mode="reverse",result=LeasingFinance.reverse(input.copy(groups=listOf(LeasePaymentGroup(998,10000.0))))),"violet")
        PdfRenderer(ParcelFileDescriptor.open(long,ParcelFileDescriptor.MODE_READ_ONLY)).use { assertTrue(it.pageCount>30) }
    }
    @Test fun settingsAndHistorySurviveClosingButDraftsOnlySurviveTheCurrentSession() {
        rule.onNodeWithTag("tab_info").performClick()
        screenshot("14-info-light")
        rule.onNodeWithText("Темная").revealAndClick()
        rule.onNodeWithContentDescription("Фиолетовая").revealAndClick()
        screenshot("05-info-dark")
        rule.onNodeWithTag("tab_loan").performClick()
        screenshot("06-loan-dark")
        rule.runOnIdle {
            model.edit("amount", "123456"); model.flush()
            val restored = LocalStore(rule.activity.filesDir).read()
            assertEquals("dark", restored.settings.theme); assertEquals("violet", restored.settings.skin)
            assertEquals(UiState().forms, restored.forms)
        }
        val session = model
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.runOnIdle { assertSame(session, model); assertEquals("123456", model.state.form.text("amount")) }
        rule.activityRule.scenario.recreate()
        rule.runOnIdle { assertSame(session, model); assertEquals("123456", model.state.form.text("amount")) }
        // A fresh ViewModel models a new Activity/task after termination, not a recreation.
        val freshModels = androidx.lifecycle.ViewModelStore()
        rule.runOnIdle {
            val reopened = ViewModelProvider(freshModels, ViewModelProvider.AndroidViewModelFactory.getInstance(rule.activity.application))[AppViewModel::class.java]
            assertEquals("deposit", reopened.state.tab)
            assertEquals(UiState().forms, reopened.state.forms)
            assertNull(reopened.state.current)
            assertEquals(session.state.settings, reopened.state.settings)
            reopened.flush()
            freshModels.clear()
        }
        val temp = File(rule.activity.cacheDir, "store-test").apply { mkdirs() }
        val corrupt = File(temp, "depocred-state.json"); corrupt.writeText("{incomplete")
        val store = LocalStore(temp)
        store.read()
        assertNotNull(store.recoveryNotice)
        assertTrue(temp.listFiles()!!.any { it.name.startsWith("depocred-recovery-") && it.readText() == "{incomplete" })
    }
    @Test fun changingToolsStartsAtTheTopOnTheFirstFrame() {
        fun offset() = rule.onNodeWithTag("content").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        val tabs = listOf("info", "leasing", "loan", "deposit", "history", "info")
        for (tab in tabs) {
            rule.onNodeWithTag("tab_$tab").performClick()
            assertEquals("The first layout of $tab must start at zero", 0f, offset(), 0f)
            if (tab == "info") {
                rule.onNodeWithText("Открытые лицензии").performScrollTo()
                assertTrue(offset() > 0f)
            } else if (tab != "history") {
                // Short forms can fit the viewport. Scroll all available content,
                // including its bottom inset, without assuming a device height.
                rule.onNodeWithTag("content").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 10000f) }
                val range = rule.onNodeWithTag("content").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
                assertEquals(range.maxValue(), range.value(), 1f)
            }
        }
        rule.onNodeWithTag("tab_deposit").performClick()
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("tab_info").performClick()
            repeat(5) {
                rule.mainClock.advanceTimeByFrame()
                assertEquals("Returning to Info must not animate an old offset", 0f, offset(), 0f)
            }
        } finally { rule.mainClock.autoAdvance = true }
        rule.onNodeWithText("Настройки").assertIsDisplayed()
        screenshot("45-info-return-at-top")
    }
    @Test fun pendingStorageAlwaysKeepsTheLatestSnapshotAndInputDoesNotWriteToDisk() {
        val temp = File(rule.activity.cacheDir, "latest-store-${System.nanoTime()}").apply { mkdirs() }
        val store = LocalStore(temp)
        val old = UiState(settings = Settings("dark", "rose"))
        val latest = UiState(settings = Settings("light", "blue"))
        store.stage(StateJson.encode(old)); store.stage(StateJson.encode(latest))
        store.flush(); store.flush()
        assertEquals(latest, store.read())
        rule.runOnIdle { model.flush(); model.clearNotice() }
        val file = File(rule.activity.filesDir, "depocred-state.json")
        val bytes = file.readBytes()
        rule.runOnIdle {
            for (n in 1..100) model.edit("amount", n.toString())
            model.flush()
            assertNull(model.notice)
        }
        assertArrayEquals(bytes, file.readBytes())
    }
}

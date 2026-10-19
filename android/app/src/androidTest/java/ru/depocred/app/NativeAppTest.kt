package ru.depocred.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            model.tab("loan"); model.mode(false); model.reset(); model.settings(Settings())
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
    private fun screenshot(name: String, tag: String? = null) {
        rule.runOnIdle { model.clearNotice() }
        rule.waitForIdle()
        val directory = File(rule.activity.filesDir, "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { (if (tag == null) rule.onAllNodes(isRoot()).onLast() else rule.onNodeWithTag(tag)).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun loanCalculationHistoryScheduleAndRecreation() {
        screenshot("01-loan-light")
        field("amount", "1000000"); field("rate", "12"); field("term", "12"); field("fixed", "2500"); field("commission", "1.5"); calculate()
        rule.runOnIdle { assertEquals(88848.788678, model.state.current!!.result.firstPayment, .01) }
        screenshot("02-loan-results")
        rule.onNodeWithContentDescription("Информация: Номинальная ставка").performScrollTo().performClick()
        rule.onNodeWithText("ЭКСКУРС").assertIsDisplayed()
        rule.onNodeWithText("J = j / m").assertExists()
        screenshot("07-nominal-info", "rate_info_nominal")
        rule.onNodeWithText("12,00% / 12 = 1,0000% в месяц").performScrollTo().assertIsDisplayed()
        screenshot("08-nominal-steps", "rate_info_nominal")
        rule.onNodeWithText("ПОНЯТНО").performClick()
        rule.onNodeWithContentDescription("Информация: Эффективная ставка").performScrollTo().performClick()
        rule.onNodeWithText("Prec = Σ [Mk / (1+r)^k]").assertExists()
        screenshot("09-effective-info", "rate_info_effective")
        rule.onNodeWithText("${money(1000000.0)} − ${money(17500.0)} = ${money(982500.0)}").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("ШАГ 3: ГОДОВАЯ ЭФФЕКТИВНАЯ СТАВКА").performScrollTo().assertIsDisplayed()
        screenshot("10-effective-steps", "rate_info_effective")
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
        rule.onNodeWithText("Раз в месяц").performClick()
        field("addition", "7500")
        calculate()
        rule.runOnIdle { assertTrue(model.state.current!!.result.finalAmount > 600000) }
        screenshot("04-deposit-results")
        rule.onNodeWithText("График начислений").performScrollTo().performClick()
        rule.onNodeWithText("ПОПОЛНЕНИЯ").assertExists()
        rule.onNodeWithText("% ЗА ПЕРИОД").assertExists()
        screenshot("13-deposit-schedule")
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Обратный").performScrollTo().performClick()
        field("amount", "100000"); field("rate", "12"); field("term", "12")
        rule.runOnIdle { assertTrue(model.state.form.number("final") > 112000); assertEquals("final", model.state.form.target) }
        field("rate", "")
        rule.runOnIdle { assertEquals("", model.state.form.text("final")) }
        field("final", "120000"); field("term", "12")
        rule.runOnIdle { assertEquals("rate", model.state.form.target); assertTrue(model.state.form.number("rate") > 18) }
        calculate()
        rule.runOnIdle { assertEquals(120000.0, model.state.current!!.result.finalAmount, 10.0) }
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

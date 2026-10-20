package ru.depocred.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StartupScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>().apply { mainClock.autoAdvance = false }

    @Test fun orangeStartupHoldsAfterDrawingThenFadesAndDoesNotReplayOnRecreation() {
        rule.waitUntil(15000) { rule.onAllNodesWithTag("startup").fetchSemanticsNodes().isNotEmpty() }
        fun whitePixels(): Int {
            // A graphicsLayer scales drawing, not the Column's layout measurements.
            val bitmap = rule.onNodeWithTag("startup_brand").captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return pixels.count { Color.red(it) > 235 && Color.green(it) > 235 && Color.blue(it) > 235 && Color.alpha(it) > 235 }
        }
        rule.mainClock.advanceTimeBy(480)
        val middlePixels = whitePixels()
        rule.mainClock.advanceTimeBy(520)
        rule.waitForIdle()
        val holdStarted = SystemClock.elapsedRealtime()
        val finalPixels = whitePixels()
        assertTrue("Drawing must still progress after 450ms: $middlePixels -> $finalPixels", finalPixels > middlePixels * 1.02)
        rule.onNodeWithTag("startup").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Загрузка"))
        rule.runOnUiThread {
            val controller = WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
            assertFalse(controller.isAppearanceLightNavigationBars)
            if (android.os.Build.VERSION.SDK_INT >= 29) assertFalse(rule.activity.window.isNavigationBarContrastEnforced)
        }
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val orange = Color.rgb(245, 158, 11)
        // Sample the actual device navigation area, not only the Compose content.
        for (y in listOf(screenshot.height / 4, screenshot.height - 6)) {
            assertEquals(orange, screenshot.getPixel(screenshot.width / 4, y))
        }
        val directory = File(rule.activity.filesDir, "qa").apply { mkdirs() }
        File(directory, "30-orange-startup.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        SystemClock.sleep((holdStarted + 1100 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("startup").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Загрузка"))
        rule.waitUntil(5000) {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodesWithTag("startup").fetchSemanticsNodes().firstOrNull()?.config?.getOrElse(SemanticsProperties.StateDescription) { "" } == "Открытие приложения"
        }
        assertTrue("The pause must follow the completed drawing", SystemClock.elapsedRealtime() - holdStarted >= 1400)
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("startup").assertExists()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("startup").assertDoesNotExist()
        rule.mainClock.autoAdvance = true
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("startup").assertDoesNotExist()
        rule.onNodeWithTag("bottom_menu").assertExists()

        // The launcher background is fixed orange; its foreground is the splash's Lucide coins.
        val launcher = rule.activity.applicationInfo.loadIcon(rule.activity.packageManager) as AdaptiveIconDrawable
        val background = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        launcher.background.setBounds(0, 0, 216, 216)
        launcher.background.draw(Canvas(background))
        assertEquals(orange, background.getPixel(108, 108))
        background.recycle()
        val actual = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        val expected = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        rule.activity.getDrawable(R.drawable.ic_launcher_foreground)!!.apply { setBounds(0, 0, 216, 216); draw(Canvas(actual)) }
        rule.activity.getDrawable(R.drawable.lucide_coins)!!.apply { setBounds(54, 54, 162, 162); draw(Canvas(expected)) }
        var different = 0
        for (y in 0 until 216) for (x in 0 until 216) if (actual.getPixel(x, y) != expected.getPixel(x, y)) different++
        assertTrue("Launcher glyph must match the splash logo", different < 216 * 216 / 100)
        actual.recycle(); expected.recycle()
    }
}

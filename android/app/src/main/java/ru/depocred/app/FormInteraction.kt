@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ru.depocred.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One outline and one translucent fill: there is no border or double alpha at the seam. */
private class AttachedPanelShape(private val selected: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val radius = CornerRadius(16.dp.toPx())
        val zero = CornerRadius.Zero
        val tabHeight = 42.dp.toPx()
        val start = selected * size.width / 2
        val tab = Path().apply { addRoundRect(RoundRect(Rect(start, 0f, start + size.width / 2, tabHeight), radius, radius, zero, zero)) }
        val body = Path().apply { addRoundRect(RoundRect(Rect(0f, tabHeight - .5f, size.width, size.height), if (selected == 0) zero else radius, if (selected == 1) zero else radius, radius, radius)) }
        Outline.Generic(Path.combine(PathOperation.Union, tab, body))
    }
}

@Composable internal fun AttachedPanel(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val pressedAlpha by animateFloatAsState(if (pressed) .10f else 0f, tween(120), label = "panel press")
    val shape = remember(selected) { AttachedPanelShape(selected) }
    Column(Modifier.fillMaxWidth().outsideShadow(shape, if (p.dark) .4f else .1f).background(p.glass, shape).border(1.dp, p.border, shape).drawWithContent {
        drawContent()
        if (pressedAlpha > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), if (p.dark) Color.White.copy(alpha = pressedAlpha) else Color.Black.copy(alpha = pressedAlpha))
    }) {
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            titles.forEachIndexed { index, title ->
                Box(Modifier.weight(1f).fillMaxHeight().semantics { this.selected = selected == index; role = Role.Tab }
                    .clickable(interactionSource = interactions, indication = null) { onSelect(index) }, contentAlignment = Alignment.Center) {
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected == index) p.text else p.muted)
                }
            }
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

internal class FormNavigation(
    val order: List<String>, val requesters: Map<String, FocusRequester>,
    val bringIntoView: Map<String, BringIntoViewRequester>,
    private val scope: CoroutineScope, private val keyboard: SoftwareKeyboardController?
) {
    fun next(field: String): String? = order.indexOf(field).takeIf { it >= 0 }?.let { order.getOrNull(it + 1) }
    fun focus(field: String) {
        scope.launch {
            requesters[field]?.requestFocus()
            // Let the new field establish its InputConnection before asking the IME to show.
            withFrameNanos { }
            if (field == "paymentType") keyboard?.hide() else keyboard?.show()
            bringIntoView[field]?.bringIntoView()
        }
    }
}
internal val LocalFormNavigation = staticCompositionLocalOf<FormNavigation?> { null }

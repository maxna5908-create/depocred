@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ru.depocred.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val SKINS = linkedMapOf("emerald" to Color(0xFF10B981), "blue" to Color(0xFF3B82F6), "amber" to Color(0xFFF59E0B), "rose" to Color(0xFFF43F5E), "violet" to Color(0xFF8B5CF6))
private val Inter = FontFamily(Font(R.font.inter))
internal data class Palette(val dark: Boolean, val primary: Color) {
    val text get() = if (dark) Color.White else Color(0xFF0F172A)
    val muted get() = if (dark) Color(0xFFCBD5E1) else Color(0xFF64748B)
    val glass get() = if (dark) Color.White.copy(alpha = .12f) else Color.White.copy(alpha = .60f)
    val field get() = if (dark) Color.White.copy(alpha = .08f) else Color.Black.copy(alpha = .05f)
    val border get() = if (dark) Color.White.copy(alpha = .18f) else Color.Black.copy(alpha = .10f)
    val solid get() = if (dark) Color(0xFF0F172A) else Color(0xFFF9FAFB)
    val backgroundColors get() = listOf(if (dark) Color(0xFF374151) else Color(0xFFF9FAFB), if (dark) Color(0xFF111827) else Color(0xFFE5E7EB))
    val background get() = Brush.radialGradient(backgroundColors, center = Offset.Zero, radius = 2400f)
}
internal val LocalPalette = staticCompositionLocalOf { Palette(false, Color(0xFFF59E0B)) }

@Composable fun DepoCredApp(model: AppViewModel, onExport: (SavedCalculation, Boolean) -> Unit, onLink: (String) -> Unit, onShareApp: () -> Unit) {
    val state = model.state
    val dark = state.settings.theme == "dark" || state.settings.theme == "system" && isSystemInDarkTheme()
    val palette = Palette(dark, SKINS[state.settings.skin] ?: SKINS.getValue("amber"))
    val scheme = if (dark) darkColorScheme(primary = palette.primary, background = palette.solid, surface = palette.solid, onSurface = palette.text)
        else lightColorScheme(primary = palette.primary, background = palette.solid, surface = palette.solid, onSurface = palette.text)
    // Small text must use its own natural line height, not Material's 24sp body line height.
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = appTypography()) {
          CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Inter, fontSize = 14.sp, color = palette.text, platformStyle = PlatformTextStyle(includeFontPadding = false))) {
            val activity = LocalActivity.current
            val view = LocalView.current
            var splash by rememberSaveable { mutableStateOf(true) }
            val splashTransition = remember { MutableTransitionState(splash) }
            splashTransition.targetState = splash
            val splashVisible = splashTransition.currentState || splashTransition.targetState
            val startupColor = colorResource(R.color.startup_orange).toArgb()
            val windowBackground = remember(splashVisible, dark, startupColor) {
                if (splashVisible) android.graphics.drawable.ColorDrawable(startupColor)
                else android.graphics.drawable.GradientDrawable().apply {
                    gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
                    colors = palette.backgroundColors.map { it.toArgb() }.toIntArray()
                    setGradientCenter(0f, 0f)
                    gradientRadius = 2400f
                }
            }
            SideEffect { activity?.let { applyAppWindow(it, view, windowBackground, !dark && !splashVisible) } }
            var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
            val selected = (state.history + listOfNotNull(state.current)).firstOrNull { it.id == selectedId }
            val scope = rememberCoroutineScope()
            // A tool visit starts at zero before its first layout. Do not restore a
            // previous tool's saved offset or animate a reset after displaying it.
            val scroll = remember(state.tab) { ScrollState(0) }
            val backdrop = rememberGraphicsLayer()
            var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
            val focus = LocalFocusManager.current
            var resultY by remember(state.tab, state.formKey, state.current?.id) { mutableIntStateOf(-1) }
            val density = LocalDensity.current
            val ime = WindowInsets.ime
            val keyboard = ime.getBottom(density) > 0
            LaunchedEffect(state.tab, state.formKey, state.current?.id, state.resultRevision) {
                if (state.current != null) {
                    // Wait for the actual result layout and keyboard resize, not a timer.
                    // Leaving a form cancels this navigation; result tab switches keep its id.
                    snapshotFlow { resultY }.first { it >= 0 }
                    snapshotFlow { ime.getBottom(density) }.first { it == 0 }
                    withFrameNanos { }
                    scroll.animateScrollTo(resultY.coerceIn(0, scroll.maxValue))
                }
            }
            LaunchedEffect(model.notice) { if (model.notice != null) { delay(3500); model.clearNotice() } }
            BackHandler(enabled = !splashVisible && state.tab != "deposit" && selected == null) { model.tab("deposit") }
            Box(Modifier.fillMaxSize().background(palette.background)) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).imePadding(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 800.dp).fillMaxSize().clipToBounds().onGloballyPositioned { backdropOrigin = it.positionInRoot() }.drawWithContent {
                        backdrop.record { drawRect(palette.background); this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    }) {
                        AppHeader(state.tab)
                        Column(Modifier.weight(1f).fillMaxWidth().clipToBounds().verticalScroll(scroll).testTag("content").padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = if (keyboard) 28.dp else 144.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            when (state.tab) {
                                "loan", "deposit", "leasing" -> {
                                    if (state.tab == "leasing") LeasingForm(state, model) else CalculatorForm(state, model)
                                    if (state.current != null) {
                                        // Position relative to scroll content, independent of keyboard/screen size.
                                        Box(Modifier.testTag("calculation_result").onGloballyPositionedCompat { y -> resultY = y }) {
                                            if (state.current.result.isLeasing) LeasingResult(state.current, model::saveCurrent, { selectedId = state.current.id }, onExport)
                                            else CalculationResult(state.current, state.form.text("paymentType"), model::paymentType,
                                                onSave = model::saveCurrent, onSchedule = { selectedId = state.current.id }, onExport = onExport)
                                        }
                                        Column(Modifier.fillMaxWidth().clickable { scope.launch { scroll.animateScrollTo(0) } }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Glyph(R.drawable.lucide_arrow_up, null, palette.muted, 32)
                                            Text("К ПАРАМЕТРАМ ${when (state.tab) { "loan" -> "КРЕДИТА"; "leasing" -> "ЛИЗИНГА"; else -> "ДЕПОЗИТА" }}", fontSize = 10.sp, color = palette.muted, modifier = Modifier.padding(top = 8.dp))
                                        }
                                    }
                                }
                                "history" -> HistoryScreen(state.history, model::delete, { selectedId = it.id }, onExport)
                                "info" -> InfoScreen(state.settings, model::settings, onLink, onShareApp)
                            }
                        }
                    }
                    if (!keyboard) BottomMenu(state.tab, { focus.clearFocus(); model.tab(it) }, Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 20.dp).widthIn(max = 752.dp), backdrop, backdropOrigin)
                    model.notice?.let { message ->
                        Surface(Modifier.align(Alignment.TopCenter).padding(16.dp).widthIn(max = 520.dp).clickable { model.clearNotice() }, shape = RoundedCornerShape(16.dp), color = palette.solid, border = BorderStroke(1.dp, palette.primary), shadowElevation = 8.dp) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Glyph(R.drawable.lucide_info, null, palette.primary)
                                Text(message, fontSize = 13.sp, color = palette.text, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                AnimatedVisibility(visibleState = splashTransition, exit = fadeOut(tween(600))) {
                    Splash(Modifier.testTag("startup").semantics { stateDescription = if (splash) "Загрузка" else "Открытие приложения" }) { splash = false }
                }
            }
            selected?.let { if (it.result.isLeasing) LeasingSchedule(it, { selectedId = null }, onExport) else ScheduleScreen(it, { selectedId = null }, onExport) }
          }
        }
    }
}

// Kept separate so positions are measured in the scroll viewport.
private fun Modifier.onGloballyPositionedCompat(block: (Int) -> Unit): Modifier = this.then(Modifier.onGloballyPositioned { block(it.positionInParent().y.toInt()) })

private fun appTypography(): Typography {
    val t = Typography()
    return Typography(displayLarge = t.displayLarge.copy(fontFamily = Inter), displayMedium = t.displayMedium.copy(fontFamily = Inter), displaySmall = t.displaySmall.copy(fontFamily = Inter),
        headlineLarge = t.headlineLarge.copy(fontFamily = Inter), headlineMedium = t.headlineMedium.copy(fontFamily = Inter), headlineSmall = t.headlineSmall.copy(fontFamily = Inter),
        titleLarge = t.titleLarge.copy(fontFamily = Inter), titleMedium = t.titleMedium.copy(fontFamily = Inter), titleSmall = t.titleSmall.copy(fontFamily = Inter),
        bodyLarge = t.bodyLarge.copy(fontFamily = Inter), bodyMedium = t.bodyMedium.copy(fontFamily = Inter), bodySmall = t.bodySmall.copy(fontFamily = Inter),
        labelLarge = t.labelLarge.copy(fontFamily = Inter), labelMedium = t.labelMedium.copy(fontFamily = Inter), labelSmall = t.labelSmall.copy(fontFamily = Inter))
}

// The starting theme can be restored during Activity recreation. Keep the
// window behind Compose in sync too, including areas reserved for system bars.
@Suppress("DEPRECATION")
private fun applyAppWindow(activity: android.app.Activity, view: android.view.View, background: android.graphics.drawable.Drawable, darkIcons: Boolean) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setBackgroundDrawable(background)
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (android.os.Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = darkIcons
        isAppearanceLightNavigationBars = darkIcons
    }
}

@Composable private fun Splash(modifier: Modifier = Modifier, onReady: () -> Unit) {
    val scale = remember { Animatable(.6f) }
    LaunchedEffect(Unit) {
        // The pause starts only after the 2x slower drawing animation has finished.
        scale.animateTo(1f, tween(900))
        delay(1500)
        onReady()
    }
    Box(modifier.fillMaxSize().background(colorResource(R.color.startup_orange))) {
        Column(Modifier.align(Alignment.Center).testTag("startup_brand").scale(scale.value), horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(R.drawable.lucide_coins, null, Color.White, 96)
            Text("DepoCred", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(top = 28.dp))
            Text("ВАШ ФИНАНСОВЫЙ АССИСТЕНТ", fontSize = 11.sp, color = Color.White.copy(alpha = .8f), letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp))
        }
        LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp).width(48.dp).height(3.dp), color = Color.White, trackColor = Color.White.copy(alpha = .3f))
    }
}
@Composable private fun AppHeader(tab: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Column(Modifier.width(IntrinsicSize.Max)) {
                Text("ВАШ ФИНАНСОВЫЙ АССИСТЕНТ", fontSize = 11.sp, color = p.muted, letterSpacing = 1.1.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(1.5.dp).background(p.primary, CircleShape))
            }
            Text(when (tab) { "loan" -> "Кредитный калькулятор"; "deposit" -> "Депозитный калькулятор"; "leasing" -> "Лизинг"; "history" -> "Сохраненные расчеты"; else -> "О приложении" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.primary, modifier = Modifier.padding(top = 6.dp))
        }
        Glyph(R.drawable.lucide_coins, null, p.primary, 32)
    }
}
@Composable private fun BottomMenu(tab: String, onTab: (String) -> Unit, modifier: Modifier, backdrop: GraphicsLayer, backdropOrigin: Offset) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(percent = 50)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val blurSupported = android.os.Build.VERSION.SDK_INT >= 31
    Box(modifier.fillMaxWidth().testTag("bottom_menu").onGloballyPositioned { origin = it.positionInRoot() }
        .outsideShadow(shape, .16f).clip(shape).border(1.dp, Color.White.copy(alpha = if (p.dark) .14f else .4f), shape)) {
        // Blur only the content behind the floating menu; labels and icons stay sharp.
        if (blurSupported) Box(Modifier.matchParentSize().graphicsLayer {
            renderEffect = BlurEffect(18.dp.toPx(), 18.dp.toPx(), TileMode.Clamp)
        }.drawWithContent {
            translate(backdropOrigin.x - origin.x, backdropOrigin.y - origin.y) { drawLayer(backdrop) }
        })
        Box(Modifier.matchParentSize().background((if (p.dark) Color(0xFF374151) else Color(0xFFE9EBEE)).copy(alpha = if (blurSupported) .90f else .96f)))
        Row(Modifier.fillMaxWidth().height(80.dp).padding(4.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                Triple("deposit", "ДЕПОЗИТ", R.drawable.lucide_piggy_bank),
                Triple("loan", "КРЕДИТ", R.drawable.lucide_credit_card),
                Triple("leasing", "ЛИЗИНГ", R.drawable.lucide_car_front),
                Triple("history", "ИСТОРИЯ", R.drawable.lucide_bookmark),
                Triple("info", "ИНФО", R.drawable.lucide_info)
            ).forEach { (id, label, icon) ->
                MenuItem(id, label, icon, tab == id, { onTab(id) }, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
@Composable private fun MenuItem(id: String, label: String, icon: Int, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var tapFlash by remember { mutableStateOf(false) }
    var tapSequence by remember { mutableIntStateOf(0) }
    LaunchedEffect(tapSequence) {
        if (tapSequence > 0) { tapFlash = true; delay(160); tapFlash = false }
    }
    // The reference crossfades capsules at their own tabs; it does not slide a
    // highlight across unrelated tabs. The short neutral flash is the tap feedback.
    val background by animateColorAsState(
        p.text.copy(alpha = when {
            pressed || tapFlash -> if (p.dark) .16f else .11f
            active -> if (p.dark) .08f else .05f
            else -> 0f
        }), tween(if (pressed || tapFlash) 70 else 220, easing = FastOutSlowInEasing), label = "menu_background_$id"
    )
    val tint by animateColorAsState(if (active) p.primary else p.text.copy(alpha = .4f), tween(180), label = "menu_tint_$id")
    val scale by animateFloatAsState(if (pressed) .94f else if (tapFlash) .985f else 1f,
        if (pressed) tween(80) else spring(dampingRatio = .7f, stiffness = 700f), label = "menu_scale_$id")
    Box(modifier.testTag("tab_$id").semantics { selected = active; role = Role.Tab }
        .clip(RoundedCornerShape(percent = 50)).clickable(interactionSource = interaction, indication = null) { tapSequence++; onClick() }, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize().scale(scale).clip(RoundedCornerShape(percent = 50)).background(background),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
            Glyph(icon, null, tint, 20)
            Text(label, fontSize = 10.sp, letterSpacing = .7.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
private fun Modifier.selectableCompat(selected: Boolean, onClick: () -> Unit) = this.semantics { this.selected = selected; role = Role.Tab }.clickable(onClick = onClick)

// CSS box-shadow excludes the interior. A translucent Material Surface with elevation
// shows its own shadow through the card and produces the grey inset/bevel seen in v25.
internal fun Modifier.outsideShadow(shape: Shape, opacity: Float) = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        setShadowLayer(16.dp.toPx(), 0f, 8.dp.toPx(), android.graphics.Color.argb((opacity * 255).toInt(), 0, 0, 0))
    }
    onDrawWithContent {
        drawContext.canvas.save()
        drawContext.canvas.clipPath(path, ClipOp.Difference)
        drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        drawContext.canvas.restore()
        drawContent()
    }
}
@Composable internal fun Glass(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(16.dp), padding: Int = 24, gap: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().outsideShadow(shape, if (p.dark) .4f else .1f).background(p.glass, shape).border(1.dp, p.border, shape).padding(padding.dp), verticalArrangement = Arrangement.spacedBy(gap.dp), content = content)
}
@Composable internal fun Glyph(resource: Int, description: String?, tint: Color = LocalPalette.current.text, size: Int = 22) {
    Icon(painterResource(resource), contentDescription = description, tint = tint, modifier = Modifier.size(size.dp))
}
@Composable internal fun SmallButton(icon: Int, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Surface(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).semantics { contentDescription = description; role = Role.Button }, shape = RoundedCornerShape(16.dp), color = p.field, border = BorderStroke(1.dp, p.border)) {
        Box(contentAlignment = Alignment.Center) { Glyph(icon, null) }
    }
}
@Composable internal fun Action(text: String, modifier: Modifier = Modifier, icon: Int? = null, primary: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Surface(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = !busy, onClick = onClick).semantics { role = Role.Button }, shape = RoundedCornerShape(16.dp), color = if (primary) p.primary else p.field, border = if (primary) null else BorderStroke(1.dp, p.border)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            val color = if (primary) Color.White else p.text
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = color, strokeWidth = 2.dp) else icon?.let { Glyph(it, null, color, 18) }
            Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
@Composable private fun Label(text: String) { Text(text.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp, color = LocalPalette.current.muted, modifier = Modifier.padding(start = 4.dp)) }
@Composable internal fun Numeric(label: String, key: String, form: Form, onEdit: (String, String) -> Unit, modifier: Modifier = Modifier, suffix: String = "₽", integer: Boolean = false, enabled: Boolean = true, calculated: Boolean = false, lockCalculated: Boolean = false, onFocused: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val navigation = LocalFormNavigation.current
    val next = navigation?.next(key)
    val inputView = LocalView.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, next) {
        if (focused) {
            onFocused?.invoke()
            // Refresh native EditorInfo after the focused field's IME options are applied.
            // restartInput updates the action without hiding the keyboard window.
            withFrameNanos { }
            val inputMethod = inputView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            inputMethod.restartInput(inputView)
        }
    }
    val fieldStyle = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (calculated) p.primary else p.text)
    val measurer = rememberTextMeasurer()
    val suffixX = with(LocalDensity.current) { measurer.measure(NumberGrouping.filter(AnnotatedString(form.text(key))).text, fieldStyle).size.width.toDp() + 4.dp }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) { if (focused) navigation?.bringIntoView?.get(key)?.bringIntoView() }
    Column(modifier.then(navigation?.bringIntoView?.get(key)?.let { Modifier.bringIntoViewRequester(it) } ?: Modifier), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label)
        BasicTextField(value = form.text(key), onValueChange = { raw ->
            val value = raw.replace(',', '.').replace(" ", "").replace("\u00a0", "")
            val pattern = if (integer) Regex("[0-9]{0,3}") else Regex("[0-9]{0,12}(\\.[0-9]{0,2})?")
            if (pattern.matches(value)) onEdit(key, value)
        }, enabled = enabled && !(calculated && lockCalculated), singleLine = true, visualTransformation = NumberGrouping,
            textStyle = fieldStyle,
            keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal, imeAction = if (next == null) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { next?.let { navigation?.focus(it) } }, onDone = { focus.clearFocus(); keyboard?.hide() }), cursorBrush = SolidColor(p.primary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(navigation?.requesters?.get(key)?.let { Modifier.focusRequester(it) } ?: Modifier).onFocusChanged { focused = it.isFocused }.testTag("field_$key").semantics { contentDescription = label; if (calculated) stateDescription = "Рассчитано автоматически" }
                .clip(RoundedCornerShape(12.dp)).background(if (!enabled || calculated && lockCalculated) p.field.copy(alpha = p.field.alpha * .5f) else Color.Transparent).border(1.dp, if (focused) p.primary else if (calculated) p.primary.copy(alpha = .4f) else if (enabled) p.border else p.border.copy(alpha = p.border.alpha * .5f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BoxWithConstraints(Modifier.weight(1f)) {
                        inner()
                        if (form.text(key).isNotEmpty() && suffixX < maxWidth - 20.dp) Text(suffix, color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterStart).offset(x = suffixX))
                    }
                    if (form.text(key).isNotEmpty() && enabled && !(calculated && lockCalculated)) Box(Modifier.size(24.dp).clickable { onEdit(key, "") }.semantics { contentDescription = "Очистить $label" }, contentAlignment = Alignment.Center) { Glyph(R.drawable.lucide_x, null, p.muted, 13) }
                }
            })
    }
}

private val NumberGrouping = VisualTransformation { input ->
    val integerLength = input.text.indexOf('.').let { if (it < 0) input.length else it }
    val insertions = (1 until integerLength).filter { (integerLength - it) % 3 == 0 }
    val text = buildString { input.text.forEachIndexed { index, c -> if (index in insertions) append(' '); append(c) } }
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = offset + insertions.count { it <= offset }
        override fun transformedToOriginal(offset: Int): Int {
            var result = offset
            insertions.forEachIndexed { index, original -> if (offset > original + index) result-- }
            return result.coerceIn(0, input.length)
        }
    }
    TransformedText(AnnotatedString(text), mapping)
}

@Composable private fun CalculatorForm(state: UiState, model: AppViewModel) {
    val p = LocalPalette.current
    val loan = state.tab == "loan"
    val form = state.form
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val keys = listOf("amount", "rate", "term", "fixed", "commission", "payment", "total", "down", "final", "addition", "paymentType")
    val requesters = remember(state.formKey) { keys.associateWith { FocusRequester() } }
    val bringIntoView = remember(state.formKey) { keys.associateWith { BringIntoViewRequester() } }
    val order = when {
        loan && state.reverse -> (if (form.text("paymentType").isBlank()) listOf("amount") else listOf("amount", "term", "payment", "total", "down")).filter { it != form.target }
        loan -> listOf("amount", "rate", "term", "fixed", "commission")
        else -> listOf("amount") + (if (state.reverse) listOf("final") else emptyList()) + listOf("rate", "term") + (if (form.text("period") != "none") listOf("addition") else emptyList())
    }
    val navigation = FormNavigation(order, requesters, bringIntoView, scope, keyboard)
    val requested = model.inputFocusRequest
    LaunchedEffect(requested, state.formKey) {
        if (requested != null && requested.formKey == state.formKey) {
            try { navigation.focusAndReveal(requested.field) }
            finally { model.consumeInputFocus(requested.sequence) }
        }
    }
    CompositionLocalProvider(LocalFormNavigation provides navigation) {
        AttachedPanel(listOf("Прямой", "Обратный"), if (state.reverse) 1 else 0, { index -> focus.clearFocus(); model.mode(index == 1) }) {
            if (state.reverse && state.settings.hints) {
                Surface(Modifier.fillMaxWidth().clickable { model.settings(state.settings.copy(hints = false)); model.notify("Подсказки можно включить в меню «Инфо»") }, color = p.primary.copy(alpha = .06f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, p.primary.copy(alpha = .2f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (loan) "Обратный расчет позволяет вычислить номинальную и эффективную ставку кредита. Укажите сумму и тип платежа, затем любые два из трёх параметров: срок, платёж, сумма возврата." else "Обратный расчет позволяет вычислить параметры депозита. Заполните любые три поля: сумма, итоговая сумма, ставка, срок. Четвёртое рассчитается автоматически.", fontSize = 12.sp, fontStyle = FontStyle.Italic, color = p.primary)
                        Text("НАЖМИТЕ, ЧТОБЫ СКРЫТЬ ПОДСКАЗКУ", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = p.primary)
                    }
                }
            }
            Numeric(if (loan) "Сумма кредита (₽)" else "Сумма депозита (₽)", "amount", form, model::edit, calculated = !loan && state.reverse && form.target == "amount")
            if (loan && state.reverse) {
                Column(Modifier.focusRequester(requesters.getValue("paymentType")).bringIntoViewRequester(bringIntoView.getValue("paymentType")).focusable(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Label("Тип ежемесячного платежа")
                listOf("annuity" to "Аннуитетный — фиксированный", "differentiated" to "Дифференцированный — уменьшаемый").forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, p.border, RoundedCornerShape(12.dp)).clickable { model.paymentType(key) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(16.dp).border(2.dp, if (form.text("paymentType") == key) p.primary else p.muted.copy(alpha = .4f), CircleShape), contentAlignment = Alignment.Center) { if (form.text("paymentType") == key) Box(Modifier.size(8.dp).background(p.primary, CircleShape)) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(label, fontSize = 11.sp, color = p.text)
                            Text(LOAN_PAYMENT_DESCRIPTIONS.getValue(key), fontSize = 11.sp, lineHeight = 16.sp, color = p.muted)
                        }
                    }
                }
                }
                if (form.text("paymentType").isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Numeric("Срок (мес.)", "term", form, model::edit, Modifier.weight(1f), "мес.", true, calculated = form.target == "term", lockCalculated = true, onFocused = { model.reverseLoanAccess() })
                    Numeric(if (form.text("paymentType") == "differentiated") "Первый платёж (₽)" else "Платёж (₽)", "payment", form, model::edit, Modifier.weight(1f), calculated = form.target == "payment", lockCalculated = true, onFocused = { model.reverseLoanAccess() })
                }
                Numeric("Сумма возврата (₽)", "total", form, model::edit, calculated = form.target == "total", lockCalculated = true, onFocused = { model.reverseLoanAccess() })
                Numeric("Первоначальный взнос (₽)", "down", form, model::edit)
                }
            } else {
                if (!loan && state.reverse) Numeric("Итоговая сумма (₽)", "final", form, model::edit, calculated = form.target == "final")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Numeric("Ставка (%)", "rate", form, model::edit, Modifier.weight(1f), "%", calculated = !loan && state.reverse && form.target == "rate")
                    Numeric("Срок (мес.)", "term", form, model::edit, Modifier.weight(1f), "мес.", true, calculated = !loan && state.reverse && form.target == "term")
                }
                if (loan) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Numeric("Комиссия (₽)", "fixed", form, model::edit, Modifier.weight(1f))
                        Numeric("Комиссия (%)", "commission", form, model::edit, Modifier.weight(1f), "%")
                    }
                    Text("*Расчет производится для обоих типов платежей.", fontSize = 13.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, color = p.primary, modifier = Modifier.padding(start = 4.dp, top = 1.dp))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OptionField("Пополнение", form.text("period"), ADD_OPTIONS, Modifier.weight(1f)) { model.edit("period", it) }
                        Numeric("Сумма взноса (₽)", "addition", form, model::edit, Modifier.weight(1f), enabled = form.text("period") != "none")
                    }
                    OptionField("Капитализация процентов", form.text("cap"), CAP_OPTIONS) { model.edit("cap", it) }
                }
            }
            form.autoError?.let { Text(it, color = Color(0xFFF43F5E), fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Рассчитать", Modifier.weight(1f).testTag("calculate"), R.drawable.lucide_calculator, busy = state.calculating) { if (model.calculate()) { focus.clearFocus(); keyboard?.hide() } }
                SmallButton(R.drawable.lucide_trash_2, "Сбросить параметры") { focus.clearFocus(); model.reset() }
            }
        }
    }
}

@Composable private fun OptionField(label: String, value: String, options: List<Pair<String, String>>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label)
        Surface(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { open = true }.semantics { contentDescription = label; role = Role.DropdownList }, shape = RoundedCornerShape(12.dp), color = Color.Transparent, border = BorderStroke(1.dp, p.border)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(options.firstOrNull { it.first == value }?.second ?: "Выбрать", fontSize = 14.sp, color = p.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Box(Modifier.size(6.dp).background(p.primary, CircleShape))
            }
        }
    }
    if (open) ChoiceDialog(if (label == "Пополнение") "Период пополнения" else "Частота капитализации", value, options, { open = false }) { onSelect(it); open = false }
}

@Composable internal fun Metric(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).semantics { contentDescription = "Информация: $label"; role = Role.Button }.padding(vertical = 4.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(label.uppercase(), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = p.muted, letterSpacing = .4.sp, modifier = Modifier.weight(1f, fill = false)); if (onClick != null) { Spacer(Modifier.width(4.dp)); Glyph(R.drawable.lucide_info, null, p.primary, 12) } }
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (accent) p.primary else p.text)
    }
}
@Composable internal fun MetricPair(left: Pair<String, String>, right: Pair<String, String>, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { Metric(left.first, left.second, Modifier.weight(1f), accent); Metric(right.first, right.second, Modifier.weight(1f), accent) }
}
@Composable internal fun Line() { HorizontalDivider(color = if (LocalPalette.current.dark) Color.White.copy(alpha = .1f) else Color.Black.copy(alpha = .05f)) }

@Composable internal fun CalculationInfoLink(text: String, onClick: () -> Unit) {
    Text(text, color = LocalPalette.current.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick).wrapContentHeight().padding(vertical = 8.dp))
}

@Composable private fun CalculationResult(entry: SavedCalculation, paymentType: String, onPaymentType: (String) -> Unit, onSave: (() -> Unit)? = null, onSchedule: () -> Unit, onExport: (SavedCalculation, Boolean) -> Unit, onDelete: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val c = entry.result
    var rateInfo by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            HorizontalDivider(color = p.border)
            Surface(shape = CircleShape, color = if (p.dark) p.solid else Color.White, border = BorderStroke(1.dp, p.border)) {
                Text("${if (entry.mode == "reverse") "Обратный" else "Прямой"} расчет по ${if (c.isLoan) "кредиту" else "депозиту"}", fontSize = 13.sp, color = p.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
        }
        val tabs = c.isLoan && entry.mode != "reverse"
        val resultContent: @Composable ColumnScope.() -> Unit = {
            if (onSave == null) Text(SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.forLanguageTag("ru-RU")).format(Date(entry.timestamp)), color = p.muted, fontSize = 10.sp)
            c.loan?.let { Text(LOAN_PAYMENT_DESCRIPTIONS.getValue(it.paymentType), color = p.muted, fontSize = 11.sp, lineHeight = 16.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (c.isLoan) "Ежемесячный платеж" else "Итоговая сумма", fontSize = 13.sp, color = p.muted, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = p.primary.copy(alpha = .12f), border = BorderStroke(1.dp, p.primary.copy(alpha = .3f))) {
                    Text(if (!c.isLoan) "Депозит" else if (c.loan!!.paymentType == "annuity") "Фиксированный" else "Уменьшаемый", color = p.primary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Text(money(if (c.isLoan) c.firstPayment else c.finalAmount), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = p.text)
            if (c.loan?.paymentType == "differentiated") Text("до ${money(c.rows.last().payment)}", color = p.muted, fontSize = 11.sp)
            Line()
            if (c.isLoan) {
                val l = c.loan!!
                MetricPair("Переплата" to money(c.totalInterest), "Сумма возврата" to money(c.totalRepayment), true)
                MetricPair("Комиссия" to money(c.totalCommission), "Срок кредита" to "${l.term} мес.")
                MetricPair("Сумма кредита" to money(l.amount), "Получено на руки" to money(c.amountReceived))
                if (l.downPayment > 0) Metric("Первоначальный взнос", money(l.downPayment))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Номинальная ставка", percent(l.rate), Modifier.weight(1f), true) { rateInfo = "nominal" }
                    Metric("Эффективная ставка", percent(c.effectiveRate), Modifier.weight(1f), true) { rateInfo = "effective" }
                }
                Line()
                Text("Переплата по кредиту составит ${percent(c.totalInterest / (l.amount - l.downPayment) * 100)} от суммы кредита.", color = p.primary, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                CalculationInfoLink("Как рассчитана номинальная ставка") { rateInfo = "nominal" }
                CalculationInfoLink("Как рассчитана эффективная ставка") { rateInfo = "effective" }
            } else {
                val d = c.deposit!!
                MetricPair("Депозит" to money(d.initialAmount), "Ставка / Срок" to "${percent(d.rate)} | ${d.term} мес.")
                MetricPair("Пополнения" to money(c.totalAdditions), "Доход" to money(c.totalInterest), true)
                Metric("Эффективная годовая ставка", percent(c.effectiveRate), accent = true) { rateInfo = "deposit" }
                Line()
                Text("Доходность с учетом сложного процента составит ${percent(if (d.initialAmount + c.totalAdditions > 0) c.totalInterest / (d.initialAmount + c.totalAdditions) * 100 else 0.0)} за весь период.", color = p.primary, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                CalculationInfoLink("Как рассчитана ставка депозита") { rateInfo = "deposit" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Action(if (c.isLoan) "График платежей" else "График начислений", Modifier.weight(1f), onClick = onSchedule)
                if (onSave != null) SmallButton(R.drawable.lucide_save, "Сохранить расчёт", onSave)
                SmallButton(R.drawable.lucide_share_2, "Отправить PDF") { onExport(entry, false) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Сохранить PDF", Modifier.weight(1f), R.drawable.lucide_download, primary = false) { onExport(entry, true) }
                if (onDelete != null) SmallButton(R.drawable.lucide_trash_2, "Удалить расчёт", onDelete)
            }
        }
        if (tabs) AttachedPanel(listOf("Аннуитетный", "Дифференцированный"), if (paymentType == "annuity") 0 else 1, { onPaymentType(if (it == 0) "annuity" else "differentiated") }, resultContent)
        else Glass(content = resultContent)
    }
    rateInfo?.let { type -> RateInfoDialog(type, c) { rateInfo = null } }
}

@Composable private fun HistoryScreen(history: List<SavedCalculation>, onDelete: (String) -> Unit, onSchedule: (SavedCalculation) -> Unit, onExport: (SavedCalculation, Boolean) -> Unit) {
    val p = LocalPalette.current
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().background(p.field, RoundedCornerShape(12.dp)).border(1.dp, p.border, RoundedCornerShape(12.dp)).padding(4.dp)) {
        listOf("deposit" to "Депозит", "loan" to "Кредит", "leasing" to "Лизинг").forEach { (key, label) ->
            val count = history.count { it.type == key }
            val active = filter == key
            Row(Modifier.weight(1f).testTag("history_filter_$key").clip(RoundedCornerShape(8.dp)).background(if (active) p.primary else Color.Transparent).selectableCompat(active) { filter = if (active) null else key }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else p.muted)
                if (count > 0) Box(Modifier.background(if (active) Color.Black.copy(alpha = .1f) else p.primary.copy(alpha = .1f), RoundedCornerShape(8.dp)).border(1.dp, p.primary.copy(alpha = .2f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(count.toString(), color = if (active) Color.White else p.primary, fontSize = 10.sp)
                }
            }
        }
    }
    val entries = history.filter { filter == null || it.type == filter }.sortedByDescending { it.timestamp }
    if (entries.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(80.dp).background(p.field, CircleShape), contentAlignment = Alignment.Center) { Glyph(R.drawable.lucide_bookmark, null, p.muted.copy(alpha = .4f), 40) }
        Text("История расчетов пуста", color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        entries.forEach { entry -> key(entry.id) {
            val c = entry.result
            val accent = if (c.isLoan) Color(0xFFF43F5E) else if (c.isLeasing) p.primary else Color(0xFF10B981)
            Glass(Modifier.testTag("history_entry_${entry.type}").drawWithContent {
                drawContent()
                val path = Path().apply { addOutline(RoundedCornerShape(16.dp).createOutline(size, layoutDirection, this@drawWithContent)) }
                drawContext.canvas.save(); drawContext.canvas.clipPath(path)
                drawRect(accent, size = Size(4.dp.toPx(), size.height)); drawContext.canvas.restore()
            }, padding = 20) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.forLanguageTag("ru-RU")).format(Date(entry.timestamp)).uppercase(), color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = .7.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Box(Modifier.background(accent, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(if (c.isLoan) "КРЕДИТ" else if (c.isLeasing) "ЛИЗИНГ" else "ДЕПОЗИТ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ИТОГОВАЯ СУММА", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = .5.sp)
                    Text(if (c.isLeasing) leaseMoney(c.totalRepayment) else money(if (c.isLoan) c.totalRepayment else c.finalAmount), fontSize = 30.sp, fontWeight = FontWeight.Black, color = p.text, letterSpacing = (-.8).sp)
                }
                HorizontalDivider(color = accent.copy(alpha = .2f))
                if (c.isLeasing) {
                    val l = c.leasing!!
                    MetricPair("Предмет лизинга" to leaseMoney(l.amount), "Аванс" to leaseMoney(l.advanceAmount))
                    MetricPair("Эквивалент кредита" to percent(c.nominalRate), "Эффективная ставка" to percent(c.effectiveRate), true)
                    MetricPair("Срок / Выкуп" to "${l.maturity} мес. / ${leaseMoney(l.buyout)}", "Переплата" to leaseMoney(c.totalInterest))
                } else if (c.isLoan) {
                    val l = c.loan!!
                    MetricPair("Кредит" to money(l.amount), "Ставка / Срок" to "${percent(l.rate)} | ${l.term} мес.")
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Metric(if (l.paymentType == "annuity") "Платёж" else "Первый платёж", money(c.firstPayment), Modifier.weight(1f))
                        CompositionLocalProvider(LocalPalette provides p.copy(primary = accent)) { Metric("Переплата", money(c.totalInterest), Modifier.weight(1f), true) }
                    }
                } else {
                    val d = c.deposit!!
                    MetricPair("Депозит" to money(d.initialAmount), "Ставка / Срок" to "${percent(d.rate)} | ${d.term} мес.")
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Metric("Пополнения", money(c.totalAdditions), Modifier.weight(1f))
                        CompositionLocalProvider(LocalPalette provides p.copy(primary = accent)) { Metric("Доход", money(c.totalInterest), Modifier.weight(1f), true) }
                    }
                }
                HorizontalDivider(color = accent.copy(alpha = .2f))
                Text(if (c.isLeasing) "${if (entry.mode == "reverse") "Обратный" else "Прямой"} расчёт. Все суммы с НДС, без налоговых вычетов." else if (c.isLoan) "Переплата по кредиту составит ${percent(c.totalInterest / c.loan!!.amount * 100)} от первоначальной суммы." else "Доходность с учетом сложного процента составит ${percent(c.totalInterest / (c.deposit!!.initialAmount + c.totalAdditions) * 100)} за весь период.", color = accent, fontSize = 11.sp, lineHeight = 14.sp, fontStyle = FontStyle.Italic)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlineAction(if (c.isLeasing) "Платежи" else if (c.isLoan) "График платежей" else "График начислений", Modifier.weight(1f)) { onSchedule(entry) }
                    OutlineAction("Отправить PDF", icon = R.drawable.lucide_share_2) { onExport(entry, false) }
                    OutlineAction("Удалить расчёт", icon = R.drawable.lucide_trash_2) { onDelete(entry.id) }
                }
            }
        } }
    }
}

@Composable private fun OutlineAction(text: String, modifier: Modifier = Modifier, icon: Int? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(modifier.then(if (icon != null) Modifier.width(56.dp) else Modifier).heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).border(2.dp, if (p.dark) Color.White.copy(alpha = .1f) else Color(0xFFE2E8F0), RoundedCornerShape(18.dp)).clickable(onClick = onClick).semantics { role = Role.Button; if (icon != null) contentDescription = text }.padding(4.dp), contentAlignment = Alignment.Center) {
        if (icon != null) Glyph(icon, null, Color(0xFF94A3B8), 24)
        else Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color(0xFF94A3B8))
    }
}

@Composable private fun ScheduleScreen(entry: SavedCalculation, onClose: () -> Unit, onExport: (SavedCalculation, Boolean) -> Unit) {
    val p = LocalPalette.current
    val c = entry.result
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let { WindowCompat.getInsetsController(it, view).apply { isAppearanceLightStatusBars = !p.dark; isAppearanceLightNavigationBars = !p.dark } } }
        Box(Modifier.fillMaxSize().background(p.solid).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 800.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).background(if (p.dark) p.solid else Color.White.copy(alpha = .8f)).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScheduleButton(R.drawable.lucide_arrow_left, "Назад", onClose)
                    Text(if (c.isLoan) "График платежей" else "График начислений", Modifier.weight(1f).padding(horizontal = 20.dp), color = p.text, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ScheduleButton(R.drawable.lucide_share_2, "Отправить PDF") { onExport(entry, false) }
                }
                HorizontalDivider(color = p.border.copy(alpha = .05f))
                val summary = if (c.isLoan) {
                    val l = c.loan!!
                    buildList {
                        add(Triple("Сумма кредита", money(l.amount), false)); add(Triple("Ставка и Срок", "${percent(l.rate)} | ${l.term} мес.", false))
                        if (c.totalCommission > 0) { add(Triple("Комиссия", money(c.totalCommission), false)); add(Triple("Проценты", money(c.totalInterest - c.totalCommission), false)) }
                        add(Triple("Переплата", money(c.totalInterest), true)); add(Triple("Сумма возврата", money(c.totalRepayment), true))
                    }
                } else {
                    val d = c.deposit!!
                    listOf(Triple("Сумма вклада", money(d.initialAmount), false), Triple("Пополнения", if (c.totalAdditions > 0) money(c.totalAdditions) else "-", false), Triple("Ставка и Срок", "${percent(d.rate)} | ${d.term} мес.", false), Triple("% за период", percent(c.totalInterest / (d.initialAmount + c.totalAdditions) * 100), true), Triple("Итоговая сумма", money(c.finalAmount), true), Triple("Доход", money(c.totalInterest), true))
                }
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    summary.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (label, value, accent) ->
                            Column(Modifier.weight(1f).background(p.field, RoundedCornerShape(12.dp)).border(1.dp, if (p.dark) Color.White.copy(alpha = .05f) else Color(0xFFF1F5F9), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(label.uppercase(), color = Color(0xFF94A3B8), fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                                Text(value, color = if (accent) p.primary else p.text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } }
                }
                LazyColumn(Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, bottom = 16.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, if (p.dark) Color.White.copy(alpha = .1f) else Color(0xFFF1F5F9), RoundedCornerShape(8.dp)).testTag("schedule")) {
                    stickyHeader {
                        Row(Modifier.fillMaxWidth().background(p.solid).padding(vertical = 14.dp)) {
                            Text("№", color = p.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
                            Text(if (c.isLoan) "ПЛАТЕЖ / ОСТАТОК" else "ПРОЦЕНТ / ВЗНОС", color = p.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp, modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
                            Text(if (c.isLoan) "ПРОЦ. / ОСН." else "БАЛАНС", color = p.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp, modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
                        }
                        HorizontalDivider(color = p.border.copy(alpha = .1f))
                    }
                    items(c.rows, key = { it.month }) { row ->
                        Row(Modifier.fillMaxWidth().background(if (p.dark) { if (row.month % 2 == 0) Color.White.copy(alpha = .01f) else p.solid } else { if (row.month % 2 == 0) Color(0xFFF8FAFC) else Color.White }).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.month.toString(), color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(if (c.isLoan) money(row.payment) else if (row.interest > 0) "+${money(row.interest)}" else "-", fontSize = if (c.isLoan) 12.sp else 11.sp, lineHeight = 16.sp, fontWeight = if (c.isLoan) FontWeight.Normal else FontWeight.Bold, color = if (c.isLoan) p.text else p.primary)
                                Text(if (c.isLoan) "ост. ${money(row.balance)}" else if (row.addition > 0) "+${money(row.addition)}" else "-", fontSize = if (c.isLoan) 10.sp else 11.sp, lineHeight = 14.sp, color = p.muted, fontStyle = if (c.isLoan) FontStyle.Italic else FontStyle.Normal)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(money(if (c.isLoan) row.interest else row.balance), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (c.isLoan) FontWeight.Normal else FontWeight.Bold, color = if (c.isLoan) p.primary else p.text)
                                if (c.isLoan) Text(money(row.principal), fontSize = 12.sp, lineHeight = 16.sp, color = p.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun ScheduleButton(icon: Int, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.field).border(1.dp, if (p.dark) Color.White.copy(alpha = .1f) else Color(0xFFE2E8F0).copy(alpha = .5f), RoundedCornerShape(12.dp)).clickable(onClick = onClick).semantics { contentDescription = description; role = Role.Button }, contentAlignment = Alignment.Center) { Glyph(icon, null, p.primary, 20) }
}

@Composable private fun InfoScreen(settings: Settings, onSettings: (Settings) -> Unit, onLink: (String) -> Unit, onShareApp: () -> Unit) {
    val p = LocalPalette.current
    var privacy by rememberSaveable { mutableStateOf(false) }
    var licenses by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.padding(start = 4.dp))
        Glass(gap = 24) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label("Тема оформления")
                Row(Modifier.fillMaxWidth().background(p.field, RoundedCornerShape(12.dp)).border(1.dp, p.border, RoundedCornerShape(12.dp)).padding(4.dp)) {
                    listOf("light" to "Светлая", "dark" to "Темная").forEach { (key, label) ->
                        val active = settings.theme == key || settings.theme == "system" && (key == "dark") == p.dark
                        Row(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(if (active) p.primary else Color.Transparent).selectableCompat(active) { onSettings(settings.copy(theme = key)) }, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                            Glyph(if (key == "light") R.drawable.lucide_sun else R.drawable.lucide_moon, null, if (active) Color.White else Color(0xFF94A3B8), 18)
                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else if (p.dark) Color.White else Color(0xFF94A3B8))
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Label("Цветовая схема")
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    SKINS.forEach { (key, color) ->
                        val active = settings.skin == key
                        Box(Modifier.size(40.dp).scale(if (active) 1.1f else 1f).then(if (active) Modifier.drawBehind { drawRoundRect(if (p.dark) Color.White.copy(alpha = .1f) else Color(0xFFE5E5E5), topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()), size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()), cornerRadius = CornerRadius(16.dp.toPx())) } else Modifier).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = if (active) 1f else .4f)).clickable { onSettings(settings.copy(skin = key)) }.semantics { contentDescription = when (key) { "emerald" -> "Изумрудная"; "blue" -> "Синяя"; "amber" -> "Янтарная"; "rose" -> "Розовая"; else -> "Фиолетовая" }; selected = active })
                    }
                }
            }
            Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(48.dp).height(24.dp).clip(CircleShape).background(if (settings.hints) p.primary else if (p.dark) Color.White.copy(alpha = .1f) else Color(0xFFE2E8F0)).toggleable(settings.hints, role = Role.Switch) { onSettings(settings.copy(hints = it)) }.padding(4.dp), contentAlignment = if (settings.hints) Alignment.CenterEnd else Alignment.CenterStart) { Box(Modifier.size(16.dp).background(Color.White, CircleShape)) }
                Text("ПОДСКАЗКИ В ПРИЛОЖЕНИИ", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, color = p.muted)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("О приложении", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.padding(start = 4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard { Text("DepoCred — финансовый инструмент для расчёта кредитов, вкладов и лизинга. Сравнивайте условия, сохраняйте расчёты и создавайте PDF-отчёты.", fontSize = 14.sp, lineHeight = 21.sp, color = p.muted) }
            InfoCard { InfoBlock(R.drawable.lucide_shield, "Конфиденциальность", "Все расчеты хранятся локально на вашем устройстве. Мы не собираем финансовые данные.", Color(0xFF10B981)) }
            InfoCard { InfoBlock(R.drawable.lucide_sparkles, "Умные алгоритмы", "Точные расчеты аннуитетных и дифференцированных платежей, а также сложного банковского процента.", Color(0xFF3B82F6)) }
            InfoCard { InfoBlock(R.drawable.lucide_smartphone, "Платформа", "Полная адаптация под смартфоны. Удобный ввод и наглядная детализация расчетов. Возможность сохранять и делиться расчетами.", Color(0xFF8B5CF6)) }
        }
    }
    Column {
        HorizontalDivider(color = p.primary.copy(alpha = .2f), modifier = Modifier.padding(vertical = 16.dp))
        LinkRow(R.drawable.lucide_globe, "depocred.ru") { onLink("https://depocred.ru") }
        LinkRow(R.drawable.lucide_mail, "support@depocred.ru") { onLink("mailto:support@depocred.ru") }
        LinkRow(R.drawable.lucide_share_2, "Поделиться приложением", onShareApp)
        LinkRow(R.drawable.lucide_star, "Поставить оценку") { onLink("https://play.google.com/store/apps/details?id=ru.depocred.app") }
        LinkRow(R.drawable.lucide_shield, "Политика конфиденциальности") { privacy = true }
        LinkRow(R.drawable.lucide_info, "Открытые лицензии") { licenses = true }
        HorizontalDivider(color = p.primary.copy(alpha = .2f), modifier = Modifier.padding(vertical = 16.dp))
    }
    Text("ВЕРСИЯ ${BuildConfig.VERSION_NAME}\n\n• • •\n\nСделано с ❤️ для вашей финансовой свободы", color = p.muted, fontSize = 10.sp, lineHeight = 16.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (privacy) AppDialog("Конфиденциальность", { privacy = false }, confirm = "ЗАКРЫТЬ") {
        Text("DepoCred работает без подключения к интернету, не содержит рекламы, аналитики и регистрации. Настройки и последние 10 явно сохранённых расчётов находятся во внутреннем хранилище устройства. Введённые поля сохраняются при сворачивании, но очищаются после полного закрытия приложения.\n\nPDF передаётся другому приложению только после вашего действия. Получатель получает временный доступ к выбранному файлу.\n\nОбновление с той же подписью сохраняет данные. Удаление приложения или очистка его данных удаляет локальную историю. Сохранённые вами во внешнюю папку PDF остаются в этой папке.\n\nКонтакт: support@depocred.ru", color = p.muted, fontSize = 14.sp, lineHeight = 22.sp)
    }
    if (licenses) {
        val text = remember { listOf("Third-party-notices.txt", "Inter-OFL.txt", "Lucide-LICENSE.txt", "Apache-2.0.txt").joinToString("\n\n") { file -> context.assets.open(file).bufferedReader().use { it.readText() } } }
        AppDialog("Открытые лицензии", { licenses = false }, confirm = "ЗАКРЫТЬ") { Text(text, fontSize = 12.sp, lineHeight = 18.sp, color = p.muted) }
    }
}
@Composable private fun InfoCard(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().background(p.field, RoundedCornerShape(16.dp)).border(1.dp, p.border, RoundedCornerShape(16.dp)).padding(horizontal = 24.dp, vertical = 20.dp)) { content() }
}
@Composable private fun InfoBlock(icon: Int, title: String, text: String, accent: Color) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(accent.copy(alpha = .1f), RoundedCornerShape(8.dp)).border(1.dp, accent.copy(alpha = .2f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Glyph(icon, null, accent) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title.uppercase(), color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(text, color = p.muted, fontSize = 13.sp, lineHeight = 19.sp) }
    }
}
@Composable private fun LinkRow(icon: Int, text: String, action: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = action).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Glyph(icon, null, p.muted, 20); Text(text, color = p.muted, fontSize = 14.sp) }
}

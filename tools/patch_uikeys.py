import io

p = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\src\main\java\io\paimon\fx991\ui\CalcScreen.kt"
s = io.open(p, encoding="utf-8").read()

# ---------- imports ----------
add_imports = """import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.isCtrlPressed
import kotlinx.coroutines.delay
"""
for line in add_imports.strip().splitlines():
    if line not in s:
        s = s.replace("import androidx.compose.foundation.BorderStroke\n",
                      "import androidx.compose.foundation.BorderStroke\n" + line + "\n", 1)

# ---------- Ctrl+Z / Ctrl+Y ----------
old = """                Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val a: KeyAction? = when (ev.key) {"""
new = """                Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    // 输入体验重构：Ctrl+Z / Ctrl+Y（外接键盘的撤销重做）
                    if (ev.isCtrlPressed) {
                        when (ev.key) {
                            ComposeKey.Z -> { vm.undoEdit(); return@onPreviewKeyEvent true }
                            ComposeKey.Y -> { vm.redoEdit(); return@onPreviewKeyEvent true }
                        }
                    }
                    val a: KeyAction? = when (ev.key) {"""
assert old in s
s = s.replace(old, new, 1)

# ---------- StatusBar: 撤销 / 重做 ----------
old = """            StatusChip(vm.displayMode.shortLabel, c, true)
        }
    }
}"""
new = """            StatusChip(vm.displayMode.shortLabel, c, true)
            // 输入体验重构：撤销 / 重做（点按生效，长按无）
            Text(
                "↶",
                color = if (vm.canUndo) c.lcdFg else c.lcdDim.copy(alpha = 0.28f),
                fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = vm.canUndo) { vm.undoEdit() }
                    .padding(horizontal = 2.dp, vertical = 1.dp),
            )
            Text(
                "↷",
                color = if (vm.canRedo) c.lcdFg else c.lcdDim.copy(alpha = 0.28f),
                fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = vm.canRedo) { vm.redoEdit() }
                    .padding(horizontal = 2.dp, vertical = 1.dp),
            )
        }
    }
}"""
assert old in s
s = s.replace(old, new, 1)

# ---------- KeyCapButton：按下即触发 + 长按连发 ----------
old = """@Composable
private fun KeyCapButton(key: Key, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalCalcColors.current
    val (bg, fg) = keyFace(key.kind, c)
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 5.dp),
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, c.keyEdge),
    ) {"""
new = """@Composable
private fun KeyCapButton(key: Key, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalCalcColors.current
    val (bg, fg) = keyFace(key.kind, c)
    var pressed by remember { mutableStateOf(false) }
    val repeatable = key.action.isRepeatable()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "keyScale")
    // 长按连发：按下即响应一次，380ms 后每 62ms 一次（DEL / 方向键）
    LaunchedEffect(pressed) {
        if (!pressed || !repeatable) return@LaunchedEffect
        delay(380)
        while (pressed) {
            onClick()
            delay(62)
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (pressed) 0.dp else 1.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (pressed) lerp(bg, Color.Black, 0.14f) else bg)
            .border(1.dp, c.keyEdge, RoundedCornerShape(8.dp))
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    onClick()
                    waitForUpOrCancellation()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {"""
assert old in s
s = s.replace(old, new, 1)

# ---------- PadKey（方向键）：按住连续走 ----------
old = """    Button(
        onClick = { click(Key(glyph, KeyKind.NAV, action)) },
        modifier = modifier.fillMaxHeight().padding(1.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (center) c.keyDigit else Color.Transparent,
            contentColor = c.keyNeutralInk,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(glyph, fontSize = if (center) 12.sp else 11.sp, color = c.keyNeutralInk)
    }"""
new = """    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (!pressed || !action.isRepeatable()) return@LaunchedEffect
        delay(380)
        while (pressed) {
            click(Key(glyph, KeyKind.NAV, action))
            delay(62)
        }
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(1.dp)
            .clip(CircleShape)
            .background(
                when {
                    pressed && center -> lerp(c.keyDigit, Color.Black, 0.14f)
                    pressed -> c.keyEdge.copy(alpha = 0.55f)
                    center -> c.keyDigit
                    else -> Color.Transparent
                }
            )
            .pointerInput(action) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    click(Key(glyph, KeyKind.NAV, action))
                    waitForUpOrCancellation()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = if (center) 12.sp else 11.sp, color = c.keyNeutralInk)
    }"""
assert old in s
s = s.replace(old, new, 1)

# ---------- 可连发动作 ----------
s = s.replace(
    "private fun keyFontSize(len: Int) = when {",
    """private fun KeyAction.isRepeatable(): Boolean =
    this is KeyAction.Del || this is KeyAction.PadLeft || this is KeyAction.PadRight ||
        this is KeyAction.PadUp || this is KeyAction.PadDown

private fun keyFontSize(len: Int) = when {""",
    1,
)

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("CalcScreen patched")

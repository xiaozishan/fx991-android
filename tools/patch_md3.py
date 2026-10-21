import io, re

p = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\src\main\java\io\paimon\fx991\ui\CalcScreen.kt"
s = io.open(p, encoding="utf-8").read()

# ---- import MaterialTheme ----
if "import androidx.compose.material3.MaterialTheme" not in s:
    s = s.replace("import androidx.compose.material3.HorizontalDivider",
                  "import androidx.compose.material3.HorizontalDivider\nimport androidx.compose.material3.MaterialTheme", 1)

# ---- keyFace: MD3 色调分层 ----
old_face = """@Composable
private fun keyFace(kind: KeyKind, c: CalcColors): Pair<Color, Color> = when (kind) {
    KeyKind.SHIFT -> c.keyShift to c.keyShiftInk
    KeyKind.ALPHA -> c.keyAlpha to c.keyAlphaInk
    KeyKind.DANGER -> c.keyAccent to c.keyAccentInk
    KeyKind.DIGIT -> c.keyDigit to c.keyDigitInk
    KeyKind.OP -> c.keyOp to c.keyOpInk
    KeyKind.EQUALS -> c.keyEquals to c.keyEqualsInk
    else -> c.keyNeutral to c.keyNeutralInk
}"""
new_face = """@Composable
private fun keyFace(kind: KeyKind, c: CalcColors): Pair<Color, Color> {
    // MD3：键帽按 tonal surface 分层，靠色阶而不是描边区分功能区
    val m = MaterialTheme.colorScheme
    return when (kind) {
        KeyKind.SHIFT -> c.keyShift to c.keyShiftInk
        KeyKind.ALPHA -> c.keyAlpha to c.keyAlphaInk
        KeyKind.DANGER -> c.keyAccent to c.keyAccentInk          // 保留橙红识别色
        KeyKind.DIGIT -> m.surfaceContainerLowest to m.onSurface  // 最亮 = 数字
        KeyKind.OP -> m.surfaceContainer to m.onSurface
        KeyKind.FUNC -> m.surfaceContainerHigh to m.onSurface
        KeyKind.EQUALS -> m.inverseSurface to m.inverseOnSurface  // 反色 = 执行键
        KeyKind.NAV -> m.surfaceContainer to m.onSurface
        else -> m.surfaceContainerHigh to m.onSurface
    }
}"""
assert old_face in s
s = s.replace(old_face, new_face, 1)

# ---- KeyCapButton：整段替换（hold 支持 + MD3 外观） ----
start = s.index("@Composable\nprivate fun KeyCapButton(")
end = s.index("private fun KeyAction.isRepeatable()", start)
old_block = s[start:end]

new_block = '''@Composable
private fun KeyCapButton(
    key: Key,
    modifier: Modifier,
    onClick: () -> Unit,
    onHold: (() -> Unit)? = null,
) {
    val c = LocalCalcColors.current
    val (bg, fg) = keyFace(key.kind, c)
    val shape = RoundedCornerShape(14.dp)          // MD3 medium 形状
    var pressed by remember { mutableStateOf(false) }
    var holdFired by remember { mutableStateOf(false) }
    val repeatable = key.action.isRepeatable()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "keyScale")

    // 长按层：有备用键 → 420ms 呼出备用键（主键不触发）；否则 DEL / 方向键连发
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        when {
            onHold != null -> {
                delay(420)
                if (pressed) {
                    holdFired = true
                    onHold()
                }
            }
            repeatable -> {
                delay(380)
                while (pressed) {
                    onClick()
                    delay(62)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (pressed) lerp(bg, fg, 0.12f) else bg)   // MD3 state layer
            .border(0.5.dp, c.keyEdge, shape)
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    holdFired = false
                    if (onHold == null) onClick()
                    waitForUpOrCancellation()
                    pressed = false
                    if (onHold != null && !holdFired) onClick()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (key.shift != null || key.alpha != null) {
                // 批次 K4：橙键（DANGER）上的 SHIFT 橙字会融进底色，改用键面墨色保证可读
                val shiftInk = if (key.kind == KeyKind.DANGER) fg else ShiftOrange
                val alphaInk = if (key.kind == KeyKind.DANGER) fg else AlphaPurple
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    key.shift?.let {
                        Text(it.label, color = shiftInk, fontSize = 7.5.sp, maxLines = 1,
                            fontWeight = FontWeight.Bold)
                    }
                    key.alpha?.let {
                        Text(it.label, color = alphaInk, fontSize = 7.5.sp, maxLines = 1,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (key.icon != KeyIcon.NONE) {
                KeyGlyph(key.icon, fg, 19.dp)
            } else {
                Text(
                    text = key.label,
                    color = fg,
                    fontFamily = Mono,
                    fontSize = keyFontSize(key.label.length) * key.labelScale,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // 长按备用键提示点（右下角一个小点）
        if (onHold != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 5.dp, end = 5.dp)
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(fg.copy(alpha = 0.38f))
            )
        }
    }
}

'''
s = s[:start] + new_block + s[end:]

# ---- KeypadDeck：传 onHold ----
old_call = """                        KeyCapButton(
                            key = key,
                            modifier = Modifier
                                .weight(key.weight)
                                .fillMaxHeight()
                                .padding(2.dp),
                            onClick = { click(key) }
                        )"""
new_call = """                        KeyCapButton(
                            key = key,
                            modifier = Modifier
                                .weight(key.weight)
                                .fillMaxHeight()
                                .padding(2.dp),
                            onClick = { click(key) },
                            onHold = key.hold?.let { h ->
                                { click(Key(key.label, key.kind, h.action)) }
                            },
                        )"""
assert old_call in s
s = s.replace(old_call, new_call, 1)

# ---- LCD 圆角：MD3 large ----
s = s.replace("    val shape = RoundedCornerShape(6.dp)", "    val shape = RoundedCornerShape(16.dp)", 1)

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("CalcScreen MD3 + hold patched")

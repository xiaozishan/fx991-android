package io.paimon.fx991.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// 批次 K3-B：二级界面的「自然书写输入框」
//   · 默认只读显示排版后的式子；点一下进编辑态 —— 不弹系统输入法，
//     由 App 自己的键盘面板（NatKeyboardPanel）接管输入，带闪烁光标、边输边排版
//   · 多字段焦点：点哪个字段键盘跟哪个，各自的文本与光标互不干扰
//   · 兜底：键盘面板可一键切回系统键盘手打（如 1e-10 习惯写法）
// ---------------------------------------------------------------------------

/** 一个可被键盘面板编辑的字段（文本 + 光标的读写通道） */
class NatFocusTarget(
    val label: String,
    val getText: () -> String,
    val getCursor: () -> Int,
    val setText: (String) -> Unit,
    val setCursor: (Int) -> Unit,
)

/** 二级界面自然输入的共享状态：当前聚焦字段 + 系统键盘兜底开关 */
class NatInput {
    var focused by mutableStateOf<NatFocusTarget?>(null)
        private set

    /** 兜底开关：true 时聚焦字段改用系统键盘（OutlinedTextField） */
    var sysMode by mutableStateOf(false)

    fun focus(t: NatFocusTarget) {
        focused = t
    }

    /** 收起键盘（保留 sysMode 选择） */
    fun dismiss() {
        focused = null
    }

    /** 切界面时全清 */
    fun clear() {
        focused = null
        sysMode = false
    }

    /** 在光标处插入文本；cursorBack 用于模板键把光标留在空位里（如 a/b 留在分子） */
    fun type(text: String, cursorBack: Int = 0) {
        val t = focused ?: return
        val r = CursorModel.insert(t.getText(), t.getCursor(), text)
        t.setText(r.text)
        t.setCursor((r.cursor - cursorBack).coerceIn(0, r.text.length))
    }

    fun backspace() {
        val t = focused ?: return
        val r = CursorModel.backspace(t.getText(), t.getCursor())
        t.setText(r.text)
        t.setCursor(r.cursor)
    }

    fun moveLeft() {
        val t = focused ?: return
        t.setCursor(CursorModel.moveLeft(t.getText(), t.getCursor()))
    }

    fun moveRight() {
        val t = focused ?: return
        t.setCursor(CursorModel.moveRight(t.getText(), t.getCursor()))
    }
}

val LocalNatInput = staticCompositionLocalOf<NatInput?> { null }

/**
 * 自然书写输入框（NumField 的自然书写版）。
 * 非聚焦态：排版后的式子（无光标）；聚焦态：闪烁光标 + 边输边排版，不弹系统输入法。
 */
@Composable
fun NatValueField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    input: NatInput,
    modifier: Modifier = Modifier,
) {
    val c = LocalCalcColors.current
    val cursorState = remember { mutableStateOf(value.length) }
    val valueState = rememberUpdatedState(value)
    val changeState = rememberUpdatedState(onChange)
    val target = remember {
        NatFocusTarget(
            label = label,
            getText = { valueState.value },
            getCursor = { cursorState.value.coerceIn(0, valueState.value.length) },
            setText = { changeState.value(it) },
            setCursor = { cursorState.value = it.coerceIn(0, valueState.value.length) },
        )
    }
    val focused = input.focused === target

    if (focused && input.sysMode) {
        // 兜底：系统键盘手打（如 1e-10 习惯写法）
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label, fontSize = 11.sp) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Mono, fontSize = 14.sp, color = c.bodyInk),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = c.keyNeutral,
                unfocusedContainerColor = c.keyNeutral,
                focusedIndicatorColor = ShiftOrange,
                unfocusedIndicatorColor = c.keyEdge,
                focusedLabelColor = ShiftOrange,
                unfocusedLabelColor = c.bodyInk,
                cursorColor = ShiftOrange,
            ),
            modifier = modifier,
        )
        return
    }

    val safeCursor = cursorState.value.coerceIn(0, value.length)
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.keyNeutral)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) ShiftOrange else c.keyEdge,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable {
                input.focus(target)
                if (cursorState.value > value.length) cursorState.value = value.length
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column {
            Text(
                label,
                fontSize = 9.5.sp,
                maxLines = 1,
                color = if (focused) ShiftOrange else c.keyNeutralInk.copy(alpha = 0.65f),
            )
            Box(Modifier.fillMaxWidth().heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (value.isEmpty() && !focused) {
                        Text(" ", fontFamily = Mono, fontSize = 14.sp, color = c.keyNeutralInk.copy(alpha = 0.4f))
                    } else {
                        NaturalView(
                            expr = value,
                            style = NatStyle(14.sp, c.bodyInk),
                            cursor = if (focused) safeCursor else -1,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 二级界面的自有键盘面板（模板键全量覆盖主行 ins 型按键）
// ---------------------------------------------------------------------------

private enum class PadAct { LEFT, RIGHT, DEL }

private class PadKey(val label: String, val insert: String? = null, val back: Int = 0, val act: PadAct? = null)

/** 主区：数字 / 四则 / 括号 / 常用函数 + 光标移动与退格 */
private val PAD_ROWS: List<List<PadKey>> = listOf(
    listOf(
        PadKey("◀", act = PadAct.LEFT), PadKey("▶", act = PadAct.RIGHT),
        PadKey("a/b", "÷", back = 1), PadKey("√", "√("),
        PadKey("x²", "²", back = 1), PadKey("xʸ", "^", back = 1),
        PadKey("⌫", act = PadAct.DEL),
    ),
    listOf(
        PadKey("7", "7"), PadKey("8", "8"), PadKey("9", "9"),
        PadKey("÷", "÷"), PadKey("(", "("), PadKey(")", ")"), PadKey("x³", "³", back = 1),
    ),
    listOf(
        PadKey("4", "4"), PadKey("5", "5"), PadKey("6", "6"),
        PadKey("×", "×"), PadKey("sin", "sin("), PadKey("cos", "cos("), PadKey("tan", "tan("),
    ),
    listOf(
        PadKey("1", "1"), PadKey("2", "2"), PadKey("3", "3"),
        PadKey("−", "−"), PadKey("ln", "ln("), PadKey("log", "log("), PadKey("x", "x"),
    ),
    listOf(
        PadKey("0", "0"), PadKey(".", "."), PadKey("π", "π"), PadKey("+", "+"),
        PadKey(",", ","), PadKey("=", "="), PadKey("y", "y"),
    ),
)

/** 模板键区（主行 ins 型按键一个不少）：幂/根、对数指数、三角反三角双曲、组合与其他 */
private val PAD_FUNCS: List<PadKey> = listOf(
    PadKey("x⁻¹", "⁻¹"),                 // 光标落在分母槽（x⁻¹ = 1/□）
    PadKey("³√", "cbrt("),
    PadKey("ʸ√x", "root("),
    PadKey("logₓy", "logb("),
    PadKey("10ˣ", "10^"),
    PadKey("eˣ", "exp("),
    PadKey("Exp", "×10^"),
    PadKey("sin⁻¹", "sin⁻¹("),
    PadKey("cos⁻¹", "cos⁻¹("),
    PadKey("tan⁻¹", "tan⁻¹("),
    PadKey("sinh", "sinh("),
    PadKey("cosh", "cosh("),
    PadKey("tanh", "tanh("),
    PadKey("hyp⁻¹", "asinh("),
    PadKey("abs", "abs("),
    PadKey("nPr", "npr("),
    PadKey("nCr", "ncr("),
    PadKey("∠", "∠"),
    PadKey("i", "i"),
    PadKey("e", "e"),
    PadKey("z", "z"),
    PadKey("!", "!"),
    PadKey("%", "%"),
    PadKey("·", "·"),
    PadKey("Ans", "Ans"),
    PadKey("'", "'"),
)

/**
 * 二级界面自有键盘：聚焦某个 NatValueField 时浮在屏幕底部。
 * sysMode 时只留控制行（切回自然键盘 / 完成）。
 */
@Composable
fun NatKeyboardPanel(input: NatInput, modifier: Modifier = Modifier) {
    val c = LocalCalcColors.current
    Column(
        modifier
            .background(c.bodyEdge)
            .border(1.dp, c.keyEdge)
            .safeAreaPadding()
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        if (!input.sysMode) {
            Column(
                Modifier
                    .heightIn(max = 296.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                PAD_ROWS.forEach { PadRow(it, input) }
                PAD_FUNCS.chunked(9).forEach { PadRow(it, input, small = true) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = { input.sysMode = !input.sysMode },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                border = BorderStroke(1.dp, ShiftOrange),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (input.sysMode) ShiftOrange else c.keyNeutral,
                    contentColor = if (input.sysMode) ShiftOrangeInk else ShiftOrange,
                ),
            ) {
                Text(
                    if (input.sysMode) "⌨ 切回自然键盘" else "⌨ 改用系统键盘",
                    fontSize = 12.sp, maxLines = 1,
                )
            }
            Button(
                onClick = { input.dismiss() },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.keyEquals,
                    contentColor = c.keyEqualsInk,
                ),
            ) { Text("完成 ▾", fontSize = 12.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun PadRow(keys: List<PadKey>, input: NatInput, small: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        keys.forEach { PadKeyCap(it, input, small) }
        repeat((if (small) 9 else 7) - keys.size) { Box(Modifier.weight(1f)) }
    }
    Box(Modifier.height(3.dp))
}

@Composable
private fun RowScope.PadKeyCap(k: PadKey, input: NatInput, small: Boolean) {
    val c = LocalCalcColors.current
    val isNav = k.act != null
    Button(
        onClick = {
            when (k.act) {
                PadAct.LEFT -> input.moveLeft()
                PadAct.RIGHT -> input.moveRight()
                PadAct.DEL -> input.backspace()
                else -> k.insert?.let { input.type(it, k.back) }
            }
        },
        modifier = Modifier.weight(1f).height(if (small) 34.dp else 40.dp),
        shape = RoundedCornerShape(7.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        border = BorderStroke(1.dp, c.keyEdge),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isNav) c.keyOp else c.keyDigit,
            contentColor = if (isNav) c.keyOpInk else c.keyDigitInk,
        ),
    ) {
        Text(
            k.label,
            fontFamily = Mono,
            fontSize = if (small) 10.5.sp else 13.sp,
            maxLines = 1,
        )
    }
}

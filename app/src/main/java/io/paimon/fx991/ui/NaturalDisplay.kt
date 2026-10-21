package io.paimon.fx991.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NatStyle(
    val size: TextUnit,
    val color: Color,
    val bold: Boolean = false,
)

private val MonoFont = FontFamily.Monospace

// 分数线 / 根号上横线的粗细、分数两侧留白 —— 与原实现保持一致（不改风格）
private val RuleThickness = 1.5.dp
private val FracPadH = 3.dp

/** 自然书写渲染：分数上下堆叠、√ 带上横线、上标指数（纯 Compose 手绘）。
 *  cursor >= 0 时在对应偏移处画出闪烁竖线光标（批次 K3-A）。 */
@Composable
fun NaturalView(
    expr: String,
    style: NatStyle,
    modifier: Modifier = Modifier,
    cursor: Int = -1,
    cursorExit: Int = 0,
) {
    val node = remember(expr, cursor, cursorExit) {
        if (cursor >= 0) buildNatCursor(expr, cursor, cursorExit) else buildNat(expr)
    }
    Box(modifier) { NatNodeView(node, style, cursor) }
}

/**
 * 闪烁竖线光标（SHIFT 橙）。
 * signal 变化（光标移动 / 文本编辑）时请求把自己滚进可视区 —— 横向滚动的 LCD 行 / 字段里光标不丢。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CursorGlyph(style: NatStyle, signal: Int) {
    val transition = rememberInfiniteTransition(label = "natCursor")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "natCursorAlpha",
    )
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(signal) { requester.bringIntoView() }
    Box(
        Modifier
            .bringIntoViewRequester(requester)
            .width(2.dp)
            .height((style.size.value * 1.25f).coerceAtLeast(13f).dp)
            .background(ShiftOrange.copy(alpha = alpha))
    )
}

@Composable
private fun NatNodeView(node: Nat, style: NatStyle, signal: Int = -1) {
    when (node) {
        is Nat.Sym -> if (node.text.isEmpty()) {
            // 空槽位（分数分子/分母、空指数…）：画一个小方框，提示这里可以输入
            val w = (style.size.value * 0.46f).dp
            val h = (style.size.value * 0.86f).dp
            Box(
                Modifier
                    .padding(horizontal = (style.size.value * 0.07f).dp)
                    .width(w)
                    .height(h)
                    .border(1.dp, style.color.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
            )
        } else Text(
            text = natDisplayName(node.text),
            color = style.color,
            fontFamily = MonoFont,
            fontSize = style.size,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )

        is Nat.Cursor -> CursorGlyph(style, signal)

        is Nat.Row -> Row(verticalAlignment = Alignment.CenterVertically) {
            node.items.forEach { NatNodeView(it, style, signal) }
        }

        is Nat.Frac -> FracView(node, style, signal)

        is Nat.Sqrt -> SqrtView(node, style, signal)

        is Nat.Sup -> SupView(node, style, signal)

        // 批次 K3：定积分模板 —— ∫ 右上是上限、右下是下限，被积式随后，末尾 dx
        is Nat.Integ -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\u222B",
                color = style.color,
                fontFamily = MonoFont,
                fontSize = style.size * 1.5f,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NatNodeView(node.hi, style.copy(size = style.size * 0.68f), signal)
                NatNodeView(node.lo, style.copy(size = style.size * 0.68f), signal)
            }
            NatNodeView(node.body, style, signal)
            // dx 与被积式之间留一点间距：被积式末尾是分数时，dx 不会看起来像分母
            Box(Modifier.padding(start = 5.dp)) {
                Text(
                    text = "dx",
                    color = style.color,
                    fontFamily = MonoFont,
                    fontSize = style.size,
                    fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 上标：指数贴在**基数的右上角**（基线抬高 ≈0.42 em、字号 0.62 倍）。
 *
 * 旧实现用 Row(Top) + 底部 padding 造“抬高”，padding 走 dp 而字号是 sp ——
 * 一旦系统字体缩放不为 1，换算就失真，指数会落回基线中央（看起来像行内居中）。
 * 这里改用自定义 Layout：按实际测量的字高推基线，用量出来的 px 精确抬升。
 */
@Composable
private fun SupView(node: Nat.Sup, style: NatStyle, signal: Int = -1) {
    val density = LocalDensity.current
    val expStyle = style.copy(size = style.size * 0.62f)
    val raisePx = style.size.value * 0.42f * density.density * density.fontScale

    Layout(
        content = {
            Box { NatNodeView(node.base, style, signal) }
            Box { NatNodeView(supExpNode(node.exp), expStyle, signal) }
        },
    ) { measurables, constraints ->
        val relaxed = constraints.copy(minWidth = 0, minHeight = 0)
        val base = measurables[0].measure(relaxed)
        val exp = measurables[1].measure(relaxed)
        // 行内基线大约在行高的 80% 处（Compose Text 默认行高 ≈ 1.2 em）
        val baseBaseline = base.height * 0.80f
        val expBaseline = exp.height * 0.80f
        var expTop = baseBaseline - raisePx - expBaseline
        var baseTop = 0f
        if (expTop < 0f) {
            // 指数高出顶边时整体下移，保证不裁切
            baseTop = -expTop
            expTop = 0f
        }
        val w = base.width + exp.width
        val h = maxOf(baseTop + base.height, expTop + exp.height).toInt()
        layout(w, h) {
            base.place(x = 0, y = baseTop.toInt())
            exp.place(x = base.width, y = expTop.toInt())
        }
    }
}

/**
 * 指数槽里「只包住一个分数」的括号隐去：分数横线本身就分组，
 * 显示成 x^(2/3) 的堆叠分数，而不是带着一对多余括号。
 */
private fun supExpNode(n: Nat): Nat {
    if (n !is Nat.Row) return n
    val items = n.items
    if (items.size < 3) return n
    val first = items.first()
    val last = items.last()
    if (!(first is Nat.Sym && first.text == "(" && last is Nat.Sym && last.text == ")")) return n
    val inner = items.subList(1, items.size - 1)
    val atoms = inner.filter { it !is Nat.Cursor }
    // 只包住一个原子（数字/变量/分数）时隐去括号：x^(2) 仍显示成 x²
    val singleAtom = atoms.size == 1 && (atoms[0] is Nat.Sym || atoms[0] is Nat.Frac)
    if (!singleAtom) return n
    val out = Nat.Row(inner)
    out.srcStart = inner.first().srcStart
    out.srcEnd = inner.last().srcEnd
    return out
}

/**
 * 分数槽的括号隐去：分子/分母整串被一层括号包住时，渲染时去掉这对括号。
 * 分数横线本身就是分组，真机也不会把这种括号画出来；源码里保留括号是为了语义正确。
 */
private fun slotNode(n: Nat): Nat {
    if (n !is Nat.Row) return n
    val items = n.items
    if (items.size < 3) return n
    val first = items.first()
    val last = items.last()
    if (!(first is Nat.Sym && first.text == "(" && last is Nat.Sym && last.text == ")")) return n
    var depth = 0
    for (i in items.indices) {
        val s = items[i]
        if (s is Nat.Sym && s.text == "(") depth++
        else if (s is Nat.Sym && s.text == ")") {
            depth--
            if (depth == 0 && i != items.lastIndex) return n
        }
    }
    if (depth != 0) return n
    val inner = Nat.Row(items.subList(1, items.size - 1))
    inner.srcStart = items[1].srcStart
    inner.srcEnd = items[items.size - 2].srcEnd
    return inner
}

/**
 * 分数：分子 / 分数线 / 分母 上下堆叠。
 *
 * 宽度**完全由内容自然包裹**（自定义 measure 取分子/分母二者较大者），
 * 分数线在 draw 阶段按**实测出来的 `size.width`** 画出 ——
 * 全程不使用内在尺寸测量、也不让子项去撑满父宽，因此在「宽度不受限」的可滚动容器里也不会测量塌陷。
 */
@Composable
private fun FracView(node: Nat.Frac, style: NatStyle, signal: Int = -1) {
    val density = LocalDensity.current
    val linePx = with(density) { RuleThickness.toPx() }
    val linePxInt = with(density) { RuleThickness.roundToPx() }

    // measure 阶段写入、draw 阶段读取：同一个组合作用域内的闭包共享，测量总在绘制之前
    var ruleCenterY = Float.NaN

    Layout(
        modifier = Modifier.drawBehind {
            if (!ruleCenterY.isNaN() && size.width > 0f) {
                drawLine(
                    color = style.color,
                    start = Offset(0f, ruleCenterY),
                    end = Offset(size.width, ruleCenterY),
                    strokeWidth = linePx,
                )
            }
        },
        content = {
            Box(Modifier.padding(horizontal = FracPadH)) { NatNodeView(slotNode(node.n), style, signal) }
            Box(Modifier.padding(horizontal = FracPadH)) { NatNodeView(slotNode(node.d), style, signal) }
        },
    ) { measurables, constraints ->
        val relaxed = constraints.copy(minWidth = 0, minHeight = 0)
        val num = measurables[0].measure(relaxed)
        val den = measurables[1].measure(relaxed)
        val w = maxOf(num.width, den.width)
        ruleCenterY = num.height + linePxInt / 2f
        val h = num.height + linePxInt + den.height
        layout(w, h) {
            num.place(x = (w - num.width) / 2, y = 0)
            den.place(x = (w - den.width) / 2, y = num.height + linePxInt)
        }
    }
}

/**
 * 根号：`√` 与「上横线 + 被开方内容」并排。
 * 上横线同样由 `drawBehind` 按实测宽度画出，被开方内容自然包裹 —— 不依赖内在尺寸、不撑满父宽。
 */
@Composable
private fun SqrtView(node: Nat.Sqrt, style: NatStyle, signal: Int = -1) {
    val density = LocalDensity.current
    val linePx = with(density) { RuleThickness.toPx() }
    val linePxInt = with(density) { RuleThickness.roundToPx() }

    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "\u221A",
            color = style.color,
            fontFamily = MonoFont,
            fontSize = style.size * 1.45f,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        Layout(
            modifier = Modifier.drawBehind {
                if (size.width > 0f) {
                    drawLine(
                        color = style.color,
                        start = Offset(0f, linePx / 2f),
                        end = Offset(size.width, linePx / 2f),
                        strokeWidth = linePx,
                    )
                }
            },
            content = { Box(Modifier.padding(horizontal = FracPadH)) { NatNodeView(node.a, style, signal) } },
        ) { measurables, constraints ->
            val inner = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(inner.width, inner.height + linePxInt) {
                inner.place(x = 0, y = linePxInt)
            }
        }
    }
}

/**
 * LCD 表达式 / 结果区（底部对齐、超宽时横向滚动）。
 *
 * - 内容按自然宽度包裹，靠 `Box(contentAlignment = End)` 贴右 —— 不依赖无限宽约束下的末端排列；
 * - 滚动容器内既无内在尺寸测量、也无撑满父宽的修饰，所以多个分数并排时不会被压成竖排、分数线也不会拿不到宽度；
 * - 初始滚到最右端：短表达式贴右对齐，超长表达式默认显示结尾、可向左滚动查看（不被截断）。
 */
@Composable
fun LcdMathLine(
    expr: String,
    style: NatStyle,
    modifier: Modifier = Modifier,
    cursor: Int = -1,
    cursorExit: Int = 0,
    align: Alignment = Alignment.BottomEnd,
    rowAlign: Alignment.Vertical = Alignment.Bottom,
    initialScroll: Int = Int.MAX_VALUE,
) {
    val scroll = rememberScrollState(initial = initialScroll)
    Box(modifier, contentAlignment = align) {
        Row(
            modifier = Modifier.horizontalScroll(scroll),
            verticalAlignment = rowAlign,
        ) {
            NaturalView(expr, style, cursor = cursor, cursorExit = cursorExit)
        }
    }
}

val LcdSmall = 15.sp
val LcdBig = 26.sp

package io.paimon.fx991.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
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
fun NaturalView(expr: String, style: NatStyle, modifier: Modifier = Modifier, cursor: Int = -1) {
    val node = remember(expr, cursor) { if (cursor >= 0) buildNatCursor(expr, cursor) else buildNat(expr) }
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
        is Nat.Sym -> Text(
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

        is Nat.Sup -> Row(verticalAlignment = Alignment.Top) {
            NatNodeView(node.base, style, signal)
            Box(Modifier.padding(bottom = (style.size.value * 0.35f).dp)) {
                NatNodeView(node.exp, style.copy(size = style.size * 0.68f), signal)
            }
        }

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
            Box(Modifier.padding(horizontal = FracPadH)) { NatNodeView(node.n, style, signal) }
            Box(Modifier.padding(horizontal = FracPadH)) { NatNodeView(node.d, style, signal) }
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
fun LcdMathLine(expr: String, style: NatStyle, modifier: Modifier = Modifier, cursor: Int = -1) {
    val scroll = rememberScrollState(initial = Int.MAX_VALUE)
    Box(modifier, contentAlignment = Alignment.BottomEnd) {
        Row(
            modifier = Modifier.horizontalScroll(scroll),
            verticalAlignment = Alignment.Bottom,
        ) {
            NaturalView(expr, style, cursor = cursor)
        }
    }
}

val LcdSmall = 15.sp
val LcdBig = 26.sp

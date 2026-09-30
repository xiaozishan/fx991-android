package io.paimon.fx991.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** 自然书写渲染：分数上下堆叠、√ 带上横线、上标指数（纯 Compose 手绘） */
@Composable
fun NaturalView(expr: String, style: NatStyle, modifier: Modifier = Modifier) {
    val node = remember(expr) { buildNat(expr) }
    Box(modifier) { NatNodeView(node, style) }
}

@Composable
private fun NatNodeView(node: Nat, style: NatStyle) {
    when (node) {
        is Nat.Sym -> Text(
            text = node.text,
            color = style.color,
            fontFamily = MonoFont,
            fontSize = style.size,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )

        is Nat.Row -> Row(verticalAlignment = Alignment.CenterVertically) {
            node.items.forEach { NatNodeView(it, style) }
        }

        is Nat.Frac -> Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(horizontal = 3.dp)) { NatNodeView(node.n, style) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(style.color)
            )
            Box(Modifier.padding(horizontal = 3.dp)) { NatNodeView(node.d, style) }
        }

        is Nat.Sqrt -> Row(
            modifier = Modifier.width(IntrinsicSize.Max),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "\u221A",
                color = style.color,
                fontFamily = MonoFont,
                fontSize = style.size * 1.45f,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(style.color)
                )
                Box(Modifier.padding(horizontal = 3.dp)) { NatNodeView(node.a, style) }
            }
        }

        is Nat.Sup -> Row(verticalAlignment = Alignment.Top) {
            NatNodeView(node.base, style)
            Box(Modifier.padding(bottom = (style.size.value * 0.35f).dp)) {
                NatNodeView(node.exp, style.copy(size = style.size * 0.68f))
            }
        }
    }
}

/** LCD 表达式 / 结果区（底部对齐、超宽时横向滚动） */
@Composable
fun LcdMathLine(expr: String, style: NatStyle, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Box(modifier, contentAlignment = Alignment.BottomStart) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll, reverseScrolling = true),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.End,
        ) {
            NaturalView(expr, style)
        }
    }
}

val LcdSmall = 15.sp
val LcdBig = 26.sp

package io.paimon.fx991.engine

/**
 * 输入归一化：把人从别处粘进来、或 ProcessText 传进来的「长得像但不是」的字符
 * 换成本引擎认识的字符。
 *
 * 对应真实复现的坑（2026-10-21 实机报「语法错误」）：
 *   - 减号是 – ‑ ‒ — ― － 而不是 − / -
 *   - 乘号是 ✕ ✖ ＊ 而不是 × / *
 *   - 分数线是 ⁄ ∕ ／ 而不是 /
 *   - 全角括号 （） 、全角数字
 *   - 粘进来夹带的零宽空格 / 软连字符 / 各种特殊空格
 *
 * 原则：只做「等价替换」，不动任何可能有语义的字符（· 是点乘、′″ 是度分秒、
 * 字母变量一律不动），避免把原意改掉。纯 Kotlin，可 JVM 回归。
 */
object InputNorm {

    /** 归一化一段原始输入；空白输入返回 "" */
    fun normalize(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                // 各种连字符/减号 → ASCII '-'
                '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015',
                '\u2212', '\uFF0D', '\u00AD', '\uFE63', '\u2E3A', '\u2E3B' -> sb.append('-')

                // 各种除号/分数线 → '/'
                '\u2044', '\u2215', '\uFF0F', '\u29F8' -> sb.append('/')

                // 各种乘号 → '*'
                '\u2715', '\u2716', '\uFF0A', '\u22C6', '\u2217' -> sb.append('*')

                // 全角标点 / 括号 → 半角
                '\uFF08' -> sb.append('(')
                '\uFF09' -> sb.append(')')
                '\uFF0B' -> sb.append('+')
                '\uFF1D' -> sb.append('=')
                '\uFF0C' -> sb.append(',')
                '\uFF1A' -> sb.append(':')
                '\uFF05' -> sb.append('%')

                // 零宽 / 方向控制字符：直接丢弃
                '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF', '\u00A0' -> Unit

                // 各种特殊空格 → 普通空格（\u00A0 已丢弃；这里处理其余）
                '\u2007', '\u2009', '\u202F', '\u3000', '\u2002', '\u2003' -> sb.append(' ')

                // 全角数字 → 半角
                in '\uFF10'..'\uFF19' -> sb.append('0' + (ch - '\uFF10'))

                else -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    /**
     * 归一化并做括号就地平衡：只补不删。
     * 多出来的右括号保留（引擎会报语法错误，比静默改算式安全）。
     */
    fun normalizeAndAutoClose(raw: String): String {
        val s = normalize(raw)
        var open = 0
        for (ch in s) when (ch) {
            '(' -> open++
            ')' -> if (open > 0) open--
        }
        return s + ")".repeat(open)
    }
}

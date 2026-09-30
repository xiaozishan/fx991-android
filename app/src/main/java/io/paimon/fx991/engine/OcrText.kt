package io.paimon.fx991.engine

// ---------------------------------------------------------------------------
// 批次 F：本地离线 OCR · 纯逻辑层（无 Android / 无 ML Kit 依赖 → 可 JVM 直跑回归）
//
// 职责：把 ML Kit 识别出的多行 / 多块原始文本整理成本引擎可直接解析的表达式：
//   多行合并 → OCR 专有符号修正（√ 变体 / 全角等号 / 小数点误识别）→
//   复用 PhotoSolve.normalizeExpr（全角→半角、×÷ 归一、**→^、去空白）→
//   易混字符纠正（O/o→0、l/I/|→1，只在数字上下文里做，避免过度纠正）。
//
// 原则：宁可漏纠，不可错纠 —— 纠正规则全部要求「相邻有数字或小数点」的上下文，
// 单独的字母（可能是变量 / 函数名）一律不动。界面会让用户先看一眼再确认求值，
// 所以漏纠可以手改，错纠会把原意改掉。
// ---------------------------------------------------------------------------

object OcrText {

    // -----------------------------------------------------------------------
    // 多行 / 多块合并
    // -----------------------------------------------------------------------

    /** 合并 ML Kit 给出的若干行：去掉首尾空白、丢掉空行、按顺序直接拼接 */
    fun mergeLines(lines: List<String>): String =
        lines.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("")

    // -----------------------------------------------------------------------
    // 主入口
    // -----------------------------------------------------------------------

    /** 从一段原始文本（可含换行）整理出表达式；空白输入返回 "" */
    fun clean(raw: String): String {
        if (raw.isBlank()) return ""
        var s = mergeLines(raw.split('\n', '\r'))
        s = preNormalize(s)
        s = PhotoSolve.normalizeExpr(s)
        s = correctConfusables(s)
        return s
    }

    /** 从 ML Kit 的行列表整理出表达式 */
    fun cleanLines(lines: List<String>): String {
        if (lines.isEmpty()) return ""
        var s = mergeLines(lines)
        if (s.isBlank()) return ""
        s = preNormalize(s)
        s = PhotoSolve.normalizeExpr(s)
        s = correctConfusables(s)
        return s
    }

    // -----------------------------------------------------------------------
    // OCR 专有符号修正（在 normalizeExpr 之前做，处理 normalizeExpr 不认识的字符）
    // -----------------------------------------------------------------------

    fun preNormalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                '✓', '✔', '∨', '⎷' -> sb.append('√')     // 根号常见误识别
                '＝' -> sb.append('=')                      // 全角等号
                '﹣', '−' -> sb.append('-')                 // 小连字符 / 数学减号
                '〇' -> sb.append('0')                      // 中文数字零
                else -> sb.append(ch)
            }
        }
        return fixDecimalDots(sb.toString())
    }

    /**
     * 小数点误识别：两个数字之间的 `。` 或 `·` 改成 `.`。
     * 不在数字之间的 `。` 是非法字符，直接丢弃（留着必报语法错误）。
     * 故意不动 `,` —— 逗号是引擎的双参数函数分隔符（logb(2,8)），改成小数点会改变原意。
     */
    fun fixDecimalDots(s: String): String {
        val ch = s.toCharArray()
        for (i in ch.indices) {
            if (ch[i] == '。' || ch[i] == '·') {
                val left = i > 0 && ch[i - 1].isDigit()
                val right = i < ch.size - 1 && ch[i + 1].isDigit()
                if (left && right) ch[i] = '.'
            }
        }
        return String(ch).filter { it != '。' }
    }

    // -----------------------------------------------------------------------
    // 易混字符纠正（在 normalizeExpr 之后做：此时全角已转半角、空白已去掉）
    // -----------------------------------------------------------------------

    private fun isNumCtx(c: Char): Boolean = c.isDigit() || c == '.'

    /**
     * O/o→0、l/I/|→1：**只在该字符左右相邻（至少一侧）是数字或小数点时纠正**。
     * 单独出现的 O / o / l / I 一律保留原样（可能是变量、函数名的一部分或真的字母）。
     */
    fun correctConfusables(s: String): String {
        val ch = s.toCharArray()
        for (i in ch.indices) {
            val left = i > 0 && isNumCtx(ch[i - 1])
            val right = i < ch.size - 1 && isNumCtx(ch[i + 1])
            if (!left && !right) continue
            when (ch[i]) {
                'O', 'o' -> ch[i] = '0'
                'l', 'I', '|' -> ch[i] = '1'
            }
        }
        return String(ch)
    }
}

package io.paimon.fx991.engine

import java.math.BigInteger

// ---------------------------------------------------------------------------
// 批次 D：基数换算 BASE-N
//
// DEC / HEX / BIN / OCT 四进制互转 + 位运算（and / or / xor / xnor / not / neg），
// 字长可选 16 / 32 / 64 位，负数按补码（two's complement）存储与显示。
//
// 内部表示：Long 存原始位形（补码），所有运算后按字长截断（与参考机一致）。
// DEC 进制显示有符号值；HEX / BIN / OCT 显示补码位形。
// ---------------------------------------------------------------------------

object BaseN {

    val WORD_SIZES = listOf(16, 32, 64)

    /** base 取值：2 / 8 / 10 / 16 */
    val BASE_LABELS = listOf("DEC", "HEX", "BIN", "OCT")
    val BASE_VALUES = listOf(10, 16, 2, 8)

    private fun mask(bits: Int): Long = if (bits >= 64) -1L else (1L shl bits) - 1L

    private fun signBit(bits: Int): Long =
        if (bits >= 64) Long.MIN_VALUE else 1L shl (bits - 1)

    /** 按字长截断（补码环绕） */
    fun wrap(v: Long, bits: Int): Long = v and mask(bits)

    /** 原始位形 → 有符号值（DEC 显示用） */
    fun toSigned(raw: Long, bits: Int): Long {
        val r = wrap(raw, bits)
        return if (r and signBit(bits) != 0L) r or mask(bits).inv() else r
    }

    /** 原始位形 → 无符号大整数（HEX / BIN / OCT 位形显示用） */
    private fun toUnsignedBig(raw: Long, bits: Int): BigInteger {
        val r = wrap(raw, bits)
        var b = BigInteger.valueOf(r)
        if (b.signum() < 0) b = b.add(BigInteger.ONE.shiftLeft(64)) // bits == 64 时才会为负
        return b
    }

    /**
     * 解析输入串。可带前导 `-` / `−` 表示负值（按补码存储）。
     * 幅值必须 < 2^bits，否则 NumericError；含该进制不合法的数字也报错。
     */
    fun parse(text: String, base: Int, bits: Int): Long {
        var s = text.trim().uppercase()
        if (s.isEmpty()) throw NumericError("请输入数值")
        var neg = false
        if (s.startsWith("-") || s.startsWith("−")) {
            neg = true
            s = s.substring(1)
        }
        if (s.isEmpty()) throw NumericError("请输入数值")
        val digits = when (base) {
            2 -> "01"
            8 -> "01234567"
            10 -> "0123456789"
            16 -> "0123456789ABCDEF"
            else -> throw NumericError("不支持的进制")
        }
        if (s.any { it !in digits }) throw NumericError("含有该进制不合法的数字")
        val m = try {
            BigInteger(s, base)
        } catch (_: Exception) {
            throw NumericError("不是合法数字")
        }
        val cap = BigInteger.ONE.shiftLeft(bits)
        if (m >= cap) throw NumericError("超出 $bits 位字长")
        var raw = m.toLong() // m < 2^64；bits==64 且 m ≥ 2^63 时 toLong 得到的就是其补码位形
        if (neg) raw = -raw
        return wrap(raw, bits)
    }

    /**
     * 按进制显示原始位形（最少位数，不补前导零）。
     * DEC 显示有符号值；HEX / BIN / OCT 显示补码位形（大写）。
     */
    fun format(raw: Long, base: Int, bits: Int): String = when (base) {
        10 -> toSigned(raw, bits).toString()
        16 -> toUnsignedBig(raw, bits).toString(16).uppercase()
        8 -> toUnsignedBig(raw, bits).toString(8)
        2 -> toUnsignedBig(raw, bits).toString(2)
        else -> throw NumericError("不支持的进制")
    }

    /** 有符号值是负数时，附一行无符号幅值（界面辅助显示） */
    fun unsignedDec(raw: Long, bits: Int): String = toUnsignedBig(raw, bits).toString()

    // ---- 位运算（结果均按字长截断） ----

    fun bitAnd(a: Long, b: Long, bits: Int): Long = wrap(a and b, bits)

    fun bitOr(a: Long, b: Long, bits: Int): Long = wrap(a or b, bits)

    fun bitXor(a: Long, b: Long, bits: Int): Long = wrap(a xor b, bits)

    fun bitXnor(a: Long, b: Long, bits: Int): Long = wrap((a xor b).inv(), bits)

    fun bitNot(a: Long, bits: Int): Long = wrap(a.inv(), bits)

    /** 补码取负 */
    fun neg(a: Long, bits: Int): Long = wrap(-a, bits)
}

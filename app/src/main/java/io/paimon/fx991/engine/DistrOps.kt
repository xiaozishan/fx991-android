package io.paimon.fx991.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// 批次 C：概率分布（正态 / 二项 / 泊松；纯 Kotlin，可 JVM 回归）
//
// 不引第三方统计库：误差函数 erf 与 lnΓ 全部手写。
// ---------------------------------------------------------------------------

/** 正态分布的三段概率：P(左尾) / Q(右尾) / R(双侧中央) */
class NormalProb(
    /** P = P(X < x)（左尾） */
    val p: Double,
    /** Q = P(X > x)（右尾）= 1 − P */
    val q: Double,
    /** R = P(μ−|x−μ| < X < μ+|x−μ|)（以均值为中心的对称区间） */
    val r: Double,
)

object DistrOps {

    // -----------------------------------------------------------------------
    // 误差函数（Abramowitz & Stegun 7.1.26，|误差| ≤ 1.5e-7）
    // -----------------------------------------------------------------------

    fun erf(x: Double): Double {
        val sign = if (x < 0.0) -1.0 else 1.0
        val a = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * a)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t
            + 0.254829592) * t * exp(-a * a)
        return sign * y
    }

    /** 标准正态累积分布 Φ(t) = P(Z < t) */
    fun normCdf(t: Double): Double = 0.5 * (1.0 + erf(t / sqrt(2.0)))

    /** 标准正态概率密度 φ(t) */
    fun normPdf(t: Double): Double = exp(-t * t / 2.0) / sqrt(2.0 * Math.PI)

    // -----------------------------------------------------------------------
    // 正态分布：给定 x（与 μ, σ）求 P / Q / R
    // -----------------------------------------------------------------------

    /** 以标准化值 t = (x − μ) / σ 计算三段概率 */
    fun normalProb(t: Double): NormalProb {
        val p = normCdf(t)
        val q = 1.0 - p
        val r = normCdf(abs(t)) - normCdf(-abs(t))
        return NormalProb(p, q, r)
    }

    /** 给定 μ / σ / x 求 P / Q / R */
    fun normal(x: Double, mu: Double, sigma: Double): NormalProb {
        if (sigma <= 0.0) throw NumericError("标准差必须为正")
        return normalProb((x - mu) / sigma)
    }

    // -----------------------------------------------------------------------
    // 二项分布 X ~ B(n, p)
    // -----------------------------------------------------------------------

    /** 二项分布概率 P(X = k) */
    fun binomialPdf(n: Int, k: Int, p: Double): Double {
        if (n < 0) throw NumericError("n 必须 ≥ 0")
        if (k < 0 || k > n) return 0.0
        if (p < 0.0 || p > 1.0) throw NumericError("概率 p 必须在 [0,1] 之间")
        if (p == 0.0) return if (k == 0) 1.0 else 0.0
        if (p == 1.0) return if (k == n) 1.0 else 0.0
        val logC = lnGamma(n + 1.0) - lnGamma(k + 1.0) - lnGamma(n - k + 1.0)
        return exp(logC + k * ln(p) + (n - k) * ln(1.0 - p))
    }

    /** 二项分布累积 P(X ≤ k) */
    fun binomialCdf(n: Int, k: Int, p: Double): Double {
        if (k < 0) return 0.0
        if (k >= n) return 1.0
        var s = 0.0
        for (i in 0..k) s += binomialPdf(n, i, p)
        return s.coerceIn(0.0, 1.0)
    }

    // -----------------------------------------------------------------------
    // 泊松分布 X ~ Poisson(λ)
    // -----------------------------------------------------------------------

    /** 泊松分布概率 P(X = k) */
    fun poissonPdf(lambda: Double, k: Int): Double {
        if (lambda <= 0.0) throw NumericError("λ 必须为正")
        if (k < 0) return 0.0
        return exp(-lambda + k * ln(lambda) - lnGamma(k + 1.0))
    }

    /** 泊松分布累积 P(X ≤ k) */
    fun poissonCdf(lambda: Double, k: Int): Double {
        if (k < 0) return 0.0
        var s = 0.0
        for (i in 0..k) s += poissonPdf(lambda, i)
        return s.coerceIn(0.0, 1.0)
    }

    // -----------------------------------------------------------------------
    // lnΓ（Lanczos 近似，g = 7，n = 9）
    // -----------------------------------------------------------------------

    private val LANCZOS = doubleArrayOf(
        0.99999999999980993,
        676.5203681218851,
        -1259.1392167224028,
        771.32342877765313,
        -176.61502916214059,
        12.507343278686905,
        -0.13857109526572012,
        9.9843695780195716e-6,
        1.5056327351493116e-7,
    )

    fun lnGamma(x: Double): Double {
        if (x < 0.5) {
            // 反射公式
            return ln(Math.PI / kotlin.math.sin(Math.PI * x)) - lnGamma(1.0 - x)
        }
        val z = x - 1.0
        var a = LANCZOS[0]
        val t = z + 7.5
        for (i in 1 until LANCZOS.size) a += LANCZOS[i] / (z + i)
        return 0.5 * ln(2.0 * Math.PI) + (z + 0.5) * ln(t) - t + ln(a)
    }
}

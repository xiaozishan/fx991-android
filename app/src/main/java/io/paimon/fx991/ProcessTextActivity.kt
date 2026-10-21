package io.paimon.fx991

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * 选中文字 → 计算。
 *
 * 系统「文本选择」菜单里的入口只负责把选中的 text/plain 转交给 MainActivity，
 * 自己不显示任何界面，转发完立刻 finish。
 * 文本会在 MainActivity → IncomingText → CalcViewModel.applyIncomingText 里
 * 做字符归一化后直接求值（所以从浏览器/文档里粘来的 – ✕ ⁄ 全角括号也能算）。
 */
class ProcessTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (!text.isNullOrBlank()) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_CALCULATE)
                    .putExtra(MainActivity.EXTRA_EXPRESSION, text)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
            )
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

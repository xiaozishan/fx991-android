package io.paimon.fx991

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.paimon.fx991.ui.CalcApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // edge-to-edge：窗口铺满整屏（含状态栏 / 导航栏区域），
        // 由 Compose 侧 Modifier.safeAreaPadding() 按系统真实 Insets 避让。
        // 不写死任何状态栏高度，刘海 / 挖孔 / 手势条高度都交给系统上报。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        routeIntent(intent)
        setContent {
            CalcApp()
        }
    }

    /**
     * 已在前台时再次收到外部文本（PROCESS_TEXT / 快捷方式）走这里，
     * 不重建 Activity，直接把文本投给 Compose 层。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeIntent(intent)
    }

    private fun routeIntent(intent: Intent?) {
        if (intent == null) return
        val text: String? = when {
            intent.action == Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            intent.hasExtra(EXTRA_EXPRESSION) -> intent.getStringExtra(EXTRA_EXPRESSION)
            else -> null
        }
        IncomingText.offer(text)
    }

    companion object {
        const val ACTION_CALCULATE = "io.paimon.fx991.action.CALCULATE"
        const val EXTRA_EXPRESSION = "io.paimon.fx991.extra.EXPRESSION"
    }
}

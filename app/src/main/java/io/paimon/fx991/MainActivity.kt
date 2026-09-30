package io.paimon.fx991

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
        setContent {
            CalcApp()
        }
    }
}

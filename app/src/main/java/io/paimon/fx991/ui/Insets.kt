package io.paimon.fx991.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 统一的系统 Insets 避让修饰符（所有界面共用这一个）。
 *
 * 应用走 edge-to-edge（见 `MainActivity` 的 `enableEdgeToEdge()`），因此窗口不会自动给
 * 系统栏留白，必须由内容自己避让。这里用官方 Compose 的 `WindowInsets.safeDrawing`：
 *
 *  - **顶**：状态栏（高度随刘海 / 挖孔 / 机型不同，由系统上报，不写死）
 *  - **底**：导航栏 / 手势条（三键导航与手势导航高度不同，同样系统上报）
 *  - **左 / 右**：横屏时的挖孔与系统手势区
 *  - 键盘弹出时一并避开 IME（面板里的输入框不会被键盘压住）
 *
 * 凡是要贴系统栏的界面根节点都加这一个修饰符即可，不要在各处手写固定 padding。
 */
@Composable
fun Modifier.safeAreaPadding(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing)

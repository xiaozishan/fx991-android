package io.paimon.fx991

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 外部文本入口（ProcessText / 快捷方式 / 其他 App 分享）到 Compose 层的桥。
 *
 * MainActivity 在 onCreate / onNewIntent 里把文本写进 pending；
 * CalcApp 用 LaunchedEffect(pending) 消费后调用 consume() 清空。
 * 用 Compose 的 mutableState 而不是普通变量，才能在 Activity 已在前台时
 * 通过 onNewIntent 触发重组，不依赖 Activity 重建。
 */
object IncomingText {
    var pending by mutableStateOf<String?>(null)
        private set

    fun offer(text: String?) {
        val t = text?.trim().orEmpty()
        if (t.isNotEmpty()) pending = t
    }

    fun consume() {
        pending = null
    }
}

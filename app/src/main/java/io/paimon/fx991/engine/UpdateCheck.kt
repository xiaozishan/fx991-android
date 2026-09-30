package io.paimon.fx991.engine

import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

// ---------------------------------------------------------------------------
// 批次 K5：自动检查更新
// 数据源：GitHub Releases 公共 API（公开仓库只读，无需 token）。
// 注意：未鉴权请求限流 60 次/小时/IP —— 所以自动检查有 12 小时缓存窗口，
// 403 时明确提示「请求太频繁」。
// 零第三方依赖：HttpURLConnection + 复用 PhotoSolve 的手写最小 JSON 解析。
// 本文件只用 java.net / java.io，纯 JVM 可测（见 tools/UpdateCheckTest.java）。
// 网络调用严禁主线程（调用方负责 Dispatchers.IO）。
// ---------------------------------------------------------------------------

/** 检查更新失败（message 全部人话，不带异常栈） */
open class UpdateException(message: String) : Exception(message)

/** 服务器有响应但非 2xx（限流 / 没 Release 等）——已到达服务器，计入缓存窗口 */
class UpdateHttpException(val code: Int) : UpdateException(UpdateCheck.httpErrorText(code))

/** 网络层失败（没网 / DNS / 超时）——不计入缓存窗口，下次启动还会再试 */
class UpdateNetException(message: String) : UpdateException(message)

/** 服务器返回了 2xx 但内容解析不了 —— 已到达服务器，计入缓存窗口 */
class UpdateParseException(message: String) : UpdateException(message)

/** 一个 Release 的关键信息 */
data class ReleaseInfo(
    val tag: String, // 原始 tag，如 "v1.10.0"
    val version: String, // 去掉 v 前缀与 - 后缀的数字串，如 "1.10.0"
    val pageUrl: String, // Release 页面（html_url）
    val apkUrl: String, // 第一个 .apk 资产直链（没有则空串）
    val title: String, // Release 标题（可空串）
)

object UpdateCheck {

    const val API_URL = "https://api.github.com/repos/xiaozishan/fx991-android/releases/latest"

    /** 自动检查的最小间隔：12 小时 */
    const val AUTO_WINDOW_MS: Long = 12L * 60 * 60 * 1000

    // -----------------------------------------------------------------------
    // 版本号比较（数值分段，绝不按字符串比；v 前缀与 - 后缀不参与比大小）
    // -----------------------------------------------------------------------

    /** 取版本号的数字核心段："v1.9.0-keymap" → [1, 9, 0]。非法段抛 IllegalArgumentException。 */
    private fun versionCore(v: String): IntArray {
        var s = v.trim()
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1)
        val dash = s.indexOf('-')
        if (dash >= 0) s = s.substring(0, dash)
        if (s.isEmpty()) throw IllegalArgumentException("空版本号：'$v'")
        return s.split('.').map { seg ->
            if (seg.isEmpty() || seg.any { !it.isDigit() }) {
                throw IllegalArgumentException("版本号段非法：'$v'")
            }
            seg.toIntOrNull() ?: throw IllegalArgumentException("版本号段过大：'$v'")
        }.toIntArray()
    }

    /** 数值分段比较：>0 表示 a 更新，<0 表示 b 更新，0 相等。位数不同按 0 补齐（1.9 == 1.9.0）。 */
    fun compareVersions(a: String, b: String): Int {
        val x = versionCore(a)
        val y = versionCore(b)
        val n = maxOf(x.size, y.size)
        for (i in 0 until n) {
            val p = if (i < x.size) x[i] else 0
            val q = if (i < y.size) y[i] else 0
            if (p != q) return if (p > q) 1 else -1
        }
        return 0
    }

    /** 远端 tag 是否比本地版本新。任何一边解析不了都当作「不新」（静默，不打扰用户）。 */
    fun isNewer(remoteTag: String, localVersion: String): Boolean = try {
        compareVersions(remoteTag, localVersion) > 0
    } catch (_: IllegalArgumentException) {
        false
    }

    // -----------------------------------------------------------------------
    // 缓存时间窗
    // -----------------------------------------------------------------------

    /** 距上次检查是否已经超过窗口（该再发请求了）。从未检查过（lastMs=0）必然该查。 */
    fun dueForAutoCheck(lastCheckMs: Long, nowMs: Long): Boolean =
        lastCheckMs <= 0L || nowMs - lastCheckMs >= AUTO_WINDOW_MS

    // -----------------------------------------------------------------------
    // Release JSON 解析（复用 PhotoSolve 的手写最小 JSON 解析器）
    // -----------------------------------------------------------------------

    fun parseRelease(json: String): ReleaseInfo {
        val root = try {
            PhotoSolve.parseJson(json)
        } catch (e: PhotoSolve.JsonError) {
            throw UpdateParseException("返回内容不是有效的更新信息（${e.message}）")
        }
        if (root !is Map<*, *>) throw UpdateParseException("返回内容不是有效的更新信息")
        val tag = (root["tag_name"] as? String)?.trim()
        if (tag.isNullOrEmpty()) throw UpdateParseException("返回内容里找不到版本号（tag_name）")
        val page = (root["html_url"] as? String)?.trim().orEmpty()
        if (page.isEmpty()) throw UpdateParseException("返回内容里找不到下载页面（html_url）")
        val title = (root["name"] as? String)?.trim().orEmpty()
        var apk = ""
        val assets = root["assets"]
        if (assets is List<*>) {
            for (a in assets) {
                if (a !is Map<*, *>) continue
                val name = (a["name"] as? String) ?: continue
                if (name.endsWith(".apk")) {
                    apk = (a["browser_download_url"] as? String)?.trim().orEmpty()
                    if (apk.isNotEmpty()) break
                }
            }
        }
        // tag 必须能解析成可比较的版本号，否则这个 Release 没法用
        try {
            versionCore(tag)
        } catch (_: IllegalArgumentException) {
            throw UpdateParseException("版本号格式无法识别：$tag")
        }
        var version = tag
        if (version.startsWith("v") || version.startsWith("V")) version = version.substring(1)
        val dash = version.indexOf('-')
        if (dash >= 0) version = version.substring(0, dash)
        return ReleaseInfo(tag = tag, version = version, pageUrl = page, apkUrl = apk, title = title)
    }

    // -----------------------------------------------------------------------
    // 错误文案（全部人话，不抛异常栈给用户）
    // -----------------------------------------------------------------------

    fun httpErrorText(code: Int): String = when (code) {
        403 -> "请求太频繁（GitHub 限流，未登录每小时 60 次），请稍后再试"
        404 -> "仓库还没有发布任何 Release"
        else -> "更新服务器返回 HTTP $code，请稍后再试"
    }

    /** 网络异常 → 人话 */
    fun netErrorText(e: Throwable): String = when (e) {
        is SocketTimeoutException -> "连接超时：网络太慢或暂时没网，稍后再试"
        is UnknownHostException -> "没有网络或域名解析失败：请检查网络连接"
        is ConnectException -> "连接失败：无法访问更新服务器"
        is javax.net.ssl.SSLException -> "安全连接失败：请检查网络环境"
        else -> "网络请求失败：${e.message ?: e.javaClass.simpleName}"
    }

    // -----------------------------------------------------------------------
    // 实际请求（调用方负责放到工作线程）
    // -----------------------------------------------------------------------

    /** GET 最新 Release。失败抛 [UpdateHttpException] / [UpdateNetException] / [UpdateParseException]。 */
    fun fetchLatest(timeoutSec: Int = 15): ReleaseInfo {
        val conn = try {
            URL(API_URL).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw UpdateNetException("无法建立连接：${e.message}")
        }
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutSec * 1000
            conn.readTimeout = timeoutSec * 1000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub API 没有 User-Agent 会直接 403
            conn.setRequestProperty("User-Agent", "fx991-android-update-check")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code !in 200..299) throw UpdateHttpException(code)
            return parseRelease(body)
        } catch (e: UpdateException) {
            throw e
        } catch (e: java.io.IOException) {
            throw UpdateNetException(netErrorText(e))
        } finally {
            conn.disconnect()
        }
    }
}

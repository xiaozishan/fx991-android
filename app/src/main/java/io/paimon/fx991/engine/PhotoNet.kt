package io.paimon.fx991.engine

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

// ---------------------------------------------------------------------------
// 拍照解题 · 网络层（HttpURLConnection，JDK 自带；不引 OkHttp / Retrofit）
// 本文件只用 java.net / java.io，纯 JVM 也能跑（回归里用本机 HttpServer 实测）。
// 调用方必须在工作线程 / 协程 IO 调度器里调用，严禁主线程。
// ---------------------------------------------------------------------------

/** 网络 / 协议层失败（携带人话提示） */
class PhotoApiException(message: String) : Exception(message)

/** HTTP 状态码非 2xx */
class PhotoHttpException(val code: Int, val body: String) :
    Exception(PhotoSolve.httpErrorText(code, body))

object PhotoNet {

    /**
     * POST chat/completions，返回响应体原文。
     * 失败时抛 [PhotoHttpException]（带状态码）或 [PhotoApiException]（人话）。
     */
    fun postChat(cfg: ApiConfig, bodyJson: String): String {
        cfg.validate()?.let { throw PhotoApiException(it) }
        val url = try {
            URL(PhotoSolve.chatUrl(cfg.baseUrl))
        } catch (_: Exception) {
            throw PhotoApiException("Base URL 不是合法地址：${cfg.baseUrl}")
        }
        val conn = try {
            url.openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw PhotoApiException("无法建立连接：${e.message}")
        }
        val timeoutMs = cfg.timeoutSec * 1000
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey.trim()}")
            conn.setRequestProperty("Accept", "application/json")
            val payload = bodyJson.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(payload.size)
            conn.outputStream.use { it.write(payload) }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.use { it.readUtf8() } ?: ""
            if (code !in 200..299) throw PhotoHttpException(code, body)
            return body
        } catch (e: PhotoHttpException) {
            throw e
        } catch (e: PhotoApiException) {
            throw e
        } catch (_: SocketTimeoutException) {
            throw PhotoApiException(
                "连接超时（${cfg.timeoutSec} 秒无响应）：网络太慢或服务器没回应，" +
                    "可在设置里把超时调大，或检查 Base URL 是否需要代理。"
            )
        } catch (_: UnknownHostException) {
            throw PhotoApiException(
                "域名解析失败（DNS）：请检查 Base URL 是否拼错、设备是否联网。"
            )
        } catch (e: java.net.ConnectException) {
            throw PhotoApiException("连接被拒绝：目标地址 / 端口不通，请检查 Base URL。")
        } catch (e: javax.net.ssl.SSLException) {
            throw PhotoApiException("TLS/证书握手失败：${e.message ?: "https 连接不安全或被拦截"}")
        } catch (e: java.io.IOException) {
            throw PhotoApiException("网络请求失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    /** 发拍照解题请求：返回模型回复文本（choices[0].message.content） */
    fun solve(cfg: ApiConfig, imageDataUri: String): String {
        val body = postChat(cfg, PhotoSolve.buildChatBody(cfg, imageDataUri))
        return try {
            PhotoSolve.extractContent(body)
        } catch (e: PhotoSolve.JsonError) {
            throw PhotoApiException(e.message ?: "响应解析失败")
        }
    }

    /** 测试连接：发一个最小请求；返回人话结果（成功也是文字） */
    fun testConnection(cfg: ApiConfig): String {
        cfg.validate()?.let { return "配置不完整：$it" }
        if (cfg.apiKey.isBlank()) return "还没填 API Key：请先填 Key 再测试。"
        return try {
            val body = postChat(cfg, PhotoSolve.buildTestBody(cfg))
            val content = try {
                PhotoSolve.extractContent(body)
            } catch (_: Exception) {
                "(响应结构不标准，但 HTTP 200)"
            }
            "连接成功：HTTP 200，模型 ${cfg.model} 可用。模型回了：${PhotoSolve.snippet(content, 40)}"
        } catch (e: PhotoHttpException) {
            e.message ?: "HTTP ${e.code}"
        } catch (e: PhotoApiException) {
            e.message ?: "连接失败"
        }
    }

    private fun java.io.InputStream.readUtf8(): String {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = read(chunk)
            if (n < 0) break
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray().toString(Charsets.UTF_8)
    }
}

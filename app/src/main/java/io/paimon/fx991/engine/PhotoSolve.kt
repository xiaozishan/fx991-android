package io.paimon.fx991.engine

import java.util.Base64

// ---------------------------------------------------------------------------
// 拍照解题 · 纯逻辑层（无 Android / 无 Compose 依赖 → 可 JVM 直跑回归）
//
// 说明：JSON 的构造与解析本可用 Android 自带的 org.json，但 org.json 在纯 JVM
// 回归环境（只挂 kotlin-stdlib 的 javac/java）里不可用。为了兑现「多段 JSON
// 响应解析可 JVM 测」，这里手写最小 JSON（字符串转义 + 递归下降解析），
// 功能范围等价于本功能对 org.json 的实际使用面。仍然零第三方依赖。
// ---------------------------------------------------------------------------

/** 解题 API 配置（用户自由填写，存 SharedPreferences 明文——界面上有告知） */
data class ApiConfig(
    val baseUrl: String = DEFAULT_BASE,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val timeoutSec: Int = DEFAULT_TIMEOUT_SEC,
) {
    /** 是否已具备发请求的最小条件（缺一即视为「没配」） */
    fun isComplete(): Boolean =
        baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** 校验；返回 null 表示合法，否则是人话错误 */
    fun validate(): String? {
        val b = baseUrl.trim()
        if (b.isEmpty()) return "Base URL 不能为空"
        if (!b.startsWith("https://") && !b.startsWith("http://"))
            return "Base URL 必须以 https:// 或 http:// 开头"
        if (b.contains(' ') || b.contains('\n') || b.contains('\t'))
            return "Base URL 里不能含空白字符"
        if (model.isBlank()) return "模型名不能为空"
        if (timeoutSec !in MIN_TIMEOUT_SEC..MAX_TIMEOUT_SEC)
            return "超时秒数应在 $MIN_TIMEOUT_SEC–$MAX_TIMEOUT_SEC 之间"
        return null
    }

    /** 收紧超时到合法区间（UI 输入宽松，存储前收紧） */
    fun clamped(): ApiConfig = copy(timeoutSec = timeoutSec.coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC))

    companion object {
        const val DEFAULT_BASE = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_TIMEOUT_SEC = 45
        const val MIN_TIMEOUT_SEC = 5
        const val MAX_TIMEOUT_SEC = 180
    }
}

/** 预设：一键填充 Base URL + 推荐的视觉模型名（不填 Key，Key 永远由用户自己填） */
data class ApiPreset(val name: String, val baseUrl: String, val model: String, val note: String)

val API_PRESETS: List<ApiPreset> = listOf(
    ApiPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-v4-flash-vision-exp",
        "带 vision 后缀的就是视觉模型（deepseek-v4-flash-vision-exp）"),
    ApiPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4v-flash",
        "glm-4v 系列支持图片输入"),
    ApiPreset("Kimi（Moonshot）", "https://api.moonshot.cn/v1", "moonshot-v1-8k-vision-preview",
        "带 vision 后缀的模型支持图片输入"),
    ApiPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini",
        "gpt-4o 系列支持图片输入"),
)

object PhotoSolve {

    /** 图片最长边压缩目标 / JPEG 质量（与任务约定一致） */
    const val MAX_EDGE = 1600
    const val JPEG_QUALITY = 85

    // -----------------------------------------------------------------------
    // URL 拼接
    // -----------------------------------------------------------------------

    /** baseUrl → chat/completions 完整地址（容忍尾部 /、容忍用户已写到端点） */
    fun chatUrl(baseUrl: String): String {
        var b = baseUrl.trim().trimEnd('/')
        if (b.endsWith("/chat/completions")) return b
        return "$b/chat/completions"
    }

    // -----------------------------------------------------------------------
    // Prompt（要求模型用固定标记返回，便于解析回填）
    // -----------------------------------------------------------------------

    const val PROMPT_TEXT: String =
        "请识别这张图片里的数学算式并完成计算，严格遵守以下输出约定：\n" +
            "1. 把图中的算式转写成一行纯文本表达式。规则：加减乘除用 + - * /，乘方用 ^，" +
            "平方根用 √(...)，圆周率用 π，三角与对数用 sin cos tan log ln，自变量用 x。" +
            "不要省略运算符，不要带单位，不要带 \$ 或 LaTeX 记号。\n" +
            "2. 计算该表达式的结果。\n" +
            "3. 严格按下面的格式输出，不要输出任何额外内容：\n" +
            "<EXPR>一行表达式</EXPR>\n" +
            "<RESULT>计算结果</RESULT>\n" +
            "<EXPLAIN>一两句中文说明</EXPLAIN>\n" +
            "4. 如果图中没有可计算的算式，只输出：<EXPR>NONE</EXPR>"

    // -----------------------------------------------------------------------
    // 请求体构造（手写 JSON 转义）
    // -----------------------------------------------------------------------

    fun buildChatBody(cfg: ApiConfig, imageDataUri: String, maxTokens: Int = 800): String {
        val sb = StringBuilder()
        sb.append("{\"model\":").append(jsonStr(cfg.model.trim()))
        sb.append(",\"messages\":[{\"role\":\"user\",\"content\":[")
        sb.append("{\"type\":\"text\",\"text\":").append(jsonStr(PROMPT_TEXT)).append("},")
        sb.append("{\"type\":\"image_url\",\"image_url\":{\"url\":")
            .append(jsonStr(imageDataUri)).append("}}")
        sb.append("]}],\"max_tokens\":").append(maxTokens).append("}")
        return sb.toString()
    }

    /** 测试连接用的最小请求（一张 1×1 白点 JPEG，尽量省 token） */
    fun buildTestBody(cfg: ApiConfig): String {
        val sb = StringBuilder()
        sb.append("{\"model\":").append(jsonStr(cfg.model.trim()))
        sb.append(",\"messages\":[{\"role\":\"user\",\"content\":")
            .append(jsonStr("回复 ok 两个字母即可。"))
        sb.append("}],\"max_tokens\":4}")
        return sb.toString()
    }

    /** JSON 字符串转义（RFC 8259） */
    fun jsonStr(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // -----------------------------------------------------------------------
    // 响应解析：最小 JSON 递归下降解析器（Map/List/String/Double/Boolean/null）
    // -----------------------------------------------------------------------

    class JsonError(message: String) : Exception(message)

    private class JsonReader(val src: String) {
        var i = 0

        fun parse(): Any? {
            skipWs()
            val v = value()
            skipWs()
            return v
        }

        private fun skipWs() {
            while (i < src.length && src[i].isWhitespace()) i++
        }

        private fun value(): Any? {
            if (i >= src.length) throw JsonError("JSON 不完整")
            return when (val c = src[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> if (c == '-' || c.isDigit()) num() else throw JsonError("JSON 里出现意外字符 '$c'")
            }
        }

        private fun expect(w: String) {
            if (src.regionMatches(i, w, 0, w.length)) i += w.length
            else throw JsonError("JSON 字面量不合法")
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (i < src.length && src[i] == '}') { i++; return m }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                if (i >= src.length || src[i] != ':') throw JsonError("对象缺冒号")
                i++
                skipWs()
                m[k] = value()
                skipWs()
                if (i >= src.length) throw JsonError("对象未闭合")
                when (src[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw JsonError("对象里出现意外字符")
                }
            }
        }

        private fun arr(): List<Any?> {
            val list = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (i < src.length && src[i] == ']') { i++; return list }
            while (true) {
                skipWs()
                list.add(value())
                skipWs()
                if (i >= src.length) throw JsonError("数组未闭合")
                when (src[i]) {
                    ',' -> i++
                    ']' -> { i++; return list }
                    else -> throw JsonError("数组里出现意外字符")
                }
            }
        }

        private fun str(): String {
            if (i >= src.length || src[i] != '"') throw JsonError("这里应该是字符串")
            i++
            val sb = StringBuilder()
            while (i < src.length) {
                val c = src[i]
                when (c) {
                    '"' -> { i++; return sb.toString() }
                    '\\' -> {
                        i++
                        if (i >= src.length) throw JsonError("转义不完整")
                        when (val e = src[i]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 >= src.length) throw JsonError("\\u 转义不完整")
                                sb.append(src.substring(i + 1, i + 5).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw JsonError("未知转义 \\$e")
                        }
                        i++
                    }
                    else -> { sb.append(c); i++ }
                }
            }
            throw JsonError("字符串未闭合")
        }

        private fun num(): Double {
            val start = i
            if (i < src.length && src[i] == '-') i++
            while (i < src.length && (src[i].isDigit() || src[i] in ".eE+-")) i++
            return src.substring(start, i).toDoubleOrNull()
                ?: throw JsonError("数字不合法")
        }
    }

    /** 解析 JSON 文本为 Kotlin 结构（供测试与内部使用） */
    fun parseJson(text: String): Any? = JsonReader(text).parse()

    /**
     * 从 chat/completions 响应里取 choices[0].message.content。
     * 响应带 error 字段 / 结构不符 / 根本不是 JSON 时抛 [JsonError]（人话消息）。
     */
    fun extractContent(body: String): String {
        val root = try {
            parseJson(body)
        } catch (e: JsonError) {
            throw JsonError("服务返回的不是 JSON（${e.message}）：${snippet(body)}")
        }
        if (root !is Map<*, *>) throw JsonError("响应不是 JSON 对象：${snippet(body)}")
        val err = root["error"]
        if (err is Map<*, *>) {
            val msg = (err["message"] as? String) ?: err.toString()
            throw JsonError("API 返回错误：$msg")
        }
        val choices = root["choices"] as? List<*>
            ?: throw JsonError("响应里没有 choices 字段：${snippet(body)}")
        val first = choices.firstOrNull() as? Map<*, *>
            ?: throw JsonError("choices 为空，模型没有给出回答")
        val message = first["message"] as? Map<*, *>
            ?: throw JsonError("响应里没有 message 字段：${snippet(body)}")
        val content = message["content"]
        // 有的实现 content 是分段数组：[{type:"text", text:"..."}]
        if (content is List<*>) {
            val joined = content.mapNotNull { seg ->
                (seg as? Map<*, *>)?.let { it["text"] as? String }
            }.joinToString("\n")
            if (joined.isNotBlank()) return joined
        }
        return content as? String
            ?: throw JsonError("message.content 不是文本：${snippet(body)}")
    }

    /** 截一段响应原文用于错误提示（去掉空白，最多 120 字） */
    fun snippet(body: String, max: Int = 120): String {
        val t = body.replace(Regex("\\s+"), " ").trim()
        return if (t.length <= max) t else t.substring(0, max) + "…"
    }

    // -----------------------------------------------------------------------
    // 标记抽取与回复解析
    // -----------------------------------------------------------------------

    /** 取 <TAG>…</TAG> 之间的内容（trim 后）；没有该标记返回 null */
    fun extractTag(text: String, tag: String): String? {
        val open = "<$tag>"
        val close = "</$tag>"
        val a = text.indexOf(open, ignoreCase = true)
        if (a < 0) return null
        val b = text.indexOf(close, startIndex = a + open.length, ignoreCase = true)
        val inner = if (b < 0) text.substring(a + open.length) else text.substring(a + open.length, b)
        return inner.trim()
    }

    /** 模型回复解析结果 */
    sealed class Reply {
        /** 成功：表达式 + 模型给的结果 + 解释 */
        data class Solved(val expr: String, val result: String, val explain: String) : Reply()

        /** 模型说图里没有算式 */
        data object NoFormula : Reply()

        /** 模型没按约定格式返回 */
        data class BadFormat(val detail: String) : Reply()
    }

    fun parseReply(content: String): Reply {
        val rawExpr = extractTag(content, "EXPR")
            ?: return Reply.BadFormat("模型回复里缺少 <EXPR> 标记。原文片段：${snippet(content)}")
        if (rawExpr.equals("NONE", ignoreCase = true) || rawExpr.isBlank()) return Reply.NoFormula
        val expr = normalizeExpr(rawExpr)
        if (expr.isBlank()) return Reply.BadFormat("表达式标记里是空的。原文片段：${snippet(content)}")
        val result = extractTag(content, "RESULT") ?: ""
        val explain = extractTag(content, "EXPLAIN") ?: ""
        return Reply.Solved(expr, result, explain)
    }

    // -----------------------------------------------------------------------
    // 表达式规整：把模型给的 ASCII / 全角写法整理成本引擎可直接解析的形式
    // -----------------------------------------------------------------------

    fun normalizeExpr(raw: String): String {
        var s = raw.trim()
        // 去掉 markdown 代码围栏 / 反引号 / 行内 $
        s = s.replace("`", "").replace("$", "")
        // 全角 → 半角
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                '＋' -> sb.append('+')
                '－', '–', '—' -> sb.append('-')   // 全角减 / 长短破折号
                '＊', '✕', '✖' -> sb.append('×')   // 全角星号 / 乘号变体
                '／', '∕' -> sb.append('÷')
                '（', '【', '[' -> sb.append('(')
                '）', '】', ']' -> sb.append(')')
                '＾' -> sb.append('^')
                '，' -> sb.append(',')
                '．' -> sb.append('.')
                '：' -> sb.append(':')
                '％' -> sb.append('%')
                '　' -> Unit                        // 全角空格丢弃
                else -> {
                    // 全角数字 ０-９
                    if (ch in '０'..'９') sb.append('0' + (ch - '０')) else sb.append(ch)
                }
            }
        }
        s = sb.toString()
        // 幂的另一种写法
        s = s.replace("**", "^")
        // ASCII 乘除 → 引擎显示用的 × ÷（引擎两种都认，统一成显示形式）
        s = s.replace('*', '×').replace('/', '÷')
        // 去掉所有空白（引擎词法会跳过空格，但去掉更稳）
        s = s.filter { !it.isWhitespace() }
        return s
    }

    // -----------------------------------------------------------------------
    // 图片压缩的尺寸计算（纯数学，Android 侧按这个结果做真实缩放）
    // -----------------------------------------------------------------------

    /** 最长边压到 maxEdge 以内（等比）；已经够小就原样；返回 (宽, 高) */
    fun scaledSize(w: Int, h: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return Pair(maxOf(1, w), maxOf(1, h))
        val longest = maxOf(w, h)
        if (longest <= maxEdge) return Pair(w, h)
        val scale = maxEdge.toDouble() / longest.toDouble()
        val nw = maxOf(1, (w * scale).toInt())
        val nh = maxOf(1, (h * scale).toInt())
        return Pair(nw, nh)
    }

    /** base64 编码后的字节数（每 3 字节 → 4 字符，含填充） */
    fun base64Length(rawBytes: Int): Int = if (rawBytes <= 0) 0 else 4 * ((rawBytes + 2) / 3)

    /** JPEG 字节 → data URI（java.util.Base64，API 26+ 自带，JVM 也可测） */
    fun jpegDataUri(jpeg: ByteArray): String =
        "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)

    // -----------------------------------------------------------------------
    // 错误文案（HTTP 状态码 → 人话）
    // -----------------------------------------------------------------------

    fun httpErrorText(code: Int, body: String): String = when (code) {
        400 -> "请求被拒（HTTP 400）：请求格式不被接受，可能是模型名不支持图片输入。${snippet(body)}"
        401, 403 -> "鉴权失败（HTTP $code）：API Key 无效、过期或权限不足，请到设置里检查 Key。"
        404 -> "接口不存在（HTTP 404）：Base URL 拼错或该服务没有 /chat/completions 端点，请检查 Base URL。"
        408 -> "请求超时（HTTP 408）：服务器处理太久，可稍后再试或加大超时秒数。"
        413 -> "图片太大（HTTP 413）：即使压缩后仍超出服务限制。"
        429 -> "请求太频繁或额度不足（HTTP 429）：稍后再试，或检查账户余额 / 速率限制。"
        in 500..599 -> "服务器内部错误（HTTP $code）：是对方服务的问题，稍后再试。${snippet(body, 80)}"
        else -> "请求失败（HTTP $code）：${snippet(body)}"
    }
}

import io.paimon.fx991.engine.ApiConfig;
import io.paimon.fx991.engine.ApiPreset;
import io.paimon.fx991.engine.PhotoApiException;
import io.paimon.fx991.engine.PhotoHttpException;
import io.paimon.fx991.engine.PhotoNet;
import io.paimon.fx991.engine.PhotoSolve;
import io.paimon.fx991.engine.PhotoSolveKt;
import io.paimon.fx991.engine.PhotoSolve.Reply;
import kotlin.Pair;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 批次 E 回归：拍照解题 + 解题 API 配置的纯逻辑部分。
 * 覆盖：baseURL 拼接 / 请求体 JSON 构造与转义 / 多段 JSON 响应解析 / EXPR 标记抽取 /
 * 表达式规整 / base64 与缩放尺寸计算 / 配置校验与默认值 / HTTP 错误文案 /
 * 本机 HttpServer 全链路（请求头、请求体、200 与 401）。
 * UI / Android Bitmap 部分无法 JVM 测，见 README 说明。
 */
public class PhotoAiTest {

    static int pass = 0;
    static int fail = 0;

    static void eq(String what, Object got, Object expect) {
        boolean good = String.valueOf(got).equals(String.valueOf(expect));
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 44) + " => " + got + (good ? "" : "  (expected " + expect + ")"));
    }

    static void check(String what, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) { return (Map<String, Object>) o; }

    public static void main(String[] args) throws Exception {
        ApiConfig dft = new ApiConfig(ApiConfig.DEFAULT_BASE, "", ApiConfig.DEFAULT_MODEL,
                ApiConfig.DEFAULT_TIMEOUT_SEC);

        // ---- ① 配置默认值与校验 ----
        eq("默认 Base URL", ApiConfig.DEFAULT_BASE, "https://api.openai.com/v1");
        eq("默认模型", ApiConfig.DEFAULT_MODEL, "gpt-4o-mini");
        eq("默认超时", ApiConfig.DEFAULT_TIMEOUT_SEC, 45);
        check("默认配置 validate 通过", dft.validate() == null);
        check("空 Key 视为没配", !dft.isComplete());
        check("三项齐全才算配好", new ApiConfig("https://a.com/v1", "sk-x", "m", 30).isComplete());
        eq("空 Base 报错", new ApiConfig("", "k", "m", 30).validate(), "Base URL 不能为空");
        check("非 http(s) 报错", new ApiConfig("ftp://x", "k", "m", 30).validate().contains("https://"));
        check("带空格报错", new ApiConfig("https://a b.com", "k", "m", 30).validate().contains("空白"));
        eq("空模型报错", new ApiConfig("https://a.com", "k", " ", 30).validate(), "模型名不能为空");
        check("超时过小报错", new ApiConfig("https://a.com", "k", "m", 2).validate().contains("超时"));
        eq("超时被收紧到上限", new ApiConfig("https://a.com", "k", "m", 9999).clamped().getTimeoutSec(), 180);
        eq("超时被收紧到下限", new ApiConfig("https://a.com", "k", "m", 0).clamped().getTimeoutSec(), 5);

        // ---- ② 预设 ----
        List<ApiPreset> presets = PhotoSolveKt.getAPI_PRESETS();
        eq("预设数量", presets.size(), 4);
        boolean allHttps = true, allModel = true, allNote = true;
        for (ApiPreset p : presets) {
            if (!p.getBaseUrl().startsWith("https://")) allHttps = false;
            if (p.getModel().isBlank()) allModel = false;
            if (p.getNote().isBlank()) allNote = false;
        }
        check("预设全是 https", allHttps);
        check("预设都给了模型名", allModel);
        check("预设都带提示语", allNote);

        // ---- ③ baseURL 拼接 ----
        eq("拼接：裸 v1", PhotoSolve.INSTANCE.chatUrl("https://a.com/v1"), "https://a.com/v1/chat/completions");
        eq("拼接：尾部斜杠", PhotoSolve.INSTANCE.chatUrl("https://a.com/v1/"), "https://a.com/v1/chat/completions");
        eq("拼接：已带端点", PhotoSolve.INSTANCE.chatUrl("https://a.com/v1/chat/completions"),
                "https://a.com/v1/chat/completions");
        eq("拼接：首尾空白", PhotoSolve.INSTANCE.chatUrl("  https://a.com/v1  "),
                "https://a.com/v1/chat/completions");

        // ---- ④ JSON 构造与转义 ----
        eq("转义引号换行", PhotoSolve.INSTANCE.jsonStr("a\"b\nc\\"), "\"a\\\"b\\nc\\\\\"");
        eq("转义控制符", PhotoSolve.INSTANCE.jsonStr("\t"), "\"\\t\\u0001\"");
        String uri = PhotoSolve.INSTANCE.jpegDataUri(new byte[]{1, 2, 3, 4});
        String body = PhotoSolve.INSTANCE.buildChatBody(
                new ApiConfig("https://a.com/v1", "k", "glm-4v", 30), uri, 800);
        Object parsed = PhotoSolve.INSTANCE.parseJson(body);   // 自构造的 JSON 必须能被自解析
        Map<String, Object> root = asMap(parsed);
        eq("请求体模型名", root.get("model"), "glm-4v");
        List<Object> msgs = (List<Object>) root.get("messages");
        List<Object> content = (List<Object>) asMap(msgs.get(0)).get("content");
        eq("请求体两段 content", content.size(), 2);
        eq("text 段类型", asMap(content.get(0)).get("type"), "text");
        check("prompt 里要求 EXPR 标记",
                ((String) asMap(content.get(0)).get("text")).contains("<EXPR>"));
        check("prompt 里要求 RESULT 标记",
                ((String) asMap(content.get(0)).get("text")).contains("<RESULT>"));
        check("prompt 里说明 NONE 约定",
                ((String) asMap(content.get(0)).get("text")).contains("NONE"));
        Map<String, Object> img = asMap(asMap(content.get(1)).get("image_url"));
        eq("image_url 原样进请求", img.get("url"), uri);
        Object tb = PhotoSolve.INSTANCE.parseJson(PhotoSolve.INSTANCE.buildTestBody(dft));
        check("测试请求体可解析且 max_tokens=4",
                ((Double) asMap(tb).get("max_tokens")).intValue() == 4);

        // ---- ⑤ JSON 解析（多段 / 转义 / unicode / 结构错误） ----
        Map<String, Object> j = asMap(PhotoSolve.INSTANCE.parseJson(
                "{\"a\":1.5,\"b\":[true,null,\"x\"],\"c\":{\"d\":\"\\u4f60\\u597d\"}}"));
        eq("数字", j.get("a"), 1.5);
        eq("布尔", ((List<Object>) j.get("b")).get(0), true);
        eq("null", String.valueOf(((List<Object>) j.get("b")).get(1)), "null");
        eq("unicode 转义", asMap(j.get("c")).get("d"), "你好");

        String ok200 = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"<EXPR>1+1</EXPR>\\n"
                + "<RESULT>2</RESULT>\\n<EXPLAIN>一行说明</EXPLAIN>\"}}]}";
        String got = PhotoSolve.INSTANCE.extractContent(ok200);
        check("正常响应取 content", got.contains("<EXPR>1+1</EXPR>") && got.contains("<EXPLAIN>"));

        String segResp = "{\"choices\":[{\"message\":{\"content\":["
                + "{\"type\":\"text\",\"text\":\"第一段\"},"
                + "{\"type\":\"text\",\"text\":\"第二段\"}]}}]}";
        eq("分段 content 拼接", PhotoSolve.INSTANCE.extractContent(segResp), "第一段\n第二段");

        String escResp = "{\"choices\":[{\"message\":{\"content\":\"a\\nb\\\"c\\u003cOK\\u003e\"}}]}";
        eq("content 内转义还原", PhotoSolve.INSTANCE.extractContent(escResp), "a\nb\"c<OK>");

        check("error 字段 → JsonError",
                errMsg(() -> PhotoSolve.INSTANCE.extractContent(
                        "{\"error\":{\"message\":\"bad key\"}}")).contains("bad key"));
        check("非 JSON → JsonError",
                errMsg(() -> PhotoSolve.INSTANCE.extractContent("<html>拦截页</html>")).contains("不是 JSON"));
        check("缺 choices → JsonError",
                errMsg(() -> PhotoSolve.INSTANCE.extractContent("{\"foo\":1}")).contains("choices"));
        check("空 choices → JsonError",
                errMsg(() -> PhotoSolve.INSTANCE.extractContent("{\"choices\":[]}")).contains("为空"));

        // ---- ⑥ 标记抽取 ----
        eq("抽 EXPR", PhotoSolve.INSTANCE.extractTag("前置 <EXPR>2+3×4</EXPR> 后置", "EXPR"), "2+3×4");
        check("无标记 → null", PhotoSolve.INSTANCE.extractTag("没有标记", "EXPR") == null);
        eq("标记内空白被裁", PhotoSolve.INSTANCE.extractTag("<EXPR>  1+2  </EXPR>", "EXPR"), "1+2");
        eq("多行标记", PhotoSolve.INSTANCE.extractTag("<EXPR>\n3×3\n</EXPR>", "EXPR"), "3×3");
        eq("大小写不敏感", PhotoSolve.INSTANCE.extractTag("<expr>7</expr>", "EXPR"), "7");

        // ---- ⑦ 回复解析 ----
        Reply r1 = PhotoSolve.INSTANCE.parseReply("<EXPR>2+3*4</EXPR><RESULT>14</RESULT><EXPLAIN>先乘后加</EXPLAIN>");
        check("完整回复 → Solved", r1 instanceof Reply.Solved);
        Reply.Solved s1 = (Reply.Solved) r1;
        eq("Solved.expr 规整", s1.getExpr(), "2+3×4");
        eq("Solved.result", s1.getResult(), "14");
        eq("Solved.explain", s1.getExplain(), "先乘后加");
        check("NONE → NoFormula", PhotoSolve.INSTANCE.parseReply("<EXPR>NONE</EXPR>") instanceof Reply.NoFormula);
        check("none 小写 → NoFormula", PhotoSolve.INSTANCE.parseReply("<expr>none</expr>") instanceof Reply.NoFormula);
        check("缺 EXPR → BadFormat", PhotoSolve.INSTANCE.parseReply("今天天气不错") instanceof Reply.BadFormat);
        check("空 EXPR → NoFormula", PhotoSolve.INSTANCE.parseReply("<EXPR>  </EXPR>") instanceof Reply.NoFormula);
        Reply r5 = PhotoSolve.INSTANCE.parseReply("<EXPR>√(16)</EXPR>");
        check("缺 RESULT/EXPLAIN 也能解", r5 instanceof Reply.Solved);

        // ---- ⑧ 表达式规整 ----
        eq("ASCII 乘除", PhotoSolve.INSTANCE.normalizeExpr("2*3/4"), "2×3÷4");
        eq("全角符号", PhotoSolve.INSTANCE.normalizeExpr("１＋２×３"), "1+2×3");
        eq("双星幂", PhotoSolve.INSTANCE.normalizeExpr("2**10"), "2^10");
        eq("全角括号", PhotoSolve.INSTANCE.normalizeExpr("（１＋２）"), "(1+2)");
        eq("方括号花括号", PhotoSolve.INSTANCE.normalizeExpr("[1+2]"), "(1+2)");
        eq("去反引号与美元符", PhotoSolve.INSTANCE.normalizeExpr("`$1+2$`"), "1+2");
        eq("去空白换行", PhotoSolve.INSTANCE.normalizeExpr("1 +\n 2"), "1+2");
        eq("破折号当减号", PhotoSolve.INSTANCE.normalizeExpr("5—3"), "5-3");
        eq("全角空格丢弃", PhotoSolve.INSTANCE.normalizeExpr("1　+　2"), "1+2");

        // ---- ⑨ 缩放与 base64 ----
        Pair<Integer, Integer> p1 = PhotoSolve.INSTANCE.scaledSize(4000, 3000, 1600);
        eq("横向图缩放宽", p1.getFirst(), 1600);
        eq("横向图缩放高", p1.getSecond(), 1200);
        Pair<Integer, Integer> p2 = PhotoSolve.INSTANCE.scaledSize(3000, 4000, 1600);
        eq("竖向图缩放", p2.getFirst() + "x" + p2.getSecond(), "1200x1600");
        Pair<Integer, Integer> p3 = PhotoSolve.INSTANCE.scaledSize(800, 600, 1600);
        eq("小图不动", p3.getFirst() + "x" + p3.getSecond(), "800x600");
        Pair<Integer, Integer> p4 = PhotoSolve.INSTANCE.scaledSize(0, 0, 1600);
        check("零尺寸兜底不崩", p4.getFirst() >= 1 && p4.getSecond() >= 1);
        eq("base64 长度", PhotoSolve.INSTANCE.base64Length(3), 4);
        eq("base64 长度含填充", PhotoSolve.INSTANCE.base64Length(4), 8);
        eq("base64 空", PhotoSolve.INSTANCE.base64Length(0), 0);
        byte[] raw = "派蒙hello".getBytes(StandardCharsets.UTF_8);
        String du = PhotoSolve.INSTANCE.jpegDataUri(raw);
        check("data URI 前缀", du.startsWith("data:image/jpeg;base64,"));
        byte[] back = Base64.getDecoder().decode(du.substring("data:image/jpeg;base64,".length()));
        eq("base64 往返", new String(back, StandardCharsets.UTF_8), "派蒙hello");

        // ---- ⑩ HTTP 错误文案 ----
        check("401 说人话", PhotoSolve.INSTANCE.httpErrorText(401, "").contains("Key"));
        check("404 说人话", PhotoSolve.INSTANCE.httpErrorText(404, "").contains("Base URL"));
        check("429 说人话", PhotoSolve.INSTANCE.httpErrorText(429, "").contains("频繁"));
        check("500 说人话", PhotoSolve.INSTANCE.httpErrorText(502, "x").contains("服务器"));
        check("其他码带原文", PhotoSolve.INSTANCE.httpErrorText(418, "teapot").contains("teapot"));

        // ---- ⑪ 本机 HttpServer 全链路（请求头 / 请求体 / 200 / 401 / 超时配置） ----
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> gotPath = new AtomicReference<>("");
        AtomicReference<String> gotAuth = new AtomicReference<>("");
        AtomicReference<String> gotBody = new AtomicReference<>("");
        srv.createContext("/v1/chat/completions", ex -> {
            gotPath.set(ex.getRequestURI().getPath());
            gotAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            gotBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String resp;
            int code;
            if (ex.getRequestHeaders().getFirst("Authorization") != null
                    && ex.getRequestHeaders().getFirst("Authorization").contains("bad")) {
                code = 401;
                resp = "{\"error\":{\"message\":\"Incorrect API key\"}}";
            } else {
                code = 200;
                resp = "{\"choices\":[{\"message\":{\"content\":\"<EXPR>1+2</EXPR>"
                        + "<RESULT>3</RESULT><EXPLAIN>测试</EXPLAIN>\"}}]}";
            }
            byte[] rb = resp.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, rb.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(rb); }
        });
        srv.start();
        try {
            String base = "http://127.0.0.1:" + srv.getAddress().getPort() + "/v1";
            ApiConfig good = new ApiConfig(base, "sk-good", "m-vision", 30);
            String netContent = PhotoNet.INSTANCE.solve(good, "data:image/jpeg;base64,AAAA");
            eq("全链路取 content", netContent.contains("<EXPR>1+2</EXPR>") ? "hit" : "miss", "hit");
            eq("路径拼对", gotPath.get(), "/v1/chat/completions");
            eq("Authorization 头", gotAuth.get(), "Bearer sk-good");
            check("请求体带图片", gotBody.get().contains("data:image/jpeg;base64,AAAA"));
            check("请求体带模型名", gotBody.get().contains("\"m-vision\""));

            Reply pr = PhotoSolve.INSTANCE.parseReply(netContent);
            check("全链路解析 Solved", pr instanceof Reply.Solved);
            eq("全链路表达式", ((Reply.Solved) pr).getExpr(), "1+2");

            ApiConfig bad = new ApiConfig(base, "bad-key", "m", 30);
            String e401 = errMsg(() -> PhotoNet.INSTANCE.solve(bad, "data:image/jpeg;base64,AAAA"));
            check("401 抛 PhotoHttpException", errType(() -> PhotoNet.INSTANCE.solve(bad, "x"))
                    .equals("PhotoHttpException"));
            check("401 文案说 Key", e401.contains("Key"));

            String testMsg = PhotoNet.INSTANCE.testConnection(good);
            check("测试连接成功文案", testMsg.startsWith("连接成功") && testMsg.contains("HTTP 200"));
            check("测试连接空 Key 提示", PhotoNet.INSTANCE.testConnection(
                    new ApiConfig(base, "", "m", 30)).contains("API Key"));
            // 连接被拒（本机关闭端口，确定性）：人话提示且不含「成功」
            String refused = PhotoNet.INSTANCE.testConnection(
                    new ApiConfig("http://127.0.0.1:1", "k", "m", 5));
            check("连接被拒说人话", refused.contains("连接") && !refused.startsWith("连接成功"));
            // DNS 失败路径在本机被透明代理拦截（no-such-host.invalid 也能「连上」），
            // 只做软断言：绝不报成功。
            String dnsMsg = PhotoNet.INSTANCE.testConnection(
                    new ApiConfig("http://no-such-host.invalid", "k", "m", 5));
            check("DNS 异常域绝不报成功", !dnsMsg.startsWith("连接成功"));
        } finally {
            srv.stop(0);
        }

        System.out.println("RESULT PhotoAiTest pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    interface Run { void run() throws Exception; }

    static String errMsg(Run r) {
        try { r.run(); return "none"; }
        catch (Throwable e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    }

    static String errType(Run r) {
        try { r.run(); return "none"; }
        catch (Throwable e) { return e.getClass().getSimpleName(); }
    }
}

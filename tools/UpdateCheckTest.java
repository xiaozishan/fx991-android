import io.paimon.fx991.engine.ReleaseInfo;
import io.paimon.fx991.engine.UpdateCheck;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * 批次 K5 回归：自动检查更新的纯逻辑部分。
 * ① 版本号数值分段比较（v 前缀 / - 后缀 / 位数不同 / 相等 / 非法串）
 * ② Release JSON 解析（正常 / 缺字段 / 非 JSON / 无 APK 资产）
 * ③ 错误文案映射（403 限流 / 404 无 Release / 超时 / DNS / 连接被拒）
 * ④ 缓存时间窗（12 小时）
 */
public class UpdateCheckTest {

    static int pass = 0;
    static int fail = 0;

    static void ok(String label, boolean cond) {
        if (cond) { pass++; }
        else { fail++; System.out.println("FAIL: " + label); }
    }

    static void eq(String label, Object a, Object b) {
        boolean same = (a == null && b == null) || (a != null && a.equals(b));
        if (same) { pass++; }
        else { fail++; System.out.println("FAIL: " + label + " 期望=" + b + " 实际=" + a); }
    }

    public static void main(String[] args) {
        // ---------- ① 版本号比较 ----------
        System.out.println("-- 版本号数值分段比较 --");
        ok("1.10.0 > 1.9.0（字符串比较会判反）", UpdateCheck.INSTANCE.compareVersions("1.10.0", "1.9.0") > 0);
        ok("v 前缀忽略：v1.10.0 > 1.9.0", UpdateCheck.INSTANCE.compareVersions("v1.10.0", "1.9.0") > 0);
        ok("V 前缀忽略：V2.0 > v1.9", UpdateCheck.INSTANCE.compareVersions("V2.0", "v1.9") > 0);
        ok("后缀不参与：1.9.0-keymap == 1.9.0", UpdateCheck.INSTANCE.compareVersions("1.9.0-keymap", "1.9.0") == 0);
        ok("后缀不参与：1.10.0-updatecheck > v1.9.0", UpdateCheck.INSTANCE.compareVersions("1.10.0-updatecheck", "v1.9.0") > 0);
        ok("位数不同补 0：1.9 == 1.9.0", UpdateCheck.INSTANCE.compareVersions("1.9", "1.9.0") == 0);
        ok("位数不同：1.9.1 > 1.9", UpdateCheck.INSTANCE.compareVersions("1.9.1", "1.9") > 0);
        ok("大版本碾压：2.0.0 > 1.99.99", UpdateCheck.INSTANCE.compareVersions("2.0.0", "1.99.99") > 0);
        ok("相等：1.9.0 == 1.9.0", UpdateCheck.INSTANCE.compareVersions("1.9.0", "1.9.0") == 0);
        ok("小于：1.8.9 < 1.9.0", UpdateCheck.INSTANCE.compareVersions("1.8.9", "1.9.0") < 0);
        ok("isNewer：v1.11.0 比 1.10.0-updatecheck 新", UpdateCheck.INSTANCE.isNewer("v1.11.0", "1.10.0-updatecheck"));
        ok("isNewer：v1.10.0 不比 1.10.0-updatecheck 新", !UpdateCheck.INSTANCE.isNewer("v1.10.0", "1.10.0-updatecheck"));
        ok("isNewer：v1.9.0 不比 1.10.0-updatecheck 新", !UpdateCheck.INSTANCE.isNewer("v1.9.0", "1.10.0-updatecheck"));
        ok("isNewer：非法远端 tag 不新（静默）", !UpdateCheck.INSTANCE.isNewer("release-latest", "1.10.0"));

        boolean threw = false;
        try { UpdateCheck.INSTANCE.compareVersions("1.x.0", "1.9.0"); } catch (IllegalArgumentException e) { threw = true; }
        ok("非法段 1.x.0 抛 IllegalArgumentException", threw);
        threw = false;
        try { UpdateCheck.INSTANCE.compareVersions("", "1.9.0"); } catch (IllegalArgumentException e) { threw = true; }
        ok("空版本号抛 IllegalArgumentException", threw);

        // ---------- ② Release JSON 解析 ----------
        System.out.println("-- Release JSON 解析 --");
        String good = "{"
            + "\"tag_name\":\"v1.10.0\","
            + "\"name\":\"v1.10.0 更新检查\","
            + "\"html_url\":\"https://github.com/xiaozishan/fx991-android/releases/tag/v1.10.0\","
            + "\"assets\":["
            + "{\"name\":\"app-debug.apk\",\"browser_download_url\":\"https://github.com/xiaozishan/fx991-android/releases/download/v1.10.0/app-debug.apk\"},"
            + "{\"name\":\"notes.txt\",\"browser_download_url\":\"https://example.com/notes.txt\"}"
            + "]}";
        ReleaseInfo info = UpdateCheck.INSTANCE.parseRelease(good);
        eq("解析 tag", info.getTag(), "v1.10.0");
        eq("解析 version 去 v 前缀", info.getVersion(), "1.10.0");
        eq("解析 pageUrl", info.getPageUrl(), "https://github.com/xiaozishan/fx991-android/releases/tag/v1.10.0");
        eq("解析 apkUrl 找到 apk 资产", info.getApkUrl(), "https://github.com/xiaozishan/fx991-android/releases/download/v1.10.0/app-debug.apk");
        eq("解析 title", info.getTitle(), "v1.10.0 更新检查");

        String noApk = "{\"tag_name\":\"v1.10.0\",\"html_url\":\"https://example.com/r\",\"assets\":[{\"name\":\"notes.txt\",\"browser_download_url\":\"https://example.com/n\"}]}";
        eq("无 apk 资产 → apkUrl 空", UpdateCheck.INSTANCE.parseRelease(noApk).getApkUrl(), "");

        String emptyAssets = "{\"tag_name\":\"v2.0.0\",\"html_url\":\"https://example.com/r\",\"assets\":[]}";
        eq("空资产列表 → apkUrl 空", UpdateCheck.INSTANCE.parseRelease(emptyAssets).getApkUrl(), "");

        String noAssets = "{\"tag_name\":\"v2.0.0\",\"html_url\":\"https://example.com/r\"}";
        eq("缺 assets 字段也能解析", UpdateCheck.INSTANCE.parseRelease(noAssets).getVersion(), "2.0.0");

        String suffixTag = "{\"tag_name\":\"v1.10.0-beta\",\"html_url\":\"https://example.com/r\"}";
        eq("tag 带后缀 → version 去后缀", UpdateCheck.INSTANCE.parseRelease(suffixTag).getVersion(), "1.10.0");

        String err;
        err = expectParseErr("{\"html_url\":\"https://example.com/r\"}");
        ok("缺 tag_name → 人话报错", err != null && err.contains("版本号"));
        err = expectParseErr("{\"tag_name\":\"\",\"html_url\":\"https://example.com/r\"}");
        ok("空 tag_name → 人话报错", err != null && err.contains("版本号"));
        err = expectParseErr("这不是 JSON");
        ok("非 JSON → 人话报错", err != null && err.contains("不是有效的更新信息"));
        err = expectParseErr("[1,2,3]");
        ok("JSON 数组根 → 人话报错", err != null && err.contains("不是有效的更新信息"));
        err = expectParseErr("{\"tag_name\":\"v1.10.0\"}");
        ok("缺 html_url → 人话报错", err != null && err.contains("下载页面"));
        err = expectParseErr("{\"tag_name\":\"release-latest\",\"html_url\":\"https://example.com/r\"}");
        ok("tag 不是版本号 → 人话报错", err != null && err.contains("无法识别"));

        // ---------- ③ 错误文案映射 ----------
        System.out.println("-- 错误文案映射 --");
        ok("403 → 明确说请求太频繁", UpdateCheck.INSTANCE.httpErrorText(403).contains("请求太频繁"));
        ok("403 → 提到限流", UpdateCheck.INSTANCE.httpErrorText(403).contains("限流"));
        ok("404 → 没有 Release", UpdateCheck.INSTANCE.httpErrorText(404).contains("Release"));
        ok("500 → 带状态码", UpdateCheck.INSTANCE.httpErrorText(500).contains("500"));
        ok("超时 → 人话", UpdateCheck.INSTANCE.netErrorText(new SocketTimeoutException()).contains("超时"));
        ok("DNS 失败 → 人话", UpdateCheck.INSTANCE.netErrorText(new UnknownHostException("x")).contains("网络"));
        ok("连接被拒 → 人话", UpdateCheck.INSTANCE.netErrorText(new ConnectException("refused")).contains("连接失败"));

        // ---------- ④ 缓存时间窗（12 小时） ----------
        System.out.println("-- 缓存时间窗 --");
        long now = 1_000_000_000_000L;
        ok("从未检查（0）→ 该查", UpdateCheck.INSTANCE.dueForAutoCheck(0L, now));
        ok("刚查过 → 不查", !UpdateCheck.INSTANCE.dueForAutoCheck(now, now));
        ok("11 小时前 → 不查", !UpdateCheck.INSTANCE.dueForAutoCheck(now - 11L * 3600_000L, now));
        ok("正好 12 小时 → 该查", UpdateCheck.INSTANCE.dueForAutoCheck(now - UpdateCheck.AUTO_WINDOW_MS, now));
        ok("13 小时前 → 该查", UpdateCheck.INSTANCE.dueForAutoCheck(now - 13L * 3600_000L, now));

        System.out.println("RESULT pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    /** 解析应当失败：返回异常消息（没抛异常则返回 null）。Kotlin 抛的异常无 throws 声明，只能 catch Exception。 */
    static String expectParseErr(String json) {
        try {
            UpdateCheck.INSTANCE.parseRelease(json);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}

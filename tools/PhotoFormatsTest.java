import io.paimon.fx991.engine.PhotoFormats;
import io.paimon.fx991.engine.PhotoFormats.Format;
import io.paimon.fx991.engine.PhotoSolve;
import kotlin.Pair;

/**
 * 批次 K3 回归：拍照解题图片格式兼容的纯逻辑部分。
 * 覆盖：魔数嗅探（JPEG/PNG/GIF/BMP/WebP/HEIC/AVIF/TIFF/未知/短头）/
 * MIME 兜底映射 / 最低 API 与 decodableOn / ImageDecoder 选择策略 /
 * 降采样 inSampleSize 算法 / 尺寸封顶 / 错误文案映射。
 * Bitmap 真实解码无法 JVM 测，见 README。
 */
public class PhotoFormatsTest {

    static int pass = 0;
    static int fail = 0;

    static void eq(String what, Object got, Object expect) {
        boolean good = String.valueOf(got).equals(String.valueOf(expect));
        if (good) pass++; else fail++;
        System.out.println((good ? "  PASS  " : "  FAIL  ")
                + pad(what, 46) + " => " + got + (good ? "" : "  (expected " + expect + ")"));
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

    /** 造一个文件头字节数组（int 方便写字面量） */
    static byte[] head(int... bytes) {
        byte[] b = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) b[i] = (byte) bytes[i];
        return b;
    }

    /** ISO BMFF 头：尺寸(4) + "ftyp" + 主品牌(4) + 填充到 32 */
    static byte[] bmff(String brand) {
        byte[] b = new byte[32];
        byte[] ftyp = "ftyp".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] br = brand.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(ftyp, 0, b, 4, 4);
        System.arraycopy(br, 0, b, 8, 4);
        return b;
    }

    static String sniffStr(byte[] h) {
        return PhotoFormats.INSTANCE.sniff(h, h.length).name();
    }

    public static void main(String[] args) {
        // ---- ① 魔数嗅探 ----
        eq("JPEG 魔数", sniffStr(head(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0)), "JPEG");
        eq("PNG 魔数", sniffStr(head(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)), "PNG");
        eq("GIF87a", sniffStr(head('G', 'I', 'F', '8', '7', 'a', 0, 0)), "GIF");
        eq("GIF89a", sniffStr(head('G', 'I', 'F', '8', '9', 'a', 0, 0)), "GIF");
        eq("BMP 魔数", sniffStr(head('B', 'M', 0, 0, 0, 0, 0, 0)), "BMP");
        eq("WebP 魔数", sniffStr(head('R', 'I', 'F', 'F', 1, 0, 0, 0, 'W', 'E', 'B', 'P')), "WEBP");
        eq("TIFF 小端", sniffStr(head('I', 'I', 0x2A, 0x00, 0, 0, 0, 0)), "TIFF");
        eq("TIFF 大端", sniffStr(head('M', 'M', 0x00, 0x2A, 0, 0, 0, 0)), "TIFF");
        // HEIC/HEIF 各主品牌
        eq("HEIC brand heic", sniffStr(bmff("heic")), "HEIC");
        eq("HEIC brand mif1", sniffStr(bmff("mif1")), "HEIC");
        eq("HEIC brand heix", sniffStr(bmff("heix")), "HEIC");
        eq("HEIC brand hevc", sniffStr(bmff("hevc")), "HEIC");
        eq("AVIF brand avif", sniffStr(bmff("avif")), "AVIF");
        eq("AVIF brand avis", sniffStr(bmff("avis")), "AVIF");
        eq("未知品牌 ftyp(mp41)", sniffStr(bmff("mp41")), "UNKNOWN");
        eq("全零头", sniffStr(new byte[32]), "UNKNOWN");
        eq("短头不足", sniffStr(head(0xFF, 0xD8)), "UNKNOWN");
        eq("JPEG 最小头", sniffStr(head(0xFF, 0xD8, 0xFF)), "JPEG");

        // ---- ② MIME 兜底 ----
        eq("mime image/heic", PhotoFormats.INSTANCE.fromMime("image/heic").name(), "HEIC");
        eq("mime image/heif", PhotoFormats.INSTANCE.fromMime("image/heif").name(), "HEIC");
        eq("mime HEIC 大写", PhotoFormats.INSTANCE.fromMime("IMAGE/HEIC").name(), "HEIC");
        eq("mime image/avif", PhotoFormats.INSTANCE.fromMime("image/avif").name(), "AVIF");
        eq("mime image/tiff", PhotoFormats.INSTANCE.fromMime("image/tiff").name(), "TIFF");
        eq("mime image/webp", PhotoFormats.INSTANCE.fromMime("image/webp").name(), "WEBP");
        eq("mime x-ms-bmp", PhotoFormats.INSTANCE.fromMime("image/x-ms-bmp").name(), "BMP");
        eq("mime null", PhotoFormats.INSTANCE.fromMime(null).name(), "UNKNOWN");
        eq("mime 乱值", PhotoFormats.INSTANCE.fromMime("application/pdf").name(), "UNKNOWN");

        // ---- ③ 最低 API / decodableOn ----
        eq("JPEG minApi", PhotoFormats.INSTANCE.minApi(Format.JPEG), "1");
        eq("WebP minApi", PhotoFormats.INSTANCE.minApi(Format.WEBP), "1");
        eq("HEIC minApi=28", PhotoFormats.INSTANCE.minApi(Format.HEIC), "28");
        eq("AVIF minApi=31", PhotoFormats.INSTANCE.minApi(Format.AVIF), "31");
        check("TIFF minApi=MAX", PhotoFormats.INSTANCE.minApi(Format.TIFF) == Integer.MAX_VALUE);
        eq("HEIC@26 不可解", PhotoFormats.INSTANCE.decodableOn(Format.HEIC, 26), "false");
        eq("HEIC@27 不可解", PhotoFormats.INSTANCE.decodableOn(Format.HEIC, 27), "false");
        eq("HEIC@28 可解", PhotoFormats.INSTANCE.decodableOn(Format.HEIC, 28), "true");
        eq("HEIC@35 可解", PhotoFormats.INSTANCE.decodableOn(Format.HEIC, 35), "true");
        eq("AVIF@30 不可解", PhotoFormats.INSTANCE.decodableOn(Format.AVIF, 30), "false");
        eq("AVIF@31 可解", PhotoFormats.INSTANCE.decodableOn(Format.AVIF, 31), "true");
        eq("TIFF@36 仍不可解", PhotoFormats.INSTANCE.decodableOn(Format.TIFF, 36), "false");
        eq("JPEG@26 可解", PhotoFormats.INSTANCE.decodableOn(Format.JPEG, 26), "true");
        eq("UNKNOWN@26 放行", PhotoFormats.INSTANCE.decodableOn(Format.UNKNOWN, 26), "true");

        // ---- ④ 解码器选择策略 ----
        eq("API26 用 BitmapFactory", PhotoFormats.INSTANCE.preferImageDecoder(26), "false");
        eq("API27 用 BitmapFactory", PhotoFormats.INSTANCE.preferImageDecoder(27), "false");
        eq("API28 用 ImageDecoder", PhotoFormats.INSTANCE.preferImageDecoder(28), "true");
        eq("API36 用 ImageDecoder", PhotoFormats.INSTANCE.preferImageDecoder(36), "true");

        // ---- ⑤ 降采样算法（防 OOM）----
        // 12000×9000 → 目标 1200×900：2→6000×4500→4→3000×2250→8→1500×1125→16→750×562<1200 ⇒ 8
        eq("12000x9000 → s=8", PhotoFormats.INSTANCE.inSampleSize(12000, 9000, 1200, 900), "8");
        // 4000×3000 → 目标 1333×1000：2→2000×1500≥，4→1000×750< ⇒ 2
        eq("4000x3000 → s=2", PhotoFormats.INSTANCE.inSampleSize(4000, 3000, 1333, 1000), "2");
        // 已小于目标
        eq("1600x1200 → s=1", PhotoFormats.INSTANCE.inSampleSize(1600, 1200, 1600, 1200), "1");
        eq("800x600 → s=1", PhotoFormats.INSTANCE.inSampleSize(800, 600, 800, 600), "1");
        // 极端长条：一边先触底
        eq("100000x100 → s=1", PhotoFormats.INSTANCE.inSampleSize(100000, 100, 1600, 100), "1");
        // 非法输入不崩
        eq("0 宽 → s=1", PhotoFormats.INSTANCE.inSampleSize(0, 3000, 1600, 1200), "1");
        eq("负高 → s=1", PhotoFormats.INSTANCE.inSampleSize(4000, -1, 1600, 1200), "1");
        // 解码结果必须 ≥ 目标（保证后续精确缩放不放大）：12000/8=1500 ≥ 1200 ✓
        check("采样后不小于目标", 12000 / PhotoFormats.INSTANCE.inSampleSize(12000, 9000, 1200, 900) >= 1200
                && 9000 / PhotoFormats.INSTANCE.inSampleSize(12000, 9000, 1200, 900) >= 900);

        // ---- ⑥ 尺寸封顶（scaledSize 回归守卫）----
        Pair<Integer, Integer> p1 = PhotoSolve.INSTANCE.scaledSize(4000, 3000, PhotoSolve.MAX_EDGE);
        eq("4000x3000 → 1600x1200", p1.getFirst() + "x" + p1.getSecond(), "1600x1200");
        Pair<Integer, Integer> p2 = PhotoSolve.INSTANCE.scaledSize(1200, 900, PhotoSolve.MAX_EDGE);
        eq("小图原样", p2.getFirst() + "x" + p2.getSecond(), "1200x900");
        Pair<Integer, Integer> p3 = PhotoSolve.INSTANCE.scaledSize(12000, 1, PhotoSolve.MAX_EDGE);
        eq("极端长条宽封顶", String.valueOf(p3.getFirst()), "1600");
        check("极端长条高 ≥1", p3.getSecond() >= 1);

        // ---- ⑦ Android 版本名 ----
        eq("API26 → 8.0", PhotoFormats.INSTANCE.androidName(26), "Android 8.0");
        eq("API27 → 8.1", PhotoFormats.INSTANCE.androidName(27), "Android 8.1");
        eq("API28 → 9", PhotoFormats.INSTANCE.androidName(28), "Android 9");
        eq("API31 → 12", PhotoFormats.INSTANCE.androidName(31), "Android 12");
        eq("API35 → 15", PhotoFormats.INSTANCE.androidName(35), "Android 15");

        // ---- ⑧ 错误文案映射（人话，含原因与出路）----
        String heic26 = PhotoFormats.INSTANCE.unsupportedText(Format.HEIC, 26);
        check("HEIC 文案点明格式", heic26.contains("HEIC"));
        check("HEIC 文案点明本机版本", heic26.contains("Android 8.0"));
        check("HEIC 文案给出路(JPG)", heic26.contains("JPG"));
        String avif30 = PhotoFormats.INSTANCE.unsupportedText(Format.AVIF, 30);
        check("AVIF 文案点明格式", avif30.contains("AVIF"));
        check("AVIF 文案点明 Android 11", avif30.contains("Android 11"));
        String tiff = PhotoFormats.INSTANCE.unsupportedText(Format.TIFF, 34);
        check("TIFF 文案点明不支持", tiff.contains("TIFF") && tiff.contains("不支持"));

        // ---- ⑨ 显示名 ----
        eq("displayName HEIC", PhotoFormats.INSTANCE.displayName(Format.HEIC), "HEIC/HEIF");
        eq("displayName UNKNOWN", PhotoFormats.INSTANCE.displayName(Format.UNKNOWN), "未知格式");

        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}

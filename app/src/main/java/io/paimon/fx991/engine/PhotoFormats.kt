package io.paimon.fx991.engine

// ---------------------------------------------------------------------------
// 拍照解题 · 图片格式兼容纯逻辑层（批次 K3，无 Android 依赖 → JVM 可回归）
//
// 抽出来的原因：格式判定 / 降采样选择 / 错误文案映射不碰 Bitmap，
// 全部可以纯逻辑测试；Android 侧（ui/PhotoImage.kt）只做真实解码。
//
// 设计原则：
//  - 不靠文件名后缀判格式（相册常返回无扩展名 Uri）；一律魔数嗅探 + MIME 兜底。
//  - 平台能力诚实标注：HEIC/HEIF 平台解码器 API 28 才有，AVIF API 31 才有，
//    TIFF 平台不保证（BitmapFactory/ImageDecoder 普遍不解）——解不了给人话，
//    不引第三方解码库硬扛。
// ---------------------------------------------------------------------------

object PhotoFormats {

    /** 可识别的图片格式 */
    enum class Format { JPEG, PNG, GIF, BMP, WEBP, HEIC, AVIF, TIFF, UNKNOWN }

    // -----------------------------------------------------------------------
    // 魔数嗅探（读文件头，不看扩展名）
    // -----------------------------------------------------------------------

    /** 从文件头字节判定格式；[len] 为实际读到的字节数。认不出返回 UNKNOWN。 */
    fun sniff(h: ByteArray, len: Int): Format {
        fun at(i: Int, b: Int) = len > i && h[i] == b.toByte()
        fun strAt(off: Int, s: String): Boolean {
            if (len < off + s.length) return false
            for (k in s.indices) if (h[off + k] != s[k].code.toByte()) return false
            return true
        }
        // JPEG: FF D8 FF
        if (at(0, 0xFF) && at(1, 0xD8) && at(2, 0xFF)) return Format.JPEG
        // PNG: 89 50 4E 47 0D 0A 1A 0A（逐字节比较，避免 \u001A 预处理坑）
        if (at(0, 0x89) && strAt(1, "PNG") && at(4, 0x0D) && at(5, 0x0A)
            && at(6, 0x1A) && at(7, 0x0A)) return Format.PNG
        // GIF: "GIF87a" / "GIF89a"
        if (strAt(0, "GIF8")) return Format.GIF
        // BMP: "BM"
        if (strAt(0, "BM")) return Format.BMP
        // WebP: "RIFF"????"WEBP"
        if (strAt(0, "RIFF") && strAt(8, "WEBP")) return Format.WEBP
        // TIFF: "II*\0"（小端）或 "MM\0*"（大端）
        if (strAt(0, "II") && at(2, 0x2A) && at(3, 0x00)) return Format.TIFF
        if (strAt(0, "MM") && at(2, 0x00) && at(3, 0x2A)) return Format.TIFF
        // ISO BMFF 家族（HEIC/HEIF/AVIF）：偏移 4 起是 "ftyp"，主品牌紧跟其后
        if (strAt(4, "ftyp") && len >= 12) {
            val brand = String(h, 8, 4, Charsets.US_ASCII)
            when (brand) {
                // HEIF 主品牌族（含 iPhone 的 heic / 序列 hevc 等）
                "heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs",
                "mif1", "msf1" -> return Format.HEIC
                // AVIF（avis 为序列）
                "avif", "avis" -> return Format.AVIF
            }
        }
        return Format.UNKNOWN
    }

    /** MIME 类型兜底判定（魔数认不出时用；相册返回的 MIME 可能是 image/heic 等） */
    fun fromMime(mime: String?): Format = when (mime?.lowercase()?.trim()) {
        "image/jpeg", "image/jpg", "image/pjpeg" -> Format.JPEG
        "image/png" -> Format.PNG
        "image/gif" -> Format.GIF
        "image/bmp", "image/x-ms-bmp" -> Format.BMP
        "image/webp" -> Format.WEBP
        "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence" -> Format.HEIC
        "image/avif", "image/avif-sequence" -> Format.AVIF
        "image/tiff", "image/tif" -> Format.TIFF
        else -> Format.UNKNOWN
    }

    // -----------------------------------------------------------------------
    // 平台能力：什么格式要什么系统版本（硬事实，不靠猜）
    // -----------------------------------------------------------------------

    /** 平台解码器支持该格式的最低 API；TIFF 平台不保证 → MAX_VALUE */
    fun minApi(f: Format): Int = when (f) {
        Format.JPEG, Format.PNG, Format.GIF, Format.BMP, Format.WEBP -> 1
        Format.HEIC -> 28   // BitmapFactory/ImageDecoder 的 HEIF 解码器是 Android 9 加的
        Format.AVIF -> 31   // Android 12 才有 AVIF 平台解码
        Format.TIFF -> Int.MAX_VALUE  // 平台解码器普遍不支持 TIFF
        Format.UNKNOWN -> 1 // 认不出不拦截，让解码器自己试试
    }

    /** 本机（[sdkInt]）理论上能不能解这个格式 */
    fun decodableOn(f: Format, sdkInt: Int): Boolean = sdkInt >= minApi(f)

    /** ImageDecoder（API 28+）优先；更低的版本走 BitmapFactory 兜底 */
    fun preferImageDecoder(sdkInt: Int): Boolean = sdkInt >= 28

    // -----------------------------------------------------------------------
    // 降采样选择（防 OOM）：2 的幂 inSampleSize，解码后仍 ≥ 目标尺寸
    // -----------------------------------------------------------------------

    /**
     * 选最大的 2 的幂采样率 s：再翻一倍就至少有一边小于目标尺寸。
     * 保证解码出来的位图不小于目标（后续精确缩放），同时内存尽量省。
     */
    fun inSampleSize(ow: Int, oh: Int, tw: Int, th: Int): Int {
        if (ow <= 0 || oh <= 0 || tw <= 0 || th <= 0) return 1
        var s = 1
        while (ow / (s * 2) >= tw && oh / (s * 2) >= th) s *= 2
        return s
    }

    // -----------------------------------------------------------------------
    // 人话文案
    // -----------------------------------------------------------------------

    /** 格式中文名（给用户看的） */
    fun displayName(f: Format): String = when (f) {
        Format.JPEG -> "JPEG"
        Format.PNG -> "PNG"
        Format.GIF -> "GIF"
        Format.BMP -> "BMP"
        Format.WEBP -> "WebP"
        Format.HEIC -> "HEIC/HEIF"
        Format.AVIF -> "AVIF"
        Format.TIFF -> "TIFF"
        Format.UNKNOWN -> "未知格式"
    }

    /** API 号 → 用户能看懂的 Android 版本名 */
    fun androidName(sdkInt: Int): String = when (sdkInt) {
        26 -> "Android 8.0"
        27 -> "Android 8.1"
        28 -> "Android 9"
        29 -> "Android 10"
        30 -> "Android 11"
        31 -> "Android 12"
        32 -> "Android 12L"
        33 -> "Android 13"
        34 -> "Android 14"
        35 -> "Android 15"
        36 -> "Android 16"
        else -> if (sdkInt > 36) "Android（API $sdkInt）" else "API $sdkInt"
    }

    /** 「本机解不了这个格式」的人话（区分格式与系统版本，给可操作的出路） */
    fun unsupportedText(f: Format, sdkInt: Int): String = when (f) {
        Format.HEIC ->
            "这张图是 HEIC/HEIF 格式（iPhone 与部分安卓相机的默认格式），" +
                "需要 Android 9 或更高版本才能解码，本机是 ${androidName(sdkInt)}。\n" +
                "出路：在相册里把它「另存为/导出为 JPG」，或在相机设置里改用 JPEG 拍照。"
        Format.AVIF ->
            "这张图是 AVIF 格式，需要 Android 12 或更高版本才能解码，" +
                "本机是 ${androidName(sdkInt)}。请先转成 JPG 或 PNG 再选。"
        Format.TIFF ->
            "这张图是 TIFF 格式，Android 系统自带解码器普遍不支持 TIFF。\n" +
                "请先转成 JPG 或 PNG 再选。"
        else ->
            "这张图的格式（${displayName(f)}）在本机系统（${androidName(sdkInt)}）上解不了，" +
                "建议先转成 JPG 再选。"
    }
}

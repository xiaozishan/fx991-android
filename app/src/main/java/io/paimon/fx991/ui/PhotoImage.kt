package io.paimon.fx991.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import io.paimon.fx991.engine.PhotoFormats
import io.paimon.fx991.engine.PhotoSolve
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

// ---------------------------------------------------------------------------
// 拍照解题 · 图片管线（批次 K3：格式兼容性增强 —— HEIC/HEIF 等）
//
// 链路：魔数嗅探格式（不看扩展名）→ 平台能力检查（解不了直接给人话）
//   → ImageDecoder（API 28+，自动应用 EXIF、直接按目标尺寸解码省内存）
//   → BitmapFactory 兜底（API 26/27，保留 EXIF 校正路径）
//   → 等比缩到 ≤1600px → JPEG 85。
//
// 格式判定 / 降采样 / 错误文案的纯逻辑在 engine/PhotoFormats.kt（JVM 已回归）；
// 本文件的 Bitmap 操作只能真机 / 模拟器验证。
// ---------------------------------------------------------------------------

object PhotoImage {

    class PhotoImageError(message: String) : Exception(message)

    data class Prepared(
        val bitmap: Bitmap,      // 压缩后的位图（用于预览）
        val jpeg: ByteArray,     // JPEG ~85 字节
        val width: Int,          // 压缩后宽
        val height: Int,         // 压缩后高
        val origWidth: Int,
        val origHeight: Int,
    ) {
        val dataUri: String get() = PhotoSolve.jpegDataUri(jpeg)
    }

    /**
     * 从 content URI 读图并压缩：最长边 ≤ [PhotoSolve.MAX_EDGE]，JPEG 质量 [PhotoSolve.JPEG_QUALITY]。
     * 必须在主线程之外调用。
     */
    fun prepare(resolver: ContentResolver, uri: Uri): Prepared {
        // ① 嗅探真实格式（文件头魔数，不看后缀；MIME 仅兜底）
        val format = detectFormat(resolver, uri)

        // ② 平台能力硬检查：HEIC 需 API 28、AVIF 需 API 31、TIFF 平台不保证。
        //    解不了就明说原因与出路，不往下走到「黑屏/崩溃/一句读取失败」。
        if (!PhotoFormats.decodableOn(format, Build.VERSION.SDK_INT)) {
            throw PhotoImageError(PhotoFormats.unsupportedText(format, Build.VERSION.SDK_INT))
        }

        // ③ 解码：API 28+ 优先 ImageDecoder（格式支持更宽、按目标尺寸解码省内存、
        //    自动应用 EXIF 方向）；更老的版本走 BitmapFactory + 手动 EXIF 校正。
        val bmp: Bitmap
        val ow: Int
        val oh: Int
        if (PhotoFormats.preferImageDecoder(Build.VERSION.SDK_INT)) {
            try {
                val r = decodeViaImageDecoder(resolver, uri)
                bmp = r.first; ow = r.second; oh = r.third
            } catch (e: ImageDecoder.DecodeException) {
                throw PhotoImageError(decodeFailText(format, e))
            } catch (e: IOException) {
                throw PhotoImageError(decodeFailText(format, e))
            } catch (e: OutOfMemoryError) {
                throw PhotoImageError("这张图太大，手机内存不够解码（内存不足）。请在相册里把它导出为较小尺寸的 JPG 后再试。")
            }
        } else {
            val r = decodeViaBitmapFactory(resolver, uri)
            bmp = r.first; ow = r.second; oh = r.third
        }

        // ④ 精确缩放到目标尺寸 + JPEG 压缩
        return finish(bmp, ow, oh)
    }

    // -----------------------------------------------------------------------
    // 格式嗅探：先读文件头魔数；认不出时用 ContentResolver 报的 MIME 兜底
    // -----------------------------------------------------------------------

    private fun detectFormat(resolver: ContentResolver, uri: Uri): PhotoFormats.Format {
        val head = ByteArray(32)
        val n = try {
            resolver.openInputStream(uri)?.use { it.read(head) } ?: -1
        } catch (e: SecurityException) {
            throw PhotoImageError("没有权限读取这张图片（Uri 授权可能已失效），请重新选择。")
        } catch (e: FileNotFoundException) {
            throw PhotoImageError("读不到这张图片（文件可能已被移动或删除），请重新选择。")
        } catch (e: IOException) {
            throw PhotoImageError("读取图片时出错（${e.message ?: "I/O 错误"}），请重新选择。")
        }
        if (n <= 0) throw PhotoImageError("读不到这张图片（内容为空或 Uri 已失效），请重新选择。")
        val sniffed = PhotoFormats.sniff(head, n)
        if (sniffed != PhotoFormats.Format.UNKNOWN) return sniffed
        return PhotoFormats.fromMime(resolver.getType(uri))
    }

    // -----------------------------------------------------------------------
    // 路径 A：ImageDecoder（API 28+）
    //   - 直接 setTargetSize 按目标尺寸解码：100MP 的 HEIC 也不会整图进内存
    //   - 自动应用 EXIF 方向，不需要手动旋转
    //   - GIF/动图 WebP 解出来是 AnimatedImageDrawable → 取首帧画到位图
    // -----------------------------------------------------------------------

    private fun decodeViaImageDecoder(resolver: ContentResolver, uri: Uri): Triple<Bitmap, Int, Int> {
        if (Build.VERSION.SDK_INT < 28) throw IllegalStateException("ImageDecoder 需要 API 28+")
        var ow = 0
        var oh = 0
        val src = ImageDecoder.createSource(resolver, uri)
        val drawable = ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
            ow = info.size.width
            oh = info.size.height
            val (tw, th) = PhotoSolve.scaledSize(ow, oh)
            decoder.setTargetSize(tw, th)
            // 软件分配：后面要 compress + 预览，避免硬件位图读不回像素的坑
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (ow <= 0 || oh <= 0) throw PhotoImageError("无法识别这张图片的格式（不是有效的图片文件）。")
        val bmp = when (drawable) {
            is AnimatedImageDrawable -> {
                // GIF / 动图：解码器停在首帧，直接画下来
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(c)
                b
            }
            is BitmapDrawable -> drawable.bitmap
            else -> throw PhotoImageError("这张图解码出了意外的结果类型，请换一张试试。")
        }
        return Triple(bmp, ow, oh)
    }

    // -----------------------------------------------------------------------
    // 路径 B：BitmapFactory（API 26/27 兜底）
    //   这两个版本解不了 HEIC（已在步骤②拦掉），到这里只会是
    //   JPEG/PNG/GIF(首帧)/BMP/WebP。保留 EXIF 手动校正。
    // -----------------------------------------------------------------------

    private fun decodeViaBitmapFactory(resolver: ContentResolver, uri: Uri): Triple<Bitmap, Int, Int> {
        // 先量边界拿原始尺寸（inJustDecodeBounds 不进像素，防 OOM）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw PhotoImageError("读不到这张图片（可能是已失效的临时文件），请重新选择。")
        val ow = bounds.outWidth
        val oh = bounds.outHeight
        if (ow <= 0 || oh <= 0) throw PhotoImageError("无法识别这张图片的格式（不是有效的图片文件，或文件已损坏）。")

        // EXIF 方向（手机相机常带旋转标记）
        val rotation = resolver.openInputStream(uri)?.use { ins ->
            try {
                when (ExifInterface(ins).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } catch (_: Exception) {
                0
            }
        } ?: 0

        // 先粗降采样（2 的幂，纯逻辑在 PhotoFormats.inSampleSize，JVM 已回归）
        val (tw, th) = PhotoSolve.scaledSize(
            if (rotation == 90 || rotation == 270) oh else ow,
            if (rotation == 90 || rotation == 270) ow else oh,
        )
        val opts = BitmapFactory.Options().apply {
            inSampleSize = PhotoFormats.inSampleSize(ow, oh, tw, th)
        }
        var bmp = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: OutOfMemoryError) {
            throw PhotoImageError("这张图太大，手机内存不够解码（内存不足）。请在相册里把它导出为较小尺寸的 JPG 后再试。")
        } ?: throw PhotoImageError("图片解码失败（文件损坏或格式不被本机系统支持）。")

        // EXIF 旋转校正
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated != bmp) bmp.recycle()
            bmp = rotated
        }
        return Triple(bmp, ow, oh)
    }

    // -----------------------------------------------------------------------
    // 公共收尾：精确缩放 + JPEG 压缩
    // -----------------------------------------------------------------------

    private fun finish(bmpIn: Bitmap, ow: Int, oh: Int): Prepared {
        var bmp = bmpIn
        val (fw, fh) = PhotoSolve.scaledSize(bmp.width, bmp.height)
        if (fw != bmp.width || fh != bmp.height) {
            val scaled = Bitmap.createScaledBitmap(bmp, fw, fh, true)
            if (scaled != bmp) bmp.recycle()
            bmp = scaled
        }
        val baos = ByteArrayOutputStream()
        if (!bmp.compress(Bitmap.CompressFormat.JPEG, PhotoSolve.JPEG_QUALITY, baos)) {
            throw PhotoImageError("JPEG 压缩失败。")
        }
        return Prepared(bmp, baos.toByteArray(), bmp.width, bmp.height, ow, oh)
    }

    /** 解码器抛异常时的人话（区分「格式其实不支持」与「文件损坏」） */
    private fun decodeFailText(format: PhotoFormats.Format, e: Exception): String {
        val name = PhotoFormats.displayName(format)
        return if (format == PhotoFormats.Format.UNKNOWN) {
            "无法识别这张图片的格式（不是有效的图片文件，或格式太冷门）。建议转成 JPG 再选。"
        } else {
            "$name 图片解码失败：文件可能已损坏，或本机系统（${PhotoFormats.androidName(android.os.Build.VERSION.SDK_INT)}）" +
                "对该编码的实现不完整（${e.message?.take(60) ?: "解码器报错"}）。建议转成 JPG 再选。"
        }
    }
}

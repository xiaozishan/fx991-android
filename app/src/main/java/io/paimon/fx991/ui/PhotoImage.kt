package io.paimon.fx991.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import io.paimon.fx991.engine.PhotoSolve
import java.io.ByteArrayOutputStream

// ---------------------------------------------------------------------------
// 拍照解题 · 图片管线（Android 侧：解码 → 方向校正 → 等比缩放 → JPEG 压缩）
// 尺寸计算用 engine/PhotoSolve.scaledSize（纯逻辑，JVM 已回归）；
// 本文件的 Bitmap 操作只能真机 / 模拟器验证，JVM 不测。
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
        // ① 只解码边界拿原始尺寸
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw PhotoImageError("读不到这张图片（可能是已失效的临时文件），请重新选择。")
        val ow = bounds.outWidth
        val oh = bounds.outHeight
        if (ow <= 0 || oh <= 0) throw PhotoImageError("无法识别这张图片的格式（不是有效的图片文件）。")

        // ② 读取 EXIF 方向（手机相机常带旋转标记）
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

        // ③ 按目标尺寸算 inSampleSize（2 的幂，先粗降采样省内存）
        val (tw, th) = PhotoSolve.scaledSize(
            if (rotation == 90 || rotation == 270) oh else ow,
            if (rotation == 90 || rotation == 270) ow else oh,
        )
        var sample = 1
        while (ow / (sample * 2) >= tw && oh / (sample * 2) >= th) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw PhotoImageError("图片解码失败（内存不足或文件损坏）。")

        // ④ EXIF 旋转校正
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated != bmp) bmp.recycle()
            bmp = rotated
        }

        // ⑤ 精确缩放到目标尺寸
        val (fw, fh) = PhotoSolve.scaledSize(bmp.width, bmp.height)
        if (fw != bmp.width || fh != bmp.height) {
            val scaled = Bitmap.createScaledBitmap(bmp, fw, fh, true)
            if (scaled != bmp) bmp.recycle()
            bmp = scaled
        }

        // ⑥ JPEG 压缩
        val baos = ByteArrayOutputStream()
        if (!bmp.compress(Bitmap.CompressFormat.JPEG, PhotoSolve.JPEG_QUALITY, baos)) {
            throw PhotoImageError("JPEG 压缩失败。")
        }
        return Prepared(bmp, baos.toByteArray(), bmp.width, bmp.height, ow, oh)
    }
}

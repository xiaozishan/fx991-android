package io.paimon.fx991.ui

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.paimon.fx991.engine.OcrText

// ---------------------------------------------------------------------------
// 批次 F：本地离线 OCR · ML Kit 封装（Android 侧，依赖 ML Kit bundled 模型）
//
// - 用 bundled 版（模型随 APK 发布，不依赖 Google Play 服务，完全离线）。
// - 中文模型本身同时识别拉丁字母与数字；整图识别为空时才退回拉丁模型再试一次。
// - 本文件只能在 Android 运行时工作（Bitmap / ML Kit native），JVM 不测；
//   可测的文本合并 / 清洗逻辑全部在 engine/OcrText.kt（纯 Kotlin，已回归）。
// - 调用方必须在工作线程 / 协程 IO 调度器里调用，严禁主线程（Tasks.await 阻塞）。
// ---------------------------------------------------------------------------

object MlKitOcr {

    class MlKitOcrError(message: String) : Exception(message)

    data class Result(
        val rawText: String,     // ML Kit 原始全文（含换行）
        val lines: List<String>, // 按 block/line 拆出的行
        val cleaned: String,     // OcrText 清洗后的表达式
        val model: String,       // 实际出结果的模型（用于界面标注）
    )

    private val chinese: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val latin: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.Builder().build())
    }

    /** 识别一张位图；什么都认不出来时抛 [MlKitOcrError] */
    fun recognize(bitmap: Bitmap): Result {
        val image = InputImage.fromBitmap(bitmap, 0)
        // ① 中文 bundled 模型（含拉丁字母 / 数字 / 常用符号）
        val zh = run(chinese, image)
        if (zh.first.isNotBlank()) return build(zh.first, zh.second, "中文模型（离线）")
        // ② 整图空白才退回拉丁模型（比如纯英文印刷体且中文模型漏检的边角情况）
        val la = run(latin, image)
        if (la.first.isBlank()) {
            throw MlKitOcrError(
                "没有从这张图里识别到任何文字。请把算式拍清楚、正对、光线充足后再试；手写体识别能力有限，建议拍印刷体。",
            )
        }
        return build(la.first, la.second, "拉丁模型（离线）")
    }

    private fun run(client: TextRecognizer, image: InputImage): Pair<String, List<String>> {
        val text: Text = try {
            Tasks.await(client.process(image))
        } catch (e: Exception) {
            throw MlKitOcrError(
                "本地识别失败：${e.cause?.message ?: e.message ?: e.javaClass.simpleName}",
            )
        }
        val lines = ArrayList<String>()
        for (block in text.textBlocks) {
            for (line in block.lines) lines.add(line.text)
        }
        return Pair(text.text ?: "", lines)
    }

    private fun build(raw: String, lines: List<String>, model: String): Result {
        val cleaned = if (lines.isNotEmpty()) OcrText.cleanLines(lines) else OcrText.clean(raw)
        if (cleaned.isBlank()) {
            throw MlKitOcrError("识别到的文字里没有可计算的表达式（原文：${raw.take(60)}）。")
        }
        return Result(raw, lines, cleaned, model)
    }
}

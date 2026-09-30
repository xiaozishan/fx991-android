package io.paimon.fx991.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.paimon.fx991.CalcViewModel
import io.paimon.fx991.Overlay
import io.paimon.fx991.Screen
import io.paimon.fx991.engine.PhotoApiException
import io.paimon.fx991.engine.PhotoHttpException
import io.paimon.fx991.engine.PhotoNet
import io.paimon.fx991.engine.PhotoSolve
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// 拍照解题界面（批次 E：API 视觉模型；批次 F：新增本地离线 OCR 主路径）
//
// 两条路并存，界面标注清楚：
//   本地识别（离线·免费）—— 默认。ML Kit bundled 模型在手机上离线识别，
//       识别出的文本先给用户看一眼（可编辑），确认后才回填主行求值。
//   用 API 识别（更准·需配置）—— 批次 E 的视觉模型路径，做兜底。
//
// 链路（本地）：相机/相册取图 → 压缩(≤1600px) → ML Kit 离线识别 →
//   OcrText 合并清洗（复用 PhotoSolve.normalizeExpr）→ 用户确认 →
//   插入主行 evaluateNow 求值。
// 链路（API）：取图 → 压缩 → base64 → POST {base}/chat/completions →
//   解析 <EXPR>/<RESULT>/<EXPLAIN> → 回填主行并求值。
// ---------------------------------------------------------------------------

private enum class PhotoPhase { IDLE, BUSY, ERROR, CONFIRM, DONE }
private enum class SolveMode { LOCAL, API }

@Composable
fun PhotoSolveScreen(vm: CalcViewModel, onBack: () -> Unit) {
    val c = LocalCalcColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(SolveMode.LOCAL) }
    var phase by remember { mutableStateOf(PhotoPhase.IDLE) }
    var prepared by remember { mutableStateOf<PhotoImage.Prepared?>(null) }
    var message by remember { mutableStateOf("") }
    var solvedExpr by remember { mutableStateOf("") }
    var solvedResult by remember { mutableStateOf("") }
    var solvedExplain by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    // 本地识别的中间结果
    var localRaw by remember { mutableStateOf("") }
    var localModel by remember { mutableStateOf("") }
    var confirmText by remember { mutableStateOf("") }

    fun fail(t: Throwable) {
        message = when (t) {
            is PhotoHttpException -> t.message ?: "HTTP ${t.code}"
            is PhotoApiException -> t.message ?: "请求失败"
            is PhotoImage.PhotoImageError -> t.message ?: "图片处理失败"
            is MlKitOcr.MlKitOcrError -> t.message ?: "本地识别失败"
            else -> "出现意外错误：${t.message ?: t.javaClass.simpleName}"
        }
        phase = PhotoPhase.ERROR
    }

    /** API 路径：发视觉模型请求（批次 E 原有链路） */
    fun solveWithApi(p: PhotoImage.Prepared) {
        phase = PhotoPhase.BUSY
        message = ""
        scope.launch {
            try {
                val cfg = vm.apiConfig()
                // 网络 + JSON 解析都在 IO 线程
                val content = withContext(Dispatchers.IO) { PhotoNet.solve(cfg, p.dataUri) }
                when (val r = PhotoSolve.parseReply(content)) {
                    is PhotoSolve.Reply.Solved -> {
                        solvedExpr = r.expr
                        solvedResult = r.result
                        solvedExplain = r.explain
                        // 直接插入主计算行并求值（复用主行解题管线），解释文字进结果区附注
                        val note = buildString {
                            if (r.result.isNotBlank()) append("模型计算：").append(r.result)
                            if (r.explain.isNotBlank()) {
                                if (isNotEmpty()) append("；")
                                append(r.explain)
                            }
                        }
                        vm.applyPhotoExpr(r.expr, note)
                        phase = PhotoPhase.DONE
                    }
                    PhotoSolve.Reply.NoFormula -> {
                        message = "没在这张图里认出可计算的算式。请把算式拍清楚、正对、光线充足后再试。"
                        phase = PhotoPhase.ERROR
                    }
                    is PhotoSolve.Reply.BadFormat -> {
                        message = r.detail + "\n（可换个模型重试，或把算式拍得更清晰）"
                        phase = PhotoPhase.ERROR
                    }
                }
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    /** 本地路径：ML Kit 离线识别 → 文本合并清洗 → 给用户确认 */
    fun solveLocally(p: PhotoImage.Prepared) {
        phase = PhotoPhase.BUSY
        message = ""
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { MlKitOcr.recognize(p.bitmap) }
                localRaw = r.rawText
                localModel = r.model
                confirmText = r.cleaned
                phase = PhotoPhase.CONFIRM
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    fun processUri(uri: Uri) {
        phase = PhotoPhase.BUSY
        message = ""
        scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { PhotoImage.prepare(ctx.contentResolver, uri) }
                prepared = p
                if (mode == SolveMode.LOCAL) solveLocally(p) else solveWithApi(p)
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    // 相册：GetContent 兼容写法（不申请任何存储权限，走系统选择器）
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) processUri(uri) }

    // 相机：TakePicture = MediaStore.ACTION_IMAGE_CAPTURE + FileProvider，不申请 CAMERA 权限
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val u = pendingCameraUri
        if (ok && u != null) processUri(u) else if (!ok) phase = PhotoPhase.IDLE
    }

    fun launchCamera() {
        try {
            val dir = File(ctx.cacheDir, "photo").apply { mkdirs() }
            val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (t: Throwable) {
            message = "无法调起系统相机：${t.message ?: "请改用相册选择"}"
            phase = PhotoPhase.ERROR
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            // 顶栏：返回 + 标题
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, c.keyEdge),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.keyNeutral, contentColor = c.bodyInk,
                    ),
                    modifier = Modifier.height(38.dp),
                ) { Text("← 返回", fontSize = 13.sp) }
                Spacer(Modifier.weight(1f))
                Text("拍照解题", color = c.bodyInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(38.dp))
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // ---- 识别路径选择：两条路并存、标注清楚 ----
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { mode = SolveMode.LOCAL },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp, if (mode == SolveMode.LOCAL) ShiftOrange else c.keyEdge,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == SolveMode.LOCAL) ShiftOrange else c.keyNeutral,
                            contentColor = if (mode == SolveMode.LOCAL) ShiftOrangeInk else c.bodyInk,
                        ),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("本地识别\n离线·免费", fontSize = 12.5.sp, lineHeight = 16.sp) }
                    Button(
                        onClick = { mode = SolveMode.API },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp, if (mode == SolveMode.API) ShiftOrange else c.keyEdge,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == SolveMode.API) ShiftOrange else c.keyNeutral,
                            contentColor = if (mode == SolveMode.API) ShiftOrangeInk else c.bodyInk,
                        ),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("用 API 识别\n更准·需配置", fontSize = 12.5.sp, lineHeight = 16.sp) }
                }
                Spacer(Modifier.height(10.dp))

                // ---- API 路径但没配置：明确说明，不假装能用 ----
                if (mode == SolveMode.API && !vm.apiReady()) {
                    Text(
                        "还没配置解题 API，这条路不可用（本地识别不需要配置，可直接用）。",
                        color = c.lcdError, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "API 识别会把照片发给一个兼容 OpenAI chat/completions 接口的视觉模型服务。" +
                            "请先去设置里填 Base URL、API Key 和模型名（有预设可一键填充）。",
                        color = c.keyNeutralInk, fontSize = 12.5.sp, lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            vm.goto(Screen.CALC)
                            vm.openOverlay(Overlay.SETTINGS)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ShiftOrange, contentColor = ShiftOrangeInk,
                        ),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) { Text("去设置里配置解题 API", fontSize = 14.sp) }
                    Spacer(Modifier.height(16.dp))
                    return@Column
                }

                // API 路径：当前配置摘要（Key 打码）
                if (mode == SolveMode.API) {
                    Text(
                        "当前服务：${vm.apiBaseUrl.trim()} ｜ 模型：${vm.apiModel.trim()} ｜ " +
                            "Key：${maskKey(vm.apiKey.trim())}（明文存在本机）",
                        color = c.keyNeutralInk.copy(alpha = 0.7f), fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // 两个取图入口（两条路共用）
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { launchCamera() },
                        enabled = phase != PhotoPhase.BUSY,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.keyNeutral, contentColor = c.bodyInk,
                        ),
                        border = BorderStroke(1.dp, c.keyEdge),
                        modifier = Modifier.weight(1f).height(64.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            KeyGlyph(KeyIcon.CAMERA, c.bodyInk, 22.dp)
                            Text("拍照", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = phase != PhotoPhase.BUSY,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.keyNeutral, contentColor = c.bodyInk,
                        ),
                        border = BorderStroke(1.dp, c.keyEdge),
                        modifier = Modifier.weight(1f).height(64.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            KeyGlyph(KeyIcon.GALLERY, c.bodyInk, 22.dp)
                            Text("相册", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // 图片预览 + 压缩信息
                prepared?.let { p ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            bitmap = p.bitmap.asImageBitmap(),
                            contentDescription = "待识别图片",
                            modifier = Modifier
                                .height(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, c.keyEdge, RoundedCornerShape(8.dp)),
                        )
                        Column {
                            Text(
                                "原图 ${p.origWidth}×${p.origHeight} → 压缩 ${p.width}×${p.height}",
                                color = c.keyNeutralInk, fontSize = 11.sp,
                            )
                            Text(
                                "JPEG ${p.jpeg.size / 1024} KB（base64 约 ${PhotoSolve.base64Length(p.jpeg.size) / 1024} KB）",
                                color = c.keyNeutralInk.copy(alpha = 0.7f), fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                when (phase) {
                    PhotoPhase.BUSY -> Text(
                        when {
                            prepared == null -> "正在处理图片…"
                            mode == SolveMode.LOCAL -> "正在本地离线识别（${
                                if (localModel.isBlank()) "ML Kit" else localModel
                            }）…"
                            else -> "正在识别与计算（${vm.apiModel.trim()}）…"
                        },
                        color = c.keyNeutralInk, fontSize = 13.sp,
                    )
                    PhotoPhase.ERROR -> {
                        Text(message, color = c.lcdError, fontSize = 12.5.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "点上方「拍照 / 相册」重新选择图片。",
                            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
                        )
                    }
                    PhotoPhase.CONFIRM -> {
                        // 本地识别：先看一眼再确认求值（不默默吃进去）
                        Text(
                            "本地识别结果（$localModel），请核对后再求值：",
                            color = c.bodyInk, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        // 批次 K3-B：识别结果确认也走自然书写输入框（自家键盘 + 光标）
                        NumField("表达式（可修改后再求值）", confirmText, { confirmText = it }, Modifier.fillMaxWidth())
                        if (localRaw.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.lcdBg)
                                    .border(1.dp, c.lcdEdge, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                            ) {
                                Text(
                                    "识别原文：${localRaw.trim()}",
                                    color = c.lcdDim, fontSize = 11.5.sp, lineHeight = 16.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val expr = confirmText.trim()
                                if (expr.isEmpty()) {
                                    message = "表达式是空的，请输入或重新选择图片。"
                                    phase = PhotoPhase.ERROR
                                    return@Button
                                }
                                solvedExpr = expr
                                solvedResult = ""
                                solvedExplain = ""
                                vm.applyPhotoExpr(expr, "本地识别（ML Kit · $localModel）")
                                phase = PhotoPhase.DONE
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = c.keyEquals, contentColor = c.keyEqualsInk,
                            ),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                        ) { Text("确认：回填主行并求值", fontSize = 14.sp) }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                phase = PhotoPhase.IDLE
                                prepared = null
                                localRaw = ""
                                confirmText = ""
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.keyEdge),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = c.keyNeutral, contentColor = c.bodyInk,
                            ),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                        ) { Text("不对，重新选图", fontSize = 13.sp) }
                    }
                    PhotoPhase.DONE -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.lcdBg)
                                .border(1.dp, c.lcdEdge, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                        ) {
                            Column {
                                Text("识别表达式：$solvedExpr", color = c.lcdFg, fontFamily = Mono, fontSize = 14.sp)
                                if (solvedResult.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("模型结果：$solvedResult", color = c.lcdFg, fontFamily = Mono, fontSize = 14.sp)
                                }
                                if (solvedExplain.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(solvedExplain, color = c.lcdDim, fontSize = 12.sp, lineHeight = 17.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "表达式已回填到主计算行并完成求值。",
                            color = c.keyNeutralInk, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onBack,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = c.keyEquals, contentColor = c.keyEqualsInk,
                            ),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) { Text("回到计算器查看结果", fontSize = 14.sp) }
                    }
                    PhotoPhase.IDLE -> Text(
                        if (mode == SolveMode.LOCAL) {
                            "拍一张算式照片（或从相册选一张），本地离线识别后会先给你看识别结果，" +
                                "确认无误再回填计算行求值。"
                        } else {
                            "拍一张算式照片（或从相册选一张），识别后会自动算出来并回填到计算行。"
                        },
                        color = c.keyNeutralInk.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 18.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    if (mode == SolveMode.LOCAL) {
                        "本地识别完全在手机上离线进行（ML Kit 模型随安装包发布），照片不会上传。" +
                            "对印刷体算式效果较好；手写体、根号、分数等二维结构识别能力有限。"
                    } else {
                        "注意：照片会被压缩后发送给你在设置里配置的服务商（最长边 1600px，JPEG 85）。"
                    },
                    color = c.keyNeutralInk.copy(alpha = 0.55f), fontSize = 10.5.sp, lineHeight = 15.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Key 打码显示（只露前 4 位） */
private fun maskKey(k: String): String =
    if (k.isEmpty()) "（未填）" else if (k.length <= 4) "****" else k.substring(0, 4) + "****"

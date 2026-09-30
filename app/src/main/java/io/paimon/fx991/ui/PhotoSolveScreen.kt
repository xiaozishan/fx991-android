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
import androidx.compose.foundation.layout.heightIn
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
// 批次 E：拍照解题界面
// 链路：相机/相册取图 → 压缩(≤1600px, JPEG 85) → base64 data URI →
//       POST {base}/chat/completions（协程 IO，不碰主线程）→
//       解析 <EXPR>/<RESULT>/<EXPLAIN> → 回填主计算行并求值
// ---------------------------------------------------------------------------

private enum class PhotoPhase { IDLE, BUSY, ERROR, DONE }

@Composable
fun PhotoSolveScreen(vm: CalcViewModel, onBack: () -> Unit) {
    val c = LocalCalcColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(PhotoPhase.IDLE) }
    var prepared by remember { mutableStateOf<PhotoImage.Prepared?>(null) }
    var message by remember { mutableStateOf("") }
    var solvedExpr by remember { mutableStateOf("") }
    var solvedResult by remember { mutableStateOf("") }
    var solvedExplain by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun fail(t: Throwable) {
        message = when (t) {
            is PhotoHttpException -> t.message ?: "HTTP ${t.code}"
            is PhotoApiException -> t.message ?: "请求失败"
            is PhotoImage.PhotoImageError -> t.message ?: "图片处理失败"
            else -> "出现意外错误：${t.message ?: t.javaClass.simpleName}"
        }
        phase = PhotoPhase.ERROR
    }

    fun solve(p: PhotoImage.Prepared) {
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

    fun processUri(uri: Uri) {
        phase = PhotoPhase.BUSY
        message = ""
        scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { PhotoImage.prepare(ctx.contentResolver, uri) }
                prepared = p
                solve(p)
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
                if (!vm.apiReady()) {
                    // 没配 API：明确说明，不假装能用
                    Text(
                        "还没配置解题 API，拍照解题不可用。",
                        color = c.lcdError, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这个功能需要把照片发给一个兼容 OpenAI chat/completions 接口的视觉模型服务。" +
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

                // 已配置：当前配置摘要（Key 打码）
                Text(
                    "当前服务：${vm.apiBaseUrl.trim()} ｜ 模型：${vm.apiModel.trim()} ｜ " +
                        "Key：${maskKey(vm.apiKey.trim())}（明文存在本机）",
                    color = c.keyNeutralInk.copy(alpha = 0.7f), fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))

                // 两个取图入口
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
                        if (prepared == null) "正在处理图片…" else "正在识别与计算（${vm.apiModel.trim()}）…",
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
                        "拍一张算式照片（或从相册选一张），识别后会自动算出来并回填到计算行。",
                        color = c.keyNeutralInk.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 18.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "注意：照片会被压缩后发送给你在设置里配置的服务商（最长边 1600px，JPEG 85）。",
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

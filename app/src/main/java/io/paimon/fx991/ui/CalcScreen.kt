package io.paimon.fx991.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.paimon.fx991.AppTheme
import io.paimon.fx991.CalcViewModel
import io.paimon.fx991.DisplayMode
import io.paimon.fx991.FuncKind
import io.paimon.fx991.Overlay
import io.paimon.fx991.PrecisionMode
import io.paimon.fx991.Screen
import io.paimon.fx991.engine.AngleMode
import io.paimon.fx991.engine.API_PRESETS
import io.paimon.fx991.engine.ApiConfig
import io.paimon.fx991.engine.ReleaseInfo
import io.paimon.fx991.engine.NumberNotation
import io.paimon.fx991.engine.SciConstants
import io.paimon.fx991.engine.SiPrefixes
import io.paimon.fx991.engine.UnitConvert
import io.paimon.fx991.engine.label

const val APP_VERSION = "1.10.0-updatecheck"
const val APP_REPO = "https://github.com/xiaozishan/workspace"

@Composable
fun CalcApp(vm: CalcViewModel = viewModel()) {
    val dark = when (vm.settings.theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    CalcTheme(darkTheme = dark) {
        val c = LocalCalcColors.current
        // 根 Surface 铺满整屏（含状态栏 / 导航栏区域）——背景色 edgeto-edge，
        // 具体内容各自用 Modifier.safeAreaPadding() 避让系统 Insets。
        CompositionLocalProvider(LocalNatInput provides vm.natInput) {
        Surface(Modifier.fillMaxSize(), color = c.body) {
            Box(Modifier.fillMaxSize()) {
                when (vm.screen) {
                    Screen.ODE -> OdeScreen(onBack = { vm.goto(Screen.CALC) })
                    Screen.CMPLX -> CmplxScreen(onBack = { vm.goto(Screen.CALC) })
                    Screen.MATRIX -> MatrixScreen(onBack = { vm.goto(Screen.CALC) }, store = vm.matrixStore)
                    Screen.VECTOR -> VectorScreen(onBack = { vm.goto(Screen.CALC) }, store = vm.vectorStore)
                    Screen.STAT -> StatScreen(onBack = { vm.goto(Screen.CALC) })
                    Screen.DISTR -> DistrScreen(onBack = { vm.goto(Screen.CALC) })
                    Screen.FUNC_HELP -> FuncHelpScreen(vm, onBack = { vm.goto(Screen.CALC) })
                    // ---- 批次 D ----
                    Screen.EQUATION -> EquationScreen(vm, onBack = { vm.goto(Screen.CALC) })
                    Screen.BASEN -> BaseNScreen(onBack = { vm.goto(Screen.CALC) })
                    Screen.TABLE -> TableScreen(onBack = { vm.goto(Screen.CALC) })
                    Screen.RATIO -> RatioScreen(onBack = { vm.goto(Screen.CALC) })
                    // ---- 批次 E ----
                    Screen.PHOTO_SOLVE -> PhotoSolveScreen(vm, onBack = { vm.goto(Screen.CALC) })
                    // ---- 批次 G ----
                    Screen.ALGEBRA -> AlgebraScreen(vm, onBack = { vm.goto(Screen.CALC) })
                    Screen.FOURIER -> FourierScreen(onBack = { vm.goto(Screen.CALC) })
                    Screen.CPLXF -> CplxFuncScreen(onBack = { vm.goto(Screen.CALC) })
                    else -> CalcSurface(vm)
                }
                when (vm.overlay) {
                    Overlay.NONE -> Unit
                    Overlay.MODE -> ModeMenuOverlay(vm)
                    Overlay.SETTINGS -> SettingsPanel(vm)
                    Overlay.MORE -> MorePanel(vm)
                    Overlay.PRO -> ProPanel(vm)
                    Overlay.HISTORY -> HistoryPanel(vm)
                    Overlay.FUNC -> FuncPanel(vm)
                    Overlay.HELP -> HelpPanel(vm)
                    Overlay.STO -> StoPanel(vm)
                    Overlay.CONST -> ConstPanel(vm)
                    Overlay.CONV -> UnitConvPanel(vm)
                    Overlay.SI -> SiPanel(vm)
                    Overlay.CLRCONFIRM -> ClearAllConfirmPanel(vm)
                }
                // 批次 K3-B：二级界面字段被点中后，浮出 App 自己的键盘面板
                // （不弹系统输入法；画在覆盖层之上，功能对话框里的字段也能用）
                val ni = vm.natInput
                if (ni.focused != null) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        NatKeyboardPanel(ni, Modifier.fillMaxWidth())
                    }
                }
                // 批次 K5：启动静默检查发现新版 → 弹提示（被「稍后」关掉的同版本不再弹；
                // 有其他覆盖层开着时也不抢焦点）
                val ua = vm.updateAvailable
                if (ua != null && ua.tag != vm.updateDismissedTag && vm.overlay == Overlay.NONE) {
                    UpdatePromptPanel(vm, ua)
                }
            }
        }
        }
    }
}

// ---------------------------------------------------------------------------
// 主计算界面：避让系统 Insets + 自适应可用高度
// ---------------------------------------------------------------------------

/**
 * 主计算界面。
 *
 * **避让**：用 [safeAreaPadding]（`WindowInsets.safeDrawing`）避开状态栏（顶）、
 * 导航栏 / 手势条（底）、横屏挖孔（左右），键盘弹出时顺带避开 IME —— 都是系统真实上报的值，
 * 不写死高度，所以顶栏那排键（菜单 / PRO / Σ / 设置 / 拍照 / 更多）不会被状态栏遮住。
 *
 * **自适配**：按扣掉安全区后的真实可用高度选布局。
 *  - 高度富裕（常见竖屏）→ 权重布局：顶栏固定，LCD [SafeArea.LCD_WEIGHT]，键盘占其余；
 *  - 横屏 / 小屏→ 紧凑布局：LCD 与按键行都用最小高度，整体可滚动，不把键盘压扁、不溢出屏幕。
 */
@Composable
private fun CalcSurface(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
    ) {
        if (SafeArea.needsScrollLayout(maxHeight.value.toInt())) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                UtilityBar(vm)
                LcdDeck(vm, Modifier.fillMaxWidth().height(SafeArea.MIN_LCD_DP.dp))
                KeypadDeck(
                    vm = vm,
                    modifier = Modifier.fillMaxWidth(),
                    rowHeight = SafeArea.rowHeightDp(0).dp,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                UtilityBar(vm)
                LcdDeck(vm, Modifier.fillMaxWidth().weight(SafeArea.LCD_WEIGHT))
                KeypadDeck(vm, Modifier.fillMaxWidth().weight(1f - SafeArea.LCD_WEIGHT))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 覆盖层通用外壳
// ---------------------------------------------------------------------------

@Composable
private fun PanelCard(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalCalcColors.current
    // 月影铺满整屏（含系统栏区域）；面板本身再按安全区收进来 —— 顶到底都不会被状态栏 / 导航栏遮住。
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .safeAreaPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.86f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.body)
                    .border(1.dp, c.keyEdge, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text(title, color = c.bodyInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, color = c.keyNeutralInk.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = ShiftOrange,
                    ),
                    border = BorderStroke(1.dp, ShiftOrange),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                ) { Text("关闭", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun MenuButton(label: String, hint: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalCalcColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, c.keyEdge),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.keyNeutral,
            contentColor = c.bodyInk,
            disabledContainerColor = c.keyNeutral.copy(alpha = 0.4f),
            disabledContentColor = c.bodyInk.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 14.sp, maxLines = 1)
            if (hint != null) Text(hint, fontSize = 11.sp, color = c.keyNeutralInk.copy(alpha = 0.7f))
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BodyText(text: String) {
    Text(
        text,
        color = LocalCalcColors.current.keyNeutralInk,
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
    )
    Spacer(Modifier.height(10.dp))
}

// ---------------------------------------------------------------------------
// ① 菜单：列出所有模式（批次 D 后全部可用）
// ---------------------------------------------------------------------------

@Composable
private fun ModeMenuOverlay(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    PanelCard(
        title = "选择模式 MODE",
        subtitle = "全部模式均已实现",
        onClose = { vm.closeOverlay() },
    ) {
        modeEntries().forEach { e ->
            MenuButton(
                label = e.title,
                hint = "进入",
                enabled = e.screen != null,
                onClick = { e.screen?.let { vm.goto(it) } },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "批次 D 新增：方程 EQN · 基数换算 BASE-N · 函数表 TABLE · 比例 RATIO；" +
                "批次 C：复数 / 矩阵 / 向量 / 统计 / 分布 / 函数帮助（SHIFT 层 2 / 4 / 5 / 1 / 3 / 6 键直达）",
            color = c.keyNeutralInk.copy(alpha = 0.65f),
            fontSize = 11.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// ① 设置面板
// ---------------------------------------------------------------------------

@Composable
private fun SettingsPanel(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    PanelCard(title = "设置", subtitle = "主题 · 角度制 · 显示精度 · 分数显示 · 按键震动 · 检查更新", onClose = { vm.closeOverlay() }) {
        Text("主题", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ChoiceChips(
            options = AppTheme.entries.map { it.title },
            selected = AppTheme.entries.indexOf(vm.settings.theme),
            onSelect = { vm.setTheme(AppTheme.entries[it]) },
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = c.keyEdge)
        Spacer(Modifier.height(14.dp))

        Text("角度制", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ChoiceChips(
            options = AngleMode.entries.map { it.label },
            selected = AngleMode.entries.indexOf(vm.angleMode),
            onSelect = { vm.setAngle(AngleMode.entries[it]) },
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = c.keyEdge)
        Spacer(Modifier.height(14.dp))

        Text("显示精度", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ChoiceChips(
            options = PrecisionMode.entries.map { it.title },
            selected = PrecisionMode.entries.indexOf(vm.settings.precision),
            onSelect = { vm.setPrecision(PrecisionMode.entries[it]) },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (vm.settings.precision == PrecisionMode.DECIMALS) "小数位数" else "有效数字位数",
                color = c.chromeInk, fontSize = 12.sp,
            )
            Stepper(
                value = if (vm.settings.precision == PrecisionMode.DECIMALS) vm.settings.decimals else vm.settings.sigDigits,
                onDec = {
                    if (vm.settings.precision == PrecisionMode.DECIMALS) vm.setDecimals(vm.settings.decimals - 1)
                    else vm.setSigDigits(vm.settings.sigDigits - 1)
                },
                onInc = {
                    if (vm.settings.precision == PrecisionMode.DECIMALS) vm.setDecimals(vm.settings.decimals + 1)
                    else vm.setSigDigits(vm.settings.sigDigits + 1)
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = c.keyEdge)
        Spacer(Modifier.height(14.dp))

        Text("分数显示（与 S⇔D 联动）", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ChoiceChips(
            options = listOf("假分数", "带分数", "小数"),
            selected = DisplayMode.entries.indexOf(vm.displayMode),
            onSelect = { vm.chooseDisplayMode(DisplayMode.entries[it]) },
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = c.keyEdge)
        Spacer(Modifier.height(14.dp))

        Text("数字显示模式（科学记数，与 ENG 键联动）", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ChoiceChips(
            options = listOf("普通", "科学记数", "工程记数"),
            selected = NumberNotation.entries.indexOf(vm.settings.notation),
            onSelect = { vm.setNotation(NumberNotation.entries[it]) },
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = c.keyEdge)
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("按键震动", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("按下按键时的触感反馈", color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp)
            }
            Switch(checked = vm.settings.vibration, onCheckedChange = { vm.setVibration(it) })
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = c.keyEdge)
        Spacer(Modifier.height(14.dp))

        // ---- 批次 K5：检查更新 ----
        Text("检查更新", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("当前版本：$APP_VERSION", color = c.bodyInk, fontSize = 12.sp)
        if (vm.latestVersionText.isNotEmpty()) {
            Text("最新版本：${vm.latestVersionText}", color = c.bodyInk, fontSize = 12.sp)
        }
        if (vm.updateCheckResult.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                vm.updateCheckResult,
                color = if (vm.updateAvailable != null) ShiftOrange else c.bodyInk,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.checkUpdateNow() },
                enabled = !vm.updateCheckBusy,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ShiftOrange),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = ShiftOrange,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(40.dp),
            ) { Text(if (vm.updateCheckBusy) "检查中…" else "立即检查", fontSize = 13.sp) }
            vm.updateAvailable?.let { info ->
                val ctx = LocalContext.current
                Button(
                    onClick = {
                        val url = info.apkUrl.ifEmpty { info.pageUrl }
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShiftOrange,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(40.dp),
                ) { Text("下载 ${info.tag}", fontSize = 13.sp, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("启动时自动检查更新", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("每 12 小时最多检查一次；有新版才提示", color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp)
            }
            Switch(checked = vm.autoCheckUpdate, onCheckedChange = { vm.setAutoCheck(it) })
        }
        Spacer(Modifier.height(6.dp))
        ApiSettingsSection(vm)
    }
}

// ---------------------------------------------------------------------------
// 批次 K5：发现新版本的提示弹层（启动静默检查 / 手动检查共用）
// ---------------------------------------------------------------------------

@Composable
private fun UpdatePromptPanel(vm: CalcViewModel, info: ReleaseInfo) {
    val ctx = LocalContext.current
    PanelCard(
        title = "发现新版本 ${info.tag}",
        subtitle = "当前版本 $APP_VERSION",
        onClose = { vm.dismissUpdate() },
    ) {
        if (info.title.isNotEmpty()) BodyText(info.title)
        BodyText("可以到发布页查看更新说明，或直接下载最新 APK 覆盖安装。")
        Spacer(Modifier.height(10.dp))
        MenuButton("下载更新（APK 直链）", enabled = info.apkUrl.isNotEmpty()) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl))) }
        }
        MenuButton("打开发布页面") {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl))) }
        }
        MenuButton("稍后") { vm.dismissUpdate() }
    }
}

// ---------------------------------------------------------------------------
// 批次 E：设置面板里的「解题 API」一节（拍照解题用）
// ---------------------------------------------------------------------------

@Composable
private fun ApiSettingsSection(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = c.keyEdge)
    Spacer(Modifier.height(14.dp))

    Text("解题 API（拍照解题用）", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "⚠️ API Key 以明文保存在本机（SharedPreferences），不加密、不上传到任何我们控制的服务器；" +
            "请勿在公共 / 他人设备上填写。",
        color = c.lcdError, fontSize = 11.sp, lineHeight = 15.sp,
    )
    Spacer(Modifier.height(8.dp))

    ApiField("Base URL", vm.apiBaseUrl) { vm.updateApiBase(it) }
    ApiField("API Key", vm.apiKey, mask = true) { vm.updateApiKey(it) }
    ApiField("模型名", vm.apiModel) { vm.updateApiModel(it) }
    ApiField("超时秒数（${ApiConfig.MIN_TIMEOUT_SEC}–${ApiConfig.MAX_TIMEOUT_SEC}）", vm.apiTimeoutText) {
        vm.updateApiTimeout(it)
    }

    Spacer(Modifier.height(2.dp))
    Text("预设一键填充（只填 Base URL 和模型名，Key 自己填）：",
        color = c.keyNeutralInk.copy(alpha = 0.7f), fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        API_PRESETS.take(2).forEach { p ->
            Button(
                onClick = { vm.applyApiPreset(p) },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(0.dp),
                border = BorderStroke(1.dp, c.keyEdge),
                colors = ButtonDefaults.buttonColors(containerColor = c.keyNeutral, contentColor = c.bodyInk),
            ) { Text(p.name, fontSize = 12.sp, maxLines = 1) }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        API_PRESETS.drop(2).forEach { p ->
            Button(
                onClick = { vm.applyApiPreset(p) },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(0.dp),
                border = BorderStroke(1.dp, c.keyEdge),
                colors = ButtonDefaults.buttonColors(containerColor = c.keyNeutral, contentColor = c.bodyInk),
            ) { Text(p.name, fontSize = 12.sp, maxLines = 1) }
        }
    }
    Spacer(Modifier.height(10.dp))

    Button(
        onClick = { vm.testApiConnection() },
        enabled = !vm.apiTestBusy,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.keyEquals, contentColor = c.keyEqualsInk),
        modifier = Modifier.fillMaxWidth().height(42.dp),
    ) { Text(if (vm.apiTestBusy) "正在测试连接…" else "测试连接", fontSize = 13.sp) }
    if (vm.apiTestResult.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        val ok = vm.apiTestResult.startsWith("连接成功")
        Text(
            vm.apiTestResult,
            color = if (ok) c.keyNeutralInk else c.lcdError,
            fontSize = 12.sp, lineHeight = 17.sp,
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ApiField(label: String, value: String, mask: Boolean = false, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChoiceChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalCalcColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Button(
                onClick = { onSelect(i) },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(0.dp),
                border = BorderStroke(1.dp, c.keyEdge),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sel) ShiftOrange else c.keyNeutral,
                    contentColor = if (sel) ShiftOrangeInk else c.bodyInk,
                ),
            ) { Text(o, fontSize = 12.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun Stepper(value: Int, onDec: () -> Unit, onInc: () -> Unit) {
    val c = LocalCalcColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton("\u2212", onDec)
        Text(
            "$value",
            color = c.bodyInk,
            fontFamily = Mono,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(30.dp),
        )
        StepButton("+", onInc)
    }
}

@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) {
    val c = LocalCalcColors.current
    Button(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, c.keyEdge),
        colors = ButtonDefaults.buttonColors(containerColor = c.keyNeutral, contentColor = c.bodyInk),
    ) { Text(glyph, fontSize = 16.sp) }
}

// ---------------------------------------------------------------------------
// ① 更多 / 关于(PRO) / 拍照说明 / 历史
// ---------------------------------------------------------------------------

@Composable
private fun MorePanel(vm: CalcViewModel) {
    PanelCard(title = "更多", onClose = { vm.closeOverlay() }) {
        MenuButton("关于 / 版本") { vm.openOverlay(Overlay.PRO) }
        MenuButton("使用帮助") { vm.openOverlay(Overlay.HELP) }
        MenuButton("历史记录（${vm.history.size} 条）") { vm.openOverlay(Overlay.HISTORY) }
    }
}

@Composable
private fun ProPanel(vm: CalcViewModel) {
    PanelCard(title = "关于本应用", subtitle = "PRO", onClose = { vm.closeOverlay() }) {
        BodyText("名称：科学计算器（自然书写 LCD）")
        BodyText("版本：$APP_VERSION")
        BodyText("构建：Gradle 8.14 · AGP 8.7.3 · Kotlin 2.0.21 · Compose BOM 2024.09.00")
        BodyText("数值内核：精确有理数（BigInteger）+ 浮点双轨；解析器、RK4、数值算法全部手写")
        BodyText("开源地址：$APP_REPO")
        BodyText(
            "非官方声明：本应用为个人学习用途的界面复刻，与任何品牌厂商均无任何关联；" +
                "未使用任何厂商的商标、logo、字体或官方素材，全部界面与图标均由代码绘制。"
        )
    }
}

@Composable
private fun HistoryPanel(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    PanelCard(
        title = "历史记录",
        subtitle = "点一条回填到输入区（最多保留 40 条）",
        onClose = { vm.closeOverlay() },
    ) {
        if (vm.history.isEmpty()) {
            BodyText("还没有记录。")
        } else {
            vm.history.forEachIndexed { i, h ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { vm.selectHistory(h.expr) },
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, c.keyEdge),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.keyNeutral,
                            contentColor = c.bodyInk,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("${i + 1}. ${h.expr}", fontFamily = Mono, fontSize = 12.sp, maxLines = 1)
                            Text("= ${h.result}", fontFamily = Mono, fontSize = 11.sp, maxLines = 1,
                                color = c.keyNeutralInk.copy(alpha = 0.7f))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { vm.removeHistory(i) },
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, c.keyEdge),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = c.lcdError,
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.width(52.dp).height(44.dp),
                    ) { Text("删除", fontSize = 11.sp, maxLines = 1) }
                }
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun HelpPanel(vm: CalcViewModel) {
    PanelCard(title = "使用帮助", onClose = { vm.closeOverlay() }) {
        BodyText("变量：表达式里的自变量统一写作 x（d/dx、∫dx、Σ、SOLVE、Limit 都认 x）。")
        BodyText("∫dx：填 f(x) 与上下限 a、b，可显式设置容差（自适应 Simpson）。")
        BodyText("d/dx：填 f(x) 与 x 值，中心差分 + Richardson 外推。")
        BodyText("Σ：填 f(x) 与整数上下界，Σ(f(x), x, a, b)。")
        BodyText("SOLVE：填 f(x)=0 的 f(x) 与初值，牛顿法为主、不收敛退二分，能还原精确分数。")
        BodyText("Limit：填 f(x) 与 x→，左右极限分别给出，不等时会提示。")
        BodyText("CALC：把当前表达式里的 x、y 代入具体值求值。")
        BodyText("°′″：度分秒 ⇄ 十进制度；只填“度”时按十进制度转度分秒，填了分/秒则按 60 进制合成度。")
        BodyText("极坐标：用 ∠ 直接输入，例如 2∠60 得到直角坐标 1 + 1.732050808i；再按 S⇔D 可切回 r∠θ。")
        BodyText("统一输入面（不需要切模式）：主行可直接写 MatA×MatB、det(MatA)、inv(MatA)、trn(MatA)、VctA·VctB、cross(VctA,VctB)、abs(VctA)、mean(1,2,3)、sd(…)、ssd(…)、normcdf(0,1,1)、binompdf(10,3,0.5) 等；∠ 是真运算符，可以多个（9∠60+5∠6）。")
        BodyText("主行求解：含未知量（x/y/z）且带 = 时按 = 即解方程。如 2x+3=7、x²-3x+2=0（给全部根含复根）、sin(x)=0.5（多点扫描多个根）、2x+y=5, x-y=1（方程组，, 或 ; 分隔）。无解 / 无穷多解会明确告知。等号用 ALPHA + CALC 输入。")
        BodyText("方程模式（MODE 菜单）：多项式方程 2 / 3 / 4 次（含复根，能精确给精确根）；联立线性方程组 2~4 元（高斯消元 + 精确分数，无解 / 无穷多解会明说）。点解可带回主行。")
        BodyText("基数换算 BASE-N（MODE 菜单）：DEC / HEX / BIN / OCT 互转，AND / OR / XOR / XNOR / NOT / NEG 位运算，字长 16 / 32 / 64，负数按补码显示。")
        BodyText("函数表 TABLE（MODE 菜单）：输 f(x) 与起值 / 终值 / 步长出数值表，可开 g(x) 双函数对照，8 行一页翻页。")
        BodyText("比例 RATIO（MODE 菜单）：a:b = c:x 与 a:b = x:d 两种形式，给精确分数解。")
        BodyText("Pol / Rec（+ − 的 SHIFT 层）：Pol(x,y) 给 r、θ；Rec(r,θ) 给 x、y；角度制跟随设置。")
        BodyText("STO（RCL 的 SHIFT 层）：把当前结果存入 A–F / x / y / M，或把变量插入表达式；CLRv（RCL 的 ALPHA 层）清空全部变量；x⇄y（S⇔D 的 SHIFT 层）交换 x 与 y。")
        BodyText("ENG（工程记数）：按一下切到指数为 3 倍数的显示，再按回到普通；数字格式也可在设置里切。")
        BodyText("CONST / CONV / SI（7 / 8 键的 SHIFT、ALPHA 层）：科学常数表 · 单位换算（含温度）· SI 前缀换算；Limit / ∞ 在 9 键。")
        BodyText("Ran# / RanInt（. 键的 SHIFT / ALPHA 层）：Ran# 插入 0–1 随机数；RanInt 指定上下界与次数。")
        BodyText("COPY / PASTE（0 键的 SHIFT / ALPHA 层）：复制 / 粘贴主行表达式；PreAns（Ans 的 ALPHA 层）：上上次结果；History（= 的 SHIFT 层）：历史记录页，可回填 / 单条删除。")
        BodyText("CLR ALL（AC 的 SHIFT 层）：全清（历史 / 变量 / M / 设置）——会先弹确认框；M− 在 M+ 的 SHIFT 层。")
        BodyText("整数与取整：gcd( / lcm(（× ÷ 的 ALPHA 层）· ceil( / floor(（+ − 的 ALPHA 层）· mod(（√x 的 ALPHA 层）；余切 Cot / Cot⁻¹ 在 ENG / ( 的 ALPHA 层；虚数单位 i 在 ENG 的 SHIFT 层。")
        BodyText("函数写法：logb(底,真数) · root(次数,被开方) · cbrt(x) · abs(x) · npr(n,r) · ncr(n,r) · sinh/cosh/tanh 与 asinh/acosh/atanh · 10^ · exp(")
        BodyText("SHIFT 层：键帽上橙字；2nd 与 SHIFT 等价。ALPHA 层为紫字。")
    }
}

// ---------------------------------------------------------------------------
// ③ 批次 B：STO / CONST / CONV / SI / CLR ALL
// ---------------------------------------------------------------------------

@Composable
private fun StoPanel(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    PanelCard(
        title = "存入变量 STO",
        subtitle = "存入当前结果，或把变量插入表达式",
        onClose = { vm.closeOverlay() },
    ) {
        val now = vm.resultText.ifEmpty { vm.previewText.ifEmpty { "（空）" } }
        BodyText("当前结果：$now")
        if (vm.storeMessage.isNotEmpty()) {
            Text(vm.storeMessage, color = c.keyEquals, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
        }
        listOf("A", "B", "C", "D", "E", "F", "x", "y", "M").forEach { name ->
            val stored = vm.variables[name]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name, fontFamily = Mono, color = c.bodyInk, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp),
                )
                Text(
                    if (stored != null) vm.formatNumber(stored) else "—",
                    fontFamily = Mono, color = c.keyNeutralInk.copy(alpha = 0.7f),
                    fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { vm.storeVariable(name) },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, c.keyEdge),
                    colors = ButtonDefaults.buttonColors(containerColor = c.keyNeutral, contentColor = c.bodyInk),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(56.dp).height(38.dp),
                ) { Text("存入", fontSize = 11.sp) }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { vm.insertVariable(name) },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, c.keyEdge),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent, contentColor = ShiftOrange,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(56.dp).height(38.dp),
                ) { Text("插入", fontSize = 11.sp) }
            }
            Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "M 是存储器（与 M+ / M− / MRC 共用）；A–F 与 x / y 可在表达式里直接引用。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}

@Composable
private fun ConstPanel(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    PanelCard(
        title = "科学常数表 CONST",
        subtitle = "共 ${SciConstants.COUNT} 条，点一条插入表达式",
        onClose = { vm.closeOverlay() },
    ) {
        SciConstants.ALL.forEach { k ->
            Button(
                onClick = { vm.insertConstant(k.value) },
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, c.keyEdge),
                colors = ButtonDefaults.buttonColors(containerColor = c.keyNeutral, contentColor = c.bodyInk),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text("${k.symbol}   ${k.name}", fontSize = 12.sp, maxLines = 1)
                    Text(
                        "${CalcEngineFmt(k.value)}  ${k.unit}",
                        fontFamily = Mono, fontSize = 11.sp, maxLines = 1,
                        color = c.keyNeutralInk.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
        }
    }
}

/** 常数表里展示数值用（可用 10 位有效数字） */
private fun CalcEngineFmt(v: Double): String = io.paimon.fx991.engine.CalcEngine.format(v, 10, null)

@Composable
private fun UnitConvPanel(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    var catIdx by remember { mutableStateOf(0) }
    var fromIdx by remember { mutableStateOf(0) }
    var toIdx by remember { mutableStateOf(1) }
    var value by remember { mutableStateOf("1") }

    val cat = UnitConvert.CATEGORIES[catIdx]
    PanelCard(
        title = "单位换算 CONV",
        subtitle = "长度 / 质量 / 时间 / 面积 / 体积 / 温度",
        onClose = { vm.closeOverlay() },
    ) {
        Text("类别", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ChoiceChips(
            options = UnitConvert.CATEGORIES.map { it.name },
            selected = catIdx,
            onSelect = {
                catIdx = it
                fromIdx = 0
                toIdx = 1
            },
        )
        Spacer(Modifier.height(12.dp))
        Text("源单位", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ChipWrap(cat.units.map { "${it.name} ${it.symbol}" }, fromIdx) { fromIdx = it }
        Text("目标单位", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ChipWrap(cat.units.map { "${it.name} ${it.symbol}" }, toIdx) { toIdx = it }
        Spacer(Modifier.height(8.dp))
        NumField("数值", value, { value = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        val parsed = value.trim().toDoubleOrNull()
        if (parsed == null) {
            Text("请输入一个数字", color = c.lcdError, fontSize = 13.sp)
        } else {
            val r = UnitConvert.convert(cat, fromIdx, toIdx, parsed)
            Text(
                "= ${vm.formatNumber(r)} ${cat.units[toIdx].symbol}",
                color = c.bodyInk, fontFamily = Mono, fontSize = 16.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "温度是仿射换算（°C / °F / K 非倍数关系），已单独处理。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}

@Composable
private fun SiPanel(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    var fromIdx by remember { mutableStateOf(SiPrefixes.NONE_INDEX) }
    var toIdx by remember { mutableStateOf(SiPrefixes.ALL.indexOfFirst { it.factor == 1e3 }.coerceAtLeast(0)) }
    var value by remember { mutableStateOf("1") }

    PanelCard(
        title = "SI 前缀换算",
        subtitle = "p / n / μ / m / c / k / M / G …",
        onClose = { vm.closeOverlay() },
    ) {
        Text("源前缀", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ChipWrap(SiPrefixes.ALL.map { it.symbol.ifEmpty { "—" } }, fromIdx) { fromIdx = it }
        Text("目标前缀", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ChipWrap(SiPrefixes.ALL.map { it.symbol.ifEmpty { "—" } }, toIdx) { toIdx = it }
        Spacer(Modifier.height(8.dp))
        NumField("数值", value, { value = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        val parsed = value.trim().toDoubleOrNull()
        if (parsed == null) {
            Text("请输入一个数字", color = c.lcdError, fontSize = 13.sp)
        } else {
            val r = SiPrefixes.convert(fromIdx, toIdx, parsed)
            val f = SiPrefixes.ALL[fromIdx]
            val t = SiPrefixes.ALL[toIdx]
            Text(
                "= ${vm.formatNumber(r)}    （${f.symbol.ifEmpty { "无" }} → ${t.symbol.ifEmpty { "无" }}）",
                color = c.bodyInk, fontFamily = Mono, fontSize = 15.sp,
            )
        }
    }
}

/** 多行芯片选择（单位 / 前缀较多时用） */
@Composable
private fun ChipWrap(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalCalcColors.current
    Column(Modifier.fillMaxWidth()) {
        options.chunked(5).forEachIndexed { rowIdx, chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                chunk.forEachIndexed { i, o ->
                    val idx = rowIdx * 5 + i
                    val sel = idx == selected
                    Button(
                        onClick = { onSelect(idx) },
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(1.dp, c.keyEdge),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sel) ShiftOrange else c.keyNeutral,
                            contentColor = if (sel) ShiftOrangeInk else c.bodyInk,
                        ),
                    ) { Text(o, fontSize = 10.5.sp, maxLines = 1) }
                }
                repeat(5 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun ClearAllConfirmPanel(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    PanelCard(
        title = "全部清除 CLR ALL",
        subtitle = "此操作不可撤销",
        onClose = { vm.closeOverlay() },
    ) {
        BodyText("将清除以下内容：")
        BodyText("· 当前表达式与结果")
        BodyText("· 全部历史记录（当前 ${vm.history.size} 条）")
        BodyText("· 已存变量 A–F / x / y")
        BodyText("· 存储器 M")
        BodyText("· 全部设置（角度制 / 精度 / 分数显示 / 数字格式 / 主题 / 震动）")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.closeOverlay() },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, c.keyEdge),
                colors = ButtonDefaults.buttonColors(containerColor = c.keyNeutral, contentColor = c.bodyInk),
            ) { Text("取消", fontSize = 13.sp) }
            Button(
                onClick = { vm.doClearAll() },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.keyAccent, contentColor = c.keyAccentInk,
                ),
            ) { Text("确认清除", fontSize = 13.sp) }
        }
    }
}

// ---------------------------------------------------------------------------
// ② 数值功能对话框（∫dx / d/dx / Σ / SOLVE / Limit / CALC / °′″ / hyp / Pol / Rec / RanInt）
// ---------------------------------------------------------------------------

@Composable
private fun FuncPanel(vm: CalcViewModel) {
    val d = vm.funcDialog ?: return
    val c = LocalCalcColors.current
    PanelCard(title = d.kind.title, onClose = { vm.closeOverlay() }) {
        if (d.kind == FuncKind.HYPER) {
            BodyText("选一个双曲函数写入表达式（反双曲同列）：")
            listOf("sinh", "cosh", "tanh", "asinh", "acosh", "atanh").forEach { name ->
                MenuButton(label = "$name(", hint = "写入") { vm.insertHyper(name) }
            }
            return@PanelCard
        }
        val labels = d.fields
        if (labels.isNotEmpty()) FuncField(labels[0], d.f) { d.f = it }
        if (labels.size > 1) FuncField(labels[1], d.a) { d.a = it }
        if (labels.size > 2) FuncField(labels[2], d.b) { d.b = it }
        if (labels.size > 3) FuncField(labels[3], d.c) { d.c = it }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { vm.runFunc() },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.keyEquals, contentColor = c.keyEqualsInk),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) { Text(if (d.kind == FuncKind.DMS) "换算" else "计算", fontSize = 14.sp) }

        Spacer(Modifier.height(10.dp))
        if (d.error.isNotEmpty()) {
            Text(d.error, color = c.lcdError, fontSize = 13.sp)
        } else if (d.result.isNotEmpty()) {
            Text(d.result, color = c.bodyInk, fontFamily = Mono, fontSize = 16.sp)
        }
    }
}

@Composable
private fun FuncField(label: String, value: String, onValueChange: (String) -> Unit) {
    // 批次 K3-B：功能对话框字段也走自然书写输入框（自家键盘 + 光标）
    NumField(label, value, onValueChange, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
}

// ---------------------------------------------------------------------------
// 顶栏：菜单 · PRO · Σ · 齿轮 · ± · 相机 ｜ MORE · DEG
// ---------------------------------------------------------------------------

@Composable
private fun UtilityBar(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    val click = rememberKeyClick(vm)
    val items = remember(vm.angleMode) { utilityBar(vm.angleMode) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SafeArea.UTILITY_BAR_DP.dp)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { k ->
            val isPill = k.kind == KeyKind.PRO || k.kind == KeyKind.MORE
            val container = when (k.kind) {
                KeyKind.PRO -> c.chromeActive
                KeyKind.MORE -> Color.Transparent
                else -> c.keyNeutral
            }
            val ink = when (k.kind) {
                KeyKind.PRO -> c.keyShiftInk
                KeyKind.MORE -> ShiftOrange
                else -> c.chromeInk
            }
            Button(
                onClick = { click(k) },
                modifier = Modifier
                    .weight(if (isPill) 1.6f else 1f)
                    .fillMaxHeight(),
                shape = if (isPill) RoundedCornerShape(50) else CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor = ink,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (isPill) 0.dp else 1.dp,
                    pressedElevation = 3.dp,
                ),
                contentPadding = PaddingValues(0.dp),
                border = if (k.kind == KeyKind.MORE) BorderStroke(1.dp, ShiftOrange) else null,
            ) {
                if (k.icon != KeyIcon.NONE) {
                    KeyGlyph(k.icon, ink, 16.dp)
                } else {
                    Text(
                        text = k.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// LCD 显示区
// ---------------------------------------------------------------------------

@Composable
private fun LcdDeck(vm: CalcViewModel, modifier: Modifier) {
    val c = LocalCalcColors.current
    Box(
        modifier
            .background(c.bodyEdge)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        LcdScreen(vm, Modifier.fillMaxSize())
    }
}

@Composable
private fun LcdScreen(vm: CalcViewModel, modifier: Modifier) {
    val c = LocalCalcColors.current
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.lcdBg)
            .border(1.dp, c.lcdEdge, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        StatusBar(vm)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomStart) {
            // 批次 K3-A：编辑态画闪烁光标；求值后（看结果）光标隐藏
            LcdMathLine(
                expr = vm.expression,
                style = NatStyle(lcdFont(vm.expression.length), c.lcdFg),
                modifier = Modifier.fillMaxSize(),
                cursor = if (vm.justEvaluated || vm.overlay != Overlay.NONE) -1 else vm.cursor,
            )
        }
        Spacer(Modifier.height(2.dp))
        ResultLine(vm)
    }
}

private fun lcdFont(len: Int) = when {
    len <= 14 -> 22.sp
    len <= 26 -> 18.sp
    len <= 44 -> 15.sp
    else -> 12.sp
}

@Composable
private fun StatusBar(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            StatusChip("S", c, vm.shiftActive)
            StatusChip("A", c, vm.alphaActive)
            StatusChip("STO", c, vm.stoActive)
            StatusChip("RCL", c, vm.rclActive)
            StatusChip("M", c, vm.memorySet)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            StatusChip(
                if (vm.settings.precision == PrecisionMode.DECIMALS) "FIX-${vm.settings.decimals}"
                else "NORM-${vm.settings.sigDigits}",
                c, true,
            )
            StatusChip(vm.angleMode.label, c, true)
            StatusChip("MATH", c, true)
            if (vm.settings.notation != NumberNotation.NORM) {
                StatusChip(vm.settings.notation.shortLabel, c, true)
            }
            StatusChip(vm.displayMode.shortLabel, c, true)
        }
    }
}

@Composable
private fun StatusChip(text: String, c: CalcColors, on: Boolean) {
    Text(
        text = text,
        color = if (on) c.lcdFg else c.lcdDim.copy(alpha = 0.45f),
        fontFamily = Mono,
        fontSize = 9.5.sp,
        letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ResultLine(vm: CalcViewModel) {
    val c = LocalCalcColors.current
    val showResult = vm.resultText.isNotEmpty()
    val text = if (showResult) vm.resultText else vm.previewText
    val color = when {
        vm.isError -> c.lcdError
        showResult -> c.lcdFg
        else -> c.lcdDim
    }
    Column(Modifier.fillMaxWidth()) {
        // 方程多解：逐条可插入（REFERENCE 第 7 条）
        if (vm.solutionItems.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                vm.solutionItems.forEach { s ->
                    val ins = s.insert
                    Button(
                        onClick = { ins?.let { vm.insertSolution(it) } },
                        enabled = !ins.isNullOrEmpty(),
                        shape = RoundedCornerShape(7.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, c.lcdEdge),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = c.lcdFg,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = c.lcdDim.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.height(26.dp),
                    ) { Text(s.label, fontFamily = Mono, fontSize = 10.5.sp, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.BottomStart) {
            if (text.contains('\n')) {
                // 矩阵结果：多行纯文本
                Text(
                    text,
                    color = color,
                    fontFamily = Mono,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LcdMathLine(
                    expr = text,
                    style = NatStyle(resultFont(text.length), color, bold = showResult && !vm.isError),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (vm.resultNote.isNotEmpty()) {
            Text(vm.resultNote, color = c.lcdDim, fontSize = 9.5.sp, maxLines = 2)
        }
    }
}

private fun resultFont(len: Int) = when {
    len <= 12 -> 28.sp
    len <= 22 -> 22.sp
    else -> 17.sp
}

// ---------------------------------------------------------------------------
// 键盘区：9 行，每颗键都是 Material 按钮
// ---------------------------------------------------------------------------

@Composable
private fun KeypadDeck(vm: CalcViewModel, modifier: Modifier, rowHeight: Dp? = null) {
    val rows = remember { keypadRows() }
    val click = rememberKeyClick(vm)
    Column(modifier.padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 5.dp)) {
        rows.forEach { row ->
            // rowHeight != null 时走紧凑布局（固定行高，整体可滚动）；否则撑满剩余高度（权重布局）
            val rowModifier = if (rowHeight != null) {
                Modifier.fillMaxWidth().height(rowHeight)
            } else {
                Modifier.fillMaxWidth().weight(1f)
            }
            Row(rowModifier) {
                row.forEach { key ->
                    if (key.kind == KeyKind.NAV) {
                        DPad(
                            click = click,
                            modifier = Modifier
                                .weight(key.weight)
                                .fillMaxHeight()
                                .padding(2.dp)
                        )
                    } else {
                        KeyCapButton(
                            key = key,
                            modifier = Modifier
                                .weight(key.weight)
                                .fillMaxHeight()
                                .padding(2.dp),
                            onClick = { click(key) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun keyFace(kind: KeyKind, c: CalcColors): Pair<Color, Color> = when (kind) {
    KeyKind.SHIFT -> c.keyShift to c.keyShiftInk
    KeyKind.ALPHA -> c.keyAlpha to c.keyAlphaInk
    KeyKind.DANGER -> c.keyAccent to c.keyAccentInk
    KeyKind.DIGIT -> c.keyDigit to c.keyDigitInk
    KeyKind.OP -> c.keyOp to c.keyOpInk
    KeyKind.EQUALS -> c.keyEquals to c.keyEqualsInk
    else -> c.keyNeutral to c.keyNeutralInk
}

@Composable
private fun KeyCapButton(key: Key, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalCalcColors.current
    val (bg, fg) = keyFace(key.kind, c)
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 5.dp),
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, c.keyEdge),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (key.shift != null || key.alpha != null) {
                // 批次 K4：橙键（DANGER）上的 SHIFT 橙字会融进底色，改用键面墨色保证可读
                val shiftInk = if (key.kind == KeyKind.DANGER) fg else ShiftOrange
                val alphaInk = if (key.kind == KeyKind.DANGER) fg else AlphaPurple
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    key.shift?.let {
                        Text(it.label, color = shiftInk, fontSize = 7.5.sp, maxLines = 1,
                            fontWeight = FontWeight.Bold)
                    }
                    key.alpha?.let {
                        Text(it.label, color = alphaInk, fontSize = 7.5.sp, maxLines = 1,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (key.icon != KeyIcon.NONE) {
                KeyGlyph(key.icon, fg, 19.dp)
            } else {
                Text(
                    text = key.label,
                    color = fg,
                    fontFamily = Mono,
                    fontSize = keyFontSize(key.label.length) * key.labelScale,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun keyFontSize(len: Int) = when {
    len <= 1 -> 18.sp
    len <= 2 -> 15.sp
    len <= 3 -> 13.sp
    len <= 4 -> 11.sp
    else -> 10.sp
}

// ---------------------------------------------------------------------------
// 五向方向键（带中心确认）
// ---------------------------------------------------------------------------

@Composable
private fun DPad(click: (Key) -> Unit, modifier: Modifier) {
    val c = LocalCalcColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = c.keyNeutral,
        border = BorderStroke(1.dp, c.keyEdge),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.weight(1f))
                PadKey("\u25B2", c, Modifier.weight(1f), KeyAction.PadUp, click)
                Box(Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                PadKey("\u25C0", c, Modifier.weight(1f), KeyAction.PadLeft, click)
                PadKey("\u25CF", c, Modifier.weight(1f), KeyAction.PadOk, click, center = true)
                PadKey("\u25B6", c, Modifier.weight(1f), KeyAction.PadRight, click)
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.weight(1f))
                PadKey("\u25BC", c, Modifier.weight(1f), KeyAction.PadDown, click)
                Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PadKey(
    glyph: String,
    c: CalcColors,
    modifier: Modifier,
    action: KeyAction,
    click: (Key) -> Unit,
    center: Boolean = false,
) {
    Button(
        onClick = { click(Key(glyph, KeyKind.NAV, action)) },
        modifier = modifier.fillMaxHeight().padding(1.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (center) c.keyDigit else Color.Transparent,
            contentColor = c.keyNeutralInk,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(glyph, fontSize = if (center) 12.sp else 11.sp, color = c.keyNeutralInk)
    }
}

// ---------------------------------------------------------------------------
// 动作分发：SHIFT/ALPHA 层优先，其余交给 ViewModel（全部按键都有真行为，无占位键）
// ---------------------------------------------------------------------------

@Composable
private fun rememberKeyClick(vm: CalcViewModel): (Key) -> Unit {
    val haptics = LocalHapticFeedback.current
    val vibrate = vm.settings.vibration
    return { key ->
        val action = when {
            vm.alphaActive && key.alpha != null -> key.alpha!!.action
            vm.shiftActive && key.shift != null -> key.shift!!.action
            else -> key.action
        }
        if (vibrate) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.onAction(action)
    }
}

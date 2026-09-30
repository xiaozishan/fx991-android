# 科学计算器（自然书写 LCD 复刻）· v1.9.0-keymap

Android 科学计算器（Kotlin + Jetpack Compose，界面交互参考一台科学计算器手机 App）。
**不含任何品牌厂商的商标、logo、字体或官方图片资源** —— 全部 UI 与图标由代码绘制。

## 产物

| 项 | 值 |
|:--|:--|
| 绝对路径 | `C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\build\outputs\apk\debug\app-debug.apk` |
| 大小 | 55,175,306 bytes（≈ 52.6 MB，`assembleDebug -PallAbi` 四 ABI；默认单 ABI arm64 约 25 MB） |
| SHA-256 | `A88017FE1056469FDB1208434AD3DF875FF4CD67B3B2C444E26D263F543B934C`（以当次构建为准） |
| 包名 / 版本 | `io.paimon.fx991` · `1.9.0-keymap` |
| 依赖 | 零新增第三方依赖（仍只有批次 F 的 ML Kit 两个 artifact）；未新增图片 / 字体资源 |

## 本批（K4：键位表逐键对齐参照截图）

- SHIFT / ALPHA 层整体重排（原表错位一行）：CONST→7 · CONV/SI→8 · Limit/∞→9 · MATRIX/VECTOR/FUNC HELP→4/5/6 ·
  STAT/CMPLX/DISTR→1/2/3 · nPr/GCD→× · nCr/LCM→÷ · Pol/Ceil→+ · Rec/Floor→− · COPY/PASTE→0 · Ran#/RanInt→. ·
  π/e→Exp · PreAns→Ans · History→= · STO/CLRv→RCL · i/Cot→ENG · %/Cot⁻¹→( · ,/x→) · x⇄y/y→S⇔D · M−/m→M+ · CLR ALL→AC。
- 新功能：cot / acot / gcd / lcm / mod / ceil / floor 进引擎（精确轨含 gcd/lcm 整数精确），∞ 字面量，
  COPY/PASTE（表达式剪贴板）、CLRv（清变量）、x⇄y（交换 x/y）；键位回归 `tools/KeymapTest.java`（310 条）。
- 回归 **1332/1332** 全过（1022 + 310）；`a/b` 主字改为 `x/y`；橙键上的层字改用键面墨色保证可读。

## 上一批（K3：可见光标 + 二级界面自然输入）

**A. 主行与自然字段都有可见光标**
- 光标 = 表达式字符串偏移量（`CursorModel`，纯 Kotlin 可 JVM 测）：左右移动按原子跳格
  （`sin(`、`√(`、`10^` 当一个整体），插入 / 退格 / 前删后光标跟随。
- `buildNatCursor` 把 `Nat.Cursor` 标记插进自然书写布局树：能走进分数分子/分母、根号内、
  上下标、括号组、空槽位；解析失败退化为「纯文本 + 末尾光标」，光标永远不丢。
- 渲染层画 SHIFT 橙闪烁竖线（530ms），`BringIntoViewRequester` 保证横向滚动时光标可见；
  求值后（看结果）光标自动隐藏。主行方向键 ◀▶ = 移光标（▲▼ 仍是历史）。

**B. 二级界面输入框全换自然书写（`NumField` 原签名换实现，17 个界面一次到位）**
- 默认只读显示排版后的式子；点一下进编辑态，**不弹系统输入法**，由 App 自有键盘面板接管
  （数字/四则/模板键全量 + ◀▶⌫），带光标、边输边排版；面板上一键切回系统键盘兜底。
- 多字段焦点：点哪个字段键盘跟哪个，各自文本与光标互不丢；预填值（`0/1/1e-10/pi`）正常显示编辑。
- 线性文本 ↔ 节点树双向转换（`natLinear`）有回归；OpenFunc 型（∫dx/Σ/SOLVE…）的对话框字段
  同样是自然输入框 —— ∫ 的 f(x)/a/b/容差四槽就地编辑。
- 回归：新增 `tools/CursorTest.java`（**81 条**），全量 **1022/1022** 全过（941 + 81）。

## 历史批次（一句话）

- **v1 → v2**：Material 3 键位 + DayNight 双主题（橙 SHIFT / 紫 ALPHA 识别色）；精确分数双轨内核
  （`Rational(BigInteger)`）；自然书写 LCD；ODE（RK4 + 常系数线性解析解）。
- **批次 A**：顶栏 6 项 + SOLVE/∫dx/d/dx/Σ/Limit/CALC/°′″/hyp/logₓy/ʸ√x/³√/10ˣ/eˣ/nPr/nCr/Pol/Rec/∠。
- **批次 B**：STO/CONST/CONV/SI/CLR ALL/History/PreAns/ENG/SCI/Ran#/RanInt/abs。
- **批次 C**：复数/矩阵/向量/统计/分布/函数帮助 六个模式界面；占位键清零。
- **系统栏避让**：edge-to-edge + `Modifier.safeAreaPadding()` 全界面全弹层；小屏/横屏紧凑布局。
- **分数堆叠修复**：删 intrinsic 测量，分数线/根号线 `drawBehind` 按实测宽度画。
- **统一输入面**：`∠` 真运算符；主行直写 MatA×MatB / VctA·VctB / mean / normcdf；主行按 `=` 解方程
  （多项式精确根含复根、超越多根扫描、方程组高斯消元）。
- **批次 D**：方程 EQN / 基数换算 BASE-N / 函数表 TABLE / 比例 RATIO —— 模式菜单 12 项全可用。
- **批次 E**：拍照解题（视觉 API 自由配置，Key 明文存本机有告知）。
- **批次 F**：本地离线 OCR（ML Kit bundled，**工程唯一第三方依赖**，旅行者批准的例外）。
- **批次 G**：GeoGebra 式自定义函数（`f(x)=x^2`、`f(3)`、`f'(2)`）；傅里叶级数；复变函数
  （`i` 进主行、留数 `res`、围道积分 `cint`）。

## 版本 / 构建

AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.14 · JDK 21（`D:\applications\jdk-21`）· compileSdk/targetSdk 36 ·
minSdk 26 · Compose BOM 2024.09.00。

```powershell
cd C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android
& "D:\applications\gradle-8.14\bin\gradle.bat" assembleDebug           # 单 ABI（arm64）
& "D:\applications\gradle-8.14\bin\gradle.bat" assembleDebug -PallAbi  # 四 ABI（模拟器用）
powershell -File tools\run-tests.ps1                                    # 15 套 JVM 回归
```

## 结构（app/src/main/java/io/paimon/fx991/）

- `CalcViewModel.kt` 状态机（表达式 + **光标** / 结果 / Ans·PreAns / 变量 / 历史 / 设置 / 对话框 / `natInput`）
- `engine/` 纯 Kotlin 引擎（词法语法求值双轨、Rational、NumericOps、Tools、Complex/Matrix/Vector/Stat/Distr、
  UnifiedEval、EquationSolver、EqnMode、BaseN、TableGen、RatioOps、OdeSolver、PhotoSolve/PhotoNet、OcrText、
  UserFunctions、FourierOps、ComplexFunc）
- `ui/` `Keys.kt` 键位表 · `CalcScreen.kt` 主界面与覆盖层 · `NaturalDisplay.kt` 自然书写渲染（**含光标**）·
  `NatModel.kt` 布局树解析 + ASCII 投影（带源码区间）· **`NatCursor.kt` 光标模型 + 落点 + natLinear（纯 Kotlin）** ·
  **`NatInput.kt` 自然输入框 + 自有键盘面板** · `Subsystems.kt` / `ModeScreens.kt` / `GeoScreens.kt` /
  `OdeScreen.kt` / `PhotoSolveScreen.kt` 各模式界面 · `Theme.kt` 双主题颜色令牌 · `Insets.kt` 安全区
- `tools/` 15 个 JVM 回归套件 + `run-tests.ps1`

## 表达式引擎（文法速览）

```
expr    -> term (('+'|'-') term)*
term    -> factor (('*'|'/') factor)*
factor  -> unary (unary)*              隐式乘法：2π、3(4+5)
unary   -> ('-'|'+') unary | power
power   -> postfix ('^' unary)?        右结合
postfix -> primary ('!'|'%'|'²'|'³'|'⁻¹')*
primary -> NUM | π | e | Ans | PreAns | M | x | y | z | A–F
         | FUNC '(' expr ')' | FUNC2 '(' expr ',' expr ')' | '√' unary | '(' expr ')'
```

角度制 DEG / RAD / GRAD；结果显示 假分数 ⇄ 带分数 ⇄ 小数（S⇔D）；NORM / SCI / ENG。

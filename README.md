# 科学计算器（自然书写 LCD 复刻）· v2 + 批次 A + 批次 B + 批次 C + 系统栏避让 + 统一输入面 + 批次 D + 批次 E（拍照解题 + API 自由配置）+ 批次 F（本地离线 OCR）

Android 科学计算器。界面与交互参考一台科学计算器手机 App（键位、配色、自然书写 LCD），
**不含任何品牌厂商的商标、logo、字体或官方图片资源** —— 全部 UI 由 Jetpack Compose 手写绘制，
图标也是矢量代码画的（`Canvas` / `Path` / vector drawable）。

## 产物

```
app/build/outputs/apk/debug/app-debug.apk
```

本批（**批次 F：本地离线 OCR 拍照解题**）交付件：

| 项 | 值 |
|:--|:--|
| 绝对路径 | `C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\build\outputs\apk\debug\app-debug.apk` |
| 大小 | 55,443,467 bytes（≈ 52.87 MB，`assembleDebug`） |
| SHA-256 | `1240E410CA2BF58628A94E556DEB75D377DE967AAC0924A7BAB7737D1A7A4745` |
| 包名 / 版本 | `io.paimon.fx991` · versionCode 1 / versionName 1.0.0（应用内版本串 `1.6.0-offline-ocr`） |
| 体积变化 | 批次 E 10,263,864 → 本批 55,443,467 bytes（**+45,179,603 bytes ≈ +43.1 MB**），增量几乎全部来自 ML Kit bundled 识别模型 |

> ⚠️ **本批引入工程的第一个第三方依赖**（旅行者明确要求的例外）：Google ML Kit 文字识别
> （`com.google.mlkit:text-recognition-chinese:16.0.1` + `com.google.mlkit:text-recognition:16.0.1`，
> **bundled 版，模型随 APK 发布、不依赖 Google Play 服务、完全离线**）。原因是「本地离线 OCR」无法用手写代码
> 合理实现。除此之外未再引入任何其他第三方库。APK 内 ML Kit 相关内容：压缩后合计 ≈ 42,995,492 bytes
> （4 个 ABI 的 `libmlkit_google_ocr_pipeline.so` ≈ 41.03 MB + 模型 assets ≈ 1.96 MB），
> 解压后 ≈ 43,551,957 bytes。

上一批（**批次 E：拍照解题 + 解题 API 自由配置**）交付件：

| 项 | 值 |
|:--|:--|
| 绝对路径 | `C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\build\outputs\apk\debug\app-debug.apk` |
| 大小 | 10,263,864 bytes（≈ 9.79 MB，`assembleDebug`） |
| SHA-256 | `CF1D36E38558CC9BE22ED49D3C79858E746B3B3960904EBB737BB8DDF8441F32` |
| 包名 / 版本 | `io.paimon.fx991` · versionCode 1 / versionName 1.0.0（应用内版本串 `1.5.0-photoai`） |

上一批（**批次 D：补齐最后 4 个模式**）交付件：

| 项 | 值 |
|:--|:--|
| 大小 | 10,205,383 bytes |
| SHA-256 | `05FBE6D40B0503AA050841914362584A6D8C2E6644CE2A8E59C717AC16AB0587` |
| 版本串 | `1.4.0-batchD` |

（自然书写分数堆叠显示修复：10,151,202 bytes，SHA-256 `2C62931A273B17725E0DB2AB23C334157C4338870B8E72D0C2295CC9AAEA0A84`；
系统栏避让：9,810,612 bytes，SHA-256 `B06B564F718D0E1083534B9696F12557F83E9EB525F2713C688ED3ABE2937B73`；
批次 C：9,810,588 bytes，SHA-256 `9E93E08D0BB9818D5024F5BB606FE378BE763016111DB8149530B98DD2B123BC`；
批次 B：9,695,900 bytes，SHA-256 `E9D2AA5C79C39ED87EB66B39048438779826B288A9CF1C90500610425CB97ECC`；
批次 A：9,663,132 bytes，SHA-256 `8F66F3D05EBF217E42D430A2B48EEDB069A97A8F0FD7D62978386794E13B5789`。）

## 批次 F 做了什么（本次）

目标：给拍照解题加「**本地离线 OCR**」主路径 —— Google ML Kit 文字识别（中文 bundled），
不联网、不花钱、不需要任何配置；原视觉模型 API 路径保留为兜底。

### 界面两条路并存（拍照解题页顶部二选一，默认本地）

| 路径 | 标注 | 链路 |
|:--|:--|:--|
| **本地识别（离线·免费）** ← 默认 | 按钮「本地识别 离线·免费」 | 拍照/相册 → 压缩（≤1600px）→ ML Kit 离线识别 → `OcrText` 多行合并清洗（复用 `PhotoSolve.normalizeExpr`）→ **先给用户看识别结果（可编辑），确认后**才回填主行 `evaluateNow` 求值 |
| **用 API 识别（更准·需配置）** | 按钮「用 API 识别 更准·需配置」 | 批次 E 原链路不变：base64 → POST chat/completions → `<EXPR>` 解析 → 回填求值；未配置时给配置引导 |

### 实现方式

- `app/build.gradle.kts`：**第一个第三方依赖** —— `com.google.mlkit:text-recognition-chinese:16.0.1`
  （中文模型，兼认拉丁字母/数字）+ `com.google.mlkit:text-recognition:16.0.1`（拉丁模型，整图空白时兜底）。
- 新增 `engine/OcrText.kt`（**纯 Kotlin，可 JVM 回归**）：多行合并、OCR 专有符号修正、小数点误识别修复、
  易混字符纠正；全角→半角 / `×÷` 归一 / `**`→`^` / 去空白 **复用批次 E 的 `PhotoSolve.normalizeExpr`**。
- 新增 `ui/MlKitOcr.kt`（Android 侧封装）：中文 bundled 模型优先，整图空白退回拉丁模型；
  抽出行列表交给 `OcrText.cleanLines`；只能真机验证（Bitmap / ML Kit native），JVM 不测。
- 改写 `ui/PhotoSolveScreen.kt`：路径选择按钮、本地识别的**确认页**（表达式可编辑 + 识别原文对照 +
  「确认：回填主行并求值」/「不对，重新选图」）；API 路径行为保持不变。
- 应用内版本串 `1.6.0-offline-ocr`；相机仍走 `ACTION_IMAGE_CAPTURE`，**不申请 CAMERA 权限**；
  未新增图片 / 字体资源。

### 文本清洗规则清单（engine/OcrText.kt）

**会纠正**：

| 规则 | 例 |
|:--|:--|
| 多行 / 多块合并：去首尾空白、丢空行、按序直接拼接 | `"12+3" × "×4"` → `12+3×4` |
| 全角→半角（数字 / 加减乘除 / 括号 / 幂 / 逗号句号等，复用 normalizeExpr） | `１２＋３４` → `12+34` |
| 根号误识别 `✓ ✔ ∨ ⎷` → `√` | `✓(4)` → `√(4)` |
| 全角等号 `＝` → `=`；数学减号 U+2212 → `-` | `x＋1＝2` → `x+1=2` |
| 中文数字 `〇` → `0` | `1〇2` → `102` |
| 小数点误识别：两个数字之间的 `。` / `·` → `.` | `3。14` → `3.14` |
| 不在数字间的 `。` 丢弃（非法字符，留着必报语法错误） | `3。+2` → `3+2` |
| 易混字符 `O/o`→`0`、`l/I/|`→`1` —— **仅左右至少一侧是数字或小数点时** | `1O2` → `102`、`3l4` → `314` |
| `**` → `^`；ASCII `*` `/` → `×` `÷`；去全部空白（复用 normalizeExpr） | `2**10` → `2^10` |

**故意不纠正**（过度纠正会改变原意；反正用户会先看一眼再确认）：

| 不纠正 | 理由 |
|:--|:--|
| 单独的 `O` / `o` / `l` / `I`（两侧无数字） | 可能是变量 / 函数名一部分；`log`、`sin` 里的字母绝不能动 |
| `,` 在数字间**不**改小数点 | 逗号是引擎双参数函数分隔符（`logb(2,8)`） |
| `x` / `X` **不**改 `×` | `x` 是合法未知数（`3x+1=5`） |
| `S`→`5`、`B`→`8`、`Z`→`2`、`G`→`6`、`q`→`9` | 字形误纠正风险高，字母可能是真变量 |
| `:` **不**改 `÷` | 比例 / 时间场景歧义大 |

### 回归与验证

- 新增 `tools/OcrTextTest.java`（**50 条**）：多行合并 / 全角半角 / 根号等 OCR 符号 / 小数点误识别 /
  易混字符纠正与不纠正边界 / 清洗结果端到端可被引擎求值。
- 全量回归 **834/834** 全过（784 + 50；`powershell -File tools\run-tests.ps1`）。
- `assembleDebug` 绿。APK 55,443,467 bytes（批次 E：10,263,864，**+45,179,603**）；
  ML Kit 相关内容在 APK 内压缩后 ≈ 42,995,492 bytes（`.so` 四 ABI ≈ 41.03 MB + 模型 assets ≈ 1.96 MB）。

### 已知限制（老实说）

- ML Kit 文字识别是**一维文本行识别**：对**印刷体**的加减乘除 / 幂 / 括号算式效果较好；
  对手写体、**根号、分数线、上下标、积分号等二维结构基本认不出来**（会认成一串符号或漏掉），
  这类场景请切「用 API 识别」（视觉模型能理解二维结构）。
- ML Kit 本体依赖 Android 运行时（Bitmap / native .so），**无法 JVM 测**；本轮验证为「编译绿 +
  834 条 JVM 回归全过（纯逻辑部分全覆盖）」，**未做真机 / 模拟器验证**（emulator-5554 被其他任务占用，按要求未动）。
- bundled 模型把 APK 从 ≈9.8 MB 撑到 ≈52.9 MB（4 个 ABI 的 .so 占大头）；如需减重可后续上
  App Bundle / ABI 分包，本批未做。

## 批次 D 做了什么（上一批）

目标：补齐 MODE 菜单里最后 4 个「待实现」模式，并清掉剩余占位 / 死代码 —— **模式菜单 12 项全部可用**。

| # | 模式 | 内容 |
|:--|:--|:--|
| ① | **方程 EQN** | **多项式方程 2 / 3 / 4 次**：系数输入 → 复用主行 `EquationSolver` 的精确根逻辑（有理根 / 根式 / 复根，如 2x²−3x+1=0 → x = 1/2, 1；x²+1=0 → ±i）；3 / 4 次无有理根时 **Durand-Kerner 数值兜底**给全部根（含复根，如 x³−2x−5=0 → 2.094551482, −1.047275741 ± 1.135939889i）。**联立线性方程组 2~4 元**：高斯消元 + 精确分数（如 2x+4y=10, 6x−2y=4 → x = 9/7, y = 13/7），无解 / 无穷多解明确告知。主行求解只到三元 x/y/z，模式界面独立支持四元 w。点一条解可带回主行继续运算 |
| ② | **基数换算 BASE-N** | DEC / HEX / BIN / OCT 四进制互转 + 位运算 and / or / xor / xnor / not / neg；字长 16 / 32 / 64 位可选；负数按补码存储与显示（HEX / BIN / OCT 显示位形，DEC 显示有符号值并附无符号幅值，如 -1 在 16 位下 = FFFF / 1111111111111111 / 177777，附无符号 65535）；输入按字长校验，超字长 / 非法数字明确报错 |
| ③ | **函数表 TABLE** | 输 f(x) 与起值 / 终值 / 步长 → 数值表，8 行一页翻页；可开 g(x) 双函数对照；单点无定义（如 1/x 在 x=0）该格显「错误」不中断整表；行数上限 200；三角函数跟随角度制（界面内可切 DEG / RAD / GRAD） |
| ④ | **比例 RATIO** | a:b = c:x 与 a:b = x:d 两种形式；输入小数当场转有理数，能精确给精确分数并附小数近似（如 4:2 = 3:x → x = 3/2（≈ 1.5））；分母为 0 明确报错 |

### 占位 / 死代码清理

| 项 | 处置 | 理由 |
|:--|:--|:--|
| `KeyAction.ThemeToggle` | **删除** | 没有任何键引用它，分发里是 `-> Unit` 空操作；主题切换走设置面板（三态：跟随系统 / 浅 / 深），原机也没有独立主题键 |
| `KeyIcon.THEME` | **连图标绘制分支一起删除** | 没有任何键引用 |
| `CalcScreen.kt` 动作分发处「占位键弹提示」过时注释 | **改写** | 批次 C 起 `KeyAction.Todo` 已连同类型定义删除，不存在弹提示的占位键 |
| MODE 菜单 4 个 `null` 条目（方程 / 基数换算 / 函数表 / 比例） | **全部换成真入口** | 本批核心任务 |
| 菜单文案「待实现 / 灰显条目为后续批次」 | **移除** | 没有未实现模式了 |

### 实现方式

- 新增 4 个**纯 Kotlin** 引擎文件（无 Compose 依赖 → 可 JVM 直跑回归）：`EqnMode.kt`（系数 → 表达式串 → 复用 `EquationSolver` 精确根；精确化失败走 Durand-Kerner；2~4 元精确高斯消元）、`BaseN.kt`、`TableGen.kt`、`RatioOps.kt`。
- 新增 `ui/ModeScreens.kt` 承载 4 个界面，复用批次 C 的 `SubHeader / NumField / SubButton / ResultBox` 组件；根节点统一 `Modifier.safeAreaPadding()`，深浅主题走同一套颜色令牌。
- `Screen` 枚举 +4；`CalcApp` when 分发 +4；`modeEntries()` 4 个 null → 真入口；应用内版本串 `1.4.0-batchD`。
- 新增 `tools/BatchDTest.java`（**87 条**），全量回归 **691/691** 全过（604 + 87）。

## 自然书写分数堆叠显示修复（上一批）

> 旅行者真机反馈：输入两个分数时，本该并排，实际**竖着叠、贴右边缘、分数线延伸出屏外、左侧大片空白**。

### 根因

原实现把**内在尺寸测量**（`IntrinsicSize.Max`）与**横向滚动**（宽度约束被放开为无限）混用，
同时 `Nat.Frac` 内又用 `fillMaxWidth()` 去撑分数线——三者互锁：
内在尺寸在无限宽约束下无法收敛，分数被迫纵向堆叠；`fillMaxWidth` 又拿到「无限宽」，
分数线长度失控；再叠加无限宽约束下的末端排列（`Arrangement.End`），内容被推到右侧截断。

### 改法（`app/src/main/java/io/paimon/fx991/ui/NaturalDisplay.kt`）

1. **删掉 `IntrinsicSize`**：分数改用自定义 `Layout` 测量——分子/分母各自自然包裹，宽度取二者较大者。
2. **分数线 / 根号上横线改由 `Modifier.drawBehind` 手绘**：在 draw 阶段按**实测出来的 `size.width`** 画线，不再让子 `Box` 去撑宽。
3. **滚动容器里不再出现内在尺寸测量、也不再出现撑满父宽修饰**：LCD 行靠 `Box(contentAlignment = Alignment.BottomEnd)` 在普通约束下贴右；
   滚动状态初始 `Int.MAX_VALUE`，短表达式贴右、超长表达式默认显示结尾并可向左滚。
4. 样式（字号/颜色/粗体/1.5dp 线宽/3dp 侧距）保持原值不动。

### 逐条对应四现象，为何现在会修好

| 真机现象 | 对应修法 | 为何修好 |
|:--|:--|:--|
| 两个分数被压成竖排 | 删 `IntrinsicSize`，分数用自定义 measure 自然包裹 | 不再有「内在尺寸在无限宽约束下无法收敛」的测量塌陷；`Nat.Row` 拿到的每个 `Nat.Frac` 都是**已定自然宽度**的节点，横排就是横排 |
| 紧贴右边缘 / 被截断 | 右对齐改为 `Box(BottomEnd)` + 滚动初值置末 | 不再依赖无限宽约束下的 `Arrangement.End`；内容窄时贴右、超宽时从右端开始并可滚动，不会被推到屏外截断 |
| 分数线算不出宽度 / 延伸出屏 | 分数线改 `drawBehind` 按 `size.width` 画 | 分数线宽度 = 分数节点**实测宽度**（= 分子分母较大者），不再被 `fillMaxWidth` 拉到无限宽 |
| 左侧/中部大片空白 | 宽度由内容决定、右对齐由 Box 承担 | 节点宽度贴合内容，空白不再来自「测量塌陷后内容跑偏」 |

### 回归

- 新增 `tools/NatLayoutTest.java`（16 条）：**源码护栏**——`NaturalDisplay.kt` 不含内在尺寸测量、不含撑满父宽修饰、改用 `drawBehind`、右对齐用 `Box` 的 End 对齐；**结构回归**——单分数 / 多分数并排 / 分数套分数 / 根号套分数 / 分数套根号 / 上标并排 / 超长表达式 的 ASCII 投影。
- 全量回归 **604/604 全过**（原 588 条 + 新增 16 条；`powershell -File tools\run-tests.ps1`）。
- `assembleDebug` 绿。

> 注：布局由 Compose 渲染，JVM 无法端到端验证；上述为**代码层面把「不依赖内在尺寸」做死** + 结构投影回归，
> 未声称「已真机验证」。

## 统一输入面 + 主行求解（`REFERENCE.md` 第 6、7 条）

> 旅行者原话：「都做到一起不行吗，我不想手动切换模式输入」「把公式敲进去，按 `=` 就出解」。
> 核心：把六个子系统从「门」降级成「工具」——不再前置切模式，主计算行一个入口什么都能算。

### ① `∠` 从「整串特例」提升为**表达式里的真运算符**（`NumericOps.PolarForm.evaluate` 旧分支已删）

- 文法新增一层：`term -> polar(('*'|'/') polar)*`、`polar -> factor(('∠'|'·') factor)*` —— `∠` 比 `+ -` 紧、比 `× ÷` 紧，紧贴两侧操作数。
- **可多个**：`9∠60+5∠6` → 两个复数相加 `9.472609 + 8.316871i`；`2∠30×3`、`(1+2)∠90−1` 同理。
- 角度制跟随 DEG / RAD / GRAD；结果落进原有**复数轨**，`S⇔D` 仍在 直角坐标 ⇄ `r∠θ` 之间切。
- 语义变化提醒：以前 `∠` 是「整串最后一个隐式边界」，所以 `1+2∠90` 会被当成 `(1+2)∠90`；现在按真运算符优先级得到 `1+(2∠90)`。

### ② 主行统一输入面（矩阵 / 向量 / 统计 / 分布不再需要切模式）

| 类别 | 主行可直接写 |
|:--|:--|
| 矩阵 | `MatA×MatB`、`MatA+MatB`、`2×MatA`、`det(MatA)`、`inv(MatA)`、`trn(MatA)` |
| 向量 | `VctA·VctB`（点乘运算符 `·`）、`dot(A,B)`、`cross(A,B)`、`VctA+VctB`、`abs(VctA)`（取模） |
| 统计 | `mean(1,2,3)`、`sd(…)`（总体 σ）、`ssd(…)`（样本 s）、`sigma(…)` |
| 分布 | `normpdf` / `normcdf` / `invnorm`、`binompdf` / `binomcdf`、`poissonpdf` / `poissoncdf` |

- 实现：新增 `engine/UnifiedEval.kt`（`CalcValue = Scalar(复数) | MatVal | VecVal`）。**纯标量子树一律交回原来的双轨内核 `ValueEvaluator`**，只有出现新语法时才走联合求值 —— 所以原有标量语义（精确分数、`%`、`√`、排列组合…）一分不差。
- `MatA–MatD` / `VctA–VctD` 的存储从「界面内 `remember`」上提到 `CalcViewModel`，因此 **MATRIX / VECTOR 界面里定义的数据，主行直接就能引用**。
- 模式菜单**保留**（编辑数据表 / 定义变量 / 看图），但不再是计算的前置条件。
- 便捷插入：FUNC HELP 新增 **矩阵 / 向量**、**统计 / 分布**、**方程求解** 三组条目，点一条直接写进主行；`×` 的 ALPHA 层给 `·`，`=` 的 ALPHA 层给 `=`。

### ③ 主行直接求解（判定：含未知量且带 `=` → 当方程；否则普通求值）

- 新增 `engine/EquationSolver.kt`（手写，零依赖）：
  - **一元多项式**：AST → 多变量多项式（精确有理数系数）→ 一次/二次闭式；判别式能开尽给**精确分数**，开不尽给**精确根式**（`x²-2=0` → `±√2`），负判别式给**复根**（`x²+1=0` → `±i`，`x²+2x+2=0` → `-1 ± i`）；三次及以上先用**有理根定理**降阶。
  - **一元超越方程**：在 `[−周期, 3×周期]`（按角度制）上多初值扫描 + 二分细化，**自动列出多个根**并去重（`sin(x)=0.5`（DEG）→ `30°, 150°, 390°, …`）。
  - **方程组**：`,` 或 `;` 分隔；线性方程组走**高斯消元 + 精确分数**（`2x+y=5, x-y=1` → `x = 2, y = 1`）；非线性走多起点数值牛顿。
  - **无解 / 无穷多解**明确告知：`x+1=x+2` → 无解；`2x=2x` → 无穷多解；`x+y=3`（欠定）→ 无穷多解；`x+y=1, x+y=2`（不相容）→ 无解。
- 结果直接回主行（进历史、可继续参与运算）：多解在 LCD 结果显示区**逐条列出**，点一条即插入主行。

### 验收自测（主行一行、不切模式、不给初值）

| 例子 | 结果 |
|:--|:--|
| `9∠60+5∠6` | ✅ `9.472609477 + 8.316871i`（复数相加） |
| `2∠30×3` | ✅ `5.196152423 + 3i` |
| `det(MatA)` | ✅ `-2`（MatA=[[1,2],[3,4]]） |
| `MatA×MatB` | ✅ `[[4,4],[10,8]]` |
| `VctA·VctB` | ✅ `12` |
| `cross(VctA,VctB)` | ✅ `(27, 6, -13)` |
| `normcdf(0,1,1)` | ✅ `0.1586552638`（手写 erf 精度 ~1e-7） |
| `nCr(100,50)` | ✅ `100891344545564193334812497256`（精确大数） |
| `2x+3=7` | ✅ `x = 2` |
| `x²-3x+2=0` | ✅ `x = 1, 2`（已排序） |
| `x²+1=0` | ✅ `x = ±i` |
| `sin(x)=0.5`（DEG） | ✅ `x = -330°, -210°, 30°, 150°, 390°, 510°, 750°, 870°`（多根） |
| `2x+y=5, x-y=1` | ✅ `x = 2, y = 1` |

## 系统栏避让（上一批 · 真机反馈修复）

> 旅行者真机实测反馈：「要做一个避让状态栏，不然上面的点不了」——顶栏那排键（菜单 / PRO / Σ / 设置 / 拍照 / 更多）
> 被系统状态栏遮住，点不到。

**根因**：`targetSdk = 36` 在 Android 15+ 强制 edge-to-edge，窗口铺满整屏，而旧代码只在主计算界面用了
`systemBarsPadding()`，且没在 Activity 里正式声明 edge-to-edge —— 其余界面 / 弹层完全没有避让。

**方案：官方 Compose Insets（读系统真实上报值，不写死高度）**

| 层 | 怎么做 |
|:--|:--|
| Activity | `MainActivity` 调 `enableEdgeToEdge()`，窗口铺满整屏，由内容自己避让 |
| 统一修饰符 | `ui/Insets.kt` 的 `Modifier.safeAreaPadding()` = `windowInsetsPadding(WindowInsets.safeDrawing)`：**顶避状态栏、底避导航栏/手势条、左右避横屏挖孔与系统手势区、键盘弹出时避 IME**，全部由系统上报，刘海 / 挖孔 / 手势条高度都自适应 |
| 覆盖范围 | 主干 8 个界面（主计算 / 微分方程 / 复数 / 矩阵 / 向量 / 统计 / 分布 / 函数帮助）根节点各挂一次；**所有弹层 / 对话框**走 `PanelCard` 统一外壳，在遮罩内层再加一次 —— 一律不漏 |
| 系统栏图标 | `Theme.kt` 里 `SideEffect` 把状态栏 / 导航栏图标明暗跟随**应用内**主题（手动覆盖也正确），不跟系统深浅色脱节 |
| 挖孔 | `themes.xml`（含 `values-night`）加 `windowLayoutInDisplayCutoutMode = shortEdges`，内容可延伸进刘海区，再由 insets 避让 |
| 小屏 / 横屏 | `ui/SafeArea.kt`（纯 Kotlin）按扣掉安全区后的**真实可用高度**选布局：够高→权重布局（LCD 30% / 键盘 70%）；横屏 / 小屏不够高→紧凑布局（LCD 与按键行用最小高度，整体可滚动），不把键盘压扁、不溢出屏幕 |

不改 `REFERENCE.md`；零新增依赖 / 图片 / 字体；只动工程内文件。

## 批次 B 做了什么（本轮）

目标：把剩下 20 个 `KeyAction.Todo` 占位键里的 **14 个**换成真实现，并为全部新功能补 JVM 回归。

### ① 记忆 / 显示（8 项）

| 键 | 行为 |
|:--|:--|
| **STO**（RCL 的 SHIFT） | 打开 STO 面板：把当前结果**存入** A–F / x / y / M，或把变量**插入**表达式；存入后给确认提示（如「已存入 A = 12」）。新增 `Registers`（纯 Kotlin）做寄存器，引擎新增 **A–F 变量引用**（`A+B`、`x*2` 可直接算） |
| **ENG** | 工程记数法显示切换（指数为 3 的倍数，尾数落在 [1,1000)）；再按回到普通 |
| **CLR ALL**（M+ 的 SHIFT） | **先弹确认对话框**（列出将清除的内容），确认后清历史 / 变量 / M / 全部设置 |
| **PreAns**（+ 的 SHIFT） | 上上次结果；与 `Ans` 并存，引擎新增 `PreAns` 记号，求值后 `preAns ← ans` 推移 |
| **History**（− 的 SHIFT） | 历史记录页：列表 + 点一条回填 + **单条删除** |
| **Exp** | 科学记数法输入键，插入 `×10^` 结构；自然书写会渲染成真上标（`2×10⁵`） |
| **\|x\|**（hyp 的 SHIFT） | 绝对值，插入 `abs(`；引擎新增 `abs`（精确轨保持精确，`abs(-1÷4) → 1/4`） |
| **科学记数**（显示模式） | 设置面板新增**数字显示模式**：普通 / 科学记数 SCI / 工程记数 ENG，与 ENG 键、显示精度共用同一状态，状态栏显示当前记数法 |

### ② 常数 / 单位（3 项）

| 键 | 行为 |
|:--|:--|
| **CONST**（ENG 的 SHIFT） | 科学常数表 **24 条**（光速 / 普朗克 / 元电荷 / 阿伏伽德罗 / 玻尔兹曼 / 引力常数 / 电子质量 / 质子质量 / 气体常数 / 法拉第 / 真空电容率 / 磁导率 / 大气压 / 斯特藩-玻尔兹曼 …），点一条插入表达式；大/小指数自动写成 `尾数×10^指数` 可求值形式 |
| **CONV**（( 的 SHIFT） | 单位换算：**长度 / 质量 / 时间 / 面积 / 体积 / 温度** 六类；**温度（°C/°F/K）是非倍数关系，单独走仿射分支** |
| **SI**（) 的 SHIFT） | SI 前缀换算 p/n/μ/m/c/d/da/h/k/M/G/T/P/E/Z/Y… |

### ③ 简单数值（2 项）

| 键 | 行为 |
|:--|:--|
| **Ran#**（1 的 SHIFT） | 插入一个 0 ≤ x < 1 的均匀随机数 |
| **RanInt**（Exp 的 SHIFT） | 对话框指定**下界 / 上界 / 次数**，给出 [a,b] 上的整数序列 |

### ④ 坐标（2 项）

| 键 | 行为 |
|:--|:--|
| **Pol**（× 的 SHIFT） | 直角 → 极坐标 `(x,y) → (r,θ)`，角度制跟随设置（DEG/RAD/GRAD） |
| **Rec**（÷ 的 SHIFT） | 极坐标 → 直角 `(r,θ) → (x,y)` |

### 实现方式

- 新建 `engine/Tools.kt`（纯 Kotlin，可 JVM 回归）：`SciConstants` / `UnitConvert` / `SiPrefixes` / `RandomOps` / `Registers`。
- 引擎扩展：`Node.VarRef`（A–F）、`Node.PreAnsRef`、`abs` 函数、`NumberNotation`（NORM/SCI/ENG）+ `scientific()` / `engineering()` / `literal()`。
- 新增 4 个覆盖层：STO / CONST / CONV / SI，外加 CLR ALL 确认框；`Overlay` 枚举、`FuncKind`（POL/REC/RANINT）、`CalculatorSettings.notation` 同步扩展。

## 批次 C 做了什么（本轮 · 最后一批）

目标：把剩下 6 个 `KeyAction.Todo` 占位键换成真实现，**`KeyAction.Todo` 归零**（连类型定义一起删掉）。
六个大子系统各有**独立界面**（参照原机的模式切换观感），可从 MODE 菜单或 SHIFT 层按键进入，均可返回主计算界面。

| # | 子系统 | 入口 | 内容 |
|:--|:--|:--|:--|
| ① | **复数 CMPLX** | SHIFT+5 · MODE 菜单 | a+bi 输入与显示；加减乘除；模（能开尽给精确值）/ 辐角 / 共轭；直角 ⇄ 极坐标（r∠θ）；角度制跟随设置；小数当场转有理数，分数轨保持精确 |
| ② | **矩阵 MATRIX** | SHIFT+7 · MODE 菜单 | MatA–MatD（最大 4×4）；逐格输入/编辑；加减乘；行列式；逆；转置；单位阵 / 零阵；标量乘；整数 / 分数矩阵全程精确 |
| ③ | **向量 VECTOR** | SHIFT+8 · MODE 菜单 | VctA–VctD（三维）；加减；数乘；点积；叉积；模；单位化；夹角（按角度制） |
| ④ | **统计 STAT** | SHIFT+4 · MODE 菜单 | 单变量：n / Σx / Σx² / 均值 / 总体σ / 样本s / 最大最小 / 中位数；双变量：线性回归 y = a + b·x 的 a、b 与相关系数 r；数据表可编辑 / 删除 |
| ⑤ | **分布 DISTR** | SHIFT+6 · MODE 菜单 | 正态（给定 μ, σ, x 求 P/Q/R + 密度）、二项（P(X=k) / P(X≤k) / P(X≥k)）、泊松（同左） |
| ⑥ | **函数帮助 FUNC HELP** | SHIFT+9 · MODE 菜单 | 37 条函数目录 + 语法说明，按类别分组；点一条把语法键插入表达式并回到计算界面 |

### 实现方式

- 新增 6 个**纯 Kotlin** 引擎文件（无 Compose 依赖 → 可 JVM 直跑回归）：`ComplexOps.kt`、`MatrixOps.kt`、
  `VectorOps.kt`、`StatOps.kt`、`DistrOps.kt`、`FuncHelp.kt`。
- 复数 / 矩阵 / 向量共用双轨内核：实部 / 元素 / 分量都是 `Value`（`Exact(Rational) | Floating`），
  新增 `ExactMath` 公共算术后，分数四则保持精确；字面量支持 `a/b` 分数形式。
- 正态分布用手写 `erf`（Abramowitz & Stegun 7.1.26），二项 / 泊松用手写 `lnΓ`（Lanczos），不引任何统计库。
- 新增 `Screen.CMPLX/MATRIX/VECTOR/STAT/DISTR/FUNC_HELP` 与 `KeyAction.GoScreen`；`CalcApp` 用 `when(screen)`
  分发到独立界面；`ui/Subsystems.kt` 承载六个界面。
- 模式菜单里 6 个条目从「待实现」置灰变为可进入；SHIFT 层 4 / 5 / 6 / 7 / 8 / 9 键直接进入对应界面。
- **占位清零**：删掉 `KeyAction.Todo` 类型与全部「未实现」提示分支，源码里已无占位键。

## v2 做了什么

| 步骤 | 内容 |
|:--|:--|
| **S1 外观包** | 每颗按键改为 **Material 3 `Button`**（涟漪 / 状态层 / 圆角 / elevation 走 Material 规范）；键位照参考图整排重排（顶栏 8 项、SHIFT/ALPHA 双功能层、五向方向键、MODE/2nd、三排函数键、橙 DEL/AC）；**DayNight 深浅色联动**（浅色浅机身 + 浅灰绿 LCD / 深色暗机身 + 暗底亮字，两模式都保留橙 SHIFT、紫 ALPHA）；**自适应图标 + `<monochrome>` 单色层**（矢量代码，浅/深两套背景） |
| **S2 数值内核** | **精确分数双轨**：小数当场转有理数（589.3→5893/10），`Rational(BigInteger)` 全程精确（+ − × ÷、整数次幂、能开尽的 √），只在无理运算落回 `Double`，分子分母超规模自动退回小数；结果默认分数/带分数，`S⇔D` 在 分数→带分数→小数 间循环；**自然书写 LCD**（分数上下堆叠、√ 带上横线、上标指数） |
| **S3 微分方程** | **MODE 新增「微分方程」入口**：一阶 `dy/dx=f(x,y)`、二阶 `y''=f(x,y,y')` 用 **RK4** 定步长数值解 → 数值表 + 曲线图；常系数线性（二阶以内）给 **特征方程解析解**；符号解 / CAS 不做 |

## 批次 A 做了什么（本轮）

目标：把参考图顶栏 6 项与一批计算键从「点下去弹未实现」换成真实现。

### ① 顶栏 6 项（真 UI）

| 键 | 行为 |
|:--|:--|
| **菜单 ☰** | 弹出 MODE 模式菜单，列出全部模式；已实现的给「进入」，未实现的**置灰并标「待实现」**，点不动 |
| **设置 ⚙** | 设置面板：**主题**（跟随系统 / 浅色 / 深色，实时联动整套配色）、**角度制**（DEG / RAD / **GRAD**）、**显示精度**（有效数字 1–15 / 小数位数 0–12，带步进器）、**分数显示**（假分数 / 带分数 / 小数，与 `S⇔D` 同一个状态）、**按键震动**开关 |
| **更多 MORE** | 二级菜单：关于 / 版本、使用帮助、历史记录入口（历史可点一条回填输入区） |
| **PRO** | 关于页：应用名、版本、构建链、开源地址、**非官方声明** |
| **求和 Σ** | 直接进 Σ 输入对话框：`Σ(f(x), x, a, b)` |
| **拍照解题 📷** | **明确说明页**：讲清为何不提供（离线、零第三方依赖、不申请相机权限），**不假装能用** |

### ② 计算类

| 键 | 实现 |
|:--|:--|
| **SOLVE** | 方程数值求根：牛顿法为主（数值导数），导数退化 / 不收敛自动**退二分法**（扫描变号区间）；能识别成小分母精确分数时给精确根，如 `x²-3x+2=0` → `x = 1` |
| **∫dx** | 自适应 Simpson，容差可显式设置；两端点与中点无定义时明确报错 |
| **d/dx** | 中心差分 + **Richardson 外推**（4 阶） |
| **Σ** | 求和，整数上下界，项数上限 1e6 |
| **Limit** | h→0 外推；**左右极限分别给**，不等时提示「左右不等」 |
| **hyp** | 对话框选 `sinh / cosh / tanh / asinh / acosh / atanh`，写入表达式 |
| **logₓy** | 写入 `logb(底, 真数)`，底/真数均为整数且结果为整数时**保持精确**（`logb(2,8)` → `3`） |
| **ˣ√y** | 写入 `root(次数, 被开方数)`，开得尽保持**精确分数**（`root(2,1÷4)` → `1/2`） |
| **³√x** | 写入 `cbrt(`，负数奇次根照算（`cbrt(-64)` → `-4`），能开尽保精确 |
| **10ˣ** | 写入 `10^` |
| **eˣ** | SHIFT 层接上，写入 `exp(`（v1 的 `^`/`exp(` 本就可算，这里补上专用键） |
| **假分数 / 带分数 ▸r** | 分数显示模式循环（假分数 ⇄ 带分数 ⇄ 小数），与 `S⇔D` 同一状态 |
| **度分秒 °′″** | 对话框双向换算：只填「度」→ 十进制度转度分秒；填了分/秒 → 60 进制合成十进制度。符号记在 `negative` 上，避免 −0 丢号 |
| **极坐标 ∠** | `∠` 可直接写进表达式（`2∠60` → 直角坐标 `1 + 1.732050808i`）；结果出来后按 `S⇔D` 在 **直角 ⇄ r∠θ** 间切回 |
| **nPr / nCr** | SHIFT 层写入 `npr(n,r)` / `ncr(n,r)`，**BigInteger 计算不溢出**（`ncr(100,50)` 精确给 100891344545564193334812497256） |
| **CALC** | 对话框填入 `x` / `y`（可留空），用当前表达式代入求值 |
| **2nd** | 与 `SHIFT` **完全同义**（等价第二功能切换） |

### 实现方式

- 所有数值功能走一个统一的 **`FuncDialog`**：按 `FuncKind` 生成对应字段（`f(x)`、上下限、容差、初值、x→、x/y、度分秒），一个「计算」按钮，结果 / 错误就地显示。
- 新增两个 SHIFT 层函数直接**写进表达式**（`logb(` `root(` `cbrt(` `npr(` `ncr(` `10^` `exp(` `∠`），解析器/求值器同步支持。

## 版本

| 组件 | 版本 |
|:--|:--|
| AGP | 8.7.3 |
| Kotlin | 2.0.21（含 `org.jetbrains.kotlin.plugin.compose`） |
| Gradle | 8.14 |
| JDK（构建用） | 21.0.5（`D:\applications\jdk-21`） |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| Java / Kotlin target | 17 |
| Compose BOM | 2024.09.00 |
| Android SDK | `D:\applications\Android\Sdk` |

**第一个第三方依赖（批次 F，旅行者明确要求的例外）**：Google ML Kit 文字识别 bundled 版
（`com.google.mlkit:text-recognition-chinese:16.0.1` + `com.google.mlkit:text-recognition:16.0.1`），
用于本地离线 OCR，模型随 APK 发布、不依赖 Google Play 服务。除此之外仍然零第三方库：其余只用
AndroidX（core-ktx / activity-compose / lifecycle-viewmodel-compose / compose ui / foundation / material3）。
解析器、有理数、RK4、数值算法全部手写。批次 A 与批次 B 均未新增任何依赖、图片、图标、字体资源；
批次 F 也未新增图片 / 字体资源。

## 构建

```powershell
$env:JAVA_HOME="D:\applications\jdk-21"     # 仅本次进程，不改系统
cd C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android
& "D:\applications\gradle-8.14\bin\gradle.bat" clean assembleDebug
```

`gradle.properties` 里已经写好 `org.gradle.java.home=D:/applications/jdk-21`、
`android.suppressUnsupportedCompileSdk=36`，以及走本机代理的 `systemProp.*.proxyHost/Port`。

## 结构

```
app/src/main/java/io/paimon/fx991/
  MainActivity.kt             入口（enableEdgeToEdge：窗口铺满整屏，内容自行避让系统栏）
  Screen.kt                   主界面枚举（计算 / 微分方程）
  Settings.kt                 设置 / 精度 / 主题 / 覆盖层 / FuncKind / FuncDialog（批次 A 新增；批次 B 增：NumberNotation、POL/REC/RANINT、STO/CONST/CONV/SI/CLRCONFIRM 覆盖层）
  CalcViewModel.kt            状态机：表达式 / 结果 / Ans·PreAns / M / 变量寄存器 / 历史 / DEG-RAD-GRAD / SHIFT-ALPHA 层 /
                              显示格式 / 记数法 / 覆盖层 / 设置 / 数值功能对话框 / a∠θ 结果（批次 A 扩充，批次 B 再扩充）
  engine/
    CalcEngine.kt             词法 → 递归下降语法 → 求值 → 格式化（浮点轨 + 精确轨），
                              批次 A 增：双参数函数（逗号）、双曲 / cbrt、GRAD、显示精度、toRadians 门面
                              批次 B 增：变量 A–F / PreAns / abs、NumberNotation（SCI/ENG）、literal()
    Rational.kt               Rational(BigInteger) 精确有理数 + Value 双轨；批次 A 增：n 次方根、连分数逼近
    OdeSolver.kt              RK4 一阶/二阶 + 常系数线性解析解
    NumericOps.kt             批次 A 新增：数值求根 / 自适应 Simpson / 数值导数 / 求和 / 极限 /
                              排列组合 / 度分秒 / 极坐标（含 Dms、LimitResult、RootResult；
                              旧「整串特例解析、最多一个 ∠」的 PolarForm.evaluate 本批已删）
    Tools.kt                  批次 B 新增：科学常数表 / 单位换算（含温度仿射）/ SI 前缀 / 随机数 / 变量寄存器（纯 Kotlin）
    ComplexOps.kt             批次 C 新增：复数（双轨）+ ExactMath 公共算术
    MatrixOps.kt              批次 C 新增：矩阵 MatA–D（≤4×4）加减乘 / 行列式 / 逆 / 转置 / 单位阵 / 标量乘
    VectorOps.kt              批次 C 新增：三维向量 VctA–D 点积 / 叉积 / 模 / 单位化 / 夹角
    StatOps.kt                批次 C 新增：单变量统计 + 双变量线性回归
    DistrOps.kt               批次 C 新增：正态（P/Q/R）/ 二项 / 泊松（erf 与 lnΓ 手写）
    FuncHelp.kt               批次 C 新增：函数帮助目录 + 语法键
    UnifiedEval.kt            统一输入面新增：CalcValue（标量/复数/矩阵/向量）+ 联合求值；
                              纯标量子树回退 ValueEvaluator（双轨内核一字不改）
    EquationSolver.kt         主行求解新增：多项式精确根（含复根/根式）、超越方程多根扫描、
                              线性方程组高斯消元（精确分数）、非线性数值牛顿、无解/无穷多解告知
    EqnMode.kt                批次 D 新增：方程模式——系数式多项式（复用 EquationSolver 精确根，
                              Durand-Kerner 数值兜底含复根）+ 2~4 元联立线性（精确高斯消元）
    BaseN.kt                  批次 D 新增：DEC/HEX/BIN/OCT 互转 + 位运算 + 字长 16/32/64 + 补码
    TableGen.kt               批次 D 新增：函数表生成（f(x) / 可选 g(x)，起终值 / 步长 / 行数上限）
    RatioOps.kt               批次 D 新增：比例 a:b=c:x 与 a:b=x:d（精确有理数）
    PhotoSolve.kt             批次 E：拍照解题纯逻辑（配置 / URL / JSON / 回复解析 / 表达式规整 / 尺寸计算）
    PhotoNet.kt               批次 E：HTTP 层（HttpURLConnection，纯 JVM 可测）
    OcrText.kt                批次 F 新增：本地 OCR 文本合并与清洗（多行合并 / 符号归一 / 易混字符纠正，纯 Kotlin）
  ui/
    Theme.kt                  DayNight 双套配色 + 计算器专用颜色令牌（橙 SHIFT / 紫 ALPHA）
    Keys.kt                   键位表（照参考图：顶栏 8 项 + 键盘 9 行）+ MODE 菜单条目
    Icons.kt                  手绘矢量图标（菜单 / PRO / Σ / 齿轮 / ± / 相机 / ⌫）
    CalcScreen.kt             LCD + 键盘 + 全部覆盖层（MODE / 设置 / 更多 / 关于 / 拍照说明 / 历史 /
                              帮助 / 数值功能对话框），按键全走 Material 3 Button
    NaturalDisplay.kt         自然书写渲染（分数堆叠 / √ 上横线 / 上标）
    NatModel.kt               自然书写布局树解析 + ASCII 投影（纯 Kotlin，可 JVM 回归）；批次 A 增：逗号 / ∠；
                              本批增：= / ; / ·
    OdeScreen.kt              微分方程界面（输入 / 数值表 / Canvas 曲线图）
    Subsystems.kt             批次 C 新增：复数 / 矩阵 / 向量 / 统计 / 分布 / 函数帮助 六个独立界面
    ModeScreens.kt            批次 D 新增：方程 / 基数换算 / 函数表 / 比例 四个独立界面
    Insets.kt                 系统栏避让新增：Modifier.safeAreaPadding()（WindowInsets.safeDrawing，全界面共用）
    SafeArea.kt               系统栏避让新增：可用高度 / 紧凑滚动布局策略（纯 Kotlin，可 JVM 回归）
    PhotoSolveScreen.kt       批次 E：拍照解题界面；批次 F 改写：本地识别 / API 识别双路径并存
    PhotoImage.kt             批次 E：图片管线（解码 / EXIF 方向校正 / 等比缩放 / JPEG 压缩）
    MlKitOcr.kt               批次 F 新增：ML Kit bundled 离线识别封装（中文优先，拉丁兜底；Android 侧，JVM 不测）
app/src/main/res/
  values, values-night        DayNight 主题与颜色
  drawable, drawable-night    自适应图标背景（浅/深两套）
  drawable/ic_launcher_foreground.xml / ic_launcher_monochrome.xml
  mipmap-anydpi-v26/          自适应图标（含 <monochrome> 单色层）
tools/
  EngineTest.java             引擎行为回归（70 条，v1 资产，不许退步）
  EngineTestV2.java           精确分数双轨回归（25 条）
  DisplayTest.java            自然书写布局投影回归（9 条 → 批次 A 增 3 条 = 12 条）
  OdeTest.java                微分方程回归（18 条）
  NumericTest.java            批次 A 数值算法回归（91 条）
  ToolsTest.java              批次 B 工具回归（119 条：常数/单位换算/温度/SI/随机数/Pol·Rec/abs/PreAns/变量/ENG·SCI/Exp）
  BatchCTest.java             批次 C 六大子系统回归（159 条：复数/矩阵/向量/统计/分布/函数帮助）
  InsetsTest.java             系统栏避让回归（18 条：最小高度 / 滚动阈值 / 机型区间 / 行高兜底）
  UnifiedTest.java          统一输入面 + 主行求解回归（69 条：多∠与角度制 / 矩阵向量统计分布主行调用 /
                            一元方程含复根根式 / 超越方程多根 / 方程组 / 无解·无穷多解）
  NatLayoutTest.java          自然书写堆叠显示护栏（16 条：源码护栏 + 结构投影）
  BatchDTest.java             批次 D 回归（87 条：多项式精确根 / Durand-Kerner 数值兜底 / 2~4 元联立 /
                            BASE-N 解析显示补码位运算 / 函数表生成与校验 / 比例精确解）
  PhotoAiTest.java            批次 E 回归（93 条：配置校验/默认值/URL 拼接/JSON 构造转义与多段解析/
                              EXPR 抽取/表达式规整/缩放与 base64/HTTP 文案/本机 HttpServer 全链路）
  OcrTextTest.java            批次 F 回归（50 条：多行合并 / 全角半角 / OCR 符号修正 / 小数点误识别 /
                              易混字符纠正与不纠正边界 / 清洗结果端到端可求值）
  run-tests.ps1               一次跑完十三套
```

## 表达式引擎

递归下降文法（隐式乘法优先级高于 `÷`，与参考机一致）：

```
expr    -> term (('+'|'-') term)*              % 参与相对百分比
term    -> factor (('*'|'/') factor)*
factor  -> unary (unary)*                      隐式乘法：2π、3(4+5)、(1+1)(2+2)
unary   -> ('-'|'+') unary | power
power   -> postfix ('^' unary)?                右结合：2^3^2=512；2^-3 合法
postfix -> primary ('!'|'%'|'²'|'³'|'⁻¹')*
primary -> NUM | π | e | Ans | M | x | y | z
         | FUNC '(' expr ')' | FUNC2 '(' expr ',' expr ')' | '√' unary | '(' expr ')'
```

- `FUNC`：`sin cos tan asin acos atan log ln exp sqrt cbrt sinh cosh tanh asinh acosh atanh`
- `FUNC2`（批次 A）：`logb(底,真数)`、`root(次数,被开方数)`、`npr(n,r)`、`ncr(n,r)`
- 角度制：`DEG / RAD / GRAD`（400 grad = 360°）

### 双轨数值内核

```
Value = Exact(Rational) | Floating(Double)
```

- 小数字面量**当场转有理数**（`589.3 → 5893/10`，`1.52 → 38/25`）
- `+ − × ÷`、整数次幂、`x^(1/2)`、`√`、`root(n,x)`、`cbrt`、`logb`（整数结果）、`nPr/nCr`（≤128bit）全程精确
- `sin/cos/tan/log/ln/eˣ/双曲` 与开不尽的根落回 `Double`
- 分子/分母超过 128 bit → 自动退回小数（参考机同样如此）
- 显示：分数（默认）→ 带分数 → 小数，`S⇔D` 循环切换；`FIX n` 精度下强制小数显示
- 连分数逼近只用于把**数值解**择回精确分数，且要求分母 ≤1000 且严格过零点（否则不硬凑）

例：`589.3 ÷ 1.52` → 精确 **29465/76**（带分数 387 53/76，小数 387.6973684）。

## 数值算法（批次 A）

| 算法 | 方法 | 备注 |
|:--|:--|:--|
| 定积分 | 自适应 Simpson | 容差显式可设，深度上限 60，`a>b` 取负 |
| 数值导数 | 中心差分 + Richardson 外推 | 步长随 |x| 缩放 |
| 求根 | 牛顿法 → 二分法兜底 | 二分以初值为中心扫描变号区间，取最近的一个 |
| 求和 | 直接累加 | 整数上下界，项数 ≤ 1e6 |
| 极限 | h→0 两点外推 | 步长比 10，左右分别计算 |
| 排列组合 | BigInteger | 大数不溢出，≤128bit 时精确显示 |
| 度分秒 | 60 进制 | `negative` 单独记号，−0 不丢符号 |
| 极坐标 | `a∠θ` ⇄ `x+yi` | 只支持单个 `∠`；直角/极坐标可用 `S⇔D` 切换显示 |

## 已实现

- 四则运算、括号（`=` 时自动补全右括号）、乘方 `^`、`x²`、`x³`、`x⁻¹`、`√`、`n!`、`%`、一元负号
- 三角函数 `sin cos tan` 与反三角 `sin⁻¹ cos⁻¹ tan⁻¹`（含定义域检查）、**双曲与反双曲**
- `log`（底 10）、`ln`、`eˣ`、**任意底对数 `logb`**、**任意次根 `root`**、**立方根 `cbrt`**、`10ˣ`、`π`、`e`
- **精确分数**：输入小数转有理数、精确四则与整数次幂、能开尽精确开方、分数/带分数/小数三态
- `DEG / RAD / GRAD` 三档角度制；`Ans`、`M` 寄存器 + `M+`/`M−`/`MRC`
- `DEL` 退格（多字符单位整体删除）、`AC` 全清
- 历史记录（`▲`/`▼` 与方向键上翻回填，保留最近 40 条；更多菜单里也有入口）
- **批次 B**：科学常数表（24 条）、单位换算（长度/质量/时间/面积/体积/温度）、SI 前缀换算、0–1 随机数、整数区间随机、Pol/Rec、绝对值 `abs`、变量 A–F/x/y 存储与引用、PreAns、工程/科学记数显示、历史单条删除、CLR ALL 确认全清
- **批次 C**：复数模式（a+bi 四则 / 模 / 辐角 / 共轭 / 直角⇄极坐标）、矩阵模式（MatA–D ≤4×4，加减乘 / det / 逆 / 转置 / 单位阵 / 标量乘）、向量模式（VctA–D，点积 / 叉积 / 模 / 单位化 / 夹角）、统计模式（单变量描述统计 + 双变量回归）、分布模式（正态 P/Q/R、二项、泊松）、函数帮助（37 条目录 + 语法键插入）
- **SHIFT / ALPHA 双功能层**（键帽上橙色 / 紫色小字，按下后下一次按键走第二功能）；**`2nd` 与 SHIFT 同义**
- **MODE 菜单** → 计算 / 微分方程 / 方程 / 矩阵 / 向量 / 统计与回归 / 复数 / 基数换算 / 函数表 / 概率分布 / 函数帮助 / 比例（**12 项全部可用**）
- **批次 D**：方程模式（多项式 2/3/4 次含复根 + 联立线性 2~4 元精确分数 + 无解/无穷多解告知）、基数换算（四进制互转 + 位运算 + 字长 + 补码）、函数表（f/g 双函数对照 + 翻页）、比例（a:b=c:x / a:b=x:d 精确解）
- **顶栏 6 项真 UI**：菜单 / 设置 / 更多 / PRO(关于) / Σ / 拍照解题说明页
- **设置**：主题（跟随系统/浅/深）、角度制、显示精度（有效数字 / 小数位）、分数显示、按键震动
- **数值功能**：SOLVE、∫dx、d/dx、Σ、Limit、CALC、hyp、度分秒、极坐标、nPr/nCr
- **微分方程**：RK4 一阶 / 二阶（内化方程组）、常系数线性解析解、数值表 + Canvas 曲线图
- **DayNight 深浅色联动**（含设置里手动覆盖）、自适应图标 + 主题图标单色层
- 错误提示：`语法错误` / `数学错误`；数值功能另有「求根失败 / 积分端点无定义 / 参数不是合法数字」等；微分方程另有「步长不合法 / 步数过多 / 数值发散」

## 未实现 / 占位键（批次 D 之后：**0**，模式菜单也清零）

`KeyAction.Todo` 已从源码中**彻底删除**（连类型定义一起），全部按键都有真行为，界面上不再出现「未实现」提示。
批次 D 再把 MODE 菜单里最后 4 个「待实现」条目（方程 / 基数换算 / 函数表 / 比例）全部实现，并顺手清掉两处
死代码：从未被任何键引用、分发里空操作的 `KeyAction.ThemeToggle` 与从未被引用的 `KeyIcon.THEME`（连同绘制分支）。
**至此：占位键 0、占位模式 0、死代码动作 0。**

> 批次 A 结束时剩 20 个；批次 B 换掉 14 个（STO / ENG / CLR ALL / PreAns / History / Exp / \|x\| / CONST / CONV / SI /
> Ran# / RanInt / Pol / Rec），余 6 个；批次 C 把最后 6 个（CMPLX / MATRIX / VECTOR / STAT / DISTR / FUNC HELP）
> 全部实现；批次 D 补齐模式菜单 4 项（EQN / BASE-N / TABLE / RATIO），**占位归零**。

已知取舍：

- 双参数函数用逗号分隔、且必须带括号：`logb(2,8)`、`root(3,27)`、`npr(5,2)`、`ncr(5,2)`
- `∠` 已是**真运算符**（可多个、优先级高于 `+ -`、紧贴两侧操作数）；因为优先级变了，`1+2∠90` 现在按 `1+(2∠90)` 解析，要 `(1+2)∠90` 得自己加括号
- 一元方程的非多项式（超越）求解是**数值扫描**：默认区间 `[−周期, 3×周期]`（按角度制），最多列出 12 个根；方程模式里 3 / 4 次多项式无有理根时走 **Durand-Kerner 数值兜底**（复根成对给出）
- 主行方程组只支持线性（精确高斯消元）与最多 3 个未知量的非线性数值牛顿；`=` 右侧留空报语法错误；**方程模式**的联立线性支持到 4 元（x / y / z / w），非线性组仍只在主行、最多 3 元
- 基数换算的输入按字长校验：幅值 ≥ 2^bits 报错；DEC 正数超过有符号范围时按补码环绕显示（如 32 位下 4294967295 → -1）
- 函数表行数上限 200（翻页展示）；某个点无定义只影响该格
- SOLVE 的精确根只在分母 ≤1000 且严格过零点时给出（`x²-2` 不会硬凑成分数）
- `(-)` 记法 `2(-)3` 不报语法错误（当作二元减号，得 -1）
- 表达式超宽时横向滚动（不再缩字号）
- 方向键 ← → ↑ ↓ 用于回看历史、中心 ● 等同 `=`（未做文本光标）
- 分数分子/分母不做千位分组（避免与隐式乘法歧义）
- 设置项（主题/角度制/精度等）为进程内状态，不落盘持久化；**解题 API 配置例外**（SharedPreferences 明文落盘，界面已告知）
- 批次 E：拍照解题的照片会压缩后发给**用户自己配置**的服务商；`usesCleartextTraffic="true"` 允许 `http://` 自建地址（本应用无内置云端，唯一网络出口即该 API）
- 批次 E：JSON 用**手写最小实现**而非 org.json——org.json 在纯 JVM 回归环境不可用，手写版可测且范围等价；DeepSeek 预设指向 **`deepseek-v4-flash-vision-exp`**（**带 vision 的就是视觉模型，可识图**）
- STO 变量（A–F / x / y）存的是 **Double**（不保留精确分数），引用时按浮点参与运算
- CLR ALL **会连同设置一起重置**（主题 / 角度制 / 精度 / 数字格式都回默认）——这是「全清」的语义
- `Ran#` 在按键时**当场取一个随机字面量**插入表达式（不引入随机函数记号）
- SI 前缀换算按 10 的幂做（k=1e3 用 1000 而非 1024）

## 验证

```powershell
powershell -File tools\run-tests.ps1
```

本次（批次 F：本地离线 OCR）的结果（全部通过，共 **834** 条）：

```
EngineTest    pass=70  fail=0     # v1 资产，不许退步
EngineTestV2  pass=25  fail=0     # 精确分数双轨
DisplayTest   pass=12  fail=0     # 自然书写布局投影
OdeTest       pass=18  fail=0     # RK4 + 解析解
NumericTest   pass=98  fail=0     # 批次 A：求根/积分/导数/求和/极限/双曲/排列组合/度分秒/极坐标/GRAD/精度/多∠
ToolsTest     pass=119 fail=0     # 批次 B：常数/单位换算（含温度）/SI 前缀/随机数/Pol·Rec/abs/PreAns/变量/ENG·SCI/Exp
BatchCTest    pass=159 fail=0     # 批次 C：复数/矩阵/向量/统计/分布/函数帮助
InsetsTest    pass=18  fail=0     # 系统栏避让：所需最小高度 / 滚动切换阈值 / 横竖屏与分屏区间 / 行高兜底
UnifiedTest   pass=69  fail=0     # 统一输入面 + 主行求解
NatLayoutTest pass=16  fail=0     # 自然书写堆叠显示护栏（源码护栏 + 结构投影）
BatchDTest    pass=87  fail=0     # 批次 D：多项式精确根/Durand-Kerner 数值兜底/2~4 元联立精确分数/
                                  #          BASE-N 解析·四进制显示·补码·位运算·字长/函数表生成与校验/比例精确解
PhotoAiTest   pass=93  fail=0     # 批次 E：配置校验与默认值/URL 拼接/JSON 构造转义与多段解析/EXPR 抽取/
                                  #          表达式规整/缩放与 base64/HTTP 文案/本机 HttpServer 全链路
OcrTextTest   pass=50  fail=0     # 批次 F：多行合并/全角半角/OCR 符号修正/小数点误识别/
                                  #          易混字符纠正与不纠正边界/清洗结果端到端可求值
```

> 784（批次 E 结束）+ 50（批次 F）= **834** 条，原有各套无一条退步。

单跑某套见 `tools/run-tests.ps1` 内的 javac/java 命令行（classpath 用
`app\build\tmp\kotlin-classes\debug` + kotlin-stdlib 2.0.21）。

> 未接设备 / 模拟器（emulator-5554 被其他任务占用，按要求未动），本轮验证为「编译绿（`assembleDebug`）+
> **834 条 JVM 回归全通过** + 产物审计（源码无商标字样、无硬编码 key、批次 F 新增 ML Kit 两个 artifact
> 外无其他第三方库、未新增图片/字体资源、未新增权限）」，未做真机 UI 走查，未真连外部 API（无真实 key）；
> ML Kit 本体依赖 Android 运行时，其识别效果未在真机 / 模拟器上验证。

# 科学计算器（自然书写 LCD 复刻）· v2 + 批次 A + 批次 B + 批次 C

Android 科学计算器。界面与交互参考一台科学计算器手机 App（键位、配色、自然书写 LCD），
**不含任何品牌厂商的商标、logo、字体或官方图片资源** —— 全部 UI 由 Jetpack Compose 手写绘制，
图标也是矢量代码画的（`Canvas` / `Path` / vector drawable）。

## 产物

```
app/build/outputs/apk/debug/app-debug.apk
```

批次 C 交付件（最后一批）：

| 项 | 值 |
|:--|:--|
| 绝对路径 | `C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\build\outputs\apk\debug\app-debug.apk` |
| 大小 | 9,810,588 bytes（≈ 9.36 MB，`clean assembleDebug`） |
| SHA-256 | `9E93E08D0BB9818D5024F5BB606FE378BE763016111DB8149530B98DD2B123BC` |
| 包名 / 版本 | `io.paimon.fx991` · versionCode 1 / versionName 1.0.0（应用内版本串 `1.3.0-batchC`） |

（批次 B 交付件：9,695,900 bytes，SHA-256 `E9D2AA5C79C39ED87EB66B39048438779826B288A9CF1C90500610425CB97ECC`；
批次 A：9,663,132 bytes，SHA-256 `8F66F3D05EBF217E42D430A2B48EEDB069A97A8F0FD7D62978386794E13B5789`。）

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

**零第三方依赖**：只用 AndroidX（core-ktx / activity-compose / lifecycle-viewmodel-compose /
compose ui / foundation / material3）。解析器、有理数、RK4、数值算法全部手写。批次 A 与批次 B 均
**未新增任何依赖、图片、图标、字体资源**。

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
  MainActivity.kt             入口
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
                              排列组合 / 度分秒 / 极坐标（含 ComplexRect、Dms、LimitResult、RootResult）
    Tools.kt                  批次 B 新增：科学常数表 / 单位换算（含温度仿射）/ SI 前缀 / 随机数 / 变量寄存器（纯 Kotlin）
    ComplexOps.kt             批次 C 新增：复数（双轨）+ ExactMath 公共算术
    MatrixOps.kt              批次 C 新增：矩阵 MatA–D（≤4×4）加减乘 / 行列式 / 逆 / 转置 / 单位阵 / 标量乘
    VectorOps.kt              批次 C 新增：三维向量 VctA–D 点积 / 叉积 / 模 / 单位化 / 夹角
    StatOps.kt                批次 C 新增：单变量统计 + 双变量线性回归
    DistrOps.kt               批次 C 新增：正态（P/Q/R）/ 二项 / 泊松（erf 与 lnΓ 手写）
    FuncHelp.kt               批次 C 新增：函数帮助目录 + 语法键
  ui/
    Theme.kt                  DayNight 双套配色 + 计算器专用颜色令牌（橙 SHIFT / 紫 ALPHA）
    Keys.kt                   键位表（照参考图：顶栏 8 项 + 键盘 9 行）+ MODE 菜单条目
    Icons.kt                  手绘矢量图标（菜单 / PRO / Σ / 齿轮 / ± / 相机 / ⌫）
    CalcScreen.kt             LCD + 键盘 + 全部覆盖层（MODE / 设置 / 更多 / 关于 / 拍照说明 / 历史 /
                              帮助 / 数值功能对话框），按键全走 Material 3 Button
    NaturalDisplay.kt         自然书写渲染（分数堆叠 / √ 上横线 / 上标）
    NatModel.kt               自然书写布局树解析 + ASCII 投影（纯 Kotlin，可 JVM 回归）；批次 A 增：逗号 / ∠
    OdeScreen.kt              微分方程界面（输入 / 数值表 / Canvas 曲线图）
    Subsystems.kt             批次 C 新增：复数 / 矩阵 / 向量 / 统计 / 分布 / 函数帮助 六个独立界面
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
  run-tests.ps1               一次跑完六套
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
- **MODE 菜单** → 计算 / 微分方程 / 复数 / 矩阵 / 向量 / 统计与回归 / 概率分布 / 函数帮助（其余模式置灰标「待实现」）
- **顶栏 6 项真 UI**：菜单 / 设置 / 更多 / PRO(关于) / Σ / 拍照解题说明页
- **设置**：主题（跟随系统/浅/深）、角度制、显示精度（有效数字 / 小数位）、分数显示、按键震动
- **数值功能**：SOLVE、∫dx、d/dx、Σ、Limit、CALC、hyp、度分秒、极坐标、nPr/nCr
- **微分方程**：RK4 一阶 / 二阶（内化方程组）、常系数线性解析解、数值表 + Canvas 曲线图
- **DayNight 深浅色联动**（含设置里手动覆盖）、自适应图标 + 主题图标单色层
- 错误提示：`语法错误` / `数学错误`；数值功能另有「求根失败 / 积分端点无定义 / 参数不是合法数字」等；微分方程另有「步长不合法 / 步数过多 / 数值发散」

## 未实现 / 占位键（批次 C 之后：**0**）

`KeyAction.Todo` 已从源码中**彻底删除**（连类型定义一起），全部按键都有真行为，界面上不再出现「未实现」提示。

> 批次 A 结束时剩 20 个；批次 B 换掉 14 个（STO / ENG / CLR ALL / PreAns / History / Exp / \|x\| / CONST / CONV / SI /
> Ran# / RanInt / Pol / Rec），余 6 个；批次 C 把最后 6 个（CMPLX / MATRIX / VECTOR / STAT / DISTR / FUNC HELP）
> 全部实现，**占位归零**。

已知取舍：

- 双参数函数用逗号分隔、且必须带括号：`logb(2,8)`、`root(3,27)`、`npr(5,2)`、`ncr(5,2)`
- `∠` 表达式只支持**一个** `∠`，两侧各是一个普通表达式（`2∠60`、`(1+2)∠90`）；不做完整复数运算
- SOLVE 的精确根只在分母 ≤1000 且严格过零点时给出（`x²-2` 不会硬凑成分数）
- `(-)` 记法 `2(-)3` 不报语法错误（当作二元减号，得 -1）
- 表达式超宽时横向滚动（不再缩字号）
- 方向键 ← → ↑ ↓ 用于回看历史、中心 ● 等同 `=`（未做文本光标）
- 分数分子/分母不做千位分组（避免与隐式乘法歧义）
- 设置项为进程内状态，不落盘持久化
- STO 变量（A–F / x / y）存的是 **Double**（不保留精确分数），引用时按浮点参与运算
- CLR ALL **会连同设置一起重置**（主题 / 角度制 / 精度 / 数字格式都回默认）——这是「全清」的语义
- `Ran#` 在按键时**当场取一个随机字面量**插入表达式（不引入随机函数记号）
- SI 前缀换算按 10 的幂做（k=1e3 用 1000 而非 1024）

## 验证

```powershell
powershell -File tools\run-tests.ps1
```

批次 C 结束时的结果（全部通过，共 **494** 条）：

```
EngineTest    pass=70  fail=0     # v1 资产，不许退步
EngineTestV2  pass=25  fail=0     # 精确分数双轨
DisplayTest   pass=12  fail=0     # 自然书写布局投影（原 9 条 + 批次 A 新增 3 条）
OdeTest       pass=18  fail=0     # RK4 + 解析解
NumericTest   pass=91  fail=0     # 批次 A：求根/积分/导数/求和/极限/双曲/排列组合/度分秒/极坐标/GRAD/精度
ToolsTest     pass=119 fail=0     # 批次 B：常数/单位换算（含温度）/SI 前缀/随机数/Pol·Rec/abs/PreAns/变量/ENG·SCI/Exp
BatchCTest    pass=159 fail=0     # 批次 C：复数四则/模辐角/共轭/直角⇄极坐标、矩阵 det/逆/乘/转置/单位阵、
                                  #          向量点积/叉积/模/单位化/夹角、统计单双变量、三种分布、帮助页语法键完整性
```

> 335（批次 B 结束时）+ 159（批次 C 新增）= **494** 条，原有 335 条无一条退步。

单跑某套见 `tools/run-tests.ps1` 内的 javac/java 命令行（classpath 用
`app\build\tmp\kotlin-classes\debug` + kotlin-stdlib 2.0.21）。

> 未接设备 / 模拟器，本轮验证为「编译绿（`clean assembleDebug`）+ **494 条 JVM 回归全通过** + 产物审计（dex 里已确认
> ComplexNum / MatrixStore / VectorStore / StatOps / DistrOps / FuncHelp / ExactMath 与六个界面类、以及
> 「复数 CMPLX / 矩阵 MATRIX / 向量 VECTOR / 统计与回归 STAT / 概率分布 DISTR / 函数帮助 FUNC HELP」全部 UI 文案均在包内；
> 源码无商标字样；`KeyAction.Todo` 用法为 0；未新增任何图片 / 图标 / 字体资源 —— res 清单仍只有 colors / strings / themes / 自适应图标）」，
> 未做真机 UI 走查。

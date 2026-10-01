# 科学计算器（自然书写 LCD 复刻）· v1.11.0-inline

Android 科学计算器（Kotlin + Jetpack Compose，界面交互参考一台科学计算器手机 App）。
**不含任何品牌厂商的商标、logo、字体或官方图片资源** —— 全部 UI 与图标由代码绘制。

## 安装

- APK：`app\build\outputs\apk\debug\app-debug.apk`（默认 arm64 单 ABI 约 25 MB；`-PallAbi` 四 ABI 约 52 MB，模拟器用）
- 包名 `io.paimon.fx991` · minSdk 26 · 仓库 Release 挂 `app-debug.apk`
- 详细设计参照见 `REFERENCE.md`

## 检查更新

- 启动时静默检查（不阻塞 UI；只在有新版本时才提示），设置里可关（默认开）
- 设置 → 检查更新：手动触发，显示当前 / 最新版本，有新版给下载入口
- 数据源 GitHub Releases 公共 API（只读无 token；限流 60 次/小时/IP，故 12 小时内不重复请求；403 会提示"请求太频繁"）

## 关键能力速查

- 精确有理数（BigInteger）+ 浮点双轨；S⇔D 在假分数 / 带分数 / 小数间切换
- 自然书写 LCD（分数堆叠）+ 可见光标；二级界面 17 个输入框同为自然书写（自有键盘面板）
- 主行直写：`MatA×MatB` `det/inv/trn` `VctA·VctB` `cross` `∠` 真运算符 `mean/sd` `normcdf/binompdf` …
- 主行按 `=` 解方程：多项式全部根（含复根，能精确给精确）、超越方程多根扫描、方程组高斯消元
- 功能键就地括号调用（不弹面板；仅 菜单/设置/历史 保留面板）：`solve(方程)` `sto(式,变量)` `int(式,下,上)` 二维积分模板 `deriv` `sum` `lim` `calc` `dms` `pol` `rec` `ranint` `const(符号)` `conv(值,从,到)` `si(值,前缀)`
- 模式菜单 12 项：复数 / 矩阵 / 向量 / 统计 / 分布 / 函数帮助 / EQN / BASE-N / TABLE / RATIO / ODE（RK4）/ 拍照解题（视觉 API 自配 Key，本机明文存储有告知）
- GeoGebra 式自定义函数 `f(x)=x^2`、傅里叶级数、复变函数（`i` 进主行、留数 `res`、围道积分 `cint`）
- 本地离线 OCR（ML Kit bundled，工程唯一第三方依赖，已批准的例外）；深 / 浅双主题，SHIFT 橙 / ALPHA 紫识别色
- ∠ 真运算符投影护栏：投影 / 全光标位 / natLinear 往返的 ∠ 个数钉死等于输入个数

## 主行语法速查

```
expr    -> term (('+'|'-') term)*      term -> factor (('*'|'/') factor)*
factor  -> unary (unary)*              隐式乘法：2π、3(4+5)；power 右结合
postfix -> primary ('!'|'%'|'²'|'³'|'⁻¹')*
primary -> NUM | π | e | Ans | PreAns | M | x | y | z | A–F | FUNC(...) | '√' unary | '(' expr ')'
```

角度制 DEG / RAD / GRAD；NORM / SCI / ENG；STO 存 A–F/x/y/M。

## 构建 / 回归

AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.14 · JDK 21 · compileSdk 36 · Compose BOM 2024.09.00

```powershell
& "D:\applications\gradle-8.14\bin\gradle.bat" assembleDebug            # 单 ABI（arm64）
& "D:\applications\gradle-8.14\bin\gradle.bat" assembleDebug -PallAbi   # 四 ABI（模拟器用）
powershell -File tools\run-tests.ps1                                    # 17 套 JVM 回归
```

## 已知限制

- 不做符号解（CAS）；ODE 为 RK4 数值解 + 二阶以内常系数线性解析解
- 分子分母超规模自动落回浮点（与真机一致）；超越方程扫描区间内给数值根
- 检查更新依赖 api.github.com 可达性；无网时静默跳过，不影响计算功能
- 拍照解题图片：JPEG/PNG/GIF(首帧)/BMP/WebP 全版本可解；HEIC/HEIF 需 Android 9+（API 28），AVIF 需 Android 12+（API 31）；TIFF 平台解码器不支持（不引第三方解码库，解不了会明确提示格式、本机系统版本与转 JPG 的出路）
- 相册走系统 Photo Picker（无权限、覆盖 HEIC）；解码优先 ImageDecoder（API 28+，自动 EXIF + 按目标尺寸解码防 OOM），API 26/27 回退 BitmapFactory + 手动 EXIF

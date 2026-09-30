# 真机复验：主键盘逐键敲方程，按 = 出解（不用 SOLVE 弹窗、不预填）
$adb = "D:\applications\Android\Sdk\platform-tools\adb.exe"
$out = "shots"
New-Item -ItemType Directory -Force $out | Out-Null

function Tap($x, $y) { & $adb shell input tap $x $y | Out-Null; Start-Sleep -Milliseconds 600 }
function Shot($name) {
    & $adb shell screencap -p /sdcard/shot.png | Out-Null
    & $adb pull /sdcard/shot.png "$out\$name.png" | Out-Null
    Write-Host "shot: $out\$name.png"
}

# 键位（来自 uiautomator dump）
$ALPHA = @(281, 947); $ANS = @(751, 2210); $EQKEY = @(963, 2210)
$K1 = @(116, 2055); $K2 = @(327, 2055); $K3 = @(539, 2055)
$PLUS = @(751, 2055); $MINUS = @(963, 2055)
$K7 = @(116, 1745); $AC = @(963, 1745)

# ---- 用例 1：2x+3=7 → 期望 x=2 ----
Tap $AC[0] $AC[1]
Tap $K2[0] $K2[1]            # 2
Tap $ALPHA[0] $ALPHA[1]; Tap $ANS[0] $ANS[1]   # x (ALPHA+Ans)
Tap $PLUS[0] $PLUS[1]        # +
Tap $K3[0] $K3[1]            # 3
Tap $ALPHA[0] $ALPHA[1]; Tap $EQKEY[0] $EQKEY[1]  # = (ALPHA+= 插入等号)
Tap $K7[0] $K7[1]            # 7
Shot "10-expr-2x+3=7"        # 表达式完整显示后再求值
Tap $EQKEY[0] $EQKEY[1]      # = 求值
Shot "11-result-2x+3=7"

# ---- 用例 2：x+1=3 → 期望 x=2 ----
Tap $AC[0] $AC[1]
Tap $ALPHA[0] $ALPHA[1]; Tap $ANS[0] $ANS[1]
Tap $PLUS[0] $PLUS[1]
Tap $K1[0] $K1[1]
Tap $ALPHA[0] $ALPHA[1]; Tap $EQKEY[0] $EQKEY[1]
Tap $K3[0] $K3[1]
Shot "12-expr-x+1=3"
Tap $EQKEY[0] $EQKEY[1]
Shot "13-result-x+1=3"

# ---- 用例 3：3x-2=7 → 期望 x=3 ----
Tap $AC[0] $AC[1]
Tap $K3[0] $K3[1]
Tap $ALPHA[0] $ALPHA[1]; Tap $ANS[0] $ANS[1]
Tap $MINUS[0] $MINUS[1]
Tap $K2[0] $K2[1]
Tap $ALPHA[0] $ALPHA[1]; Tap $EQKEY[0] $EQKEY[1]
Tap $K7[0] $K7[1]
Shot "14-expr-3x-2=7"
Tap $EQKEY[0] $EQKEY[1]
Shot "15-result-3x-2=7"

# 复现脚本：主键盘 x → + 失灵 bug
$adb = "D:\applications\Android\Sdk\platform-tools\adb.exe"
$out = "shots"
New-Item -ItemType Directory -Force $out | Out-Null

function Tap($x, $y) { & $adb shell input tap $x $y | Out-Null; Start-Sleep -Milliseconds 700 }
function Shot($name) {
    & $adb shell screencap -p /sdcard/shot.png | Out-Null
    & $adb pull /sdcard/shot.png "$out\$name.png" | Out-Null
    Write-Host "shot: $out\$name.png"
}

# 确保回到主界面并清空
Tap 963 1745   # AC
Shot "01-cleared"

# ALPHA → Ans 插入 x
Tap 281 947    # ALPHA
Tap 751 2210   # Ans (alpha 层 = x)
Shot "02-after-x"

# 按 +
Tap 751 2055   # +
Shot "03-after-plus"

# 对照：按 −（应该能进去）
Tap 963 1745   # AC
Tap 281 947    # ALPHA
Tap 751 2210   # x
Tap 963 2055   # −
Shot "04-after-minus"

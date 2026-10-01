# 批次 K3 真机复验（单会话内完成，避免 qemu 被会话清理连带杀掉）
$ErrorActionPreference = "Continue"
$adb = "D:\applications\Android\Sdk\platform-tools\adb.exe"
$emu = "D:\applications\Android\Sdk\emulator\emulator.exe"
$dev = "emulator-5554"
$proj = "C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android"

function Shot($name) {
    & $adb -s $dev shell screencap -p /data/local/tmp/$name.png
    & $adb -s $dev pull /data/local/tmp/$name.png "$proj\shots\$name.png" | Out-Null
    Write-Host "shot: $name"
}
function Tap($x, $y) { & $adb -s $dev shell input tap $x $y | Out-Null; Start-Sleep -Milliseconds 350 }
function TypeText($t) { & $adb -s $dev shell input text $t | Out-Null; Start-Sleep -Milliseconds 400 }

# ---- 0. 起模拟器 ----
$devices = & $adb devices
if ($devices -notmatch "emulator-5554\s+device") {
    Write-Host "starting emulator..."
    Start-Process -FilePath $emu -ArgumentList "-avd","fx991","-no-audio","-no-boot-anim","-no-snapshot","-gpu","swiftshader_indirect","-memory","1536" -WindowStyle Hidden
    & $adb -s $dev wait-for-device
    $t0 = Get-Date
    do {
        Start-Sleep -Seconds 3
        $boot = (& $adb -s $dev shell getprop sys.boot_completed 2>$null) -join ""
    } while ($boot -notmatch "1" -and ((Get-Date) - $t0).TotalSeconds -lt 150)
    Write-Host "boot_completed=$boot"
    Start-Sleep -Seconds 8
}
& $adb -s $dev shell rm -f /sdcard/k3-angle-v110.png 2>$null | Out-Null

# ---- 1. 旧版（v1.10.0，还装着）：敲 9∠60+5∠6，看两个 ∠ ----
& $adb -s $dev shell am start -n io.paimon.fx991/.MainActivity | Out-Null
Start-Sleep -Seconds 4
# 9 SHIFT (−) 6 0 + 5 SHIFT (−) 6
Tap 539 1745; Tap 101 947; Tap 100 1435; Tap 539 1900; Tap 116 2210
Tap 751 2055; Tap 327 1900; Tap 101 947; Tap 100 1435; Tap 539 1900
Start-Sleep -Milliseconds 500
Shot "k3-angle-v110"
Tap 963 2210   # =
Start-Sleep -Seconds 1
Shot "k3-angle-v110-eval"

# ---- 2. 装新版 ----
Write-Host "installing new apk..."
& $adb -s $dev install -r "$proj\app\build\outputs\apk\debug\app-debug.apk"
& $adb -s $dev shell am start -n io.paimon.fx991/.MainActivity | Out-Null
Start-Sleep -Seconds 4

# ---- 3. 找 CALC 键坐标，推 ∫dx（同排右邻）----
& $adb -s $dev shell uiautomator dump /data/local/tmp/ui.xml | Out-Null
Start-Sleep -Seconds 1
$xml = (& $adb -s $dev shell cat /data/local/tmp/ui.xml) -join "`n"
$pat = '<node[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
$calc = $null
$nodes = @()
foreach ($m in [regex]::Matches($xml, $pat)) {
    $label = $m.Groups[1].Value
    $x1 = [int]$m.Groups[2].Value; $y1 = [int]$m.Groups[3].Value
    $x2 = [int]$m.Groups[4].Value; $y2 = [int]$m.Groups[5].Value
    $nodes += ,@($label, $x1, $y1, $x2, $y2)
    if ($label -eq "CALC") { $calc = ,@($x1, $y1, $x2, $y2) }
}
if ($null -eq $calc) { Write-Host "FATAL: CALC key not found"; exit 1 }
$calcCx = [int](($calc[0] + $calc[2]) / 2); $calcCy = [int](($calc[1] + $calc[3]) / 2)
Write-Host "CALC center=($calcCx,$calcCy)"
# ∫dx = 与 CALC 同排（y1 相近）、紧挨右侧的键
$intg = $null
foreach ($n in $nodes) {
    if ([math]::Abs($n[2] - $calc[1]) -lt 30 -and $n[1] -gt $calc[2]) {
        if ($null -eq $intg -or $n[1] -lt $intg[1]) { $intg = $n }
    }
}
$intCx = [int](($intg[1] + $intg[3]) / 2); $intCy = [int](($intg[2] + $intg[4]) / 2)
Write-Host "intdx label=$($intg[0]) center=($intCx,$intCy)"

# ---- 4. 场景一：按 ∫dx → 不弹面板，主行出 ∫ 模板，光标在槽里 ----
Tap 963 1745   # AC
Tap $intCx $intCy
Start-Sleep -Milliseconds 600
Shot "k3-inline-int"

# ---- 5. 场景二：SHIFT+CALC（SOLVE）→ 不弹面板，主行 solve() ----
Tap 963 1745   # AC
Tap 101 947    # SHIFT
Tap $calcCx $calcCy
Start-Sleep -Milliseconds 600
Shot "k3-inline-solve"

# ---- 6. 场景三：新版敲 9∠60+5∠6 → 两个 ∠ 都在 ----
Tap 963 1745   # AC
Tap 539 1745; Tap 101 947; Tap 100 1435; Tap 539 1900; Tap 116 2210
Tap 751 2055; Tap 327 1900; Tap 101 947; Tap 100 1435; Tap 539 1900
Start-Sleep -Milliseconds 500
Shot "k3-angle-v111"
Tap 963 2210   # =
Start-Sleep -Seconds 1
Shot "k3-angle-v111-eval"

# ---- 7. 主行直算：int(x^2,0,1) 与 solve(2x+3=7) ----
Tap 963 1745   # AC
TypeText "int(x^2,0,1)"
Tap 963 2210   # =
Start-Sleep -Seconds 2
Shot "k3-inline-int-eval"

Tap 963 1745   # AC
TypeText "solve(2x+3=7)"
Tap 963 2210   # =
Start-Sleep -Seconds 2
Shot "k3-inline-solve-eval"

# ---- 8. 收工：杀模拟器 ----
& $adb -s $dev emu kill
Write-Host "VERIFY DONE"

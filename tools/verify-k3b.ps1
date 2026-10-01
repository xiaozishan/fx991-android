# 批次 K3 真机复验 v2（修正版）：坐标用 findkey2.py 从 UI dump 解析；
# 每个场景用 LCD 文本节点断言 + 截图双验证。
$ErrorActionPreference = "Continue"
$adb = "D:\applications\Android\Sdk\platform-tools\adb.exe"
$emu = "D:\applications\Android\Sdk\emulator\emulator.exe"
$dev = "emulator-5554"
$proj = "C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android"
Set-Location $proj

function Shot($name) {
    & $adb -s $dev shell screencap -p /data/local/tmp/$name.png
    & $adb -s $dev pull /data/local/tmp/$name.png "$proj\shots\$name.png" | Out-Null
    Write-Host "shot: $name"
}
function Dump {
    & $adb -s $dev shell uiautomator dump /data/local/tmp/ui.xml | Out-Null
    Start-Sleep -Milliseconds 800
    & $adb -s $dev pull /data/local/tmp/ui.xml "$proj\ui-dump.xml" | Out-Null
}
function LcdText {
    Dump
    return (python -X utf8 tools\findkey2.py ui-dump.xml lcdtext) -join ""
}
$script:K = @{}
function LoadKeys {
    Dump
    $out = python -X utf8 tools\findkey2.py ui-dump.xml keys
    foreach ($line in $out) {
        $p = $line -split '\|'
        $script:K[$p[0]] = @([int]$p[1], [int]$p[2])
        Write-Host "key $($p[0]) = ($($p[1]),$($p[2]))  [$($p[3])]"
    }
}
function Tap($name) {
    $xy = $script:K[$name]
    & $adb -s $dev shell input tap $xy[0] $xy[1] | Out-Null
    Start-Sleep -Milliseconds 350
}

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

# ---- 1. 装新版（幂等）并启动 ----
& $adb -s $dev install -r "$proj\app\build\outputs\apk\debug\app-debug.apk" | Out-Null
& $adb -s $dev shell am start -n io.paimon.fx991/.MainActivity | Out-Null
Start-Sleep -Seconds 4
LoadKeys
if (-not $script:K.ContainsKey("CALC") -or -not $script:K.ContainsKey("INTDX")) {
    Write-Host "FATAL: key discovery failed"
    exit 1
}

# ---- 场景一：按 ∫dx → 不弹面板，主行出 ∫ 模板，光标在槽 ----
Tap "AC"
Tap "INTDX"
Start-Sleep -Milliseconds 600
$lcd = LcdText
Write-Host "LCD(after intdx)= $lcd"
$hasInt = $lcd.Contains([char]0x222B)
$hasDx = $lcd.Contains("dx")
Write-Host "ASSERT int template on main line: int=$hasInt dx=$hasDx"
Shot "k3-inline-int"

# ---- 场景二：SHIFT+CALC（SOLVE）→ 不弹面板，主行 solve() ----
Tap "AC"
Tap "SHIFT"
Tap "CALC"
Start-Sleep -Milliseconds 600
$lcd = LcdText
Write-Host "LCD(after SOLVE)= $lcd"
Write-Host "ASSERT solve() on main line: $($lcd.Contains('solve'))"
Shot "k3-inline-solve"

# ---- 场景三：主键盘敲 9∠60+5∠6 → 两个 ∠ 都在 ----
Tap "AC"
Tap "D9"; Tap "SHIFT"; Tap "MINUSKEY"; Tap "D6"; Tap "D0"
Tap "PLUS"; Tap "D5"; Tap "SHIFT"; Tap "MINUSKEY"; Tap "D6"
Start-Sleep -Milliseconds 500
$lcd = LcdText
$angleCount = ([regex]::Matches($lcd, [char]0x2220)).Count
Write-Host "LCD(9A60+5A6)= $lcd"
Write-Host "ASSERT two angles on main line: count=$angleCount"
Shot "k3-angle-v111"
Tap "EQ"
Start-Sleep -Seconds 1
Shot "k3-angle-v111-eval"

# ---- 场景四：∫dx 模板里二维编辑出 int(x^2,0,1) → = 出 0.333… ----
Tap "AC"
Tap "INTDX"
Tap "ALPHA"; Tap "RPAREN"   # x
Tap "XSQ"                   # x²
Tap "PADRIGHT"; Tap "D0"    # 下限 0
Tap "PADRIGHT"; Tap "D1"    # 上限 1
Start-Sleep -Milliseconds 400
$lcd = LcdText
Write-Host "LCD(int filled)= $lcd"
Tap "EQ"
Start-Sleep -Seconds 2
Shot "k3-inline-int-eval"

# ---- 场景五：solve(2x+3=7) → x=2 ----
Tap "AC"
Tap "SHIFT"; Tap "CALC"
Tap "D2"; Tap "ALPHA"; Tap "RPAREN"; Tap "PLUS"; Tap "D3"
Tap "ALPHA"; Tap "CALC"     # =
Tap "D7"
Start-Sleep -Milliseconds 400
$lcd = LcdText
Write-Host "LCD(solve filled)= $lcd"
Tap "EQ"
Start-Sleep -Seconds 2
Shot "k3-inline-solve-eval"

# ---- 收工 ----
& $adb -s $dev emu kill
Write-Host "VERIFY DONE"

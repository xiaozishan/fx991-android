# 批次 K3 真机复验 v3：只补场景四（int 模板二维编辑求值）与场景五（solve 求解）
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
    }
    Write-Host "INTDX=$($script:K['INTDX']) XSQ=$($script:K['XSQ']) CALC=$($script:K['CALC'])"
}
function Tap($name) {
    $xy = $script:K[$name]
    & $adb -s $dev shell input tap $xy[0] $xy[1] | Out-Null
    Start-Sleep -Milliseconds 350
}

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

& $adb -s $dev shell am start -n io.paimon.fx991/.MainActivity | Out-Null
Start-Sleep -Seconds 4
LoadKeys
if (-not $script:K.ContainsKey("INTDX")) { Write-Host "FATAL: key discovery failed"; exit 1 }

# ---- 场景四：∫dx 模板里二维编辑出 int(x^2,0,1) → = 出 1/3 ----
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
$lcd = LcdText
Write-Host "LCD(int result)= $lcd"
Shot "k3-inline-int-eval"

# ---- 场景五：solve(2x+3=7) → x=2 ----
Tap "AC"
Tap "SHIFT"; Tap "CALC"
Tap "D2"; Tap "ALPHA"; Tap "RPAREN"; Tap "PLUS"; Tap "D3"
Tap "ALPHA"; Tap "CALC"     # 等号
Tap "D7"
Start-Sleep -Milliseconds 400
$lcd = LcdText
Write-Host "LCD(solve filled)= $lcd"
Tap "EQ"
Start-Sleep -Seconds 2
$lcd = LcdText
Write-Host "LCD(solve result)= $lcd"
Shot "k3-inline-solve-eval"

& $adb -s $dev emu kill
Write-Host "VERIFY DONE"

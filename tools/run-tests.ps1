# 跑全部回归测试（EngineTest 70 条 + v2 精确分数 25 条 + 自然书写布局 12 条 + 微分方程 18 条
# + 批次 A 数值算法 91 条 + 批次 B 工具 119 条 + 批次 C 六大子系统 + Insets 避让布局
# + 自然书写堆叠显示护栏 16 条 + 批次 D 四新模式）
# 用法：powershell -File tools\run-tests.ps1
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "D:\applications\jdk-21"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$java = "D:\applications\jdk-21\bin\java.exe"
$javac = "D:\applications\jdk-21\bin\javac.exe"

$stdlib = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib" `
    -Recurse -Filter "kotlin-stdlib-2.0.21.jar" | Select-Object -First 1 -ExpandProperty FullName
$classes = "app\build\tmp\kotlin-classes\debug"

$suites = @(
    @{ name = "EngineTest";   file = "tools\EngineTest.java";   out = "$env:TEMP\fx991-t1" },
    @{ name = "EngineTestV2"; file = "tools\EngineTestV2.java"; out = "$env:TEMP\fx991-t2" },
    @{ name = "DisplayTest";  file = "tools\DisplayTest.java";  out = "$env:TEMP\fx991-t3" },
    @{ name = "OdeTest";      file = "tools\OdeTest.java";      out = "$env:TEMP\fx991-t4" },
    @{ name = "NumericTest";  file = "tools\NumericTest.java";  out = "$env:TEMP\fx991-t5" },
    @{ name = "ToolsTest";    file = "tools\ToolsTest.java";    out = "$env:TEMP\fx991-t6" },
    @{ name = "BatchCTest";   file = "tools\BatchCTest.java";   out = "$env:TEMP\fx991-t7" },
    @{ name = "InsetsTest";   file = "tools\InsetsTest.java";   out = "$env:TEMP\fx991-t8" },
    @{ name = "UnifiedTest";  file = "tools\UnifiedTest.java";  out = "$env:TEMP\fx991-t9" },
    @{ name = "NatLayoutTest"; file = "tools\NatLayoutTest.java"; out = "$env:TEMP\fx991-t10" },
    @{ name = "BatchDTest";   file = "tools\BatchDTest.java";   out = "$env:TEMP\fx991-t11" },
    @{ name = "PhotoAiTest";  file = "tools\PhotoAiTest.java";  out = "$env:TEMP\fx991-t12" }
)

$failed = 0
foreach ($s in $suites) {
    Write-Host "==== $($s.name) ====" -ForegroundColor Cyan
    & $javac -encoding UTF-8 -cp "$classes;$stdlib" -d $s.out $s.file
    $out = & $java "-Dfile.encoding=UTF-8" -cp "$($s.out);$classes;$stdlib" $s.name 2>&1
    $out | Where-Object { $_ -match 'RESULT|FAIL' } | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0 -or ($out -match 'fail=[1-9]')) { $failed++ }
    Write-Host ""
}
if ($failed -gt 0) {
    Write-Host "有测试未通过：$failed 个套件" -ForegroundColor Red
    exit 1
}
Write-Host "全部通过。" -ForegroundColor Green

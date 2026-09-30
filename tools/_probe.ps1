$root = 'C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android'
$files = Get-ChildItem -Recurse "$root\app\src\main\java" -Filter *.kt
$files += Get-Item "$root\app\build.gradle.kts"
$patterns = @('1.6.0','versionName','off','Version','版本')
foreach ($f in $files) {
  $ln = 0
  foreach ($line in [System.IO.File]::ReadLines($f.FullName)) {
    $ln++
    foreach ($p in $patterns) {
      if ($line.Contains($p)) {
        Write-Output ("{0}:{1}: [{2}] {3}" -f $f.Name, $ln, $p, $line.Trim())
      }
    }
  }
}

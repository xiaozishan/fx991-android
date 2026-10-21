import io

p = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\build.gradle.kts"
s = io.open(p, encoding="utf-8").read()
s = s.replace("versionCode = 131", "versionCode = 132")
s = s.replace('versionName = "1.12.17-integnav2"', 'versionName = "1.12.18-uxpack"')
io.open(p, "w", encoding="utf-8", newline="").write(s)
print([l.strip() for l in s.splitlines() if "versionName" in l or "versionCode" in l][:4])

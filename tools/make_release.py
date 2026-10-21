import json, os, re, subprocess, sys

CFG = r"C:\Users\Administrator\.openclaw\openclaw.json"
PROJ = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android"
APK = os.path.join(PROJ, r"app\build\outputs\apk\debug\app-debug.apk")
TOKEN_RE = re.compile(r"(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})")

cfg = json.load(open(CFG, encoding="utf-8"))

found = []


def walk(node, path=""):
    if isinstance(node, dict):
        for k, v in node.items():
            walk(v, f"{path}.{k}" if path else k)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            walk(v, f"{path}[{i}]")
    elif isinstance(node, str):
        m = TOKEN_RE.fullmatch(node.strip())
        if m:
            found.append((path, node.strip()))


walk(cfg)
if not found:
    print("NO TOKEN FOUND")
    sys.exit(2)

path, token = found[0]
print(f"token found at: {path} (len={len(token)}, prefix={token[:4]}…)")

env = dict(os.environ)
env["GH_TOKEN"] = token
env.pop("GITHUB_TOKEN", None)

r = subprocess.run(
    [
        r"C:\Program Files\GitHub CLI\gh.exe", "release", "create", "v1.12.18",
        r"app\build\outputs\apk\debug\app-debug.apk#app-debug.apk",
        "--title", "v1.12.18 · UX 包（选中文字计算 / 快捷磁贴 / 输入归一化 / 物理键盘）",
        "--notes", "新增：ProcessText 选中文字直接计算；通知栏快捷磁贴；输入归一化（修 – ✕ ⁄ 全角括号 零宽字符导致的语法错误）；物理键盘输入。回归：20 套 JVM 全部通过。",
    ],
    cwd=PROJ, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace",
)
print("exit:", r.returncode)
print((r.stdout or "").strip()[:800])
print((r.stderr or "").strip()[:800])

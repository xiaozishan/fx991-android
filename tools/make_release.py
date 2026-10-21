import json, os, re, subprocess, sys

CFG = r"C:\Users\Administrator\.openclaw\openclaw.json"
PROJ = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android"
TOKEN_RE = re.compile(r"(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})")

tag = sys.argv[1] if len(sys.argv) > 1 else "v1.13.0"
title = sys.argv[2] if len(sys.argv) > 2 else tag
notes = sys.argv[3] if len(sys.argv) > 3 else ""

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
        if TOKEN_RE.fullmatch(node.strip()):
            found.append((path, node.strip()))


walk(cfg)
if not found:
    print("NO TOKEN FOUND")
    sys.exit(2)
path, token = found[0]
print(f"token: {path} (prefix={token[:4]}…)")

env = dict(os.environ)
env["GH_TOKEN"] = token
env.pop("GITHUB_TOKEN", None)

cmd = [
    r"C:\Program Files\GitHub CLI\gh.exe", "release", "create", tag,
    r"app\build\outputs\apk\debug\app-debug.apk#app-debug.apk",
    "--title", title,
]
if notes:
    cmd += ["--notes", notes]
else:
    cmd += ["--generate-notes"]
r = subprocess.run(cmd, cwd=PROJ, env=env, capture_output=True, text=True,
                   encoding="utf-8", errors="replace")
print("exit:", r.returncode)
print((r.stdout or "").strip()[:600])
print((r.stderr or "").strip()[:600])

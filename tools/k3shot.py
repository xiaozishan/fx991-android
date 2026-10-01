# K3-symbolic 截图驱动 v2：solve(x^2+1=0) → ±i ；方向键上下移光标
# v2 修复："=" 等标签在键帽小字里也有同名节点 → 按最靠下（y 最大）的真键选；
#          光标闪烁 → 连拍多张提高捕获率
import re, subprocess, sys, time

ADB = r"D:\applications\Android\Sdk\platform-tools\adb.exe"
DEV = "emulator-5554"

def sh(*args):
    return subprocess.run([ADB, "-s", DEV] + list(args), capture_output=True, text=True)

def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    sh("pull", "/sdcard/ui.xml", "ui-dump-k3.xml")
    return open("ui-dump-k3.xml", encoding="utf-8").read()

def nodes(xml):
    """label -> [(cx, cy, y1), ...]（一个标签可能有多个节点：真键 + 键帽小字）"""
    pat = re.compile(r"<node\b[^>]*?/>", re.S)
    out = {}
    for m in pat.finditer(xml):
        tag = m.group(0)
        t = re.search(r'text="([^"]*)"', tag)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if not t or not b:
            continue
        label = t.group(1)
        if not label:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        out.setdefault(label, []).append(((x1 + x2) // 2, (y1 + y2) // 2, y1))
    return out

def pick(ks, label):
    """同名节点取最靠下的（真键在键区，小字在键帽上沿）"""
    cands = ks[label]
    return max(cands, key=lambda c: c[2])

def tap(xy):
    sh("shell", "input", "tap", str(xy[0]), str(xy[1]))
    time.sleep(0.45)

def shot(name):
    sh("shell", "screencap", "-p", "/sdcard/" + name)
    sh("pull", "/sdcard/" + name, "shots\\" + name)
    print("saved shots\\" + name)

def main():
    time.sleep(2)
    ks = nodes(dump())
    want = ["SHIFT", "ALPHA", "CALC", ")", "x²", "+", "1", "0", "=", "AC", "▲", "▼", "x/y"]
    missing = [w for w in want if w not in ks]
    if missing:
        print("MISSING KEYS:", missing)
        print("available:", sorted(ks.keys())[:80])
        sys.exit(1)

    def k(label):
        tap(pick(ks, label))

    # ---- Shot 1：solve(x^2+1=0) 按 = → x = ±i ----
    k("AC")
    k("SHIFT")          # SHIFT 层
    k("CALC")           # SOLVE → 就地插入 solve()
    k("ALPHA")          # ALPHA 层
    k(")")              # x
    k("x²")             # x²
    k("+")
    k("1")
    k("ALPHA")
    k("CALC")           # =
    k("0")
    time.sleep(0.8)     # 等预览
    k("=")              # 求解（取最靠下的真 = 键）
    time.sleep(1.2)
    shot("k3-solve-x2+1.png")

    # ---- Shot 2/3：1÷2 光标在分母 → ▲ 移到分子（连拍抓闪烁光标）----
    k("AC")
    k("1")
    k("x/y")            # 分数键 → ÷
    k("2")
    time.sleep(0.6)
    for i in range(3):
        shot(f"k3-cursor-den-{i}.png")
        time.sleep(0.35)
    k("▲")              # ↑ → 光标进分子
    time.sleep(0.3)
    for i in range(3):
        shot(f"k3-cursor-num-{i}.png")
        time.sleep(0.35)
    # 再按 ▼ 回到分母，验证双向
    k("▼")
    time.sleep(0.3)
    shot("k3-cursor-back-den.png")

if __name__ == "__main__":
    main()

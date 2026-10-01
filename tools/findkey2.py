# 批次 K3 按键坐标解析 + LCD 文本断言（读本地 ui.xml，UTF-8 安全）
# 用法：python tools\findkey2.py <ui.xml> keys    → 打印 NAME|cx|cy
#       python tools\findkey2.py <ui.xml> lcdtext → 打印 LCD 区全部文本节点（y < 键盘顶）
import re, sys, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

xml = open(sys.argv[1], encoding='utf-8').read()
mode = sys.argv[2] if len(sys.argv) > 2 else 'keys'

pat = re.compile(r'<node\b[^>]*?/?>', re.S)
nodes = []
for m in pat.finditer(xml):
    tag = m.group(0)
    t = re.search(r'text="([^"]*)"', tag)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    nodes.append({'text': t.group(1) if t else '', 'x1': x1, 'y1': y1, 'x2': x2, 'y2': y2,
                  'cx': (x1 + x2) // 2, 'cy': (y1 + y2) // 2, 'area': (x2 - x1) * (y2 - y1)})

def biggest(label):
    cands = [n for n in nodes if n['text'] == label]
    return max(cands, key=lambda n: n['area']) if cands else None

if mode == 'keys':
    out = {}
    for label, name in [('CALC', 'CALC'), ('ALPHA', 'ALPHA'), ('SHIFT', 'SHIFT'), ('AC', 'AC'),
                        (')', 'RPAREN'), ('0', 'D0'), ('1', 'D1'), ('2', 'D2'), ('3', 'D3'),
                        ('5', 'D5'), ('6', 'D6'), ('7', 'D7'), ('9', 'D9'),
                        ('+', 'PLUS'), ('(−)', 'MINUSKEY'), ('▶', 'PADRIGHT')]:
        n = biggest(label)
        if n:
            out[name] = n
    # '=' 真键 = 面积最大的 '='
    eq = biggest('=')
    if eq:
        out['EQ'] = eq
    # ∫dx = 与 CALC 同排、紧挨右侧的键
    calc = out.get('CALC')
    if calc:
        right = [n for n in nodes if abs(n['cy'] - calc['cy']) < 40 and n['x1'] > calc['x2'] and n['area'] > 20000]
        if right:
            out['INTDX'] = min(right, key=lambda n: n['x1'])
    # x² = 与 log 同排的可点击键中从左数第 3 颗（容器节点不算）
    log = biggest('log')
    if log:
        row = sorted([n for n in nodes if abs(n['cy'] - log['cy']) < 40 and n['area'] > 20000
                      and n['text'] and n['area'] < 200000],
                     key=lambda n: n['x1'])
        if len(row) >= 3:
            out['XSQ'] = row[2]
    for name, n in out.items():
        print(f"{name}|{n['cx']}|{n['cy']}|{n['text']}")
elif mode == 'lcdtext':
    # LCD 区 = 屏幕上半部（键盘顶约在 y=900 以下）
    texts = [n['text'] for n in nodes if n['text'] and n['cy'] < 900]
    print('|'.join(texts))

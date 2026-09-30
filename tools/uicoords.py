import re, sys
xml = open('ui-dump.xml', encoding='utf-8').read()
# match node tags with text and bounds attributes in any order
pat = re.compile(r'<node\b[^>]*?/>', re.S)
want = {'+', '\u2212', 'x', '=', 'ALPHA', 'SHIFT', 'Ans', 'AC', '1', '2', '3', '7', '×', '÷'}
for m in pat.finditer(xml):
    tag = m.group(0)
    t = re.search(r'text="([^"]*)"', tag)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if not t or not b:
        continue
    txt = t.group(1)
    if txt in want:
        x1, y1, x2, y2 = map(int, b.groups())
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        print(f"{txt!r:10} center=({cx},{cy}) bounds=[{x1},{y1}][{x2},{y2}]")

import re, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
xml = open('ui-dump.xml', encoding='utf-8').read()
pat = re.compile(r'<node\b[^>]*?/>', re.S)
want = sys.argv[1].split('|')
for m in pat.finditer(xml):
    tag = m.group(0)
    t = re.search(r'text="([^"]*)"', tag)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if not t or not b:
        continue
    txt = t.group(1)
    if txt in want:
        x1, y1, x2, y2 = map(int, b.groups())
        print(f"{txt!r:12} center=({(x1+x2)//2},{(y1+y2)//2})")

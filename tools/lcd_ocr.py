import base64, json, subprocess, sys, urllib.request, os

IMG = r"C:\Users\Administrator\.openclaw\workspace\media\inbound\openclaw-staged-1b61f86c-b4f9-4f75-b6f0-1c38ecce6820\input-a10fb28f-27b3-4b1e-84a7-acbb39bda35b.jpg"
MAGICK = r"D:\applications\media\ImageMagic\ImageMagick-7.1.2-Q16-HDRI\magick.exe"
CROP = r"C:\Users\Administrator\.openclaw\workspace\media\inbound\crop-lcd.png"

subprocess.run([MAGICK, IMG, "-gravity", "North", "-crop", "100%x22%+0+0", "+repage", CROP], check=True)

cfg = json.load(open(r"C:\Users\Administrator\.openclaw\openclaw.json", encoding="utf-8"))
key = cfg["memorySearch"]["remote"]["apiKey"]

b64 = base64.b64encode(open(CROP, "rb").read()).decode()

payload = {
    "model": "glm-4v-flash",
    "messages": [{
        "role": "user",
        "content": [
            {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64}"}},
            {"type": "text", "text": "这是计算器屏幕的显示区。请逐字转写屏幕上的表达式（包括分数线、指数、括号），并说明：1) 有没有括号字符？出现在哪里？2) 闪烁光标（竖线）在哪个字符的左边或右边？只描述看到的，不要推测。"},
        ],
    }],
}

req = urllib.request.Request(
    "https://open.bigmodel.cn/api/paas/v4/chat/completions",
    data=json.dumps(payload).encode(),
    headers={"Content-Type": "application/json", "Authorization": f"Bearer {key}"},
)
try:
    r = json.load(urllib.request.urlopen(req, timeout=60))
    print(r["choices"][0]["message"]["content"])
except Exception as e:
    print("ERR", e)
    try:
        print(e.read().decode())
    except Exception:
        pass

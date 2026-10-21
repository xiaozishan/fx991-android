import io

p = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\src\main\java\io\paimon\fx991\ui\CalcScreen.kt"
s = io.open(p, encoding="utf-8").read()

# imports
if "androidx.compose.ui.input.key.onPreviewKeyEvent" not in s:
    s = s.replace(
        "import androidx.compose.ui.Modifier",
        "import androidx.compose.ui.Modifier\r\nimport androidx.compose.ui.input.key.Key\r\nimport androidx.compose.ui.input.key.KeyEventType\r\nimport androidx.compose.ui.input.key.key\r\nimport androidx.compose.ui.input.key.onPreviewKeyEvent\r\nimport androidx.compose.ui.input.key.type",
        1,
    )

anchor = "        Surface(Modifier.fillMaxSize(), color = c.body) {\r\n            Box(Modifier.fillMaxSize()) {"
anchor2 = "        Surface(Modifier.fillMaxSize(), color = c.body) {\n            Box(Modifier.fillMaxSize()) {"

handler = '''        Surface(Modifier.fillMaxSize(), color = c.body) {
            Box(
                Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val a: KeyAction? = when (ev.key) {
                        Key.Zero, Key.NumPad0 -> KeyAction.Insert("0")
                        Key.One, Key.NumPad1 -> KeyAction.Insert("1")
                        Key.Two, Key.NumPad2 -> KeyAction.Insert("2")
                        Key.Three, Key.NumPad3 -> KeyAction.Insert("3")
                        Key.Four, Key.NumPad4 -> KeyAction.Insert("4")
                        Key.Five, Key.NumPad5 -> KeyAction.Insert("5")
                        Key.Six, Key.NumPad6 -> KeyAction.Insert("6")
                        Key.Seven, Key.NumPad7 -> KeyAction.Insert("7")
                        Key.Eight, Key.NumPad8 -> KeyAction.Insert("8")
                        Key.Nine, Key.NumPad9 -> KeyAction.Insert("9")
                        Key.Period, Key.NumPadDot -> KeyAction.Insert(".")
                        Key.Plus, Key.NumPadAdd -> KeyAction.Insert("+")
                        Key.Minus, Key.NumPadSubtract -> KeyAction.Insert("-")
                        Key.Multiply, Key.NumPadMultiply -> KeyAction.Insert("×")
                        Key.Slash, Key.NumPadDivide -> KeyAction.Insert("/")
                        Key.LeftBracket -> KeyAction.Insert("(")
                        Key.RightBracket -> KeyAction.Insert(")")
                        Key.Equals -> KeyAction.Insert("=")
                        Key.Enter, Key.NumPadEnter -> KeyAction.Equals
                        Key.Backspace -> KeyAction.Del
                        Key.Escape -> KeyAction.Ac
                        Key.DirectionLeft -> KeyAction.PadLeft
                        Key.DirectionRight -> KeyAction.PadRight
                        Key.DirectionUp -> KeyAction.PadUp
                        Key.DirectionDown -> KeyAction.PadDown
                        else -> null
                    }
                    if (a == null) false else { vm.onAction(a); true }
                }
            ) {'''

if "onPreviewKeyEvent" not in s or "KeyAction? = when (ev.key)" not in s:
    replaced = False
    for a in (anchor, anchor2):
        if a in s:
            s = s.replace(a, handler, 1)
            replaced = True
            break
    assert replaced, "CalcApp surface anchor missing"

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("hardware keyboard mapping added")

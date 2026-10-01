package io.paimon.fx991.engine

// ---------------------------------------------------------------------------
// 批次 K3：语法树 → 线性文本（全括号化，保证能被 Lexer/Parser 原样读回）
//
// 用途：int(式,下,上) / deriv(式,x) / sum(式,a,b) / lim(式,x0) 的第一个参数
// 是「x 的表达式」，落进 NumericOps 时需要文本形态 —— 算法一字不改，只做 AST 转写。
// ---------------------------------------------------------------------------

internal object NodeText {

    fun render(n: Node): String = when (n) {
        is Node.Num -> n.lit ?: CalcEngine.literal(n.v)
        is Node.AnsRef -> "Ans"
        is Node.PreAnsRef -> "PreAns"
        is Node.MemRef -> "M"
        is Node.XRef -> "x"
        is Node.YRef -> "y"
        is Node.ZRef -> "z"
        is Node.IRef -> "i"
        is Node.VarRef -> n.name
        is Node.MatRef -> "Mat${n.name}"
        is Node.VecRef -> "Vct${n.name}"
        is Node.Neg -> "-(" + render(n.a) + ")"
        is Node.Add -> "(" + render(n.a) + ")+(" + render(n.b) + ")"
        is Node.Sub -> "(" + render(n.a) + ")-(" + render(n.b) + ")"
        is Node.Mul -> "(" + render(n.a) + ")*(" + render(n.b) + ")"
        is Node.Div -> "(" + render(n.a) + ")/(" + render(n.b) + ")"
        is Node.Pow -> "(" + render(n.a) + ")^(" + render(n.b) + ")"
        is Node.Fact -> "(" + render(n.a) + ")!"
        is Node.Pct -> "(" + render(n.a) + ")%"
        is Node.Recip -> "(" + render(n.a) + ")\u207B\u00B9"
        is Node.Sqrt -> "\u221A(" + render(n.a) + ")"
        is Node.Fn -> n.name + "(" + render(n.a) + ")"
        is Node.Fn2 -> n.name + "(" + render(n.a) + "," + render(n.b) + ")"
        is Node.FnN -> n.args.joinToString(",", prefix = n.name + "(", postfix = ")") { render(it) }
        is Node.Polar -> "(" + render(n.r) + ")\u2220(" + render(n.t) + ")"
        is Node.Dot -> "(" + render(n.a) + ")\u00B7(" + render(n.b) + ")"
        is Node.UFn -> n.args.joinToString(
            ",",
            prefix = n.name + "'".repeat(n.deriv) + "(",
            postfix = ")",
        ) { render(it) }
    }
}

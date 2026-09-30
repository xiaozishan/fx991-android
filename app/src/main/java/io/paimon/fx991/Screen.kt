package io.paimon.fx991

/** 主界面（MODE 菜单切换） */
enum class Screen(val title: String) {
    CALC("计算"),
    ODE("微分方程"),

    // ---- 批次 C ----
    CMPLX("复数"),
    MATRIX("矩阵"),
    VECTOR("向量"),
    STAT("统计与回归"),
    DISTR("概率分布"),
    FUNC_HELP("函数帮助"),

    // ---- 批次 D ----
    EQUATION("方程"),
    BASEN("基数换算"),
    TABLE("函数表"),
    RATIO("比例"),
}

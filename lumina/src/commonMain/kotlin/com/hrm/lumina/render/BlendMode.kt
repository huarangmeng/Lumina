package com.hrm.lumina.render

import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

/**
 * 粒子混合模式枚举，映射到 Compose 的 [ComposeBlendMode]。
 */
enum class ParticleBlendMode(val compose: ComposeBlendMode) {
    /** 正常混合 */
    Normal(ComposeBlendMode.SrcOver),

    /** 叠加（发光效果） */
    Add(ComposeBlendMode.Plus),

    /** 滤色（柔和发光） */
    Screen(ComposeBlendMode.Screen),
}

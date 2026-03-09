package com.hrm.lumina.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import com.hrm.lumina.core.ParticleConfig
import com.hrm.lumina.render.ParticleBlendMode
import com.hrm.lumina.render.drawParticles
import androidx.compose.foundation.layout.Box

/**
 * Lumina 粒子效果的顶层 Composable。
 *
 * 使用示例：
 * ```kotlin
 * LuminaParticleView(
 *     config = luminaConfig {
 *         maxCount = 500
 *         emitRate = 80f
 *         colors = listOf(Color.Cyan, Color.White)
 *     },
 *     modifier = Modifier.fillMaxSize()
 * )
 * ```
 *
 * @param config     粒子系统配置，通过 [luminaConfig] DSL 创建
 * @param blendMode  粒子混合模式，默认 [ParticleBlendMode.Normal]
 * @param modifier   Compose Modifier
 */
@Composable
fun LuminaParticleView(
    config: ParticleConfig = ParticleConfig(),
    blendMode: ParticleBlendMode = ParticleBlendMode.Normal,
    modifier: Modifier = Modifier,
) {
    val state = rememberLuminaState(config)

    // 帧循环：使用 withFrameMillis 与 Compose 渲染时钟同步
    LaunchedEffect(state) {
        var lastTime = 0L
        while (true) {
            withFrameMillis { frameTime ->
                val delta = if (lastTime == 0L) 16f else (frameTime - lastTime).toFloat()
                lastTime = frameTime
                // 限制最大 delta，防止后台恢复时粒子跳变
                state.advance(delta.coerceAtMost(100f))
            }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer()  // 独立硬件加速层，BlendMode.Plus/Screen 生效的必要条件
            .drawWithContent {
                // 读取 frameCount：在 drawWithContent 的 block 里读取 mutableStateOf
                // 会建立 Snapshot 读取依赖，frameCount 变化时自动触发 invalidate
                @Suppress("UNUSED_VARIABLE")
                val frameCount = state.frameCount
                drawParticles(
                    particles = state.engine.particles,
                    camera = state.camera,
                    blendMode = blendMode,
                )
            }
    )
}

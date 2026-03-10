package com.hrm.lumina.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.hrm.lumina.core.ParticleConfig
import com.hrm.lumina.render.ParticleBlendMode
import com.hrm.lumina.render.drawParticles
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

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
            .onSizeChanged { canvasSize = it }
            .graphicsLayer()  // 独立硬件加速层，BlendMode.Plus/Screen 生效的必要条件
            // 跟手手势：发射源跟随手指移动，手指抬起后归位到屏幕中心
            .pointerInput(state) {
                awaitEachGesture {
                    // 等待第一个按下事件，立即将发射源移到触摸位置
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = canvasSize.width.toFloat()
                    val h = canvasSize.height.toFloat()
                    if (w > 0f && h > 0f) {
                        state.onTouch(down.position.x, down.position.y, w, h)
                    }
                    // 持续追踪拖动，发射源实时跟随手指
                    var isDragging = true
                    while (isDragging) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed && w > 0f && h > 0f) {
                                state.onTouch(change.position.x, change.position.y, w, h)
                            }
                        }
                        isDragging = event.changes.any { it.pressed }
                    }
                    // 手指抬起，发射源归位到屏幕中心
                    state.onTouchEnd()
                }
            }
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

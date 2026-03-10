package com.hrm.lumina.ui

import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.hrm.lumina.core.ImageParticleEngine
import com.hrm.lumina.core.ImagePixelData
import com.hrm.lumina.projection.Camera3D
import com.hrm.lumina.projection.CameraRotation
import com.hrm.lumina.render.ParticleBlendMode
import com.hrm.lumina.render.drawImageParticles
import kotlin.math.PI

/**
 * 图片粒子化视图的状态持有者。
 */
class ImageParticleState(
    pixelData: ImagePixelData,
    maxParticles: Int = 8000,
    particleSize: Float = 0.012f,
    floatAmplitude: Float = 0.015f,
    floatSpeed: Float = 0.0008f,
    depthRange: Float = 0.3f,
    alphaThreshold: Float = 0.1f,
    cameraZ: Float = 3.5f,
) {
    val engine = ImageParticleEngine(
        pixelData = pixelData,
        maxParticles = maxParticles,
        particleSize = particleSize,
        floatAmplitude = floatAmplitude,
        floatSpeed = floatSpeed,
        depthRange = depthRange,
        alphaThreshold = alphaThreshold,
    )

    val camera = Camera3D(z = cameraZ)

    /** 相机旋转状态 */
    var rotation by mutableStateOf(CameraRotation())
        private set

    /** 帧计数器，触发重绘 */
    var frameCount by mutableStateOf(0)
        private set

    /** 每帧推进 */
    fun advance(deltaMs: Float) {
        engine.tick(deltaMs)
        frameCount++
    }

    /**
     * 根据拖拽偏移更新相机旋转角度。
     * @param dx 水平拖拽像素
     * @param dy 垂直拖拽像素
     * @param sensitivity 灵敏度（弧度/像素）
     */
    fun onDrag(dx: Float, dy: Float, sensitivity: Float = 0.005f) {
        val newYaw = (rotation.yaw + dx * sensitivity)
            .coerceIn(-PI.toFloat() * 0.6f, PI.toFloat() * 0.6f)
        val newPitch = (rotation.pitch - dy * sensitivity)
            .coerceIn(-PI.toFloat() * 0.4f, PI.toFloat() * 0.4f)
        rotation = CameraRotation(yaw = newYaw, pitch = newPitch)
    }

    /** 重置视角 */
    fun resetRotation() {
        rotation = CameraRotation()
    }
}

/**
 * 创建并记住 [ImageParticleState]。
 */
@Composable
fun rememberImageParticleState(
    pixelData: ImagePixelData,
    maxParticles: Int = 8000,
    particleSize: Float = 0.012f,
    floatAmplitude: Float = 0.015f,
    floatSpeed: Float = 0.0008f,
    depthRange: Float = 0.3f,
    alphaThreshold: Float = 0.1f,
    cameraZ: Float = 3.5f,
): ImageParticleState = remember(pixelData) {
    ImageParticleState(
        pixelData = pixelData,
        maxParticles = maxParticles,
        particleSize = particleSize,
        floatAmplitude = floatAmplitude,
        floatSpeed = floatSpeed,
        depthRange = depthRange,
        alphaThreshold = alphaThreshold,
        cameraZ = cameraZ,
    )
}

/**
 * 图片粒子化视图。
 * 将图片像素转化为 3D 粒子点云，支持手势拖拽旋转视角。
 *
 * 使用示例：
 * ```kotlin
 * ImageParticleView(
 *     pixelData = myImagePixelData,
 *     modifier = Modifier.fillMaxSize(),
 * )
 * ```
 *
 * @param pixelData     图片像素数据（通过平台实现的 loadImagePixels 获取）
 * @param maxParticles  最大粒子数量
 * @param particleSize  粒子大小（3D 空间单位）
 * @param floatAmplitude 漂浮幅度
 * @param floatSpeed    漂浮速度
 * @param depthRange    Z 轴深度范围（景深感）
 * @param alphaThreshold 像素透明度阈值
 * @param cameraZ       相机 Z 距离
 * @param blendMode     混合模式
 * @param modifier      Modifier
 */
@Composable
fun ImageParticleView(
    pixelData: ImagePixelData,
    maxParticles: Int = 8000,
    particleSize: Float = 0.012f,
    floatAmplitude: Float = 0.015f,
    floatSpeed: Float = 0.0008f,
    depthRange: Float = 0.3f,
    alphaThreshold: Float = 0.1f,
    cameraZ: Float = 3.5f,
    blendMode: ParticleBlendMode = ParticleBlendMode.Add,
    modifier: Modifier = Modifier,
) {
    val state = rememberImageParticleState(
        pixelData = pixelData,
        maxParticles = maxParticles,
        particleSize = particleSize,
        floatAmplitude = floatAmplitude,
        floatSpeed = floatSpeed,
        depthRange = depthRange,
        alphaThreshold = alphaThreshold,
        cameraZ = cameraZ,
    )

    // 帧循环：tick 已用 LUT 优化，直接在帧回调里同步执行，避免协程切换开销
    LaunchedEffect(state) {
        var lastTime = 0L
        while (true) {
            withFrameMillis { frameTime ->
                val delta = if (lastTime == 0L) 16f else (frameTime - lastTime).toFloat()
                lastTime = frameTime
                state.advance(delta.coerceAtMost(100f))
            }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer()  // 独立硬件加速层
            // 拖拽手势：控制相机旋转视角
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var isDragging = true
                    while (isDragging) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val delta = change.positionChange()
                                if (delta.x != 0f || delta.y != 0f) {
                                    state.onDrag(delta.x, delta.y)
                                    change.consume()
                                }
                            }
                        }
                        isDragging = event.changes.any { it.pressed }
                    }
                }
            }
            .drawWithContent {
                @Suppress("UNUSED_VARIABLE")
                val frameCount = state.frameCount
                drawImageParticles(
                    engine = state.engine,
                    camera = state.camera,
                    rotation = state.rotation,
                    blendMode = blendMode,
                )
            }
    )
}

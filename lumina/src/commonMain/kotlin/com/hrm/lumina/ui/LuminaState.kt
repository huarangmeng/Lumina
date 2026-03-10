package com.hrm.lumina.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hrm.lumina.core.ParticleConfig
import com.hrm.lumina.core.ParticleEngine
import com.hrm.lumina.projection.Camera3D
import kotlin.math.tan

/**
 * LuminaParticleView 的状态持有者。
 * 通过 [rememberLuminaState] 创建并在重组间保持。
 */
class LuminaState(config: ParticleConfig) {

    /** 粒子物理引擎 */
    val engine = ParticleEngine(config)

    /** 透视相机（从 config 初始化） */
    val camera = Camera3D(z = config.cameraZ)

    /**
     * 帧计数器，每次 tick 后自增，触发 Canvas 重绘。
     * 使用 mutableStateOf 使 Compose 感知变化。
     */
    var frameCount by mutableStateOf(0)
        private set

    /** 由帧循环调用，推进一帧并通知 Compose 重绘 */
    fun advance(deltaMs: Float) {
        engine.tick(deltaMs)
        frameCount++
    }

    /**
     * 接收屏幕触摸坐标，反投影到 3D 空间（z=0 平面）后更新粒子发射原点。
     * 后续所有自动发射的粒子都将从该位置发出，实现发射源跟手效果。
     *
     * @param screenX  触摸点屏幕 X（px）
     * @param screenY  触摸点屏幕 Y（px）
     * @param canvasW  Canvas 宽度（px）
     * @param canvasH  Canvas 高度（px）
     */
    fun onTouch(
        screenX: Float,
        screenY: Float,
        canvasW: Float,
        canvasH: Float,
    ) {
        // 反推透视投影：在 z=0 平面上求 3D 坐标
        // project 公式：screenX = x * scale * (canvasH/2) + canvasW/2
        //               screenY = -y * scale * (canvasH/2) + canvasH/2
        // 其中 scale = 1 / (tan(fov/2) * cameraZ)
        val halfH = tan(camera.fov / 2f)
        val scale = 1f / (halfH * camera.z)
        engine.emitOriginX = (screenX - canvasW / 2f) / (scale * canvasH / 2f)
        engine.emitOriginY = -(screenY - canvasH / 2f) / (scale * canvasH / 2f)
    }

    /** 手指抬起时，发射原点归位到屏幕中心 */
    fun onTouchEnd() {
        engine.emitOriginX = 0f
        engine.emitOriginY = 0f
    }
}

/**
 * 创建并记住 [LuminaState]。
 * config 变化时会重建状态（引擎重置）。
 */
@Composable
fun rememberLuminaState(config: ParticleConfig): LuminaState =
    remember(config) { LuminaState(config) }

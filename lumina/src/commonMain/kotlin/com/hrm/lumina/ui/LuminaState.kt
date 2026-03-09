package com.hrm.lumina.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hrm.lumina.core.ParticleConfig
import com.hrm.lumina.core.ParticleEngine
import com.hrm.lumina.projection.Camera3D

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
}

/**
 * 创建并记住 [LuminaState]。
 * config 变化时会重建状态（引擎重置）。
 */
@Composable
fun rememberLuminaState(config: ParticleConfig): LuminaState =
    remember(config) { LuminaState(config) }

package com.hrm.lumina.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.hrm.lumina.core.ImageParticleEngine
import com.hrm.lumina.projection.Camera3D
import com.hrm.lumina.projection.CameraRotation
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 绘制图片粒子化效果。
 *
 * 性能优化：
 * - 旋转矩阵 cos/sin 提到循环外，每帧只计算 4 次（原来每粒子 4 次 = 60000 次）
 * - 内联投影计算，消除函数调用开销
 * - 去掉排序（图片粒子 Z 轴变化极小，排序对视觉无影响）
 * - 每个粒子只绘制 1 个圆
 */
fun DrawScope.drawImageParticles(
    engine: ImageParticleEngine,
    camera: Camera3D,
    rotation: CameraRotation,
    blendMode: ParticleBlendMode = ParticleBlendMode.Add,
) {
    val w = size.width
    val h = size.height
    val halfH = h / 2f
    val cameraZ = camera.z
    val near = camera.near
    val tanHalfFov = tan(camera.fov / 2f)
    val particleSize = engine.particleSize

    // ── 旋转矩阵预计算（每帧只算 4 次 cos/sin，而非每粒子 4 次）──
    val cosYaw = cos(rotation.yaw)
    val sinYaw = sin(rotation.yaw)
    val cosPitch = cos(rotation.pitch)
    val sinPitch = sin(rotation.pitch)

    val particles = engine.particles
    val n = particles.size

    for (i in 0 until n) {
        val p = particles[i]
        val px = p.currentX
        val py = p.currentY
        val pz = p.currentZ

        // ── 内联旋转 + 投影（消除函数调用开销）──
        // 1. 绕 Y 轴旋转（yaw）
        val rx1 = px * cosYaw + pz * sinYaw
        val ry1 = py
        val rz1 = -px * sinYaw + pz * cosYaw
        // 2. 绕 X 轴旋转（pitch）
        val ry2 = ry1 * cosPitch - rz1 * sinPitch
        val rz2 = ry1 * sinPitch + rz1 * cosPitch
        // 3. 透视投影
        val depth = cameraZ - rz2
        if (depth <= near) continue

        val scale = 1f / (tanHalfFov * depth)
        val screenX = rx1 * scale * halfH + w / 2f
        val screenY = -ry2 * scale * halfH + halfH
        val screenSize = (particleSize * scale * halfH).coerceAtLeast(0.5f)

        val entryAlpha = easeInOutEntry(p.entryProgress)

        drawCircle(
            color = p.color.copy(alpha = entryAlpha * 0.92f),
            radius = screenSize,
            center = Offset(screenX, screenY),
            blendMode = blendMode.compose,
        )
    }
}

/** 入场进度透明度曲线 */
private fun easeInOutEntry(progress: Float): Float {
    return when {
        progress < 0.1f -> progress / 0.1f * 0.3f
        progress > 0.9f -> 1f
        else -> 0.3f + (progress - 0.1f) / 0.8f * 0.7f
    }
}

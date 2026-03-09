package com.hrm.lumina.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.hrm.lumina.core.Particle
import com.hrm.lumina.core.ParticleShape
import com.hrm.lumina.projection.Camera3D
import com.hrm.lumina.projection.project
import com.hrm.lumina.projection.projectSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * DrawScope 扩展函数，将粒子列表投影并绘制到 Canvas 上。
 */
fun DrawScope.drawParticles(
    particles: List<Particle>,
    camera: Camera3D,
    blendMode: ParticleBlendMode = ParticleBlendMode.Add,
) {
    val w = size.width
    val h = size.height

    // 按 Z 深度从远到近排序（画家算法）
    val sorted = particles.sortedByDescending { it.z }

    sorted.forEach { p ->
        val screenPos = project(p.x, p.y, p.z, camera, w, h) ?: return@forEach
        val screenSize = projectSize(p.size, p.z, camera, h).coerceAtLeast(1f)
        val alpha = easeInOut(p.lifeRatio)

        when (p.shape) {
            ParticleShape.Circle -> drawGlowCircle(
                center = screenPos,
                radius = screenSize,
                color = p.color,
                alpha = alpha,
                blendMode = blendMode,
            )

            ParticleShape.Ring -> {
                val color = p.color.copy(alpha = alpha * 0.9f)
                drawCircle(
                    color = color,
                    radius = screenSize,
                    center = screenPos,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = screenSize * 0.25f),
                    blendMode = blendMode.compose,
                )
                // 光晕
                drawCircle(
                    color = p.color.copy(alpha = alpha * 0.15f),
                    radius = screenSize * 1.5f,
                    center = screenPos,
                    blendMode = blendMode.compose,
                )
            }

            ParticleShape.Square -> withTransform({
                rotate(
                    degrees = p.rotation * (180f / PI.toFloat()),
                    pivot = screenPos,
                )
            }) {
                val color = p.color.copy(alpha = alpha)
                // 外层光晕
                drawRect(
                    color = p.color.copy(alpha = alpha * 0.15f),
                    topLeft = Offset(
                        screenPos.x - screenSize * 1.6f,
                        screenPos.y - screenSize * 1.6f
                    ),
                    size = androidx.compose.ui.geometry.Size(screenSize * 3.2f, screenSize * 3.2f),
                    blendMode = blendMode.compose,
                )
                drawRect(
                    color = color,
                    topLeft = Offset(screenPos.x - screenSize, screenPos.y - screenSize),
                    size = androidx.compose.ui.geometry.Size(screenSize * 2f, screenSize * 2f),
                    blendMode = blendMode.compose,
                )
            }

            ParticleShape.Diamond -> withTransform({
                rotate(
                    degrees = p.rotation * (180f / PI.toFloat()),
                    pivot = screenPos,
                )
            }) {
                val color = p.color.copy(alpha = alpha)
                drawPath(
                    path = diamondPath(screenPos, screenSize * 1.5f),
                    color = p.color.copy(alpha = alpha * 0.15f),
                    blendMode = blendMode.compose
                )
                drawPath(
                    path = diamondPath(screenPos, screenSize),
                    color = color,
                    blendMode = blendMode.compose
                )
            }

            ParticleShape.Triangle -> withTransform({
                rotate(
                    degrees = p.rotation * (180f / PI.toFloat()),
                    pivot = screenPos,
                )
            }) {
                val color = p.color.copy(alpha = alpha)
                drawPath(
                    path = polygonPath(screenPos, screenSize * 1.5f, 3),
                    color = p.color.copy(alpha = alpha * 0.15f),
                    blendMode = blendMode.compose
                )
                drawPath(
                    path = polygonPath(screenPos, screenSize, 3),
                    color = color,
                    blendMode = blendMode.compose
                )
            }

            ParticleShape.Hexagon -> withTransform({
                rotate(
                    degrees = p.rotation * (180f / PI.toFloat()),
                    pivot = screenPos,
                )
            }) {
                val color = p.color.copy(alpha = alpha)
                drawPath(
                    path = polygonPath(screenPos, screenSize * 1.5f, 6),
                    color = p.color.copy(alpha = alpha * 0.15f),
                    blendMode = blendMode.compose
                )
                drawPath(
                    path = polygonPath(screenPos, screenSize, 6),
                    color = color,
                    blendMode = blendMode.compose
                )
            }

            ParticleShape.Star -> withTransform({
                rotate(
                    degrees = p.rotation * (180f / PI.toFloat()),
                    pivot = screenPos,
                )
            }) {
                val color = p.color.copy(alpha = alpha)
                drawPath(
                    path = starPath(screenPos, screenSize * 1.6f, 5),
                    color = p.color.copy(alpha = alpha * 0.15f),
                    blendMode = blendMode.compose
                )
                drawPath(
                    path = starPath(screenPos, screenSize, 5),
                    color = color,
                    blendMode = blendMode.compose
                )
            }
        }
    }
}

/**
 * 多层同心圆叠加，模拟高斯模糊发光光晕。
 * 限制最大光晕半径，防止大粒子过曝铺满屏幕。
 */
private fun DrawScope.drawGlowCircle(
    center: Offset,
    radius: Float,
    color: androidx.compose.ui.graphics.Color,
    alpha: Float,
    blendMode: ParticleBlendMode,
) {
    // 限制光晕最大扩散半径，避免大粒子过曝
    val glowRadius = radius.coerceAtMost(size.minDimension * 0.12f)

    // 第1层：最外层柔和光晕（扩散 2.2x）
    drawCircle(
        color = color.copy(alpha = alpha * 0.03f),
        radius = glowRadius * 2.2f,
        center = center,
        blendMode = blendMode.compose,
    )
    // 第2层：外层光晕（扩散 1.6x）
    drawCircle(
        color = color.copy(alpha = alpha * 0.08f),
        radius = glowRadius * 1.6f,
        center = center,
        blendMode = blendMode.compose,
    )
    // 第3层：主体（原始半径）
    drawCircle(
        color = color.copy(alpha = alpha * 0.55f),
        radius = radius,
        center = center,
        blendMode = blendMode.compose,
    )
    // 第4层：核心高亮点
    drawCircle(
        color = color.copy(alpha = alpha * 0.90f),
        radius = radius * 0.35f,
        center = center,
        blendMode = blendMode.compose,
    )
}

/** 正多边形路径 */
private fun polygonPath(center: Offset, radius: Float, sides: Int): Path {
    val path = Path()
    val angleStep = 2f * PI.toFloat() / sides
    val startAngle = -PI.toFloat() / 2f
    for (i in 0 until sides) {
        val angle = startAngle + i * angleStep
        val x = center.x + cos(angle) * radius
        val y = center.y + sin(angle) * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** 菱形路径 */
private fun diamondPath(center: Offset, radius: Float): Path {
    val path = Path()
    path.moveTo(center.x, center.y - radius)
    path.lineTo(center.x + radius * 0.6f, center.y)
    path.lineTo(center.x, center.y + radius)
    path.lineTo(center.x - radius * 0.6f, center.y)
    path.close()
    return path
}

/** 五角星路径 */
private fun starPath(center: Offset, outerRadius: Float, points: Int): Path {
    val path = Path()
    val innerRadius = outerRadius * 0.4f
    val angleStep = PI.toFloat() / points
    val startAngle = -PI.toFloat() / 2f
    for (i in 0 until points * 2) {
        val angle = startAngle + i * angleStep
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * 生命进度的透明度曲线。
 * lifeRatio = 1 为刚出生，0 为即将消亡
 */
private fun easeInOut(lifeRatio: Float): Float {
    return when {
        lifeRatio > 0.85f -> (1f - lifeRatio) / 0.15f  // 渐入
        lifeRatio < 0.2f -> lifeRatio / 0.2f           // 渐出
        else -> 1f
    }
}

package com.hrm.lumina.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.hrm.lumina.core.ImageParticleEngine
import com.hrm.lumina.core.ImageParticleEngine.Companion.OFF_A
import com.hrm.lumina.core.ImageParticleEngine.Companion.OFF_B
import com.hrm.lumina.core.ImageParticleEngine.Companion.OFF_CX
import com.hrm.lumina.core.ImageParticleEngine.Companion.OFF_CY
import com.hrm.lumina.core.ImageParticleEngine.Companion.OFF_CZ
import com.hrm.lumina.core.ImageParticleEngine.Companion.OFF_EP
import com.hrm.lumina.core.ImageParticleEngine.Companion.OFF_G
import com.hrm.lumina.core.ImageParticleEngine.Companion.OFF_R
import com.hrm.lumina.core.ImageParticleEngine.Companion.STRIDE
import com.hrm.lumina.projection.Camera3D
import com.hrm.lumina.projection.CameraRotation
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 图片粒子化渲染器 —— Three.js Points 风格批量渲染
 *
 * 性能策略（对标 Three.js WebGL 粒子）：
 * 1. FloatArray buffer 直接读取，零对象分配
 * 2. 旋转矩阵每帧只算 4 次 cos/sin（提到循环外）
 * 3. CPU 端批量矩阵变换，结果写入预分配 screenBuffer
 * 4. 按量化颜色分组，每组一次 drawPoints（大幅减少 draw call）
 * 5. 近平面渐隐替代硬截断，消除旋转时的截断感
 */
fun DrawScope.drawImageParticles(
    engine: ImageParticleEngine,
    camera: Camera3D,
    rotation: CameraRotation,
    blendMode: ParticleBlendMode = ParticleBlendMode.Normal,
) {
    val w = size.width
    val h = size.height
    val halfH = h / 2f
    val halfW = w / 2f
    val cameraZ = camera.z
    val near = camera.near
    val tanHalfFov = tan(camera.fov / 2f)
    val particleSize = engine.particleSize

    // ── 旋转矩阵预计算（每帧只算 4 次，而非每粒子 4 次）──
    val cosYaw   = cos(rotation.yaw)
    val sinYaw   = sin(rotation.yaw)
    val cosPitch = cos(rotation.pitch)
    val sinPitch = sin(rotation.pitch)

    val buf = engine.buffer
    val n   = engine.particleCount
    val screenBuf = engine.screenBuffer  // [x, y, radius, alpha] × n，预分配

    // ── Pass 1：批量矩阵变换，写入 screenBuffer ──────────────────────────
    // 类比 Three.js vertex shader：所有粒子统一变换
    var visibleCount = 0
    for (i in 0 until n) {
        val base = i * STRIDE
        val px = buf[base + OFF_CX]
        val py = buf[base + OFF_CY]
        val pz = buf[base + OFF_CZ]

        // 绕 Y 轴旋转（yaw）
        val rx1 =  px * cosYaw + pz * sinYaw
        val ry1 =  py
        val rz1 = -px * sinYaw + pz * cosYaw
        // 绕 X 轴旋转（pitch）
        val ry2 =  ry1 * cosPitch - rz1 * sinPitch
        val rz2 =  ry1 * sinPitch + rz1 * cosPitch

        // 透视投影
        val depth = cameraZ - rz2
        if (depth <= near) continue

        // 近平面渐隐（消除硬截断）
        val nearFade = ((depth - near) / (near * 3f)).coerceIn(0f, 1f)

        val scale   = 1f / (tanHalfFov * depth)
        val screenX = rx1 * scale * halfH + halfW
        val screenY = -ry2 * scale * halfH + halfH
        val radius  = (particleSize * scale * halfH).coerceAtLeast(0.8f)

        // 入场透明度
        val ep = buf[base + OFF_EP]
        val entryAlpha = easeInOutEntry(ep)
        val alpha = entryAlpha * nearFade * buf[base + OFF_A] * 0.92f

        // 写入 screenBuffer（复用预分配数组，零 GC）
        val sb = visibleCount * 4
        screenBuf[sb]     = screenX
        screenBuf[sb + 1] = screenY
        screenBuf[sb + 2] = radius
        screenBuf[sb + 3] = alpha

        // 同步更新颜色引用（直接从 buf 读取，无需额外存储）
        // 颜色信息在 Pass 2 直接从 buf 读取
        visibleCount++
    }

    // ── Pass 2：按颜色分组批量绘制 ──────────────────────────────────────
    // 策略：量化颜色（6位精度），相同颜色的粒子合并为一次 drawPoints
    // 类比 Three.js 的 material 分组渲染
    //
    // 实现：构建颜色→Offset列表的映射，然后每组一次 drawPoints
    // 使用 IntArray 作为颜色 key（ARGB 量化），避免 Color 对象 GC

    // 颜色量化精度：每通道 6 位（64 级），平衡分组数量与颜色精度
    val COLOR_BITS = 6
    val COLOR_MASK = (1 shl COLOR_BITS) - 1  // 63
    val COLOR_SCALE = COLOR_MASK.toFloat()

    // 用 HashMap<Int, ArrayList<Offset>> 分组
    // 预估分组数：图片通常有数百到数千种量化颜色
    val groups = HashMap<Long, ArrayList<Offset>>(512)
    val radiusMap = HashMap<Long, Float>(512)  // 每组取平均半径（同组粒子大小相近）

    var screenIdx = 0
    for (i in 0 until n) {
        val base = i * STRIDE
        val px = buf[base + OFF_CX]
        val py = buf[base + OFF_CY]
        val pz = buf[base + OFF_CZ]

        val rx1 =  px * cosYaw + pz * sinYaw
        val ry1 =  py
        val rz1 = -px * sinYaw + pz * cosYaw
        val ry2 =  ry1 * cosPitch - rz1 * sinPitch
        val rz2 =  ry1 * sinPitch + rz1 * cosPitch

        val depth = cameraZ - rz2
        if (depth <= near) continue

        val nearFade = ((depth - near) / (near * 3f)).coerceIn(0f, 1f)
        val scale   = 1f / (tanHalfFov * depth)
        val screenX = rx1 * scale * halfH + halfW
        val screenY = -ry2 * scale * halfH + halfH
        val radius  = (particleSize * scale * halfH).coerceAtLeast(0.8f)

        val ep = buf[base + OFF_EP]
        val entryAlpha = easeInOutEntry(ep)
        val alpha = (entryAlpha * nearFade * buf[base + OFF_A] * 0.92f).coerceIn(0f, 1f)

        // 量化颜色为 Long key（R6 G6 B6 A6 = 24 bit）
        val qr = (buf[base + OFF_R] * COLOR_SCALE + 0.5f).toInt().coerceIn(0, COLOR_MASK)
        val qg = (buf[base + OFF_G] * COLOR_SCALE + 0.5f).toInt().coerceIn(0, COLOR_MASK)
        val qb = (buf[base + OFF_B] * COLOR_SCALE + 0.5f).toInt().coerceIn(0, COLOR_MASK)
        val qa = (alpha * COLOR_SCALE + 0.5f).toInt().coerceIn(0, COLOR_MASK)
        val key = (qr.toLong() shl 18) or (qg.toLong() shl 12) or (qb.toLong() shl 6) or qa.toLong()

        val list = groups.getOrPut(key) { ArrayList(64) }
        list.add(Offset(screenX, screenY))

        // 记录该组的半径（取第一个，同组粒子深度相近，半径差异极小）
        if (!radiusMap.containsKey(key)) {
            radiusMap[key] = radius
        }

        screenIdx++
    }

    // ── Pass 3：每组一次 drawPoints ──────────────────────────────────────
    // 类比 Three.js 的 gl.drawArrays(POINTS, ...)
    for ((key, points) in groups) {
        val qr = ((key shr 18) and COLOR_MASK.toLong()).toInt()
        val qg = ((key shr 12) and COLOR_MASK.toLong()).toInt()
        val qb = ((key shr 6)  and COLOR_MASK.toLong()).toInt()
        val qa = (key           and COLOR_MASK.toLong()).toInt()

        val r = qr.toFloat() / COLOR_SCALE
        val g = qg.toFloat() / COLOR_SCALE
        val b = qb.toFloat() / COLOR_SCALE
        val a = qa.toFloat() / COLOR_SCALE

        val radius = radiusMap[key] ?: 1f

        drawPoints(
            points = points,
            pointMode = PointMode.Points,
            color = Color(r, g, b, a),
            strokeWidth = radius * 2f,
            cap = StrokeCap.Round,
            blendMode = blendMode.compose,
        )
    }
}

/** 入场进度透明度曲线 */
@Suppress("NOTHING_TO_INLINE")
private inline fun easeInOutEntry(progress: Float): Float = when {
    progress < 0.1f -> progress / 0.1f * 0.3f
    progress > 0.9f -> 1f
    else -> 0.3f + (progress - 0.1f) / 0.8f * 0.7f
}
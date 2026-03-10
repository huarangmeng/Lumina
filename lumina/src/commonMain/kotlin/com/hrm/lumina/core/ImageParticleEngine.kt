package com.hrm.lumina.core

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── LUT 查找表（256点，避免每帧大量 sin/cos 调用）──────────────────────────
private val SIN_LUT: FloatArray = FloatArray(256) { sin(it * 2.0 * PI / 256).toFloat() }
private val COS_LUT: FloatArray = FloatArray(256) { cos(it * 2.0 * PI / 256).toFloat() }
private const val LUT_SCALE = 256f / (2f * PI.toFloat())

@Suppress("NOTHING_TO_INLINE")
private inline fun lutSin(angle: Float): Float = SIN_LUT[((angle * LUT_SCALE).toInt() and 0xFF)]

@Suppress("NOTHING_TO_INLINE")
private inline fun lutCos(angle: Float): Float = COS_LUT[((angle * LUT_SCALE).toInt() and 0xFF)]

/**
 * 图片像素数据
 */
data class ImagePixelData(
    val pixels: List<Color>,
    val width: Int,
    val height: Int,
)

/**
 * 图片粒子化引擎 —— Three.js BufferGeometry 风格
 *
 * 所有粒子数据存储在连续 FloatArray 中（类似 WebGL BufferAttribute），
 * 彻底消除对象列表 GC 压力，CPU 缓存命中率极高。
 *
 * 每个粒子占 STRIDE 个 float，布局如下：
 *   [0] targetX      目标 X
 *   [1] targetY      目标 Y
 *   [2] targetZ      目标 Z
 *   [3] currentX     当前 X（含漂浮）
 *   [4] currentY     当前 Y（含漂浮）
 *   [5] currentZ     当前 Z（含漂浮）
 *   [6] startX       入场起始 X
 *   [7] startY       入场起始 Y
 *   [8] startZ       入场起始 Z
 *   [9] colorR       颜色 R（0~1）
 *  [10] colorG       颜色 G（0~1）
 *  [11] colorB       颜色 B（0~1）
 *  [12] colorA       颜色 A（0~1）
 *  [13] phaseX       漂浮相位 X
 *  [14] phaseY       漂浮相位 Y
 *  [15] phaseZ       漂浮相位 Z
 *  [16] freqX        漂浮频率 X
 *  [17] freqY        漂浮频率 Y
 *  [18] freqZ        漂浮频率 Z
 *  [19] entryDelay   入场延迟（ms）
 *  [20] entryDuration 入场时长（ms）
 *  [21] entryProgress 入场进度（0~1）
 */
class ImageParticleEngine(
    val pixelData: ImagePixelData,
    val maxParticles: Int = 20000,
    val particleSize: Float = 0.006f,
    val floatAmplitude: Float = 0.010f,
    val floatSpeed: Float = 0.0004f,
    val depthRange: Float = 1.8f,
    val alphaThreshold: Float = 0.1f,
) {
    companion object {
        const val STRIDE = 22          // 每粒子占用的 float 数量
        // 字段偏移常量（类似 Three.js BufferAttribute offset）
        const val OFF_TX = 0
        const val OFF_TY = 1
        const val OFF_TZ = 2
        const val OFF_CX = 3
        const val OFF_CY = 4
        const val OFF_CZ = 5
        const val OFF_SX = 6
        const val OFF_SY = 7
        const val OFF_SZ = 8
        const val OFF_R  = 9
        const val OFF_G  = 10
        const val OFF_B  = 11
        const val OFF_A  = 12
        const val OFF_PX = 13
        const val OFF_PY = 14
        const val OFF_PZ = 15
        const val OFF_FX = 16
        const val OFF_FY = 17
        const val OFF_FZ = 18
        const val OFF_ED = 19   // entryDelay
        const val OFF_EDU = 20  // entryDuration
        const val OFF_EP = 21   // entryProgress
    }

    /** 粒子数量 */
    var particleCount: Int = 0
        private set

    /**
     * 粒子缓冲区 —— 类比 Three.js Float32Array position buffer
     * 所有粒子数据平铺存储，CPU 缓存友好，零 GC
     */
    val buffer: FloatArray

    /**
     * 渲染用屏幕坐标缓冲区（由渲染器写入）
     * 布局：[x0, y0, r0, a0,  x1, y1, r1, a1, ...]
     * 预分配，避免每帧 GC
     */
    val screenBuffer: FloatArray

    /** 全局时间（ms） */
    private var time: Float = 0f

    init {
        buffer = buildBuffer()
        screenBuffer = FloatArray(particleCount * 4)
    }

    /** 从图片像素采样，填充 FloatArray buffer */
    private fun buildBuffer(): FloatArray {
        val imgW = pixelData.width
        val imgH = pixelData.height
        val aspect = imgW.toFloat() / imgH.toFloat()

        // 收集候选像素
        val candidates = ArrayList<Int>(imgW * imgH / 4)
        for (row in 0 until imgH) {
            for (col in 0 until imgW) {
                val idx = row * imgW + col
                if (idx < pixelData.pixels.size && pixelData.pixels[idx].alpha >= alphaThreshold) {
                    candidates.add(idx)
                }
            }
        }

        if (candidates.isEmpty()) {
            particleCount = 0
            return FloatArray(0)
        }

        val count = candidates.size.coerceAtMost(maxParticles)
        particleCount = count
        val buf = FloatArray(count * STRIDE)

        val step = candidates.size.toFloat() / count.toFloat()
        var sampleIdx = 0f

        for (i in 0 until count) {
            val pixelIdx = candidates[sampleIdx.toInt().coerceAtMost(candidates.size - 1)]
            val col = pixelIdx % imgW
            val row = pixelIdx / imgW
            val color = pixelData.pixels[pixelIdx]

            // 归一化坐标
            val nx = (col.toFloat() / (imgW - 1).coerceAtLeast(1) * 2f - 1f) * aspect
            val ny = -(row.toFloat() / (imgH - 1).coerceAtLeast(1) * 2f - 1f)

            // Z 轴分布：亮度浮雕 + 随机散布
            val brightness = (color.red + color.green + color.blue) / 3f
            val baseZ = (brightness - 0.5f) * depthRange * 0.6f
            val rand = Random.nextFloat()
            val scatterZ = if (rand < 0.95f)
                (Random.nextFloat() - 0.5f) * depthRange * 0.3f
            else
                (Random.nextFloat() - 0.5f) * depthRange * 0.8f
            val nz = baseZ + scatterZ

            // XY 随机偏移（与 Z 散布成正比，保证各旋转面都有发散感）
            val xyScatter = kotlin.math.abs(scatterZ) * 0.4f
            val tx = nx + (Random.nextFloat() - 0.5f) * xyScatter
            val ty = ny + (Random.nextFloat() - 0.5f) * xyScatter

            // 入场起始位置
            val entryAngle = Random.nextFloat() * 2f * PI.toFloat()
            val entryDist = 2f + Random.nextFloat() * 2f
            val sx = cos(entryAngle) * entryDist
            val sy = sin(entryAngle) * entryDist
            val sz = nz + (Random.nextFloat() - 0.5f) * 1.5f

            val base = i * STRIDE
            buf[base + OFF_TX] = tx
            buf[base + OFF_TY] = ty
            buf[base + OFF_TZ] = nz
            buf[base + OFF_CX] = sx   // 初始当前位置 = 入场起始
            buf[base + OFF_CY] = sy
            buf[base + OFF_CZ] = sz
            buf[base + OFF_SX] = sx
            buf[base + OFF_SY] = sy
            buf[base + OFF_SZ] = sz
            buf[base + OFF_R]  = color.red
            buf[base + OFF_G]  = color.green
            buf[base + OFF_B]  = color.blue
            buf[base + OFF_A]  = color.alpha
            buf[base + OFF_PX] = Random.nextFloat() * 2f * PI.toFloat()
            buf[base + OFF_PY] = Random.nextFloat() * 2f * PI.toFloat()
            buf[base + OFF_PZ] = Random.nextFloat() * 2f * PI.toFloat()
            buf[base + OFF_FX] = 0.5f + Random.nextFloat() * 1.0f
            buf[base + OFF_FY] = 0.5f + Random.nextFloat() * 1.0f
            buf[base + OFF_FZ] = 0.3f + Random.nextFloat() * 0.7f
            buf[base + OFF_ED] = Random.nextFloat() * 800f
            buf[base + OFF_EDU] = 800f + Random.nextFloat() * 600f
            buf[base + OFF_EP] = 0f

            sampleIdx += step
        }

        return buf
    }

    /**
     * 每帧更新粒子位置 —— 类比 Three.js vertex shader（CPU 版本）
     * 全程操作 FloatArray，零对象分配，零 GC
     */
    fun tick(deltaMs: Float) {
        time += deltaMs
        val t = time
        val fs = floatSpeed
        val fa = floatAmplitude
        val buf = buffer
        val n = particleCount

        for (i in 0 until n) {
            val base = i * STRIDE

            val ep = buf[base + OFF_EP]

            // 漂浮偏移（LUT 查表）
            val floatX = lutSin(t * fs * buf[base + OFF_FX] + buf[base + OFF_PX]) * fa
            val floatY = lutCos(t * fs * buf[base + OFF_FY] + buf[base + OFF_PY]) * fa * 0.7f
            val floatZ = lutSin(t * fs * buf[base + OFF_FZ] + buf[base + OFF_PZ]) * fa * 0.5f

            if (ep < 1f) {
                // 入场动画
                val effectiveTime = (t - buf[base + OFF_ED]).coerceAtLeast(0f)
                val rawProgress = (effectiveTime / buf[base + OFF_EDU]).coerceIn(0f, 1f)
                val eased = easeOutCubic(rawProgress)
                buf[base + OFF_EP] = rawProgress

                val tx = buf[base + OFF_TX]
                val ty = buf[base + OFF_TY]
                val tz = buf[base + OFF_TZ]
                val sx = buf[base + OFF_SX]
                val sy = buf[base + OFF_SY]
                val sz = buf[base + OFF_SZ]

                buf[base + OFF_CX] = sx + (tx + floatX - sx) * eased
                buf[base + OFF_CY] = sy + (ty + floatY - sy) * eased
                buf[base + OFF_CZ] = sz + (tz + floatZ - sz) * eased
            } else {
                // 漂浮
                buf[base + OFF_CX] = buf[base + OFF_TX] + floatX
                buf[base + OFF_CY] = buf[base + OFF_TY] + floatY
                buf[base + OFF_CZ] = buf[base + OFF_TZ] + floatZ
            }
        }
    }

    fun reset() { time = 0f }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun easeOutCubic(t: Float): Float {
        val t1 = 1f - t
        return 1f - t1 * t1 * t1
    }
}
package com.hrm.lumina.core

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** sin 查找表，256个采样点，避免每帧大量 sin/cos 调用 */
private val SIN_LUT: FloatArray = FloatArray(256) { sin(it * 2.0 * PI / 256).toFloat() }
private val COS_LUT: FloatArray = FloatArray(256) { cos(it * 2.0 * PI / 256).toFloat() }
private const val LUT_SCALE = 256f / (2f * PI.toFloat())

@Suppress("NOTHING_TO_INLINE")
private inline fun lutSin(angle: Float): Float {
    val idx = ((angle * LUT_SCALE).toInt() and 0xFF)
    return SIN_LUT[idx]
}

@Suppress("NOTHING_TO_INLINE")
private inline fun lutCos(angle: Float): Float {
    val idx = ((angle * LUT_SCALE).toInt() and 0xFF)
    return COS_LUT[idx]
}

/**
 * 图片像素数据，用于图片粒子化。
 *
 * @param pixels  像素颜色列表（行优先，从左上到右下）
 * @param width   图片宽度（像素数）
 * @param height  图片高度（像素数）
 */
data class ImagePixelData(
    val pixels: List<Color>,
    val width: Int,
    val height: Int,
)

/**
 * 图片粒子化引擎。
 * 将图片像素采样为粒子，每个粒子的目标位置对应图片中的一个像素，
 * 颜色取自该像素颜色，粒子在目标位置附近轻微漂浮。
 *
 * @param pixelData     图片像素数据
 * @param maxParticles  最大粒子数量（采样密度）
 * @param particleSize  粒子大小（3D 空间单位）
 * @param floatAmplitude 漂浮抖动幅度
 * @param floatSpeed    漂浮速度
 * @param depthRange    Z 轴深度随机范围（产生景深感）
 * @param alphaThreshold 像素透明度阈值，低于此值的像素不生成粒子（0~1）
 */
class ImageParticleEngine(
    val pixelData: ImagePixelData,
    val maxParticles: Int = 8000,
    val particleSize: Float = 0.012f,
    val floatAmplitude: Float = 0.015f,
    val floatSpeed: Float = 0.0008f,
    val depthRange: Float = 0.3f,
    val alphaThreshold: Float = 0.1f,
) {
    /** 图片粒子数据（目标位置 + 颜色 + 漂浮参数） */
    data class ImageParticle(
        // 目标位置（3D 空间，归一化到 [-aspectRatio, aspectRatio] x [-1, 1]）
        val targetX: Float,
        val targetY: Float,
        val targetZ: Float,
        // 像素颜色
        val color: Color,
        // 漂浮动画参数
        val floatPhaseX: Float,   // X 方向漂浮相位
        val floatPhaseY: Float,   // Y 方向漂浮相位
        val floatPhaseZ: Float,   // Z 方向漂浮相位
        val floatFreqX: Float,    // X 方向漂浮频率
        val floatFreqY: Float,    // Y 方向漂浮频率
        val floatFreqZ: Float,    // Z 方向漂浮频率
        // 当前实际位置（含漂浮偏移）
        var currentX: Float = targetX,
        var currentY: Float = targetY,
        var currentZ: Float = targetZ,
        // 入场动画：粒子从随机位置飞向目标位置
        var entryProgress: Float = 0f,   // 0~1，1 表示已到达目标
        val entryStartX: Float = 0f,
        val entryStartY: Float = 0f,
        val entryStartZ: Float = 0f,
        val entryDelay: Float = 0f,      // 入场延迟（ms）
        val entryDuration: Float = 1200f, // 入场动画时长（ms）
    )

    /** 所有图片粒子 */
    val particles: List<ImageParticle>

    /** 预分配排序索引缓冲区，避免每帧 GC */
    val sortBuffer: IntArray

    /** 预分配 Z 值快照缓冲区，避免每帧 GC */
    val currentZSnapshot: FloatArray

    /** 全局时间（ms），用于漂浮动画 */
    private var time: Float = 0f

    init {
        particles = buildParticles()
        sortBuffer = IntArray(particles.size)
        currentZSnapshot = FloatArray(particles.size)
    }

    /** 从图片像素采样粒子 */
    private fun buildParticles(): List<ImageParticle> {
        val imgW = pixelData.width
        val imgH = pixelData.height
        val aspect = imgW.toFloat() / imgH.toFloat()

        // 收集不透明像素
        val candidates = mutableListOf<Pair<Int, Int>>()  // (col, row)
        for (row in 0 until imgH) {
            for (col in 0 until imgW) {
                val idx = row * imgW + col
                if (idx < pixelData.pixels.size) {
                    val color = pixelData.pixels[idx]
                    if (color.alpha >= alphaThreshold) {
                        candidates.add(Pair(col, row))
                    }
                }
            }
        }

        if (candidates.isEmpty()) return emptyList()

        // 均匀采样
        val step = (candidates.size.toFloat() / maxParticles.toFloat()).coerceAtLeast(1f)
        val result = mutableListOf<ImageParticle>()

        var sampleIdx = 0f
        while (sampleIdx < candidates.size && result.size < maxParticles) {
            val (col, row) = candidates[sampleIdx.toInt()]
            val pixelIdx = row * imgW + col
            val color = pixelData.pixels[pixelIdx]

            // 归一化坐标：X 范围 [-aspect, aspect]，Y 范围 [-1, 1]
            val nx = (col.toFloat() / (imgW - 1).coerceAtLeast(1) * 2f - 1f) * aspect
            val ny = -(row.toFloat() / (imgH - 1).coerceAtLeast(1) * 2f - 1f)  // Y 翻转
            // Z 轴由像素亮度决定：亮色向前凸出，暗色向后凹陷，形成浮雕地形感
            val brightness = (color.red + color.green + color.blue) / 3f
            val nz = (brightness - 0.5f) * depthRange + (Random.nextFloat() - 0.5f) * depthRange * 0.15f

            // 入场起始位置：从随机方向飞入
            val entryAngle = Random.nextFloat() * 2f * PI.toFloat()
            val entryDist = 2f + Random.nextFloat() * 2f
            val startX = cos(entryAngle) * entryDist
            val startY = sin(entryAngle) * entryDist
            val startZ = (Random.nextFloat() - 0.5f) * 3f

            result.add(
                ImageParticle(
                    targetX = nx,
                    targetY = ny,
                    targetZ = nz,
                    color = color,
                    floatPhaseX = Random.nextFloat() * 2f * PI.toFloat(),
                    floatPhaseY = Random.nextFloat() * 2f * PI.toFloat(),
                    floatPhaseZ = Random.nextFloat() * 2f * PI.toFloat(),
                    floatFreqX = 0.5f + Random.nextFloat() * 1.0f,
                    floatFreqY = 0.5f + Random.nextFloat() * 1.0f,
                    floatFreqZ = 0.3f + Random.nextFloat() * 0.7f,
                    currentX = startX,
                    currentY = startY,
                    currentZ = startZ,
                    entryProgress = 0f,
                    entryStartX = startX,
                    entryStartY = startY,
                    entryStartZ = startZ,
                    entryDelay = Random.nextFloat() * 800f,
                    entryDuration = 800f + Random.nextFloat() * 600f,
                )
            )
            sampleIdx += step
        }

        return result
    }

    /**
     * 每帧更新粒子位置（漂浮动画 + 入场动画）。
     * 使用 LUT 查表替代 sin/cos，大幅降低 CPU 开销。
     * @param deltaMs 帧时间差（ms）
     */
    fun tick(deltaMs: Float) {
        time += deltaMs
        val t = time
        val fs = floatSpeed
        val fa = floatAmplitude
        val list = particles
        val size = list.size
        for (i in 0 until size) {
            val p = list[i]
            // 使用 LUT 查表替代 sin/cos（速度提升 ~10x）
            val floatX = lutSin(t * fs * p.floatFreqX + p.floatPhaseX) * fa
            val floatY = lutCos(t * fs * p.floatFreqY + p.floatPhaseY) * fa * 0.7f
            val floatZ = lutSin(t * fs * p.floatFreqZ + p.floatPhaseZ) * fa * 0.5f

            if (p.entryProgress < 1f) {
                // 入场动画
                val effectiveTime = (t - p.entryDelay).coerceAtLeast(0f)
                val rawProgress = (effectiveTime / p.entryDuration).coerceIn(0f, 1f)
                val eased = easeOutCubic(rawProgress)
                p.entryProgress = rawProgress
                p.currentX = lerp(p.entryStartX, p.targetX + floatX, eased)
                p.currentY = lerp(p.entryStartY, p.targetY + floatY, eased)
                p.currentZ = lerp(p.entryStartZ, p.targetZ + floatZ, eased)
            } else {
                // 已到达目标，只做漂浮
                p.currentX = p.targetX + floatX
                p.currentY = p.targetY + floatY
                p.currentZ = p.targetZ + floatZ
            }
        }
    }

    /** 重置引擎 */
    fun reset() {
        time = 0f
    }

    // ── 工具函数 ──────────────────────────────────────────────────────────

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun easeOutCubic(t: Float): Float {
        val t1 = 1f - t
        return 1f - t1 * t1 * t1
    }
}

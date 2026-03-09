package com.hrm.lumina.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 粒子物理引擎。
 * 负责每帧更新粒子位置、速度、生命值，以及按配置发射新粒子。
 *
 * @param config 粒子系统配置
 */
class ParticleEngine(val config: ParticleConfig) {

    val pool = ParticlePool(config.maxCount)

    /** 发射计时器（ms 累计） */
    private var emitAccumulator: Float = 0f

    /** 每个粒子的发射间隔（ms） */
    private val emitInterval: Float get() = 1000f / config.emitRate

    /** 网格发射索引 */
    private var gridIndex: Int = 0

    /** 螺旋发射角度 */
    private var spiralAngle: Float = 0f

    /**
     * 每帧调用，推进粒子系统状态。
     * @param deltaMs 距上一帧的时间差（毫秒）
     */
    fun tick(deltaMs: Float) {
        // 1. 更新所有活跃粒子
        pool.tickAndRecycle { p ->
            // 应用重力
            p.vy += config.gravity * deltaMs

            // 应用外力加速度
            p.vx += p.ax * deltaMs
            p.vy += p.ay * deltaMs
            p.vz += p.az * deltaMs
            p.ax = 0f; p.ay = 0f; p.az = 0f

            // 更新位置
            p.x += p.vx * deltaMs
            p.y += p.vy * deltaMs
            p.z += p.vz * deltaMs

            // 更新旋转
            p.rotation += p.rotationSpeed * deltaMs

            // 衰减生命值
            p.life -= deltaMs
        }

        // 2. 按发射速率生成新粒子
        emitAccumulator += deltaMs
        while (emitAccumulator >= emitInterval) {
            emitAccumulator -= emitInterval
            emit()
        }
    }

    /**
     * 在指定 3D 位置施加一个径向力，影响范围内的粒子。
     */
    fun applyForce(x: Float, y: Float, z: Float = 0f, radius: Float, strength: Float) {
        pool.activeParticles().forEach { p ->
            val dx = p.x - x
            val dy = p.y - y
            val dz = p.z - z
            val dist2 = dx * dx + dy * dy + dz * dz
            val r2 = radius * radius
            if (dist2 < r2 && dist2 > 0.0001f) {
                val dist = sqrt(dist2)
                val factor = strength * (1f - dist / radius) / dist
                p.ax += dx * factor
                p.ay += dy * factor
                p.az += dz * factor
            }
        }
    }

    /** 获取当前所有活跃粒子（供渲染层使用） */
    val particles: List<Particle> get() = pool.activeParticles()

    /** 重置引擎，清空所有粒子 */
    fun reset() {
        pool.clear()
        emitAccumulator = 0f
        gridIndex = 0
        spiralAngle = 0f
    }

    // ── 私有：发射单个粒子 ──────────────────────────────────────────────────

    private fun emit() {
        val p = pool.acquire() ?: return

        when (config.emitMode) {
            EmitMode.Sphere -> emitSphere(p)
            EmitMode.Grid   -> emitGrid(p)
            EmitMode.Ring   -> emitRing(p)
            EmitMode.Spiral -> emitSpiral(p)
            EmitMode.Burst  -> emitSphere(p)
        }

        // 生命周期
        p.maxLife = config.lifeRange.start +
                Random.nextFloat() * (config.lifeRange.endInclusive - config.lifeRange.start)
        p.life = p.maxLife

        // 大小
        p.size = config.sizeRange.start +
                Random.nextFloat() * (config.sizeRange.endInclusive - config.sizeRange.start)

        // 颜色
        p.color = config.colors[Random.nextInt(config.colors.size)]

        // 形状
        p.shape = config.shapes[Random.nextInt(config.shapes.size)]

        // 旋转
        p.rotation = Random.nextFloat() * 2f * PI.toFloat()
        val rMin = config.rotationSpeedRange.start
        val rMax = config.rotationSpeedRange.endInclusive
        p.rotationSpeed = if (rMax > 0f) {
            val sign = if (Random.nextBoolean()) 1f else -1f
            sign * (rMin + Random.nextFloat() * (rMax - rMin))
        } else 0f
    }

    /** 球形随机发射 */
    private fun emitSphere(p: Particle) {
        val theta = Random.nextFloat() * 2f * PI.toFloat()
        val phi = Random.nextFloat() * PI.toFloat()
        val r = Random.nextFloat() * config.emitRadius
        p.x = r * sin(phi) * cos(theta)
        p.y = r * sin(phi) * sin(theta)
        p.z = r * cos(phi)

        val speed = config.speedRange.start +
                Random.nextFloat() * (config.speedRange.endInclusive - config.speedRange.start)
        val nx = sin(phi) * cos(theta)
        val ny = sin(phi) * sin(theta)
        val nz = cos(phi)
        p.vx = nx * speed
        p.vy = ny * speed
        p.vz = nz * speed
    }

    /** 网格均匀发射（在 XY 平面上均匀分布） */
    private fun emitGrid(p: Particle) {
        val cols = config.gridColumns
        val rows = config.gridRows
        val total = cols * rows
        val idx = gridIndex % total
        gridIndex++

        val col = idx % cols
        val row = idx / cols

        // 归一化到 [-emitRadius, emitRadius]
        val r = config.emitRadius
        p.x = (col.toFloat() / (cols - 1).coerceAtLeast(1) * 2f - 1f) * r
        p.y = (row.toFloat() / (rows - 1).coerceAtLeast(1) * 2f - 1f) * r
        p.z = (Random.nextFloat() - 0.5f) * r * 0.2f

        // 速度：轻微随机扰动 + 向外
        val speed = config.speedRange.start +
                Random.nextFloat() * (config.speedRange.endInclusive - config.speedRange.start)
        p.vx = (Random.nextFloat() - 0.5f) * speed * 0.3f
        p.vy = (Random.nextFloat() - 0.5f) * speed * 0.3f
        p.vz = speed * (if (Random.nextBoolean()) 1f else -1f) * 0.5f
    }

    /** 圆环发射 */
    private fun emitRing(p: Particle) {
        val angle = Random.nextFloat() * 2f * PI.toFloat()
        val r = config.emitRadius
        p.x = cos(angle) * r
        p.y = sin(angle) * r
        p.z = (Random.nextFloat() - 0.5f) * r * 0.1f

        val speed = config.speedRange.start +
                Random.nextFloat() * (config.speedRange.endInclusive - config.speedRange.start)
        // 沿切线方向 + 向外扩散
        p.vx = cos(angle) * speed * 0.5f + (-sin(angle)) * speed * 0.5f
        p.vy = sin(angle) * speed * 0.5f + cos(angle) * speed * 0.5f
        p.vz = (Random.nextFloat() - 0.5f) * speed
    }

    /** 螺旋发射 */
    private fun emitSpiral(p: Particle) {
        spiralAngle += 0.3f
        val r = config.emitRadius * (0.1f + (spiralAngle % (2f * PI.toFloat())) / (2f * PI.toFloat()) * 0.9f)
        p.x = cos(spiralAngle) * r
        p.y = sin(spiralAngle) * r
        p.z = (Random.nextFloat() - 0.5f) * config.emitRadius * 0.2f

        val speed = config.speedRange.start +
                Random.nextFloat() * (config.speedRange.endInclusive - config.speedRange.start)
        p.vx = cos(spiralAngle) * speed
        p.vy = sin(spiralAngle) * speed
        p.vz = (Random.nextFloat() - 0.5f) * speed * 0.3f
    }
}

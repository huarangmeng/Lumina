package com.hrm.lumina.core

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 粒子发射模式 */
enum class EmitMode {
    Sphere,     // 球形随机（默认）
    Grid,       // 网格均匀分布
    Ring,       // 圆环
    Spiral,     // 螺旋
    Burst,      // 爆炸（一次性全量发射）
}

/**
 * 粒子系统配置。
 * 使用 DSL 构建器风格，通过 [luminaConfig] 创建。
 */
data class ParticleConfig(
    /** 最大粒子数量 */
    val maxCount: Int = 300,

    /** 粒子发射速率（每秒发射数量） */
    val emitRate: Float = 60f,

    /** 粒子生命周期范围（ms） */
    val lifeRange: ClosedFloatingPointRange<Float> = 800f..2000f,

    /** 粒子初始速度范围（单位/ms），推荐 0.0003~0.003 */
    val speedRange: ClosedFloatingPointRange<Float> = 0.0003f..0.002f,

    /** 粒子大小范围（3D 空间单位，通过透视投影转换为屏幕像素） */
    val sizeRange: ClosedFloatingPointRange<Float> = 0.02f..0.06f,

    /** 粒子颜色列表，随机选取 */
    val colors: List<Color> = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFADD8E6),
        Color(0xFF87CEEB),
        Color(0xFFE0E0FF),
    ),

    /** 重力加速度（单位/ms²），正值向下 */
    val gravity: Float = 0.0002f,

    /** 发射区域半径（3D 空间单位） */
    val emitRadius: Float = 0.5f,

    /** 透视相机 Z 距离 */
    val cameraZ: Float = 3f,

    /** 粒子形状列表，随机选取 */
    val shapes: List<ParticleShape> = listOf(ParticleShape.Circle),

    /** 粒子旋转速度范围（弧度/ms），0 表示不旋转 */
    val rotationSpeedRange: ClosedFloatingPointRange<Float> = 0f..0f,

    /** 发射模式 */
    val emitMode: EmitMode = EmitMode.Sphere,

    /** 网格列数（EmitMode.Grid 时使用） */
    val gridColumns: Int = 8,

    /** 网格行数（EmitMode.Grid 时使用） */
    val gridRows: Int = 8,
)

/** DSL 构建器 */
fun luminaConfig(block: ParticleConfigBuilder.() -> Unit): ParticleConfig =
    ParticleConfigBuilder().apply(block).build()

class ParticleConfigBuilder {
    var maxCount: Int = 300
    var emitRate: Float = 60f
    var lifeRange: ClosedFloatingPointRange<Float> = 800f..2000f
    var speedRange: ClosedFloatingPointRange<Float> = 0.0003f..0.002f
    var sizeRange: ClosedFloatingPointRange<Float> = 0.02f..0.06f
    var colors: List<Color> = listOf(Color.White, Color(0xFFADD8E6))
    var gravity: Float = 0.0002f
    var emitRadius: Float = 0.5f
    var cameraZ: Float = 3f
    var shapes: List<ParticleShape> = listOf(ParticleShape.Circle)
    var rotationSpeedRange: ClosedFloatingPointRange<Float> = 0f..0f
    var emitMode: EmitMode = EmitMode.Sphere
    var gridColumns: Int = 8
    var gridRows: Int = 8

    fun build() = ParticleConfig(
        maxCount, emitRate, lifeRange, speedRange, sizeRange, colors,
        gravity, emitRadius, cameraZ, shapes, rotationSpeedRange, emitMode, gridColumns, gridRows
    )
}

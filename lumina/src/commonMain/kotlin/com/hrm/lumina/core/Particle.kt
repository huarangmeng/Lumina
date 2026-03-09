package com.hrm.lumina.core

import androidx.compose.ui.graphics.Color

/** 粒子形状枚举 */
enum class ParticleShape {
    Circle,     // 圆形
    Square,     // 正方形
    Triangle,   // 三角形
    Diamond,    // 菱形
    Star,       // 五角星
    Hexagon,    // 六边形
    Ring,       // 空心圆环
}

/**
 * 单个粒子的数据结构。
 * 使用普通 class（非 data class）配合对象池复用，避免频繁 GC。
 */
class Particle {
    // 3D 位置
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f

    // 3D 速度
    var vx: Float = 0f
    var vy: Float = 0f
    var vz: Float = 0f

    // 外力加速度（每帧叠加后清零）
    var ax: Float = 0f
    var ay: Float = 0f
    var az: Float = 0f

    // 生命周期（ms）
    var life: Float = 0f
    var maxLife: Float = 1000f

    // 外观
    var size: Float = 4f
    var color: Color = Color.White

    // 旋转（弧度）
    var rotation: Float = 0f
    var rotationSpeed: Float = 0f   // 弧度/ms

    // 形状
    var shape: ParticleShape = ParticleShape.Circle

    /** 是否存活 */
    val isAlive: Boolean get() = life > 0f

    /** 生命进度 0~1，1 为刚出生，0 为即将消亡 */
    val lifeRatio: Float get() = if (maxLife > 0f) life / maxLife else 0f

    /** 重置粒子，供对象池复用 */
    fun reset() {
        x = 0f; y = 0f; z = 0f
        vx = 0f; vy = 0f; vz = 0f
        ax = 0f; ay = 0f; az = 0f
        life = 0f; maxLife = 1000f
        size = 4f; color = Color.White
        rotation = 0f; rotationSpeed = 0f
        shape = ParticleShape.Circle
    }
}

package com.hrm.lumina.projection

import androidx.compose.ui.geometry.Offset

/**
 * 透视相机参数。
 *
 * @param fov    垂直视角（弧度），默认 60°
 * @param near   近裁剪面距离
 * @param far    远裁剪面距离
 * @param z      相机在 Z 轴上的位置（相机朝 -Z 方向看）
 */
data class Camera3D(
    val fov: Float = Math.PI.toFloat() / 3f,  // 60°
    val near: Float = 0.1f,
    val far: Float = 100f,
    val z: Float = 3f,
)

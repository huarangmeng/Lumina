package com.hrm.lumina.projection

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 3D → 2D 透视投影工具函数。
 *
 * 坐标系约定：
 *  - 3D 空间：X 向右，Y 向上，Z 向屏幕外（相机在 +Z 看向 -Z）
 *  - 2D 屏幕：原点在 Canvas 中心，X 向右，Y 向下
 *
 * @param x, y, z   粒子 3D 坐标
 * @param camera    透视相机参数
 * @param canvasW   Canvas 宽度（px）
 * @param canvasH   Canvas 高度（px）
 * @return 投影后的屏幕坐标（以 Canvas 左上角为原点），若粒子在相机后方则返回 null
 */
fun project(
    x: Float, y: Float, z: Float,
    camera: Camera3D,
    canvasW: Float,
    canvasH: Float,
): Offset? {
    // 相机空间中粒子的 Z 深度（相机在 camera.z，粒子在 z）
    val depth = camera.z - z
    if (depth <= camera.near) return null  // 粒子在相机后方或近裁剪面内，不渲染

    // 透视除法：fov 决定视锥半高
    val halfH = tan(camera.fov / 2f)
    val scale = 1f / (halfH * depth)

    // 投影到 NDC（-1~1），再映射到屏幕像素（以 Canvas 中心为原点）
    val screenX = x * scale * (canvasH / 2f) + canvasW / 2f
    val screenY = -y * scale * (canvasH / 2f) + canvasH / 2f  // Y 轴翻转

    return Offset(screenX, screenY)
}

/**
 * 带相机旋转的 3D → 2D 透视投影。
 * 先对粒子坐标应用 yaw（绕Y轴）和 pitch（绕X轴）旋转，再做透视投影。
 */
fun projectWithRotation(
    x: Float, y: Float, z: Float,
    camera: Camera3D,
    rotation: CameraRotation,
    canvasW: Float,
    canvasH: Float,
): Offset? {
    // 1. 绕 Y 轴旋转（yaw）
    val cosYaw = cos(rotation.yaw)
    val sinYaw = sin(rotation.yaw)
    val rx1 = x * cosYaw + z * sinYaw
    val ry1 = y
    val rz1 = -x * sinYaw + z * cosYaw

    // 2. 绕 X 轴旋转（pitch）
    val cosPitch = cos(rotation.pitch)
    val sinPitch = sin(rotation.pitch)
    val rx2 = rx1
    val ry2 = ry1 * cosPitch - rz1 * sinPitch
    val rz2 = ry1 * sinPitch + rz1 * cosPitch

    // 3. 透视投影
    val depth = camera.z - rz2
    if (depth <= camera.near) return null

    val halfH = tan(camera.fov / 2f)
    val scale = 1f / (halfH * depth)

    val screenX = rx2 * scale * (canvasH / 2f) + canvasW / 2f
    val screenY = -ry2 * scale * (canvasH / 2f) + canvasH / 2f

    return Offset(screenX, screenY)
}

/**
 * 根据深度计算投影后的粒子视觉大小（近大远小）。
 *
 * @param originalSize 粒子原始大小（px）
 * @param z            粒子 Z 坐标
 * @param camera       透视相机
 * @param canvasH      Canvas 高度（px），用于归一化
 */
fun projectSize(
    originalSize: Float,
    z: Float,
    camera: Camera3D,
    canvasH: Float,
): Float {
    val depth = camera.z - z
    if (depth <= camera.near) return 0f
    val halfH = tan(camera.fov / 2f)
    val scale = 1f / (halfH * depth)
    return originalSize * scale * (canvasH / 2f)
}

/**
 * 带相机旋转的粒子大小投影。
 */
fun projectSizeWithRotation(
    originalSize: Float,
    x: Float, y: Float, z: Float,
    camera: Camera3D,
    rotation: CameraRotation,
    canvasH: Float,
): Float {
    val cosYaw = cos(rotation.yaw)
    val sinYaw = sin(rotation.yaw)
    val rz1 = -x * sinYaw + z * cosYaw

    val cosPitch = cos(rotation.pitch)
    val sinPitch = sin(rotation.pitch)
    val rz2 = y * sinPitch + rz1 * cosPitch

    val depth = camera.z - rz2
    if (depth <= camera.near) return 0f
    val halfH = tan(camera.fov / 2f)
    val scale = 1f / (halfH * depth)
    return originalSize * scale * (canvasH / 2f)
}
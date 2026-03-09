package com.hrm.lumina.projection

import androidx.compose.ui.geometry.Offset
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

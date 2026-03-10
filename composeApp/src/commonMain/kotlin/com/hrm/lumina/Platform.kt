package com.hrm.lumina

import com.hrm.lumina.core.ImagePixelData

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * 将图片字节数组解码为像素数据（各平台实现）。
 * 返回的像素列表为行优先顺序（从左上到右下），颜色为 sRGB。
 */
expect suspend fun loadImagePixelData(bytes: ByteArray): ImagePixelData
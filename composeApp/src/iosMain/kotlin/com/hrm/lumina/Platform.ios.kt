package com.hrm.lumina

import androidx.compose.ui.graphics.Color
import com.hrm.lumina.core.ImagePixelData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.*
import platform.UIKit.UIDevice
import platform.UIKit.UIImage

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

@OptIn(ExperimentalForeignApi::class)
actual suspend fun loadImagePixelData(bytes: ByteArray): ImagePixelData {
    val nsData = bytes.usePinned { pinned ->
        platform.Foundation.NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
    }
    val uiImage = UIImage(data = nsData)
        ?: return ImagePixelData(emptyList(), 0, 0)

    val width = uiImage.size.useContents { width.toInt() }
    val height = uiImage.size.useContents { height.toInt() }
    if (width == 0 || height == 0) return ImagePixelData(emptyList(), 0, 0)

    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bytesPerPixel = 4
    val bytesPerRow = bytesPerPixel * width
    val rawData = ByteArray(height * bytesPerRow)

    rawData.usePinned { pinned ->
        val context = CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = bytesPerRow.toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        )
        CGContextDrawImage(
            context,
            CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
            uiImage.CGImage,
        )
        CGContextRelease(context)
    }
    CGColorSpaceRelease(colorSpace)

    val pixels = mutableListOf<Color>()
    for (i in 0 until width * height) {
        val offset = i * bytesPerPixel
        val r = (rawData[offset].toInt() and 0xFF) / 255f
        val g = (rawData[offset + 1].toInt() and 0xFF) / 255f
        val b = (rawData[offset + 2].toInt() and 0xFF) / 255f
        val a = (rawData[offset + 3].toInt() and 0xFF) / 255f
        pixels.add(Color(red = r, green = g, blue = b, alpha = a))
    }

    return ImagePixelData(pixels = pixels, width = width, height = height)
}
package com.hrm.lumina

import androidx.compose.ui.graphics.Color
import com.hrm.lumina.core.ImagePixelData
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual suspend fun loadImagePixelData(bytes: ByteArray): ImagePixelData {
    val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
        ?: return ImagePixelData(emptyList(), 0, 0)

    val width = bufferedImage.width
    val height = bufferedImage.height
    val pixels = mutableListOf<Color>()

    for (row in 0 until height) {
        for (col in 0 until width) {
            val argb = bufferedImage.getRGB(col, row)
            val a = ((argb shr 24) and 0xFF) / 255f
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            pixels.add(Color(red = r, green = g, blue = b, alpha = a))
        }
    }

    return ImagePixelData(pixels = pixels, width = width, height = height)
}
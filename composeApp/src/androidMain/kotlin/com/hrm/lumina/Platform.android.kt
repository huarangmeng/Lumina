package com.hrm.lumina

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.Color
import com.hrm.lumina.core.ImagePixelData

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual suspend fun loadImagePixelData(bytes: ByteArray): ImagePixelData {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return ImagePixelData(emptyList(), 0, 0)

    val width = bitmap.width
    val height = bitmap.height
    val argbArray = IntArray(width * height)
    bitmap.getPixels(argbArray, 0, width, 0, 0, width, height)
    bitmap.recycle()

    val pixels = argbArray.map { argb ->
        val a = ((argb shr 24) and 0xFF) / 255f
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        Color(red = r, green = g, blue = b, alpha = a)
    }

    return ImagePixelData(pixels = pixels, width = width, height = height)
}
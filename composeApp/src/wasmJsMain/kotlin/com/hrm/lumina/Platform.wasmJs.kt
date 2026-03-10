package com.hrm.lumina

import androidx.compose.ui.graphics.Color
import com.hrm.lumina.core.ImagePixelData
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.coroutines.resume

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

actual suspend fun loadImagePixelData(bytes: ByteArray): ImagePixelData {
    return suspendCancellableCoroutine { cont ->
        val blob = Blob(arrayOf(bytes.toTypedArray()), BlobPropertyBag(type = "image/jpeg"))
        val url = URL.createObjectURL(blob)
        val img = document.createElement("img") as HTMLImageElement
        img.onload = {
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.width = img.naturalWidth
            canvas.height = img.naturalHeight
            val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
            ctx.drawImage(img, 0.0, 0.0)
            val imageData = ctx.getImageData(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
            val data = imageData.data
            val pixels = mutableListOf<Color>()
            var i = 0
            while (i < data.length) {
                val r = data[i].toInt() and 0xFF
                val g = data[i + 1].toInt() and 0xFF
                val b = data[i + 2].toInt() and 0xFF
                val a = data[i + 3].toInt() and 0xFF
                pixels.add(Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = a / 255f))
                i += 4
            }
            URL.revokeObjectURL(url)
            cont.resume(ImagePixelData(pixels = pixels, width = canvas.width, height = canvas.height))
        }
        img.onerror = { _, _, _, _, _ ->
            URL.revokeObjectURL(url)
            cont.resume(ImagePixelData(emptyList(), 0, 0))
        }
        img.src = url
    }
}
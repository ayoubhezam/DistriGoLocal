package com.distrigo.app.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A photo with transparency, picked for a product, a client or a supplier, is stored on white: JPEG has no alpha,
 * and written as it was, its transparent pixels came out black.
 */
@RunWith(AndroidJUnit4::class)
class ImageCaptureTransparencyTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A 200 × 200 PNG: transparent, with a navy square in its middle. */
    private fun transparentPng(): Uri {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (x in 70 until 130) for (y in 70 until 130) setPixel(x, y, Color.rgb(0x0F, 0x2D, 0x6B))
        }
        val file = File(context.cacheDir, "transparent-test.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    private fun isWhite(pixel: Int) = Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240

    @Test
    fun aTransparentImageIsStoredOnWhiteNotBlack() = runBlocking {
        val jpeg = ImageCapture.compressFromUri(context, transparentPng())!!
        val stored = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertTrue("corner is white, was #${Integer.toHexString(stored.getPixel(5, 5))}", isWhite(stored.getPixel(5, 5)))
        assertTrue("edge is white", isWhite(stored.getPixel(199, 100)))
        // The drawing itself is kept
        val middle = stored.getPixel(100, 100)
        assertTrue("middle stays navy, was #${Integer.toHexString(middle)}", Color.blue(middle) in 80..140 && Color.red(middle) < 50)
    }
}

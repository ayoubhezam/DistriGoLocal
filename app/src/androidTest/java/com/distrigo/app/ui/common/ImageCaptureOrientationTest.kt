package com.distrigo.app.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * A camera photo stored sideways with its EXIF orientation comes out upright, for an entity's photo and for the
 * profile picture: a 400 × 200 picture, red on its left half and blue on its right, tagged « turn 90° clockwise »,
 * must come out tall, red at the top and blue at the bottom.
 */
@RunWith(AndroidJUnit4::class)
class ImageCaptureOrientationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "orientation-test").apply { mkdirs() }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun sidewaysPhoto(orientation: Int): Uri {
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawRect(0f, 0f, 200f, 200f, Paint().apply { color = Color.RED })
            drawRect(200f, 0f, 400f, 200f, Paint().apply { color = Color.BLUE })
        }
        val file = File(dir, "photo-$orientation.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    private fun decode(jpeg: ByteArray?): Bitmap {
        assertNotNull("nothing encoded", jpeg)
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg!!.size)
    }

    private fun assertNear(expected: Int, actual: Int, where: String) {
        val close = abs(Color.red(expected) - Color.red(actual)) < 60 &&
            abs(Color.green(expected) - Color.green(actual)) < 60 &&
            abs(Color.blue(expected) - Color.blue(actual)) < 60
        assertTrue("$where: expected ${Integer.toHexString(expected)}, was ${Integer.toHexString(actual)}", close)
    }

    @Test
    fun anEntityPhotoTaggedRotate90ComesOutUpright() = runBlocking {
        val out = decode(ImageCapture.compressFromUri(context, sidewaysPhoto(ExifInterface.ORIENTATION_ROTATE_90)))
        assertEquals(200, out.width)
        assertEquals(400, out.height)
        assertNear(Color.RED, out.getPixel(100, 50), "top")
        assertNear(Color.BLUE, out.getPixel(100, 350), "bottom")
    }

    @Test
    fun anEntityPhotoTaggedRotate270ComesOutUpright() = runBlocking {
        val out = decode(ImageCapture.compressFromUri(context, sidewaysPhoto(ExifInterface.ORIENTATION_ROTATE_270)))
        assertEquals(200, out.width)
        assertEquals(400, out.height)
        assertNear(Color.BLUE, out.getPixel(100, 50), "top")
        assertNear(Color.RED, out.getPixel(100, 350), "bottom")
    }

    @Test
    fun anUntaggedPhotoIsLeftAsItIs() = runBlocking {
        val out = decode(ImageCapture.compressFromUri(context, sidewaysPhoto(ExifInterface.ORIENTATION_NORMAL)))
        assertEquals(400, out.width)
        assertEquals(200, out.height)
        assertNear(Color.RED, out.getPixel(50, 100), "left")
        assertNear(Color.BLUE, out.getPixel(350, 100), "right")
    }

    @Test
    fun theProfilePictureTaggedRotate90ComesOutUprightAndSquare() = runBlocking {
        // The middle square of the 400 × 200 picture is red on its left half and blue on its right; turned upright,
        // red on top
        val out = decode(ImageCapture.compressAvatarFromUri(context, sidewaysPhoto(ExifInterface.ORIENTATION_ROTATE_90)))
        assertEquals(200, out.width)
        assertEquals(200, out.height)
        assertNear(Color.RED, out.getPixel(100, 40), "top")
        assertNear(Color.BLUE, out.getPixel(100, 160), "bottom")
    }
}

@file:Suppress("unused")

package io.github.toyota32k.camera.lib.utils

import android.graphics.*
import androidx.camera.core.ImageProxy
import io.github.toyota32k.camera.lib.TcLib
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer


object ImageUtils {
    // ImageProxy → Bitmap
    fun imageToBitmap(image: ImageProxy, rotationDegrees: Float): Bitmap? {
        return try {
            val data = imageToByteArray(image)
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data!!.size)
            if (rotationDegrees == 0f) {
                bitmap
            } else {
                rotateBitmap(bitmap, rotationDegrees)
            }
        } catch (e:Throwable) {
            TcLib.logger.error(e)
            null
        }
    }

    // Bitmapの回転
    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Float): Bitmap? {
        val mat = Matrix()
        mat.postRotate(rotationDegrees)
        return Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height, mat, true
        )
    }

    // Image → JPEGのバイト配列
    private fun imageToByteArray(image: ImageProxy): ByteArray? {
        var data: ByteArray? = null
        if (image.format == ImageFormat.JPEG) {
            val planes: Array<ImageProxy.PlaneProxy> = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            data = ByteArray(buffer.capacity())
            buffer.get(data)
            return data
        } else if (image.format == ImageFormat.YUV_420_888) {
            data = nv21_to_jpeg(
                yuv420_888_to_nv21(image),
                image.width, image.height
            )
        }
        return data
    }

    // YUV_420_888 → NV21
    private fun yuv420_888_to_nv21(image: ImageProxy): ByteArray {
        val nv21: ByteArray
        val yBuffer: ByteBuffer = image.planes[0].buffer
        val uBuffer: ByteBuffer = image.planes[1].buffer
        val vBuffer: ByteBuffer = image.planes[2].buffer
        val ySize: Int = yBuffer.remaining()
        val uSize: Int = uBuffer.remaining()
        val vSize: Int = vBuffer.remaining()
        nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        return nv21
    }

    // NV21 → JPEG
    private fun nv21_to_jpeg(nv21: ByteArray, width: Int, height: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        yuv.compressToJpeg(Rect(0, 0, width, height), 100, out)
        return out.toByteArray()
    }}
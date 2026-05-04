package com.ronin71.myopencv.services

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class BackgroundRemoveService {

    private lateinit var segmenter: SubjectSegmenter

    init {
        // Khởi tạo segmenter ngay khi tạo service
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        segmenter = SubjectSegmentation.getClient(options)
    }

    fun handle(bitmap: Bitmap, onResult: (Bitmap) -> Unit, onError: (Exception) -> Unit) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        segmenter.process(inputImage)
            .addOnSuccessListener { result ->
                val foregroundBitmap = result.getForegroundBitmap()
                if (foregroundBitmap != null) {
                    val binaryBitmap = toBinaryBitmap(foregroundBitmap)
                    onResult(binaryBitmap)
                } else {
                    onError(Exception("Không thể tách nền, foreground bitmap null"))
                }
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    fun toBinaryBitmap(bitmap: Bitmap): Bitmap {
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(resultBitmap)

        // Vẽ nền đen trước
        canvas.drawColor(android.graphics.Color.BLACK)

        val paint = android.graphics.Paint()
        // Ma trận màu biến đổi:
        // Nếu Alpha > 0 -> RGB sẽ trở thành 255 (Trắng)
        val matrix = floatArrayOf(
            0f, 0f, 0f, 255f, -1f, // R = 255 * A - 1
            0f, 0f, 0f, 255f, -1f, // G = 255 * A - 1
            0f, 0f, 0f, 255f, -1f, // B = 255 * A - 1
            0f, 0f, 0f, 0f, 255f   // A = 255
        )
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)

        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return resultBitmap
    }
}
 
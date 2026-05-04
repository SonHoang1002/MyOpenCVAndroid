package com.ronin71.myopencv.services

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import androidx.core.graphics.createBitmap
import org.opencv.android.OpenCVLoader
import android.util.Log
import java.util.ArrayList

/**
 *
 */
object EdgeDetectionService {
    val TAG = "OutlineProcessor"
    init {
        if (OpenCVLoader.initLocal()) {
            Log.d("OutlineProcessor", "OpenCV initialized successfully")
        } else {
            Log.e("OutlineProcessor", "OpenCV initialization failed")
        }
    }

    /**
     * [bitmap] : Kiểm tra bitmap truyền vào xem có alpha hay không
     */
    fun isTransparentBackground(bitmap: Bitmap): Boolean {
        try {
            if (!bitmap.hasAlpha()) {
                return false
            }
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)
            if (src.channels() < 4) {
                src.release()
                return false
            }
            val alphaMat = Mat()
            Core.extractChannel(src, alphaMat, 3)
            val minMax = Core.minMaxLoc(alphaMat)
            val hasTransparency = minMax.minVal < 255.0
            alphaMat.release()
            src.release()
            return hasTransparency
        }catch (e: Exception){
            Log.e(TAG, "isTransparentBackground error: ${e.toString()} ", )
            throw Exception("isTransparentBackground error: ${e.toString()}")
        }
    }

    /**
     * Tạo ảnh đen trắng chứa đường biên của vật thể
     */
    fun generateContourEdgeImage(bitmap: Bitmap, thickness: Int = 1): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        if (src.channels() < 4) {
            src.release()
            return bitmap
        }

        val channels = ArrayList<Mat>()
        Core.split(src, channels)
        val alphaMat = channels[3]

        // 1. Tạo mask nhị phân từ kênh Alpha
        val binaryMask = Mat()
        Imgproc.threshold(alphaMat, binaryMask, 1.0, 255.0, Imgproc.THRESH_BINARY)

        // 2. Tìm các đường bao
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binaryMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        // 3. Vẽ contour lên nền đen
        val contourMat = Mat.zeros(src.size(), CvType.CV_8UC1)
        Imgproc.drawContours(contourMat, contours, -1, Scalar(255.0), thickness, Imgproc.LINE_AA)

        // 4. Chuyển đổi về Bitmap
        val resultBitmap = createBitmap(bitmap.width, bitmap.height)
        val resultMat = Mat()
        Imgproc.cvtColor(contourMat, resultMat, Imgproc.COLOR_GRAY2RGBA)
        Utils.matToBitmap(resultMat, resultBitmap)

        // Cleanup
        src.release()
        channels.forEach { it.release() }
        alphaMat.release()
        binaryMask.release()
        hierarchy.release()
        contours.forEach { it.release() }
        contourMat.release()
        resultMat.release()

        return resultBitmap
    }

    fun generateContourPoints(bitmap: Bitmap): ArrayList<ArrayList<Double>> {
        try {
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)

            if (src.channels() < 4) {
                src.release()
                return ArrayList()
            }

            val channels = ArrayList<Mat>()
            Core.split(src, channels)
            val alphaMat = channels[3]

            val binaryMask = Mat()
            Imgproc.threshold(alphaMat, binaryMask, 1.0, 255.0, Imgproc.THRESH_BINARY)

            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(binaryMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val normalizedPoints = toListNormalizedPoint(contours, bitmap.width, bitmap.height)

            src.release()
            channels.forEach { it.release() }
            alphaMat.release()
            binaryMask.release()
            hierarchy.release()
            contours.forEach { it.release() }

            return normalizedPoints
        }catch (e : Exception){
            Log.e(TAG, "generateContourPoints error: ${e.toString()} ", )
            throw Exception("generateContourPoints error: ${e.toString()}")
        }
    }

    fun toListNormalizedPoint(contours: List<MatOfPoint>, width:Int, height: Int): ArrayList<ArrayList<Double>> {
        val allContoursList = ArrayList<ArrayList<Double>>()

        for (contour in contours) {
            val points = contour.toArray()
            if (points.isEmpty()) continue

            val pointCoords = ArrayList<Double>()
            for (p in points) {
                pointCoords.add(p.x/ width)
                pointCoords.add(p.y/height)
            }
            allContoursList.add(pointCoords)
        }
        return allContoursList
    }
}
package com.ronin71.myopencv.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import androidx.core.graphics.createBitmap
import org.opencv.core.MatOfPoint
import org.opencv.core.Point

object ObjectDetectionService {
    fun detectObjects(bitmap:Bitmap): Bitmap {
        return try {
            val mat  = Mat()
             Utils.bitmapToMat(bitmap, mat)

            if (mat.empty()) {
                return bitmap
            }

            // Chuyển đổi sang ảnh xám
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

            // Phát hiện cạnh bằng Canny
            val edgesMat = Mat()
            Imgproc.Canny(grayMat, edgesMat, 50.0, 150.0)

            // Tìm contours (viền của đối tượng)
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                edgesMat, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )

            // Vẽ contours lên ảnh gốc
//            for (contour in contours) {
//                val rect = Imgproc.boundingRect(contour)
//                // Lọc bỏ các vùng quá nhỏ (nhiễu)
//                if (rect.area() > 1000) {
//                    Imgproc.rectangle(
//                        mat, rect,
//                        Scalar(0.0, 255.0, 0.0), 2
//                    )
//
////                    // Thêm text label nếu cần
////                    Imgproc.putText(
////                        mat, "Object",
////                        Point(rect.x.toDouble(), rect.y.toDouble() - 5.0),
////                        Imgproc.FONT_HERSHEY_SIMPLEX,
////                        0.8, Scalar(0.0, 255.0, 0.0), 2
////                    )
//                }
//            }
            val targetBitmap = createBitmap(bitmap.width, bitmap.height)
            Utils.matToBitmap(mat,targetBitmap )
            targetBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }
    /**
     * Tự động detect object và tạo binary mask:
     *   - Ảnh có alpha (PNG transparent) → dùng alpha channel trực tiếp
     *   - Ảnh không có alpha (JPG solid bg) → dùng GrabCut
     *
     * Output: Bitmap cùng kích thước gốc
     *   - Object = trắng (255,255,255)
     *   - Background = đen (0,0,0)
     */
    fun generateObjectMask(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)   // RGBA

        return try {
            if (hasTransparentBackground(src)) {
                generateMaskFromAlpha(bitmap, src)
            } else {
                generateMaskGrabCut(bitmap, src)
            }
        } finally {
            src.release()
        }
    }

    // ── Check xem ảnh có nền transparent không ────────────────────────────────
    private fun hasTransparentBackground(src: Mat): Boolean {
        if (src.channels() < 4) return false
        val alpha   = Mat()
        val minMax  = try {
            Core.extractChannel(src, alpha, 3)
            Core.minMaxLoc(alpha)
        } finally {
            alpha.release()
        }
        // Có ít nhất 1 pixel alpha < 255 → có transparent
        return minMax.minVal < 255.0
    }

    // ── Case 1: PNG có alpha → dùng alpha channel làm mask ───────────────────
    // Nhanh, chính xác tuyệt đối vì đã có sẵn thông tin alpha
    private fun generateMaskFromAlpha(bitmap: Bitmap, src: Mat): Bitmap {
        // Tách alpha channel
        val channels   = ArrayList<Mat>()
        val alphaMat   = Mat()
        val binaryMask = Mat()
        val closedMask = Mat()
        val resultMat  = Mat()

        try {
            Core.split(src, channels)
            alphaMat.create(src.size(), CvType.CV_8UC1)
            channels[3].copyTo(alphaMat)

            // Alpha > 1 → object (trắng), còn lại → đen
            Imgproc.threshold(
                alphaMat, binaryMask,
                1.0, 255.0,
                Imgproc.THRESH_BINARY,
            )

            // Morphological close: lấp các lỗ nhỏ bên trong object
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0)
            )
            Imgproc.morphologyEx(binaryMask, closedMask, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            // Grayscale → RGBA để tạo Bitmap
            Imgproc.cvtColor(closedMask, resultMat, Imgproc.COLOR_GRAY2RGBA)

            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultMat, result)
            return result

        } finally {
            channels.forEach { it.release() }
            alphaMat.release()
            binaryMask.release()
            closedMask.release()
            resultMat.release()
        }
    }

    // ── Case 2: JPG solid background → GrabCut ────────────────────────────────
    // GrabCut segment foreground dựa vào vùng trung tâm ảnh
    private fun generateMaskGrabCut(bitmap: Bitmap, src: Mat): Bitmap {
        val bgr        = Mat()
        val mask       = Mat()
        val bgdModel   = Mat()
        val fgdModel   = Mat()
        val fgMask1    = Mat()
        val fgMask2    = Mat()
        val fgCombined = Mat()
        val binaryMask = Mat()
        val closedMask = Mat()
        val resultMat  = Mat()

        try {
            // GrabCut yêu cầu BGR
            Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR)

            // Rect bỏ 5% viền → GrabCut tập trung vào vùng trung tâm
            val marginX  = (bgr.width()  * 0.05).toInt()
            val marginY  = (bgr.height() * 0.05).toInt()
            val grabRect = Rect(
                marginX, marginY,
                bgr.width() - marginX * 2,
                bgr.height() - marginY * 2,
            )

            mask.create(bgr.size(), CvType.CV_8UC1)

            // Chạy GrabCut 5 iteration
            Imgproc.grabCut(
                bgr, mask, grabRect,
                bgdModel, fgdModel,
                5,
                Imgproc.GC_INIT_WITH_RECT,
            )

            // Pixel GC_FGD (1) + GC_PR_FGD (3) → foreground
            Core.compare(mask, Scalar(3.0), fgMask1, Core.CMP_EQ)  // probable fg
            Core.compare(mask, Scalar(1.0), fgMask2, Core.CMP_EQ)  // definite fg
            Core.bitwise_or(fgMask1, fgMask2, fgCombined)

            // Convert sang CV_8UC1: foreground = 255
            fgCombined.convertTo(binaryMask, CvType.CV_8UC1)

            // Morphological close: bịt lỗ hổng
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0)
            )
            Imgproc.morphologyEx(binaryMask, closedMask, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            // Grayscale → RGBA → Bitmap
            Imgproc.cvtColor(closedMask, resultMat, Imgproc.COLOR_GRAY2RGBA)

            val result = createBitmap(bitmap.width, bitmap.height)
            Utils.matToBitmap(resultMat, result)
            return result

        } finally {
            bgr.release()
            mask.release()
            bgdModel.release()
            fgdModel.release()
            fgMask1.release()
            fgMask2.release()
            fgCombined.release()
            binaryMask.release()
            closedMask.release()
            resultMat.release()
        }
    }
}
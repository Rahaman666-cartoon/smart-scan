package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Utility class for OpenCV image processing.
 *
 * Provides operations including grayscale conversion, Gaussian blur, Canny edge detection,
 * and automated image preprocessing pipelines to prepare captured images for contour analysis.
 */
object OpenCvImageProcessor {

    private const val TAG = "OpenCvImageProcessor"

    @Volatile
    private var isInitialized: Boolean = false

    /**
     * Initializes the OpenCV native library.
     * Tries OpenCVLoader.initLocal() first (preferred for OpenCV 4.9.0+ AAR),
     * and falls back to initDebug() if needed.
     */
    fun init(context: Context? = null): Boolean {
        if (isInitialized) return true

        synchronized(this) {
            if (isInitialized) return true
            try {
                if (OpenCVLoader.initLocal()) {
                    isInitialized = true
                    Log.i(TAG, "OpenCV initialized successfully via initLocal()")
                    return true
                }
            } catch (t: Throwable) {
                Log.w(TAG, "OpenCVLoader.initLocal() failed, trying fallback: ${t.message}")
            }

            try {
                @Suppress("DEPRECATION")
                if (OpenCVLoader.initDebug()) {
                    isInitialized = true
                    Log.i(TAG, "OpenCV initialized successfully via initDebug()")
                    return true
                }
            } catch (t: Throwable) {
                Log.e(TAG, "OpenCVLoader.initDebug() failed: ${t.message}")
            }

            Log.e(TAG, "Unable to initialize OpenCV library")
            return false
        }
    }

    /**
     * Ensures that OpenCV is loaded before any image processing call.
     */
    fun ensureInitialized(): Boolean {
        if (!isInitialized) {
            init()
        }
        return isInitialized
    }

    /**
     * Converts an Android [Bitmap] into an OpenCV [Mat].
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        ensureInitialized()
        val mat = Mat()
        val bmpConfig = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
        Utils.bitmapToMat(bmpConfig, mat)
        return mat
    }

    /**
     * Converts an OpenCV [Mat] back into an Android [Bitmap].
     */
    fun matToBitmap(mat: Mat): Bitmap {
        ensureInitialized()
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }

    /**
     * Converts an input image [Mat] to single-channel Grayscale.
     * Supports RGBA, RGB, BGR, or already single-channel matrices.
     *
     * @param src Source image matrix
     * @return Grayscale single-channel [Mat] (CV_8UC1)
     */
    fun convertToGrayscale(src: Mat): Mat {
        ensureInitialized()
        val gray = Mat()
        when (src.channels()) {
            4 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            3 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
            1 -> src.copyTo(gray)
            else -> {
                Log.w(TAG, "Unexpected channel count ${src.channels()}, attempting default grayscale conversion")
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
            }
        }
        return gray
    }

    /**
     * Converts a [Bitmap] to a grayscale [Bitmap].
     */
    fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val srcMat = bitmapToMat(bitmap)
        val grayMat = convertToGrayscale(srcMat)
        val result = matToBitmap(grayMat)
        srcMat.release()
        grayMat.release()
        return result
    }

    /**
     * Applies Gaussian Blur smoothing to an image to suppress high-frequency noise.
     *
     * @param src Source image matrix (typically grayscale)
     * @param kernelSize Width and height of the Gaussian kernel (must be positive and odd, e.g., 3.0, 5.0, 7.0)
     * @param sigmaX Gaussian kernel standard deviation in X direction (0.0 implies calculated from kernel size)
     * @param sigmaY Gaussian kernel standard deviation in Y direction (0.0 implies same as sigmaX)
     * @return Blurred [Mat]
     */
    fun applyGaussianBlur(
        src: Mat,
        kernelSize: Double = 5.0,
        sigmaX: Double = 0.0,
        sigmaY: Double = 0.0
    ): Mat {
        ensureInitialized()
        var k = kernelSize
        if (k <= 0) k = 3.0
        if (k.toInt() % 2 == 0) k += 1.0 // Ensure odd kernel size

        val dst = Mat()
        Imgproc.GaussianBlur(src, dst, Size(k, k), sigmaX, sigmaY)
        return dst
    }

    /**
     * Applies Gaussian Blur to a [Bitmap].
     */
    fun applyGaussianBlur(
        bitmap: Bitmap,
        kernelSize: Double = 5.0,
        sigmaX: Double = 0.0
    ): Bitmap {
        val srcMat = bitmapToMat(bitmap)
        val blurredMat = applyGaussianBlur(srcMat, kernelSize, sigmaX)
        val result = matToBitmap(blurredMat)
        srcMat.release()
        blurredMat.release()
        return result
    }

    /**
     * Performs Canny Edge Detection on a grayscale or blurred image.
     *
     * @param src Grayscale or blurred input [Mat]
     * @param threshold1 First threshold for the hysteresis procedure (lower bound)
     * @param threshold2 Second threshold for the hysteresis procedure (upper bound)
     * @param apertureSize Aperture size for the Sobel operator (3, 5, or 7)
     * @param l2gradient Flag indicating whether to use L2 norm (true) or L1 norm (false)
     * @return Binary edge image [Mat] where edges are 255 and background is 0
     */
    fun detectCannyEdges(
        src: Mat,
        threshold1: Double = 50.0,
        threshold2: Double = 150.0,
        apertureSize: Int = 3,
        l2gradient: Boolean = false
    ): Mat {
        ensureInitialized()
        val edges = Mat()
        val validAperture = when (apertureSize) {
            3, 5, 7 -> apertureSize
            else -> 3
        }
        Imgproc.Canny(src, edges, threshold1, threshold2, validAperture, l2gradient)
        return edges
    }

    /**
     * Performs Canny Edge Detection on a [Bitmap].
     */
    fun detectCannyEdges(
        bitmap: Bitmap,
        threshold1: Double = 50.0,
        threshold2: Double = 150.0
    ): Bitmap {
        val srcMat = bitmapToMat(bitmap)
        val grayMat = convertToGrayscale(srcMat)
        val edgesMat = detectCannyEdges(grayMat, threshold1, threshold2)
        val result = matToBitmap(edgesMat)
        srcMat.release()
        grayMat.release()
        edgesMat.release()
        return result
    }

    /**
     * Calculates automatic Canny lower and upper thresholds based on the median
     * pixel intensity of a grayscale image.
     *
     * @param grayMat Grayscale image matrix
     * @param sigma Percentage range around the median (default 0.33 gives a robust 33% spread)
     * @return Pair of (lowerThreshold, upperThreshold)
     */
    fun computeAutoCannyThresholds(grayMat: Mat, sigma: Double = 0.33): Pair<Double, Double> {
        val median = computeMedian(grayMat)
        val lower = max(0.0, (1.0 - sigma) * median)
        val upper = min(255.0, (1.0 + sigma) * median)
        return Pair(lower, upper)
    }

    /**
     * Dilates binary edges to connect fragmented lines and close small gaps before contour extraction.
     *
     * @param edgesMat Binary edge matrix from Canny
     * @param kernelSize Kernel dimension (e.g., 3.0)
     * @param iterations Number of times dilation is applied
     */
    fun applyDilation(edgesMat: Mat, kernelSize: Double = 3.0, iterations: Int = 1): Mat {
        ensureInitialized()
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(kernelSize, kernelSize)
        )
        val dilated = Mat()
        Imgproc.dilate(edgesMat, dilated, kernel, Point(-1.0, -1.0), iterations)
        kernel.release()
        return dilated
    }

    /**
     * Preprocessing pipeline container containing intermediate Mats for debugging or multi-step analysis.
     */
    data class PreprocessingPipelineResult(
        val original: Mat,
        val grayscale: Mat,
        val blurred: Mat,
        val edges: Mat,
        val dilatedEdges: Mat
    ) : AutoCloseable {
        override fun close() {
            original.release()
            grayscale.release()
            blurred.release()
            edges.release()
            dilatedEdges.release()
        }
    }

    /**
     * Full OpenCV image processing pipeline preparing a captured image for contour analysis:
     * 1. Convert to Grayscale
     * 2. Apply Gaussian Blur to eliminate high-frequency noise
     * 3. Perform Canny edge detection (with optional automatic threshold estimation)
     * 4. Perform light morphological dilation to close gaps in contours
     *
     * @param src Original image [Mat]
     * @param autoThreshold If true, computes adaptive thresholds based on pixel median; otherwise uses [cannyThreshold1] & [cannyThreshold2]
     * @param blurKernelSize Size of Gaussian blur kernel (default 5.0)
     * @param cannyThreshold1 Manual Canny low threshold (default 50.0)
     * @param cannyThreshold2 Manual Canny high threshold (default 150.0)
     * @param dilateEdges Whether to dilate edges to aid contour tracing
     * @return Binary [Mat] ready for [Imgproc.findContours]
     */
    fun prepareForContourAnalysis(
        src: Mat,
        autoThreshold: Boolean = true,
        blurKernelSize: Double = 5.0,
        cannyThreshold1: Double = 50.0,
        cannyThreshold2: Double = 150.0,
        dilateEdges: Boolean = true
    ): Mat {
        ensureInitialized()

        // Step 1: Grayscale Conversion
        val gray = convertToGrayscale(src)

        // Step 2: Gaussian Blur smoothing
        val blurred = applyGaussianBlur(gray, blurKernelSize)
        gray.release()

        // Step 3: Canny Edge Detection
        val (th1, th2) = if (autoThreshold) {
            computeAutoCannyThresholds(blurred)
        } else {
            Pair(cannyThreshold1, cannyThreshold2)
        }
        val edges = detectCannyEdges(blurred, th1, th2)
        blurred.release()

        // Step 4: Edge dilation (optional, connects broken boundaries)
        return if (dilateEdges) {
            val dilated = applyDilation(edges, kernelSize = 3.0, iterations = 1)
            edges.release()
            dilated
        } else {
            edges
        }
    }

    /**
     * Prepares a captured [Bitmap] for contour analysis.
     * Returns the binary edge [Mat] ready for [Imgproc.findContours].
     */
    fun prepareForContourAnalysis(
        bitmap: Bitmap,
        autoThreshold: Boolean = true,
        blurKernelSize: Double = 5.0,
        cannyThreshold1: Double = 50.0,
        cannyThreshold2: Double = 150.0,
        dilateEdges: Boolean = true
    ): Mat {
        val srcMat = bitmapToMat(bitmap)
        val preparedMat = prepareForContourAnalysis(
            src = srcMat,
            autoThreshold = autoThreshold,
            blurKernelSize = blurKernelSize,
            cannyThreshold1 = cannyThreshold1,
            cannyThreshold2 = cannyThreshold2,
            dilateEdges = dilateEdges
        )
        srcMat.release()
        return preparedMat
    }

    /**
     * Executes the complete preprocessing pipeline and returns all intermediate [Mat] stages
     * in a [PreprocessingPipelineResult]. Caller should call [PreprocessingPipelineResult.close]
     * when done with the Mats.
     */
    fun executePipeline(
        bitmap: Bitmap,
        autoThreshold: Boolean = true,
        blurKernelSize: Double = 5.0,
        cannyThreshold1: Double = 50.0,
        cannyThreshold2: Double = 150.0
    ): PreprocessingPipelineResult {
        ensureInitialized()

        val original = bitmapToMat(bitmap)
        val grayscale = convertToGrayscale(original)
        val blurred = applyGaussianBlur(grayscale, blurKernelSize)

        val (th1, th2) = if (autoThreshold) {
            computeAutoCannyThresholds(blurred)
        } else {
            Pair(cannyThreshold1, cannyThreshold2)
        }

        val edges = detectCannyEdges(blurred, th1, th2)
        val dilatedEdges = applyDilation(edges, kernelSize = 3.0, iterations = 1)

        return PreprocessingPipelineResult(
            original = original,
            grayscale = grayscale,
            blurred = blurred,
            edges = edges,
            dilatedEdges = dilatedEdges
        )
    }

    /**
     * Finds contours on a preprocessed binary edge [Mat].
     *
     * @param edgeMat Binary matrix output from [prepareForContourAnalysis]
     * @param mode Contour retrieval mode (default [Imgproc.RETR_LIST] or [Imgproc.RETR_EXTERNAL])
     * @param method Contour approximation method (default [Imgproc.CHAIN_APPROX_SIMPLE])
     */
    fun findContours(
        edgeMat: Mat,
        mode: Int = Imgproc.RETR_EXTERNAL,
        method: Int = Imgproc.CHAIN_APPROX_SIMPLE
    ): List<MatOfPoint> {
        ensureInitialized()
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edgeMat, contours, hierarchy, mode, method)
        hierarchy.release()
        return contours
    }

    /**
     * Computes the approximate median pixel intensity of a single-channel image
     * using a 256-bin histogram.
     */
    private fun computeMedian(mat: Mat): Double {
        val hist = Mat()
        val channels = org.opencv.core.MatOfInt(0)
        val histSize = org.opencv.core.MatOfInt(256)
        val ranges = org.opencv.core.MatOfFloat(0f, 256f)

        try {
            Imgproc.calcHist(
                listOf(mat),
                channels,
                Mat(),
                hist,
                histSize,
                ranges
            )

            val totalPixels = (mat.rows() * mat.cols()).toDouble()
            val halfPixels = totalPixels / 2.0
            var cumulative = 0.0

            for (i in 0 until 256) {
                cumulative += hist.get(i, 0)[0]
                if (cumulative >= halfPixels) {
                    return i.toDouble()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute exact histogram median, using mean fallback: ${e.message}")
            val mean = Core.mean(mat)
            return mean.`val`[0]
        } finally {
            hist.release()
            channels.release()
            histSize.release()
            ranges.release()
        }

        return 128.0
    }
}

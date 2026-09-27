package com.example.utils

import android.graphics.*
import kotlin.math.*

object ImageUtils {

    enum class FilterType {
        ORIGINAL, B_AND_W, MAGIC_COLOR, GRAYSCALE
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f || degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated != source) {
            // Wait, we don't recycle source here in case it's reused, let caller manage it or rely on GC.
        }
        return rotated
    }

    fun applyFilter(source: Bitmap, filterType: FilterType): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { isAntiAlias = true }

        val colorMatrix = ColorMatrix()
        when (filterType) {
            FilterType.ORIGINAL -> return source
            FilterType.GRAYSCALE -> {
                colorMatrix.setSaturation(0f)
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
            FilterType.B_AND_W -> {
                // High contrast thresholding matrix
                val scale = 85f
                val offset = -128f * scale
                colorMatrix.set(floatArrayOf(
                    scale, scale, scale, 0f, offset,
                    scale, scale, scale, 0f, offset,
                    scale, scale, scale, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
            FilterType.MAGIC_COLOR -> {
                // Enhance saturation and brighten/contrast
                val satMatrix = ColorMatrix().apply { setSaturation(1.4f) }
                val contrastVal = 1.2f
                val translateVal = 15f
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    contrastVal, 0f, 0f, 0f, translateVal,
                    0f, contrastVal, 0f, 0f, translateVal,
                    0f, 0f, contrastVal, 0f, translateVal,
                    0f, 0f, 0f, 1f, 0f
                ))
                contrastMatrix.postConcat(satMatrix)
                paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
        }
        return result
    }

    /**
     * Performs a 4-point perspective warp on a bitmap.
     * Takes four normalized points (0f to 1f) and stretches the contained quadrilaterals back into a square sheet of paper.
     */
    fun cropAndWarpPerspective(
        source: Bitmap,
        topLeft: PointF,
        topRight: PointF,
        bottomLeft: PointF,
        bottomRight: PointF
    ): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        // Absolute corner coordinates on source bitmap
        val srcTL = PointF(topLeft.x * w, topLeft.y * h)
        val srcTR = PointF(topRight.x * w, topRight.y * h)
        val srcBL = PointF(bottomLeft.x * w, bottomLeft.y * h)
        val srcBR = PointF(bottomRight.x * w, bottomRight.y * h)

        // Calculate target dimensions (average top/bottom width, left/right height)
        val widthTop = sqrt((srcTR.x - srcTL.x).pow(2) + (srcTR.y - srcTL.y).pow(2))
        val widthBottom = sqrt((srcBR.x - srcBL.x).pow(2) + (srcBR.y - srcBL.y).pow(2))
        val targetWidth = max(widthTop, widthBottom).toInt().coerceAtLeast(100)

        val heightLeft = sqrt((srcBL.x - srcTL.x).pow(2) + (srcBL.y - srcTL.y).pow(2))
        val heightRight = sqrt((srcBR.x - srcTR.x).pow(2) + (srcBR.y - srcTR.y).pow(2))
        val targetHeight = max(heightLeft, heightRight).toInt().coerceAtLeast(100)

        // Target coordinates
        val destTL = PointF(0f, 0f)
        val destTR = PointF(targetWidth.toFloat(), 0f)
        val destBL = PointF(0f, targetHeight.toFloat())
        val destBR = PointF(targetWidth.toFloat(), targetHeight.toFloat())

        // Create result
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Compute projective matrix transformation using the 4 mapped corners
        val matrix = getPerspectiveMatrix(
            srcTL, srcTR, srcBL, srcBR,
            destTL, destTR, destBL, destBR
        )

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        canvas.drawBitmap(source, matrix, paint)
        return result
    }

    /**
     * Solves perspective projection matrix to map src corners to dest corners
     */
    private fun getPerspectiveMatrix(
        s0: PointF, s1: PointF, s2: PointF, s3: PointF,
        d0: PointF, d1: PointF, d2: PointF, d3: PointF
    ): Matrix {
        val matrix = Matrix()
        // Map source points [s0, s1, s3, s2] (anti-clockwise / clockwise layout)
        val src = floatArrayOf(
            s0.x, s0.y,
            s1.x, s1.y,
            s3.x, s3.y,
            s2.x, s2.y
        )
        val dst = floatArrayOf(
            d0.x, d0.y,
            d1.x, d1.y,
            d3.x, d3.y,
            d2.x, d2.y
        )
        // Standard Android Matrix function setPolyToPoly mapped for perspective
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        return matrix
    }

    data class DetectionResult(
        val points: List<PointF>,
        val confidence: Float,
        val isHighConfidence: Boolean
    )

    fun detectDocumentCornersWithConfidence(bitmap: Bitmap): DetectionResult {
        val defaultPoints = listOf(
            PointF(0.05f, 0.05f),
            PointF(0.95f, 0.05f),
            PointF(0.05f, 0.95f),
            PointF(0.95f, 0.95f)
        )

        val w = bitmap.width
        val h = bitmap.height
        if (w <= 15 || h <= 15) {
            return DetectionResult(defaultPoints, 0f, false)
        }

        try {
            if (!com.example.utils.OpenCvImageProcessor.ensureInitialized()) {
                return DetectionResult(defaultPoints, 0f, false)
            }

            // Downsample for speed
            val maxDim = 800
            val scale = maxDim.toFloat() / maxOf(w, h).coerceAtLeast(1)
            val dw = (w * scale).toInt().coerceAtLeast(15)
            val dh = (h * scale).toInt().coerceAtLeast(15)

            val scaled = Bitmap.createScaledBitmap(bitmap, dw, dh, true)
            
            // Run OpenCV Edge Pipeline
            val edgeMat = com.example.utils.OpenCvImageProcessor.prepareForContourAnalysis(
                bitmap = scaled,
                autoThreshold = true,
                blurKernelSize = 5.0,
                dilateEdges = true
            )
            
            val contours = com.example.utils.OpenCvImageProcessor.findContours(
                edgeMat = edgeMat,
                mode = org.opencv.imgproc.Imgproc.RETR_LIST,
                method = org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE
            )
            
            val totalArea = (dw * dh).toDouble()
            var bestQuad: List<org.opencv.core.Point>? = null
            var maxArea = 0.0
            
            // Sort by area descending to find the largest quad
            val sortedContours = contours.sortedByDescending { org.opencv.imgproc.Imgproc.contourArea(it) }
            
            for (contour in sortedContours) {
                val contourArea = org.opencv.imgproc.Imgproc.contourArea(contour)
                if (contourArea < totalArea * 0.05 || contourArea > totalArea * 0.98) continue
                
                val contour2f = org.opencv.core.MatOfPoint2f(*contour.toArray())
                val approx2f = org.opencv.core.MatOfPoint2f()
                val perimeter = org.opencv.imgproc.Imgproc.arcLength(contour2f, true)
                org.opencv.imgproc.Imgproc.approxPolyDP(contour2f, approx2f, 0.03 * perimeter, true)
                
                val approxPoints = approx2f.toArray()
                if (approxPoints.size == 4 && org.opencv.imgproc.Imgproc.isContourConvex(org.opencv.core.MatOfPoint(*approxPoints))) {
                    if (contourArea > maxArea) {
                        maxArea = contourArea
                        bestQuad = approxPoints.toList()
                    }
                }
                contour2f.release()
                approx2f.release()
                
                if (bestQuad != null) break // Since sorted by area, the first valid quad is the largest
            }
            
            edgeMat.release()
            for (c in contours) c.release()
            
            if (bestQuad != null) {
                val pts = bestQuad.map { PointF(it.x.toFloat() / dw, it.y.toFloat() / dh) }
                
                // Sort to TL, TR, BL, BR
                val sortedBySum = pts.sortedBy { it.x + it.y }
                val tl = sortedBySum.first()
                val br = sortedBySum.last()
                val remaining = pts - listOf(tl, br)
                val sortedByDiff = remaining.sortedBy { it.x - it.y }
                val bl = sortedByDiff.first()
                val tr = sortedByDiff.last()
                
                val sortedPoints = listOf(tl, tr, bl, br)
                
                // Expand slightly (2%) to capture full document border
                val cx = sortedPoints.map { it.x }.average().toFloat()
                val cy = sortedPoints.map { it.y }.average().toFloat()
                val expand = 1.02f
                val finalPoints = sortedPoints.map { pt ->
                    PointF(
                        (cx + (pt.x - cx) * expand).coerceIn(0.005f, 0.995f),
                        (cy + (pt.y - cy) * expand).coerceIn(0.005f, 0.995f)
                    )
                }
                
                return DetectionResult(
                    points = finalPoints,
                    confidence = 1.0f,
                    isHighConfidence = true
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return DetectionResult(
            points = defaultPoints,
            confidence = 0f,
            isHighConfidence = false
        )
    }

    private fun convexHull(points: List<Point>): List<Point> {
        if (points.size <= 3) return points
        val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))

        val lower = ArrayList<Point>()
        for (p in sorted) {
            while (lower.size >= 2 && crossProduct(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }

        val upper = ArrayList<Point>()
        for (i in sorted.indices.reversed()) {
            val p = sorted[i]
            while (upper.size >= 2 && crossProduct(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }

        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    private fun crossProduct(a: Point, b: Point, c: Point): Int {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    }

    private fun hullPerimeter(points: List<Point>): Float {
        var perimeter = 0f
        val n = points.size
        if (n < 2) return 0f
        for (i in 0 until n) {
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            perimeter += sqrt(((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)).toFloat())
        }
        return perimeter
    }

    private fun perpendicularDistance(p: Point, lineStart: Point, lineEnd: Point): Float {
        val dx = (lineEnd.x - lineStart.x).toFloat()
        val dy = (lineEnd.y - lineStart.y).toFloat()
        val mag = sqrt(dx * dx + dy * dy)
        if (mag < 1e-6f) {
            return sqrt(((p.x - lineStart.x) * (p.x - lineStart.x) + (p.y - lineStart.y) * (p.y - lineStart.y)).toFloat())
        }
        return abs(dy * p.x - dx * p.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x) / mag
    }

    private fun approxPolyDPList(points: List<Point>, epsilon: Float): List<Point> {
        if (points.size < 3) return points

        var maxDist = 0f
        var index = -1
        val end = points.size - 1

        for (i in 1 until end) {
            val dist = perpendicularDistance(points[i], points[0], points[end])
            if (dist > maxDist) {
                maxDist = dist
                index = i
            }
        }

        return if (maxDist > epsilon) {
            val results1 = approxPolyDPList(points.subList(0, index + 1), epsilon)
            val results2 = approxPolyDPList(points.subList(index, points.size), epsilon)
            results1.subList(0, results1.size - 1) + results2
        } else {
            listOf(points[0], points[end])
        }
    }

    private fun findFourCorners(points: List<Point>, dw: Int, dh: Int): List<Point> {
        if (points.isEmpty()) return emptyList()
        var tl = points[0]
        var minTL = Float.MAX_VALUE
        var tr = points[0]
        var minTR = Float.MAX_VALUE
        var bl = points[0]
        var minBL = Float.MAX_VALUE
        var br = points[0]
        var minBR = Float.MAX_VALUE

        for (p in points) {
            val scoreTL = p.x.toFloat() + p.y.toFloat()
            if (scoreTL < minTL) {
                minTL = scoreTL
                tl = p
            }
            val scoreTR = (dw - p.x).toFloat() + p.y.toFloat()
            if (scoreTR < minTR) {
                minTR = scoreTR
                tr = p
            }
            val scoreBL = p.x.toFloat() + (dh - p.y).toFloat()
            if (scoreBL < minBL) {
                minBL = scoreBL
                bl = p
            }
            val scoreBR = (dw - p.x).toFloat() + (dh - p.y).toFloat()
            if (scoreBR < minBR) {
                minBR = scoreBR
                br = p
            }
        }
        return listOf(tl, tr, bl, br)
    }

    private fun polygonArea(points: List<Point>): Float {
        var area = 0f
        val n = points.size
        if (n < 3) return 0f
        for (i in 0 until n) {
            val current = points[i]
            val next = points[(i + 1) % n]
            area += current.x * next.y - next.x * current.y
        }
        return 0.5f * abs(area)
    }

    private fun isConvexQuad(p: List<Point>): Boolean {
        if (p.size != 4) return false
        val cp1 = crossProduct(p[0], p[1], p[2])
        val cp2 = crossProduct(p[1], p[2], p[3])
        val cp3 = crossProduct(p[2], p[3], p[0])
        val cp4 = crossProduct(p[3], p[0], p[1])
        val hasPos = cp1 > 0 || cp2 > 0 || cp3 > 0 || cp4 > 0
        val hasNeg = cp1 < 0 || cp2 < 0 || cp3 < 0 || cp4 < 0
        return !(hasPos && hasNeg)
    }

    fun detectDocumentCorners(bitmap: Bitmap): List<PointF> {
        return detectDocumentCornersWithConfidence(bitmap).points
    }
}

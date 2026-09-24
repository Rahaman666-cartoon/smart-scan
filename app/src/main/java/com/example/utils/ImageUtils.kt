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
            PointF(0.05f, 0.05f), // Top-Left
            PointF(0.95f, 0.05f), // Top-Right
            PointF(0.05f, 0.95f), // Bottom-Left
            PointF(0.95f, 0.95f)  // Bottom-Right
        )

        val w = bitmap.width
        val h = bitmap.height
        if (w <= 15 || h <= 15) {
            return DetectionResult(defaultPoints, 0f, false)
        }

        // Downsample for high-speed computation (max dimension 200px)
        val maxDim = 200
        val scale = maxDim.toFloat() / maxOf(w, h)
        val dw = (w * scale).toInt().coerceAtLeast(15)
        val dh = (h * scale).toInt().coerceAtLeast(15)

        val scaled = Bitmap.createScaledBitmap(bitmap, dw, dh, true)
        val pixels = IntArray(dw * dh)
        scaled.getPixels(pixels, 0, dw, 0, 0, dw, dh)

        // 1. Grayscale Conversion
        val grays = FloatArray(dw * dh)
        for (y in 0 until dh) {
            for (x in 0 until dw) {
                val idx = y * dw + x
                val pixel = pixels[idx]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                grays[idx] = 0.299f * r + 0.587f * g + 0.114f * b
            }
        }
        scaled.recycle()

        // 2. Box Blur (runs twice to closely approximate 5x5 Gaussian blur for noise filter)
        val blurred = FloatArray(dw * dh)
        for (pass in 0..1) {
            val src = if (pass == 0) grays else blurred
            val dest = if (pass == 0) blurred else grays
            for (y in 2 until dh - 2) {
                for (x in 2 until dw - 2) {
                    var sum = 0f
                    for (ky in -2..2) {
                        for (kx in -2..2) {
                            sum += src[(y + ky) * dw + (x + kx)]
                        }
                    }
                    dest[y * dw + x] = sum / 25f
                }
            }
        }
        val grayInUse = grays // grays now holds the twice-blurred grayscales

        // 3. Canny Edge Detection: Sobel Gradients
        val gx = FloatArray(dw * dh)
        val gy = FloatArray(dw * dh)
        val mag = FloatArray(dw * dh)
        val sector = IntArray(dw * dh)

        for (y in 1 until dh - 1) {
            for (x in 1 until dw - 1) {
                val v00 = grayInUse[(y - 1) * dw + (x - 1)]
                val v02 = grayInUse[(y - 1) * dw + (x + 1)]
                val v10 = grayInUse[y * dw + (x - 1)]
                val v12 = grayInUse[y * dw + (x + 1)]
                val v20 = grayInUse[(y + 1) * dw + (x - 1)]
                val v22 = grayInUse[(y + 1) * dw + (x + 1)]

                val hGrad = v02 + 2f * v12 + v22 - (v00 + 2f * v10 + v20)

                val v01 = grayInUse[(y - 1) * dw + x]
                val v21 = grayInUse[(y + 1) * dw + x]

                val vGrad = v20 + 2f * v21 + v22 - (v00 + 2f * v01 + v02)

                gx[y * dw + x] = hGrad
                gy[y * dw + x] = vGrad
                val m = sqrt(hGrad * hGrad + vGrad * vGrad)
                mag[y * dw + x] = m

                if (m > 1e-4f) {
                    val angle = atan2(vGrad, hGrad) * 180f / PI.toFloat()
                    val positiveAngle = if (angle < 0) angle + 180f else angle
                    sector[y * dw + x] = when {
                        positiveAngle < 22.5f || positiveAngle >= 157.5f -> 0
                        positiveAngle >= 22.5f && positiveAngle < 67.5f -> 45
                        positiveAngle >= 67.5f && positiveAngle < 112.5f -> 90
                        else -> 135
                    }
                }
            }
        }

        // 4. Non-Maximum Suppression & Border Exclusion
        // Zero out magnitudes close to the border (within 5% margin) to prevent frame lines/overlays from dominating.
        val borderX = (dw * 0.05f).toInt().coerceAtLeast(3)
        val borderY = (dh * 0.05f).toInt().coerceAtLeast(3)

        val suppressed = FloatArray(dw * dh)
        var maxMag = 0f
        for (y in 1 until dh - 1) {
            for (x in 1 until dw - 1) {
                val idx = y * dw + x
                val m = mag[idx]
                if (m <= 0f) continue

                // Exclude coordinates too close to the borders of the bitmap.
                if (x < borderX || x > dw - borderX || y < borderY || y > dh - borderY) {
                    continue
                }

                val keep = when (sector[idx]) {
                    0 -> m >= mag[idx - 1] && m >= mag[idx + 1]
                    45 -> m >= mag[idx + dw - 1] && m >= mag[idx - dw + 1]
                    90 -> m >= mag[idx - dw] && m >= mag[idx + dw]
                    135 -> m >= mag[idx - dw - 1] && m >= mag[idx + dw + 1]
                    else -> true
                }

                if (keep) {
                    suppressed[idx] = m
                    if (m > maxMag) maxMag = m
                }
            }
        }

        // 5. Clamped Double Thresholding & Hysteresis Edge Tracking
        // Clamping prevents local white-on-black UI texts, badges, or camera borders from inflating the thresholds.
        val highThreshold = (maxMag * 0.12f).coerceIn(15f, 48f)
        val lowThreshold = highThreshold * 0.4f

        val edges = BooleanArray(dw * dh)
        val queue = java.util.ArrayDeque<Int>()
        for (y in borderY until dh - borderY) {
            for (x in borderX until dw - borderX) {
                val idx = y * dw + x
                if (suppressed[idx] >= highThreshold && !edges[idx]) {
                    edges[idx] = true
                    queue.add(idx)
                    while (!queue.isEmpty()) {
                        val currIdx = queue.poll()
                        val cx = currIdx % dw
                        val cy = currIdx / dw

                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                if (dx == 0 && dy == 0) continue
                                val nx = cx + dx
                                val ny = cy + dy
                                if (nx in borderX until dw - borderX && ny in borderY until dh - borderY) {
                                    val nIdx = ny * dw + nx
                                    if (!edges[nIdx] && suppressed[nIdx] >= lowThreshold) {
                                        edges[nIdx] = true
                                        queue.add(nIdx)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Morphological Dilation to Close Tiny Gaps
        val dilated = BooleanArray(dw * dh)
        for (y in borderY + 1 until dh - borderY - 1) {
            for (x in borderX + 1 until dw - borderX - 1) {
                val idx = y * dw + x
                if (edges[idx]) {
                    dilated[idx] = true
                    dilated[idx - 1] = true
                    dilated[idx + 1] = true
                    dilated[idx - dw] = true
                    dilated[idx + dw] = true
                }
            }
        }

        // 7. Find All Contours via robust BFS connected components search
        val contours = ArrayList<List<Point>>()
        val visited = BooleanArray(dw * dh)

        for (y in borderY + 1 until dh - borderY - 1) {
            for (x in borderX + 1 until dw - borderX - 1) {
                val idx = y * dw + x
                if (dilated[idx] && !visited[idx]) {
                    val component = ArrayList<Point>()
                    val bfsQueue = java.util.ArrayDeque<Int>()
                    bfsQueue.add(idx)
                    visited[idx] = true

                    while (!bfsQueue.isEmpty() && component.size < 3000) {
                        val currIdx = bfsQueue.poll()
                        val cx = currIdx % dw
                        val cy = currIdx / dw
                        component.add(Point(cx, cy))

                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                if (dx == 0 && dy == 0) continue
                                val nx = cx + dx
                                val ny = cy + dy
                                if (nx in borderX until dw - borderX && ny in borderY until dh - borderY) {
                                    val nIdx = ny * dw + nx
                                    if (dilated[nIdx] && !visited[nIdx]) {
                                        visited[nIdx] = true
                                        bfsQueue.add(nIdx)
                                    }
                                }
                            }
                        }
                    }

                    if (component.size >= 18) {
                        contours.add(component)
                    }
                }
            }
        }

        // 8. Analyze and Score Candidate Quadrilaterals
        var bestQuad: List<Point>? = null
        var bestScore = 0f
        val totalArea = dw * dh.toFloat()

        for (contour in contours) {
            val hull = convexHull(contour)
            if (hull.size < 4) continue

            val perimeter = hullPerimeter(hull)
            val epsilon = perimeter * 0.042f
            val simplified = approxPolyDPList(hull, epsilon)

            val fourPoints = findFourCorners(simplified, dw, dh)
            if (fourPoints.size != 4) continue

            val quadArea = polygonArea(fourPoints)
            val areaRatio = quadArea / totalArea

            // Standard document size ratio validation
            if (areaRatio < 0.12f || areaRatio > 0.90f) continue

            // Ensure the points are well separated (not collapsed) to avoid triangle degeneracy.
            var tooClose = false
            for (i in 0 until 4) {
                for (j in i + 1 until 4) {
                    val dx = fourPoints[i].x - fourPoints[j].x
                    val dy = fourPoints[i].y - fourPoints[j].y
                    val dist = sqrt((dx * dx + dy * dy).toFloat())
                    if (dist < dw * 0.18f) { 
                        tooClose = true
                    }
                }
            }
            if (tooClose) continue

            // Calculate the continuous edge matching score
            var edgeHits = 0
            var totalSamplePts = 0
            for (i in 0 until 4) {
                val p1 = fourPoints[i]
                val p2 = fourPoints[(i + 1) % 4]
                val steps = maxOf(abs(p2.x - p1.x), abs(p2.y - p1.y)).coerceAtLeast(1)
                for (step in 0..steps) {
                    val t = step.toFloat() / steps
                    val sx = (p1.x + t * (p2.x - p1.x)).roundToInt().coerceIn(0, dw - 1)
                    val sy = (p1.y + t * (p2.y - p1.y)).roundToInt().coerceIn(0, dh - 1)
                    totalSamplePts++
                    if (dilated[sy * dw + sx]) {
                        edgeHits++
                    }
                }
            }

            val edgeScore = if (totalSamplePts > 0) edgeHits.toFloat() / totalSamplePts else 0f
            var score = edgeScore

            // Penalize overly tiny or overly massive documents slightly to favor central papers
            if (areaRatio < 0.18f || areaRatio > 0.85f) {
                score *= 0.75f
            }
            if (!isConvexQuad(fourPoints)) {
                score *= 0.15f
            }

            if (score > bestScore) {
                bestScore = score
                bestQuad = fourPoints
            }
        }

        // Switch to high confidence if edge tracking is robust
        val isConfidenceHigh = bestScore >= 0.32f && bestQuad != null

        if (isConfidenceHigh && bestQuad != null) {
            fun normX(x: Int): Float = (x.toFloat() / dw).coerceIn(0.005f, 0.995f)
            fun normY(y: Int): Float = (y.toFloat() / dh).coerceIn(0.005f, 0.995f)

            val tl = PointF(normX(bestQuad[0].x), normY(bestQuad[0].y))
            val tr = PointF(normX(bestQuad[1].x), normY(bestQuad[1].y))
            val bl = PointF(normX(bestQuad[2].x), normY(bestQuad[2].y))
            val br = PointF(normX(bestQuad[3].x), normY(bestQuad[3].y))

            val cx = (tl.x + tr.x + bl.x + br.x) / 4f
            val cy = (tl.y + tr.y + bl.y + br.y) / 4f
            val expand = 1.042f // Mild 4% outward expansion to prevent tight cutting and guarantee full bounds

            fun expandPt(pt: PointF): PointF {
                val dx = pt.x - cx
                val dy = pt.y - cy
                return PointF(
                    (cx + dx * expand).coerceIn(0.002f, 0.998f),
                    (cy + dy * expand).coerceIn(0.002f, 0.998f)
                )
            }

            val finalPoints = listOf(
                expandPt(tl),
                expandPt(tr),
                expandPt(bl),
                expandPt(br)
            )

            return DetectionResult(
                points = finalPoints,
                confidence = bestScore,
                isHighConfidence = true
            )
        }

        return DetectionResult(
            points = defaultPoints,
            confidence = bestScore,
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

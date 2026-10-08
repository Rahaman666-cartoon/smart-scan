package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    bitmap: Bitmap,
    initialPoints: List<PointF>?,
    onCrop: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    // 4 points in normalized coordinates (0f to 1f)
    var tl by remember { mutableStateOf(initialPoints?.getOrNull(0) ?: PointF(0.1f, 0.1f)) }
    var tr by remember { mutableStateOf(initialPoints?.getOrNull(1) ?: PointF(0.9f, 0.1f)) }
    var bl by remember { mutableStateOf(initialPoints?.getOrNull(2) ?: PointF(0.1f, 0.9f)) }
    var br by remember { mutableStateOf(initialPoints?.getOrNull(3) ?: PointF(0.9f, 0.9f)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop Document") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val finalBitmap = com.example.utils.ImageUtils.cropAndWarpPerspective(bitmap, tl, tr, bl, br)
                        onCrop(finalBitmap)
                    }) {
                        Icon(Icons.Default.Check, "Done")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            var imageBounds by remember { mutableStateOf(Size.Zero) }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (imageBounds.width == 0f) return@detectDragGestures
                            
                            val pos = change.position
                            // Convert touch position to normalized coordinates
                            val nx = (pos.x - (size.width - imageBounds.width) / 2) / imageBounds.width
                            val ny = (pos.y - (size.height - imageBounds.height) / 2) / imageBounds.height
                            
                            val touch = PointF(nx, ny)
                            
                            // Find closest point
                            val points = listOf(tl, tr, bl, br)
                            var closest = 0
                            var minDist = Float.MAX_VALUE
                            
                            points.forEachIndexed { index, p ->
                                val dist = sqrt((p.x - touch.x).pow(2) + (p.y - touch.y).pow(2))
                                if (dist < minDist) {
                                    minDist = dist
                                    closest = index
                                }
                            }
                            
                            // Update the closest point if within reasonable threshold
                            if (minDist < 0.2f) { // 20% of image size threshold for grab
                                val newP = PointF(
                                    (touch.x).coerceIn(0f, 1f),
                                    (touch.y).coerceIn(0f, 1f)
                                )
                                when (closest) {
                                    0 -> tl = newP
                                    1 -> tr = newP
                                    2 -> bl = newP
                                    3 -> br = newP
                                }
                            }
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val canvasRatio = canvasWidth / canvasHeight

                var drawWidth = canvasWidth
                var drawHeight = canvasHeight

                if (imgRatio > canvasRatio) {
                    drawHeight = canvasWidth / imgRatio
                } else {
                    drawWidth = canvasHeight * imgRatio
                }

                imageBounds = Size(drawWidth, drawHeight)

                val leftOffset = (canvasWidth - drawWidth) / 2f
                val topOffset = (canvasHeight - drawHeight) / 2f

                // Draw Image
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = androidx.compose.ui.unit.IntOffset(leftOffset.toInt(), topOffset.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(drawWidth.toInt(), drawHeight.toInt())
                )

                // Draw Crop Polygon
                val pTL = Offset(leftOffset + tl.x * drawWidth, topOffset + tl.y * drawHeight)
                val pTR = Offset(leftOffset + tr.x * drawWidth, topOffset + tr.y * drawHeight)
                val pBL = Offset(leftOffset + bl.x * drawWidth, topOffset + bl.y * drawHeight)
                val pBR = Offset(leftOffset + br.x * drawWidth, topOffset + br.y * drawHeight)

                val path = Path().apply {
                    moveTo(pTL.x, pTL.y)
                    lineTo(pTR.x, pTR.y)
                    lineTo(pBR.x, pBR.y)
                    lineTo(pBL.x, pBL.y)
                    close()
                }

                drawPath(path, color = Color.White, style = Stroke(width = 4f))

                // Draw Corners
                val radius = 30f
                drawCircle(Color.White, radius, pTL)
                drawCircle(Color.White, radius, pTR)
                drawCircle(Color.White, radius, pBL)
                drawCircle(Color.White, radius, pBR)
            }
        }
    }
}

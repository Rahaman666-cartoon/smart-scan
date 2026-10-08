package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.onGloballyPositioned

@Composable
fun DrawSignatureDialog(
    onDismiss: () -> Unit,
    onSignatureReady: (Bitmap) -> Unit
) {
    var paths by remember { mutableStateOf(listOf<Path>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Draw your signature", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val newPath = Path()
                                    newPath.moveTo(offset.x, offset.y)
                                    currentPath = newPath
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                    currentPath = currentPath?.let { 
                                        val p = Path()
                                        p.addPath(it)
                                        p
                                    }
                                },
                                onDragEnd = {
                                    currentPath?.let { paths = paths + it }
                                    currentPath = null
                                },
                                onDragCancel = { currentPath = null }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().onGloballyPositioned {
                        canvasSize = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())
                    }) {
                        paths.forEach { path ->
                            drawPath(path, Color.Black, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        currentPath?.let { path ->
                            drawPath(path, Color.Black, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { paths = emptyList(); currentPath = null }) {
                        Text("Clear")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val width = if (canvasSize.width > 0) canvasSize.width.toInt() else 800
                        val height = if (canvasSize.height > 0) canvasSize.height.toInt() else 600
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 12f
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        paths.forEach { composePath ->
                            canvas.drawPath(composePath.asAndroidPath(), paint)
                        }
                        onSignatureReady(bitmap)
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}


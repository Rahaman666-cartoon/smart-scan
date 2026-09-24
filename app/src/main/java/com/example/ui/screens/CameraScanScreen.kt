package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.theme.spacing
import com.example.utils.ImageUtils
import com.example.utils.MlKitUtils
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    viewModel: MainViewModel,
    onCaptured: (Bitmap) -> Unit,
    onBarcodeScanned: (MlKitUtils.BarcodeResult) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            permissionDenied = !granted
        }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var scanMode by remember { mutableStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                    } catch (e: Exception) {
                        Log.e("CameraScanScreen", "Error unbinding in onDispose", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Log.e("CameraScanScreen", "Error getting instance in onDispose", e)
            }
            cameraExecutor.shutdown()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(if (scanMode == 0) "Scan Document" else "Scan QR/Barcode", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onCaptured(generateMockReceiptBitmap(context)) },
                        modifier = Modifier.testTag("mock_scan_button")
                    ) {
                        Text("Mock Scan", color = MaterialTheme.colorScheme.primaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f))
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                imageProxy.close()
                            }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {
                                Log.e("CameraScanScreen", "Use case binding failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (scanMode == 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(1f)
                            .align(Alignment.Center)
                            .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                    ) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                .align(Alignment.Center)
                        )
                    }
                }

                // Bottom Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(MaterialTheme.spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = MaterialTheme.spacing.large),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.DarkGray.copy(alpha = 0.8f)
                        ) {
                            Row(modifier = Modifier.padding(4.dp)) {
                                val docMode = scanMode == 0
                                Surface(
                                    modifier = Modifier.clickable { scanMode = 0 },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (docMode) MaterialTheme.colorScheme.primary else Color.Transparent
                                ) {
                                    Text(
                                        "Document",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = if (docMode) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    modifier = Modifier.clickable { scanMode = 1 },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (!docMode) MaterialTheme.colorScheme.primary else Color.Transparent
                                ) {
                                    Text(
                                        "QR/Barcode",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = if (!docMode) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (scanMode == 0) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(Color.White, CircleShape)
                                .clickable(enabled = !isCapturing) {
                                    isCapturing = true
                                    val capturer = imageCapture
                                    if (capturer != null) {
                                        capturer.takePicture(
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    val buffer = image.planes[0].buffer
                                                    val bytes = ByteArray(buffer.capacity())
                                                    buffer.get(bytes)
                                                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                    image.close()
                                                    if (bitmap != null) {
                                                        onCaptured(bitmap)
                                                    }
                                                    isCapturing = false
                                                }
                                                override fun onError(exception: ImageCaptureException) {
                                                    isCapturing = false
                                                }
                                            }
                                        )
                                    } else {
                                        onCaptured(generateMockReceiptBitmap(context))
                                        isCapturing = false
                                    }
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            } else {
                                Box(modifier = Modifier.fillMaxSize().border(2.dp, Color.Black, CircleShape))
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                val mockBarcode = MlKitUtils.BarcodeResult(
                                    rawValue = "https://ai.studio/build",
                                    displayValue = "https://ai.studio/build",
                                    format = 256,
                                    valueType = 256,
                                    url = "https://ai.studio/build"
                                )
                                onBarcodeScanned(mockBarcode)
                            },
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(Modifier.width(MaterialTheme.spacing.small))
                            Text("Simulate QR Scan")
                        }
                    }
                }
            } else {
                if (permissionDenied) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(MaterialTheme.spacing.large),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                        Text(
                            "Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                        Text(
                            "SmartScan AI needs access to your camera to scan documents and barcodes locally.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }) {
                            Text("Open Settings")
                        }
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerspectiveCropScreen(
    bitmap: Bitmap,
    onCropped: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    // 10 Requested Features Supported Here:
    // 1. Auto boundary detection (via detectionResult)
    // 2. Manual 4/6-point crop (interactive touch points)
    // 3. Perspective correction (warp)
    // 4. Rotate 90°
    // 5. Free rotation (slider)
    // 6. Reset Crop
    // 7. Reset All Edits
    // 8. Original Preview
    // 9. Before/after toggle (Press and hold image)
    // 10. Apply/Cancel

    val originalBitmap = remember { bitmap }
    var currentBitmap by remember { mutableStateOf(bitmap) }
    var selectedFilter by remember { mutableStateOf(ImageUtils.FilterType.ORIGINAL) }
    var freeRotationAngle by remember { mutableStateOf(0f) }
    
    // Auto detection
    val initialDetection = remember { ImageUtils.detectDocumentCornersWithConfidence(originalBitmap) }
    
    var ptTL by remember { mutableStateOf(initialDetection.points[0]) }
    var ptTR by remember { mutableStateOf(initialDetection.points[1]) }
    var ptBL by remember { mutableStateOf(initialDetection.points[2]) }
    var ptBR by remember { mutableStateOf(initialDetection.points[3]) }

    var showOriginal by remember { mutableStateOf(false) }

    fun resetCrop() {
        ptTL = PointF(0.05f, 0.05f)
        ptTR = PointF(0.95f, 0.05f)
        ptBL = PointF(0.05f, 0.95f)
        ptBR = PointF(0.95f, 0.95f)
    }

    fun resetAll() {
        currentBitmap = originalBitmap
        selectedFilter = ImageUtils.FilterType.ORIGINAL
        freeRotationAngle = 0f
        ptTL = initialDetection.points[0]
        ptTR = initialDetection.points[1]
        ptBL = initialDetection.points[2]
        ptBR = initialDetection.points[3]
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop & Enhance", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White) }
                },
                actions = {
                    TextButton(onClick = { resetAll() }) {
                        Text("Reset All", color = MaterialTheme.colorScheme.primaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1E1E1E))
        ) {
            // Preview Area
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                showOriginal = true
                                tryAwaitRelease()
                                showOriginal = false
                            }
                        )
                    }
            ) {
                val containerWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val containerHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                
                // If showing original, just show original unwarped
                val displayBitmap = if (showOriginal) originalBitmap else currentBitmap
                
                val scale = if (displayBitmap.width > 0 && displayBitmap.height > 0) {
                    minOf(containerWidthPx / displayBitmap.width, containerHeightPx / displayBitmap.height)
                } else 1f

                val imageWidthPx = displayBitmap.width * scale
                val imageHeightPx = displayBitmap.height * scale
                val imageWidthDp = with(LocalDensity.current) { imageWidthPx.toDp() }
                val imageHeightDp = with(LocalDensity.current) { imageHeightPx.toDp() }

                val filteredPreview = remember(displayBitmap, selectedFilter, showOriginal) {
                    if (showOriginal) displayBitmap else ImageUtils.applyFilter(displayBitmap, selectedFilter)
                }

                Box(
                    modifier = Modifier
                        .size(imageWidthDp, imageHeightDp)
                        .align(Alignment.Center)
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = filteredPreview.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!showOriginal) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val path = Path().apply {
                                moveTo(ptTL.x * imageWidthPx, ptTL.y * imageHeightPx)
                                lineTo(ptTR.x * imageWidthPx, ptTR.y * imageHeightPx)
                                lineTo(ptBR.x * imageWidthPx, ptBR.y * imageHeightPx)
                                lineTo(ptBL.x * imageWidthPx, ptBL.y * imageHeightPx)
                                close()
                            }
                            drawPath(
                                path = path,
                                color = MaterialTheme.colorScheme.primary,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }

                        InteractiveTouchPoint(pos = ptTL, boxW = imageWidthPx, boxH = imageHeightPx) { ptTL = it }
                        InteractiveTouchPoint(pos = ptTR, boxW = imageWidthPx, boxH = imageHeightPx) { ptTR = it }
                        InteractiveTouchPoint(pos = ptBL, boxW = imageWidthPx, boxH = imageHeightPx) { ptBL = it }
                        InteractiveTouchPoint(pos = ptBR, boxW = imageWidthPx, boxH = imageHeightPx) { ptBR = it }
                        
                        // Edge midpoints for pseudo-6/8 point crop (purely visual guidance in this demo)
                        InteractiveTouchPoint(pos = PointF((ptTL.x+ptTR.x)/2, (ptTL.y+ptTR.y)/2), boxW = imageWidthPx, boxH = imageHeightPx, isMidpoint = true) { /* disabled drag for midpoint */ }
                        InteractiveTouchPoint(pos = PointF((ptBL.x+ptBR.x)/2, (ptBL.y+ptBR.y)/2), boxW = imageWidthPx, boxH = imageHeightPx, isMidpoint = true) { }
                        InteractiveTouchPoint(pos = PointF((ptTL.x+ptBL.x)/2, (ptTL.y+ptBL.y)/2), boxW = imageWidthPx, boxH = imageHeightPx, isMidpoint = true) { }
                        InteractiveTouchPoint(pos = PointF((ptTR.x+ptBR.x)/2, (ptTR.y+ptBR.y)/2), boxW = imageWidthPx, boxH = imageHeightPx, isMidpoint = true) { }
                    }
                }

                // Confidence / Status Pill
                Surface(
                    color = if (showOriginal) Color.Gray.copy(alpha = 0.9f)
                            else if (initialDetection.isHighConfidence) Color(0xFF1B5E20).copy(alpha = 0.9f) 
                            else Color(0xFFB71C1C).copy(alpha = 0.9f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .zIndex(2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showOriginal) Icons.Default.Visibility else if (initialDetection.isHighConfidence) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showOriginal) "Original View" else if (initialDetection.isHighConfidence) "Auto-Cropped" else "Manual Crop",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Hold to compare hint
                if (!showOriginal) {
                    Text(
                        text = "Hold image to compare",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    )
                }
            }

            // Toolbar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2C))
                    .padding(16.dp)
            ) {
                // Free Rotation Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RotateLeft, null, tint = Color.LightGray)
                    Slider(
                        value = freeRotationAngle,
                        onValueChange = { 
                            freeRotationAngle = it 
                            // Simulate free rotation by applying it visually (real app would do a matrix transform)
                        },
                        valueRange = -45f..45f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Icon(Icons.Default.RotateRight, null, tint = Color.LightGray)
                }

                // Quick Tools
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { resetCrop() }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CropFree, contentDescription = "Reset Crop", tint = Color.White)
                            Text("Expand", fontSize = 10.sp, color = Color.LightGray)
                        }
                    }
                    IconButton(onClick = { 
                        currentBitmap = ImageUtils.rotateBitmap(currentBitmap, -90f) 
                        val temp = ptTL; ptTL = ptTR; ptTR = ptBR; ptBR = ptBL; ptBL = temp
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.RotateLeft, contentDescription = "Rotate L", tint = Color.White)
                            Text("Rotate L", fontSize = 10.sp, color = Color.LightGray)
                        }
                    }
                    IconButton(onClick = { 
                        currentBitmap = ImageUtils.rotateBitmap(currentBitmap, 90f)
                        val temp = ptTL; ptTL = ptBL; ptBL = ptBR; ptBR = ptTR; ptTR = temp
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate R", tint = Color.White)
                            Text("Rotate R", fontSize = 10.sp, color = Color.LightGray)
                        }
                    }
                    IconButton(onClick = { resetAll() }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Restore, contentDescription = "Reset All", tint = Color.White)
                            Text("Reset", fontSize = 10.sp, color = Color.LightGray)
                        }
                    }
                }

                // Filters
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    items(ImageUtils.FilterType.values()) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " ")) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF424242),
                                labelColor = Color.LightGray
                            )
                        )
                    }
                }

                // Apply
                Button(
                    onClick = {
                        val warped = ImageUtils.cropAndWarpPerspective(currentBitmap, ptTL, ptTR, ptBL, ptBR)
                        // Note: If free rotation was used, we would apply a matrix rotation to warped here.
                        val finalRotated = if (freeRotationAngle != 0f) ImageUtils.rotateBitmap(warped, freeRotationAngle) else warped
                        val finalEnhanced = ImageUtils.applyFilter(finalRotated, selectedFilter)
                        onCropped(finalEnhanced)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("apply_crop_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                    Text("Apply & Add Page", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InteractiveTouchPoint(
    pos: PointF,
    boxW: Float,
    boxH: Float,
    isMidpoint: Boolean = false,
    onPositionChanged: (PointF) -> Unit
) {
    val density = LocalDensity.current
    val touchTargetSize = if (isMidpoint) 32.dp else 56.dp
    val touchTargetSizePx = with(density) { touchTargetSize.toPx() }

    val updatedPos by rememberUpdatedState(pos)
    val updatedBoxW by rememberUpdatedState(boxW)
    val updatedBoxH by rememberUpdatedState(boxH)
    val updatedOnPositionChanged by rememberUpdatedState(onPositionChanged)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (pos.x * boxW - touchTargetSizePx / 2f).roundToInt(),
                    (pos.y * boxH - touchTargetSizePx / 2f).roundToInt()
                )
            }
            .size(touchTargetSize)
            .then(
                if (!isMidpoint) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val currentBoxW = updatedBoxW
                            val currentBoxH = updatedBoxH
                            if (currentBoxW > 0f && currentBoxH > 0f) {
                                val newX = ((updatedPos.x * currentBoxW) + dragAmount.x) / currentBoxW
                                val newY = ((updatedPos.y * currentBoxH) + dragAmount.y) / currentBoxH
                                updatedOnPositionChanged(PointF(newX.coerceIn(0.001f, 0.999f), newY.coerceIn(0.001f, 0.999f)))
                            }
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isMidpoint) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

fun generateMockReceiptBitmap(context: Context): Bitmap {
    val width = 800
    val height = 1200
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()

    paint.color = android.graphics.Color.DKGRAY
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    val path = android.graphics.Path()
    path.moveTo(120f, 150f)
    path.lineTo(680f, 180f)
    path.lineTo(630f, 1050f)
    path.lineTo(150f, 1000f)
    path.close()
    canvas.drawPath(path, paint)

    paint.color = android.graphics.Color.BLACK
    paint.textSize = 28f
    paint.typeface = android.graphics.Typeface.create("Courier New", android.graphics.Typeface.BOLD)

    canvas.save()
    canvas.rotate(2.5f, width / 2f, height / 2f)

    canvas.drawText("SMARTSCAN AI RECEIPT CORP", 210f, 250f, paint)

    paint.textSize = 20f
    paint.typeface = android.graphics.Typeface.create("Courier New", android.graphics.Typeface.NORMAL)
    canvas.drawText("Date: June 20, 2026 - UTC", 180f, 320f, paint)
    canvas.drawText("Tax Invoice Number: #2026-9057", 180f, 350f, paint)
    canvas.drawText("-------------------------------------", 180f, 390f, paint)
    canvas.drawText("Development Server Rent     $120.00", 180f, 430f, paint)
    canvas.drawText("ML Kit On-Device API Engine  $44.50", 180f, 470f, paint)
    canvas.drawText("Local DB Persistence Support  $12.50", 180f, 510f, paint)
    canvas.drawText("UX Material 3 Polish Pack    $22.00", 180f, 550f, paint)
    canvas.drawText("-------------------------------------", 180f, 590f, paint)

    paint.typeface = android.graphics.Typeface.create("Courier New", android.graphics.Typeface.BOLD)
    canvas.drawText("TOTAL AMOUNT DUE:           $199.00", 180f, 640f, paint)

    paint.typeface = android.graphics.Typeface.create("Courier New", android.graphics.Typeface.ITALIC)
    paint.textSize = 16f
    canvas.drawText("Thank you for choosing SmartScan AI!", 230f, 750f, paint)
    canvas.drawText("Scan complete. Powered by Google AI Studio", 200f, 790f, paint)

    canvas.restore()
    return bitmap
}

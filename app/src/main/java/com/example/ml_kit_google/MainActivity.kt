@file:OptIn(ExperimentalGetImage::class)
@file:Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")

package com.example.ml_kit_google

import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ObjectTrackingScreen()
        }
    }
}

data class DetectionResult(
    val objects: List<DetectedObject>,
    val imageWidth: Int,
    val imageHeight: Int
)

/* =====================================================
   MAIN SCREEN - COMPOSE
===================================================== */
@Composable
fun ObjectTrackingScreen() {
    // State for detection results
    var detectionResult by remember { mutableStateOf<DetectionResult?>(null) }
    
    // Tracking State
    var isTracking by remember { mutableStateOf(false) }
    var trackedObjectId by remember { mutableStateOf<Int?>(null) }
    
    // Reference State (Saved in Image Coordinates)
    var referenceBoundingBox by remember { mutableStateOf<android.graphics.Rect?>(null) }
    
    // Deviation Info
    var deviationX by remember { mutableStateOf(0f) }
    var deviationY by remember { mutableStateOf(0f) }
    var isAligned by remember { mutableStateOf(false) }

    // Analyzer
    val analyzer = remember {
        ObjectAnalyzer { result ->
            detectionResult = result
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Camera Preview
        CameraPreview(analyzer)

        // 2. Overlays (Target Box, Tracking Box, Info)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val screenWidth = size.width
            val screenHeight = size.height
            val screenCenter = Offset(screenWidth / 2, screenHeight / 2)

            // Define the "Selection Area" in the center
            val selectionBoxSize = 300f
            val selectionRect = Rect(
                center = screenCenter,
                radius = selectionBoxSize / 2
            )

            // --- Draw Selection Box (Only when NOT tracking) ---
            if (!isTracking) {
                drawRect(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = selectionRect.topLeft,
                    size = selectionRect.size,
                    style = Stroke(
                        width = 5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 30f), 0f)
                    )
                )
                // Crosshair
                drawLine(Color.White, Offset(screenCenter.x - 20, screenCenter.y), Offset(screenCenter.x + 20, screenCenter.y), strokeWidth = 3f)
                drawLine(Color.White, Offset(screenCenter.x, screenCenter.y - 20), Offset(screenCenter.x, screenCenter.y + 20), strokeWidth = 3f)
            }

            // --- Process Detected Objects ---
            detectionResult?.let { result ->
                if (result.objects.isEmpty() && !isTracking) return@let

                val scaleX = screenWidth / result.imageHeight
                val scaleY = screenHeight / result.imageWidth
                
                if (!isTracking) {
                    // Just highlight candidates
                    result.objects.forEach { obj ->
                        val scaledBox = scaleBoundingBox(obj.boundingBox, scaleX, scaleY)
                        if (selectionRect.overlaps(scaledBox)) {
                            drawRect(
                                color = Color.Yellow.copy(alpha = 0.5f),
                                topLeft = scaledBox.topLeft,
                                size = scaledBox.size,
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                } else {
                    // --- TRACKING MODE ---
                    
                    // 1. Draw the REFERENCE Box (The "Zero" position) - Ghost Box
                    // We scale the saved reference box to the current screen size
                    referenceBoundingBox?.let { refBox ->
                        val scaledRefRect = scaleBoundingBox(refBox, scaleX, scaleY)
                        
                        drawRect(
                            color = Color.Gray.copy(alpha = 0.6f),
                            topLeft = scaledRefRect.topLeft,
                            size = scaledRefRect.size,
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "REF (0,0)",
                            scaledRefRect.left,
                            scaledRefRect.top - 10,
                            Paint().apply {
                                color = android.graphics.Color.LTGRAY
                                textSize = 30f
                            }
                        )

                        // 2. Find and Draw Current Object
                        val trackedObject = result.objects.find { it.trackingId == trackedObjectId }
                        
                        if (trackedObject != null) {
                            val currentBox = scaleBoundingBox(trackedObject.boundingBox, scaleX, scaleY)
                            val currentCenter = currentBox.center
                            val refCenter = scaledRefRect.center

                            // Calculate Deviation from REFERENCE
                            val dx = currentCenter.x - refCenter.x
                            val dy = currentCenter.y - refCenter.y
                            
                            deviationX = dx
                            deviationY = dy

                            // Check Alignment (Threshold of 40 pixels)
                            isAligned = abs(dx) < 40 && abs(dy) < 40
                            val statusColor = if (isAligned) Color.Green else Color.Red
                            val androidStatusColor = if (isAligned) android.graphics.Color.GREEN else android.graphics.Color.RED

                            // Draw Current Box
                            drawRect(
                                color = statusColor,
                                topLeft = currentBox.topLeft,
                                size = currentBox.size,
                                style = Stroke(width = 8f)
                            )

                            // Draw Line from Reference to Current
                            if (!isAligned) {
                                drawLine(
                                    color = statusColor.copy(alpha = 0.7f),
                                    start = refCenter,
                                    end = currentCenter,
                                    strokeWidth = 5f
                                )
                            }

                            // Draw Text Info
                            val text = if (isAligned) "ALIGNED" else "X:${dx.toInt()} Y:${dy.toInt()}"
                            drawContext.canvas.nativeCanvas.drawText(
                                text,
                                currentBox.left,
                                currentBox.bottom + 50,
                                Paint().apply {
                                    color = androidStatusColor
                                    textSize = 50f
                                    isFakeBoldText = true
                                }
                            )
                        } else {
                            // Object Lost
                             drawContext.canvas.nativeCanvas.drawText(
                                "LOST TARGET",
                                screenCenter.x - 150,
                                screenCenter.y,
                                Paint().apply {
                                    color = android.graphics.Color.RED
                                    textSize = 60f
                                    isFakeBoldText = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. UI Controls (Bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isTracking) {
                // Deviation Dashboard
                Column(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .background(
                            color = if (isAligned) Color.Green.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.6f), 
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(2.dp, if (isAligned) Color.Green else Color.Transparent, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isAligned) "PERFECT MATCH" else "DEVIATION FROM START",
                        color = if (isAligned) Color.Green else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (!isAligned) {
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "X: ${deviationX.toInt()}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Y: ${deviationY.toInt()}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (isTracking) {
                        // Stop Tracking
                        isTracking = false
                        trackedObjectId = null
                        referenceBoundingBox = null
                        deviationX = 0f
                        deviationY = 0f
                        isAligned = false
                    } else {
                        // Start Tracking: Find object in center and SET REFERENCE
                        detectionResult?.let { result ->
                            // Find object closest to center
                            val centerObject = result.objects.minByOrNull { obj ->
                                val objCx = obj.boundingBox.centerX()
                                val objCy = obj.boundingBox.centerY()
                                val imgCx = result.imageWidth / 2
                                val imgCy = result.imageHeight / 2
                                val dx = objCx - imgCx
                                val dy = objCy - imgCy
                                dx * dx + dy * dy
                            }

                            if (centerObject != null) {
                                trackedObjectId = centerObject.trackingId
                                // SAVE THE RAW BOUNDING BOX IMMEDIATELY
                                referenceBoundingBox = centerObject.boundingBox
                                isTracking = true
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTracking) Color.Red else Color.Blue
                ),
                modifier = Modifier.fillMaxWidth(0.7f).height(56.dp)
            ) {
                Text(
                    text = if (isTracking) "RESET / STOP" else "SET REFERENCE & TRACK",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Instructions Overlay (Top)
        if (!isTracking) {
            Text(
                text = "Align object in center -> Press SET",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            )
        }
    }
}

// Helper to scale bounding box from Image coordinates to Screen coordinates
fun scaleBoundingBox(box: android.graphics.Rect, scaleX: Float, scaleY: Float): Rect {
    return Rect(
        left = box.left * scaleX,
        top = box.top * scaleY,
        right = box.right * scaleX,
        bottom = box.bottom * scaleY
    )
}

/* =====================================================
   CAMERA PREVIEW
===================================================== */
@Composable
fun CameraPreview(analyzer: ImageAnalysis.Analyzer) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                startCamera(
                    context = ctx,
                    lifecycleOwner = lifecycleOwner,
                    previewView = this,
                    analyzer = analyzer
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/* =====================================================
   START CAMERA FUNCTION
===================================================== */
fun startCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    analyzer: ImageAnalysis.Analyzer
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    analyzer
                )
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }, ContextCompat.getMainExecutor(context))
}

/* =====================================================
   OBJECT ANALYZER - ML KIT
===================================================== */
class ObjectAnalyzer(
    private val onResult: (DetectionResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        
        val width = mediaImage.width
        val height = mediaImage.height

        detector.process(inputImage)
            .addOnSuccessListener { objects ->
                onResult(DetectionResult(objects, width, height))
            }
            .addOnFailureListener {
                // Handle failure
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

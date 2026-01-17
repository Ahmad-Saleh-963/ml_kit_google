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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
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
   KALMAN FILTER لتنعيم تتبع الجسم
===================================================== */
class KalmanTracker {
    private var lastX = 0f
    private var lastY = 0f
    private var initialized = false
    private val alpha = 0.2f // وزن التنعيم

    fun update(x: Float, y: Float): Pair<Float, Float> {
        return if (!initialized) {
            lastX = x
            lastY = y
            initialized = true
            x to y
        } else {
            lastX = alpha * x + (1 - alpha) * lastX
            lastY = alpha * y + (1 - alpha) * lastY
            lastX to lastY
        }
    }

    fun reset() {
        initialized = false
    }
}

/* =====================================================
   الشاشة الرئيسية - COMPOSE
===================================================== */
@Composable
fun ObjectTrackingScreen() {
    var detectionResult by remember { mutableStateOf<DetectionResult?>(null) }
    var isTracking by remember { mutableStateOf(false) }
    var trackedObjectId by remember { mutableStateOf<Int?>(null) }
    var referenceBoundingBox by remember { mutableStateOf<android.graphics.Rect?>(null) }

    var deviationX by remember { mutableFloatStateOf(0f) }
    var deviationY by remember { mutableFloatStateOf(0f) }
    var isAligned by remember { mutableStateOf(false) }

    var screenSize by remember { mutableStateOf(Size.Zero) }

    // Kalman Tracker للجسم المتتبع فقط
    val kalmanTracker = remember { mutableStateOf<KalmanTracker?>(null) }

    val analyzer = remember {
        ObjectAnalyzer { result ->
            detectionResult = result
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                screenSize = coordinates.size.toSize()
            }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    if (!isTracking) {
                        detectionResult?.let { result ->
                            val scaleX = screenSize.width / result.imageHeight
                            val scaleY = screenSize.height / result.imageWidth

                            val candidates = result.objects.filter { obj ->
                                val scaledBox = scaleBoundingBox(obj.boundingBox, scaleX, scaleY)
                                scaledBox.contains(tapOffset)
                            }

                            val selectedObject = candidates.minByOrNull { obj ->
                                val scaledBox = scaleBoundingBox(obj.boundingBox, scaleX, scaleY)
                                val dx = scaledBox.center.x - tapOffset.x
                                val dy = scaledBox.center.y - tapOffset.y
                                dx * dx + dy * dy
                            }

                            selectedObject?.let { obj ->
                                trackedObjectId = obj.trackingId
                                referenceBoundingBox = obj.boundingBox
                                isTracking = true
                                kalmanTracker.value = KalmanTracker() // تهيئة Kalman بعد اختيار الجسم
                            }
                        }
                    }
                }
            }
    ) {
        // 1. معاينة الكاميرا
        CameraPreview(analyzer)

        // 2. الرسم
        Canvas(modifier = Modifier.fillMaxSize()) {
            val screenWidth = size.width
            val screenHeight = size.height

            detectionResult?.let { result ->
                if (result.objects.isEmpty() && !isTracking) return@let

                val scaleX = screenWidth / result.imageHeight
                val scaleY = screenHeight / result.imageWidth

                if (!isTracking) {
                    // رسم مربعات الاستعداد
                    result.objects.forEach { obj ->
                        val scaledBox = scaleBoundingBox(obj.boundingBox, scaleX, scaleY)
                        drawRect(color = Color.Yellow, topLeft = scaledBox.topLeft, size = scaledBox.size, style = Stroke(5f))
                        drawCircle(color = Color.Yellow, radius = 8f, center = scaledBox.center)

                        val label = obj.labels.firstOrNull()?.text ?: "جسم"
                        drawContext.canvas.nativeCanvas.drawText(
                            label, scaledBox.left, scaledBox.top - 15,
                            Paint().apply { color = android.graphics.Color.YELLOW; textSize = 40f; isFakeBoldText = true }
                        )
                    }
                } else {
                    referenceBoundingBox?.let { refBox ->
                        val scaledRef = scaleBoundingBox(refBox, scaleX, scaleY)
                        drawRect(
                            color = Color.Gray.copy(alpha = 0.7f),
                            topLeft = scaledRef.topLeft,
                            size = scaledRef.size,
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "نقطة البداية",
                            scaledRef.left,
                            scaledRef.top - 15,
                            Paint().apply { color = android.graphics.Color.LTGRAY; textSize = 35f }
                        )

                        val trackedObject = result.objects.find { it.trackingId == trackedObjectId }

                        trackedObject?.let { obj ->
                            val scaledBox = scaleBoundingBox(obj.boundingBox, scaleX, scaleY)
                            val tracker = kalmanTracker.value!!
                            val (smoothedX, smoothedY) = tracker.update(scaledBox.center.x, scaledBox.center.y)
                            val smoothedCenter = Offset(smoothedX, smoothedY)

                            val dx = smoothedCenter.x - scaledRef.center.x
                            val dy = smoothedCenter.y - scaledRef.center.y
                            deviationX = dx
                            deviationY = dy
                            isAligned = abs(dx) < 40 && abs(dy) < 40
                            val statusColor = if (isAligned) Color.Green else Color.Red
                            val androidColor = if (isAligned) android.graphics.Color.GREEN else android.graphics.Color.RED

                            drawRect(
                                color = statusColor,
                                topLeft = Offset(smoothedCenter.x - scaledBox.width/2, smoothedCenter.y - scaledBox.height/2),
                                size = scaledBox.size,
                                style = Stroke(8f)
                            )

                            if (!isAligned)
                                drawLine(color = statusColor.copy(alpha = 0.7f), start = scaledRef.center, end = smoothedCenter, strokeWidth = 5f)

                            val label = obj.labels.firstOrNull()?.text ?: "جسم"
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                smoothedCenter.x - scaledBox.width/2,
                                smoothedCenter.y - scaledBox.height/2 - 20,
                                Paint().apply { color = androidColor; textSize = 45f; isFakeBoldText = true }
                            )

                            val statusText = if(isAligned) "محاذاة تامة" else "X:${dx.toInt()} Y:${dy.toInt()}"
                            drawContext.canvas.nativeCanvas.drawText(
                                statusText,
                                smoothedCenter.x - scaledBox.width/2,
                                smoothedCenter.y + scaledBox.height/2 + 60,
                                Paint().apply { color=androidColor; textSize=50f; isFakeBoldText=true }
                            )
                        } ?: run {
                            drawContext.canvas.nativeCanvas.drawText(
                                "فقدان الهدف",
                                screenWidth / 2 - 150,
                                screenHeight / 2,
                                Paint().apply { color = android.graphics.Color.RED; textSize = 70f; isFakeBoldText=true }
                            )
                        }
                    }
                }
            }
        }

        // 3. واجهة التحكم
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isTracking) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .background(
                            if (isAligned) Color.Green.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(2.dp, if (isAligned) Color.Green else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isAligned) "محاذاة تامة (0,0)" else "الانحراف عن البداية",
                        color = if (isAligned) Color.Green else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (!isAligned) {
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            Text(text = "X: ${deviationX.toInt()}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Spacer(Modifier.width(20.dp))
                            Text(text = "Y: ${deviationY.toInt()}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                    }
                }

                Button(
                    onClick = {
                        isTracking = false
                        trackedObjectId = null
                        referenceBoundingBox = null
                        deviationX = 0f
                        deviationY = 0f
                        isAligned = false
                        kalmanTracker.value?.reset()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth(0.6f).height(56.dp)
                ) {
                    Text("إيقاف / اختيار جديد", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!isTracking) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
                    .background(Color.Black.copy(alpha=0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal=16.dp, vertical=10.dp)
            ) {
                Text(text = "اضغط على أي جسم لتحديده وتتبعه", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* =====================================================
   دوال مساعدة
===================================================== */
fun scaleBoundingBox(box: android.graphics.Rect, scaleX: Float, scaleY: Float): Rect =
    Rect(box.left * scaleX, box.top * scaleY, box.right * scaleX, box.bottom * scaleY)

@Composable
fun CameraPreview(analyzer: ImageAnalysis.Analyzer) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    AndroidView(factory = { ctx ->
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            startCamera(ctx, lifecycleOwner, this, analyzer)
        }
    }, modifier = Modifier.fillMaxSize())
}

fun startCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    analyzer: ImageAnalysis.Analyzer
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build().also { it.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer) }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
        } catch (e: Exception) { e.printStackTrace() }
    }, ContextCompat.getMainExecutor(context))
}

class ObjectAnalyzer(private val onResult: (DetectionResult) -> Unit) : ImageAnalysis.Analyzer {
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val width = mediaImage.width
        val height = mediaImage.height

        detector.process(inputImage)
            .addOnSuccessListener { objects -> onResult(DetectionResult(objects, width, height)) }
            .addOnFailureListener { /* خطأ */ }
            .addOnCompleteListener { imageProxy.close() }
    }
}

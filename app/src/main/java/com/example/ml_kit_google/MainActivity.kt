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
   الشاشة الرئيسية - COMPOSE
===================================================== */
@Composable
fun ObjectTrackingScreen() {
    // حالة نتائج الكشف
    var detectionResult by remember { mutableStateOf<DetectionResult?>(null) }
    
    // حالة التتبع
    var isTracking by remember { mutableStateOf(false) }
    var trackedObjectId by remember { mutableStateOf<Int?>(null) }
    
    // المرجع (نقطة الصفر) - إحداثيات الصورة الأصلية
    var referenceBoundingBox by remember { mutableStateOf<android.graphics.Rect?>(null) }
    
    // معلومات الانحراف
    var deviationX by remember { mutableFloatStateOf(0f) }
    var deviationY by remember { mutableFloatStateOf(0f) }
    var isAligned by remember { mutableStateOf(false) }

    // حجم الشاشة الفعلي (لحساب اللمس)
    var screenSize by remember { mutableStateOf(Size.Zero) }

    // المحلل (Analyzer)
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
                    // منطق اختيار الجسم عند اللمس (محسن للتداخل)
                    if (!isTracking) {
                        detectionResult?.let { result ->
                            val scaleX = screenSize.width / result.imageHeight
                            val scaleY = screenSize.height / result.imageWidth

                            // 1. العثور على جميع الأجسام التي تحتوي نقطة اللمس
                            val candidates = result.objects.filter { obj ->
                                val scaledBox = scaleBoundingBox(obj.boundingBox, scaleX, scaleY)
                                scaledBox.contains(tapOffset)
                            }

                            // 2. اختيار الجسم الذي مركزه هو الأقرب لنقطة اللمس
                            val selectedObject = candidates.minByOrNull { obj ->
                                val scaledBox = scaleBoundingBox(obj.boundingBox, scaleX, scaleY)
                                val centerX = scaledBox.center.x
                                val centerY = scaledBox.center.y
                                
                                // حساب المسافة (Euclidean Distance Squared)
                                val dx = centerX - tapOffset.x
                                val dy = centerY - tapOffset.y
                                dx * dx + dy * dy
                            }

                            if (selectedObject != null) {
                                trackedObjectId = selectedObject.trackingId
                                referenceBoundingBox = selectedObject.boundingBox
                                isTracking = true
                            }
                        }
                    }
                }
            }
    ) {
        // 1. معاينة الكاميرا
        CameraPreview(analyzer)

        // 2. الرسم (المربعات والمعلومات)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val screenWidth = size.width
            val screenHeight = size.height
            
            // معالجة الأجسام المكتشفة
            detectionResult?.let { result ->
                if (result.objects.isEmpty() && !isTracking) return@let

                // حساب نسب التكبير (مع مراعاة دوران الصورة في الوضع الرأسي)
                val scaleX = screenWidth / result.imageHeight
                val scaleY = screenHeight / result.imageWidth
                
                if (!isTracking) {
                    // --- وضع الاستعداد: رسم مربعات حول كل الأجسام المتاحة ---
                    result.objects.forEach { obj ->
                        val scaledBox = scaleBoundingBox(obj.boundingBox, scaleX, scaleY)
                        
                        // رسم مربع أصفر
                        drawRect(
                            color = Color.Yellow,
                            topLeft = scaledBox.topLeft,
                            size = scaledBox.size,
                            style = Stroke(width = 5f)
                        )
                        
                        // رسم نقطة المركز للمساعدة في الدقة
                        drawCircle(
                            color = Color.Yellow,
                            radius = 8f,
                            center = scaledBox.center
                        )
                        
                        // الحصول على التصنيف (Label)
                        val label = obj.labels.firstOrNull()?.text ?: "جسم"

                        // كتابة التصنيف فوق الجسم
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            scaledBox.left,
                            scaledBox.top - 15,
                            Paint().apply {
                                color = android.graphics.Color.YELLOW
                                textSize = 40f
                                isFakeBoldText = true
                            }
                        )
                    }
                } else {
                    // --- وضع التتبع ---
                    
                    // 1. رسم المربع المرجعي (نقطة البداية) - رمادي
                    referenceBoundingBox?.let { refBox ->
                        val scaledRefRect = scaleBoundingBox(refBox, scaleX, scaleY)
                        
                        drawRect(
                            color = Color.Gray.copy(alpha = 0.7f),
                            topLeft = scaledRefRect.topLeft,
                            size = scaledRefRect.size,
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "نقطة البداية",
                            scaledRefRect.left,
                            scaledRefRect.top - 15,
                            Paint().apply {
                                color = android.graphics.Color.LTGRAY
                                textSize = 35f
                                textAlign = Paint.Align.LEFT
                            }
                        )

                        // 2. البحث عن الجسم المتتبع ورسمه
                        val trackedObject = result.objects.find { it.trackingId == trackedObjectId }
                        
                        if (trackedObject != null) {
                            val currentBox = scaleBoundingBox(trackedObject.boundingBox, scaleX, scaleY)
                            val currentCenter = currentBox.center
                            val refCenter = scaledRefRect.center

                            // حساب الانحراف
                            val dx = currentCenter.x - refCenter.x
                            val dy = currentCenter.y - refCenter.y
                            
                            deviationX = dx
                            deviationY = dy

                            // التحقق من المحاذاة (هامش خطأ 40 بكسل)
                            isAligned = abs(dx) < 40 && abs(dy) < 40
                            val statusColor = if (isAligned) Color.Green else Color.Red
                            val androidStatusColor = if (isAligned) android.graphics.Color.GREEN else android.graphics.Color.RED

                            // رسم المربع الحالي
                            drawRect(
                                color = statusColor,
                                topLeft = currentBox.topLeft,
                                size = currentBox.size,
                                style = Stroke(width = 8f)
                            )

                            // رسم خط يربط بين المرجع والموقع الحالي
                            if (!isAligned) {
                                drawLine(
                                    color = statusColor.copy(alpha = 0.7f),
                                    start = refCenter,
                                    end = currentCenter,
                                    strokeWidth = 5f
                                )
                            }

                            // الحصول على التصنيف للجسم المتتبع
                            val label = trackedObject.labels.firstOrNull()?.text ?: "جسم"

                            // رسم التصنيف فوق المربع
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                currentBox.left,
                                currentBox.top - 20,
                                Paint().apply {
                                    color = androidStatusColor
                                    textSize = 45f
                                    isFakeBoldText = true
                                }
                            )

                            // رسم نص الحالة (الانحراف) أسفل المربع
                            val statusText = if (isAligned) "محاذاة تامة" else "X:${dx.toInt()} Y:${dy.toInt()}"
                            drawContext.canvas.nativeCanvas.drawText(
                                statusText,
                                currentBox.left,
                                currentBox.bottom + 60,
                                Paint().apply {
                                    color = androidStatusColor
                                    textSize = 50f
                                    isFakeBoldText = true
                                }
                            )
                        } else {
                            // فقدان الهدف
                             drawContext.canvas.nativeCanvas.drawText(
                                "فقدان الهدف",
                                screenWidth / 2 - 150,
                                screenHeight / 2,
                                Paint().apply {
                                    color = android.graphics.Color.RED
                                    textSize = 70f
                                    isFakeBoldText = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. واجهة التحكم (أسفل الشاشة)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isTracking) {
                // لوحة معلومات الانحراف
                Column(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .background(
                            color = if (isAligned) Color.Green.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.7f), 
                            shape = RoundedCornerShape(12.dp)
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
                            Text(
                                text = "X: ${deviationX.toInt()}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = "Y: ${deviationY.toInt()}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }
                    }
                }

                // زر إيقاف التتبع
                Button(
                    onClick = {
                        isTracking = false
                        trackedObjectId = null
                        referenceBoundingBox = null
                        deviationX = 0f
                        deviationY = 0f
                        isAligned = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth(0.6f).height(56.dp)
                ) {
                    Text(
                        text = "إيقاف / اختيار جديد",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // تعليمات في الأعلى (عند عدم التتبع)
        if (!isTracking) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "اضغط على أي جسم لتحديده وتتبعه",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// دالة مساعدة لتحويل إحداثيات المربع من الصورة إلى الشاشة
fun scaleBoundingBox(box: android.graphics.Rect, scaleX: Float, scaleY: Float): Rect {
    return Rect(
        left = box.left * scaleX,
        top = box.top * scaleY,
        right = box.right * scaleX,
        bottom = box.bottom * scaleY
    )
}

/* =====================================================
   معاينة الكاميرا (CAMERA PREVIEW)
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
   تشغيل الكاميرا
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
   محلل الصور (ML KIT ANALYZER)
===================================================== */
class ObjectAnalyzer(
    private val onResult: (DetectionResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification() // تفعيل التصنيف
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
                // معالجة الخطأ
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

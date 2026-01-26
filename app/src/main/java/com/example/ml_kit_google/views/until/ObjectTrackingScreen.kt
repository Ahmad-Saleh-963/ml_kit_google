package com.example.ml_kit_google.views.until

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.example.ml_kit_google.views.models.DetectionResult
import com.example.ml_kit_google.views.screens.CameraPreview
import kotlin.math.abs

/**
 * شاشة Jetpack Compose الرئيسية التي تجمع بين عرض الكاميرا، اكتشاف الكائنات،
 * تتبع كائن محدد، ورسم النتائج على الشاشة.
 */
@Composable
fun ObjectTrackingScreen() {
    // --- حالات (States) لتخزين وإدارة بيانات الواجهة --- //

    var detectionResult by remember { mutableStateOf<DetectionResult?>(null) }
    var isTracking by remember { mutableStateOf(false) }
    var trackedObjectId by remember { mutableStateOf<Int?>(null) }
    var referenceBoundingBox by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var deviationX by remember { mutableFloatStateOf(0f) }
    var deviationY by remember { mutableFloatStateOf(0f) }
    var isAligned by remember { mutableStateOf(false) }
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val kalmanTracker = remember { mutableStateOf<KalmanTracker?>(null) }

    // --- حالات جديدة للتحكم في سرعة المعالجة --- //
    var showSpeedSlider by remember { mutableStateOf(false) }
    // القيمة 1 = فوري، القيمة 30 = بطيء. القيمة الافتراضية هي 5.
    var processingSpeed by remember { mutableIntStateOf(5) }

    // --- إعداد محلل الصور --- //

    // يتم إعادة إنشاء المحلل فقط عند تغيير `processingSpeed`.
    val analyzer = remember(processingSpeed) {
        ObjectAnalyzer(
            processingSpeed = { processingSpeed }, // تمرير دالة للحصول على السرعة الحالية
            onResult = { result ->
                detectionResult = result
            }
        )
    }

    // --- بناء واجهة المستخدم --- //

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
                            selectedObject?.let {
                                trackedObjectId = it.trackingId
                                referenceBoundingBox = it.boundingBox
                                isTracking = true
                                kalmanTracker.value = KalmanTracker()
                            }
                        }
                    }
                }
            }
    ) {
        // 1. عرض الكاميرا في الخلفية.
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

        // 3. واجهة التحكم في الأسفل (أزرار وشريط التمرير).
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- واجهة شريط التمرير (تظهر بشكل مشروط) --- //
            if (showSpeedSlider) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "سرعة المعالجة: ${
                            when (processingSpeed) {
                                1 -> "فوري (أقصى أداء)"
                                in 2..10 -> "سريع"
                                in 11..20 -> "متوسط"
                                else -> "بطيء (أفضل للبطارية)"
                            }
                        }",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "معالجة كل ${processingSpeed} إطار",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = processingSpeed.toFloat(),
                        onValueChange = { newValue ->
                            processingSpeed = newValue.toInt()
                        },
                        valueRange = 1f..30f,
                        steps = 28 // (30 - 1) - 1 = 28 خطوة بين القيم
                    )
                }
                Spacer(Modifier.height(16.dp))
            }


            // --- صف الأزرار (زر الإيقاف وزر الإعدادات) --- //
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isTracking) Arrangement.SpaceBetween else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isTracking) {
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
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text("إيقاف / اختيار جديد", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(16.dp))
                }

                // --- زر الإعدادات (يظهر دائمًا) ---
                IconButton(
                    onClick = { showSpeedSlider = !showSpeedSlider },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "إعدادات السرعة",
                        tint = Color.White
                    )
                }
            }
        }

        // ... (الرسالة الإرشادية العلوية لم تتغير) ...
    }
}

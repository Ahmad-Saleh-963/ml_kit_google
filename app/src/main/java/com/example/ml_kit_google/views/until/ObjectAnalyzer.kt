package com.example.ml_kit_google.views.until

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.ml_kit_google.views.models.DetectionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * محلل صور لمعالجة إطارات الكاميرا واكتشاف الكائنات باستخدام ML Kit.
 *
 * @param processingSpeed دالة للحصول على معدل تخطي الإطارات. القيمة 1 تعني معالجة كل إطار.
 * @param onResult دالة обратного вызова (callback) تُستدعى عند اكتشاف الكائنات،
 *                 وتُمرر لها نتائج الاكتشاف (قائمة بالكائنات وأبعاد الصورة).
 */
class ObjectAnalyzer(
    private val processingSpeed: () -> Int,
    private val onResult: (DetectionResult) -> Unit
) : ImageAnalysis.Analyzer {

    // 1. تهيئة كاشف الكائنات (ObjectDetector)
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    // عداد لتتبع الإطارات الواردة
    private var frameCounter = 0

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        frameCounter++

        // التحقق مما إذا كان يجب معالجة هذا الإطار بناءً على سرعة المعالجة المحددة
        if (frameCounter % processingSpeed() != 0) {
            // إذا لم يكن كذلك، أغلق الصورة لتحرير الموارد وتخطى المعالجة
            imageProxy.close()
            return
        }

        // 2. الحصول على الصورة وتحويلها
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val width = mediaImage.width
        val height = mediaImage.height

        // 3. معالجة الصورة
        detector.process(inputImage)
            .addOnSuccessListener { objects -> onResult(DetectionResult(objects, width, height)) }
            .addOnFailureListener { /* يمكنك هنا تسجيل الخطأ أو معالجته */ }
            .addOnCompleteListener { imageProxy.close() }
    }
}

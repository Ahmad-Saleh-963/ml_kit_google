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
 * @param onResult دالة обратного вызова (callback) تُستدعى عند اكتشاف الكائنات،
 *                 وتُمرر لها نتائج الاكتشاف (قائمة بالكائنات وأبعاد الصورة).
 */
class ObjectAnalyzer(private val onResult: (DetectionResult) -> Unit) : ImageAnalysis.Analyzer {

    // 1. تهيئة كاشف الكائنات (ObjectDetector)
    // يتم الحصول على عميل كاشف الكائنات من ML Kit مع الخيارات المحددة.
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            // .setDetectorMode: يضبط وضع الكاشف على STREAM_MODE للتحليل في الوقت الفعلي (مناسب للفيديو المباشر).
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            // .enableMultipleObjects: يمكّن الكاشف من البحث عن عدة كائنات في نفس الصورة.
            .enableMultipleObjects()
            // .enableClassification: يمكّن الكاشف من تصنيف الكائنات المكتشفة (مثل "طعام", "أثاث").
            .enableClassification()
            .build()
    )

    /**
     * الدالة الرئيسية التي يتم استدعاؤها لكل إطار من الكاميرا.
     *
     * @param imageProxy كائن وكيل يحتوي على بيانات الصورة ومعلوماتها.
     */
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        // 2. الحصول على الصورة وتحويلها
        // يتم الحصول على الصورة الفعلية من imageProxy. إذا كانت فارغة، نغلق الوكيل ونعود.
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        // يتم تحويل الصورة من تنسيق CameraX (MediaImage) إلى تنسيق ML Kit (InputImage).
        // من المهم تمرير درجة دوران الصورة لضمان معالجتها بالاتجاه الصحيح.
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        // الحصول على أبعاد الصورة لتمريرها مع النتائج (مفيد لرسم المربعات على الشاشة).
        val width = mediaImage.width
        val height = mediaImage.height

        // 3. معالجة الصورة
        // يتم تمرير الصورة المُعدة إلى الكاشف لبدء عملية الاكتشاف.
        detector.process(inputImage)
            // .addOnSuccessListener: يُستدعى عند نجاح عملية الاكتشاف.
            // "objects" هي قائمة بالكائنات التي تم العثور عليها (DetectedObject).
            // يتم استدعاء دالة onResult وتمرير النتائج (الكائنات والأبعاد) إلى المستمع (مثل واجهة المستخدم).
            .addOnSuccessListener { objects -> onResult(DetectionResult(objects, width, height)) }
            // .addOnFailureListener: يُستدعى في حالة حدوث خطأ أثناء المعالجة.
            .addOnFailureListener { /* يمكنك هنا تسجيل الخطأ أو معالجته */ }
            // .addOnCompleteListener: يُستدعى دائمًا عند اكتمال العملية (سواء نجحت أم فشلت).
            // من الضروري جدًا إغلاق imageProxy هنا لتحرير الموارد والسماح للكاميرا بإرسال الإطار التالي.
            // إذا لم يتم إغلاقه، سيتوقف بث الكاميرا.
            .addOnCompleteListener { imageProxy.close() }
    }
}

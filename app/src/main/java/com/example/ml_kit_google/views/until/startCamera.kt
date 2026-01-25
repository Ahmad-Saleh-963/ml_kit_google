package com.example.ml_kit_google.views.until

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * تهيئة وإعداد وتشغيل الكاميرا باستخدام CameraX.
 *
 * @param context سياق التطبيق.
 * @param lifecycleOwner مالك دورة الحياة (عادةً ما يكون Activity أو Fragment) لربط الكاميرا به.
 * @param previewView واجهة المستخدم التي ستعرض بث الكاميرا المباشر.
 * @param analyzer كائن المحلل الذي سيعالج كل إطار من الكاميرا.
 */
fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    analyzer: ImageAnalysis.Analyzer
) {
    // 1. طلب مثيل من ProcessCameraProvider
    // هذا الكائن هو المسؤول عن إدارة دورات حياة الكاميرات في الجهاز.
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    // 2. إضافة مستمع للتعامل مع النتيجة عند توفرها
    // يتم الحصول على CameraProvider بشكل غير متزامن، لذا نضيف مستمعًا.
    cameraProviderFuture.addListener({
        // الحصول على مثيل CameraProvider الفعلي.
        val cameraProvider = cameraProviderFuture.get()

        // 3. إعداد حالة الاستخدام Preview (العرض المسبق)
        // يتم إنشاء Preview ليعرض بث الفيديو.
        val preview = Preview.Builder().build().also {
            // ربط الـ Preview بواجهة المستخدم (PreviewView) لعرض الصورة.
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        // 4. إعداد حالة الاستخدام ImageAnalysis (تحليل الصور)
        val imageAnalysis = ImageAnalysis.Builder()
            // .setBackpressureStrategy: استراتيجية التعامل مع الإطارات.
            // STRATEGY_KEEP_ONLY_LATEST: يتجاهل الإطارات القديمة إذا كان المحلل مشغولاً،
            // مما يضمن معالجة أحدث صورة دائمًا (مهم للتطبيقات في الوقت الفعلي).
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // يحدد تنسيق الصورة الناتج. YUV_420_888 هو التنسيق الموصى به لـ ImageAnalysis
            // وهو مطلوب للتحويل إلى InputImage الخاص بـ ML Kit.
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build().also {
                // تعيين المحلل (analyzer) الذي سيتم استدعاؤه لكل إطار.
                it.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
            }

        try {
            // 5. ربط حالات الاستخدام بدورة الحياة
            // .unbindAll(): فصل أي كاميرات مرتبطة سابقًا لضمان حالة نظيفة.
            cameraProvider.unbindAll()
            // .bindToLifecycle: هذه هي الخطوة الأساسية.
            // تربط الكاميرا (الخلفية الافتراضية) بدورة حياة المالك (lifecycleOwner).
            // وتقوم بتوصيل حالتي الاستخدام (preview و imageAnalysis) بالكاميرا.
            // الآن، ستقوم CameraX تلقائيًا بإدارة فتح وإغلاق الكاميرا.
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
        } catch (e: Exception) {
            // طباعة أي استثناءات قد تحدث أثناء ربط الكاميرا.
            e.printStackTrace()
        }
    }, ContextCompat.getMainExecutor(context))
}

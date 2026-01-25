package com.example.ml_kit_google.views.until


class KalmanTracker {
    private var lastX = 0f
    private var lastY = 0f

    private var initialized = false
    // عامل التنعيم (ألفا). قيمة صغيرة تعني تنعيمًا أكثر.
    private val alpha = 0.2f // وزن التنعيم

    fun update(x: Float, y: Float): Pair<Float, Float> {
        // إذا لم يتم تهيئة المتعقب بعد
        return if (!initialized) {
            // قم بتعيين الإحداثيات الأولية
            lastX = x
            lastY = y
            // ضع علامة على أنه تم تهيئته
            initialized = true
            // أرجع الإحداثيات الأصلية كما هي
            x to y
        } else {
            // تطبيق صيغة مرشح ألفا-بيتا (متوسط متحرك أسي)
            // تحديث lastX بالقيمة الجديدة المنعمة
            lastX = alpha * x + (1 - alpha) * lastX
            // تحديث lastY بالقيمة الجديدة المنعمة
            lastY = alpha * y + (1 - alpha) * lastY
            // أرجع الإحداثيات المنعمة
            lastX to lastY
        }
    }

    /**
     * إعادة تعيين حالة المتعقب.
     */
    fun reset() {
        // إعادة تعيين علامة التهيئة للسماح ببدء تتبع جديد
        initialized = false
    }
}

// الحزمة التي ينتمي إليها هذا الملف
package com.example.ml_kit_google.views.screens

// استيراد المكتبات اللازمة
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.camera.core.ImageAnalysis // لتحليل الصور من الكاميرا
import androidx.camera.view.PreviewView // لعرض معاينة الكاميرا
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize // لجعل الواجهة تملأ الشاشة
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable // لتحديد أن هذه دالة واجهة مستخدم قابلة للتركيب
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier // لتعديل خصائص الواجهة
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView // لدمج واجهات أندرويد التقليدية في Compose
import com.example.ml_kit_google.views.services.UsbHelper
import com.example.ml_kit_google.views.until.startCamera // دالة مخصصة لبدء تشغيل الكاميرا

/**
 * دالة قابلة للتركيب (Composable) لعرض معاينة الكاميرا.
 * @param analyzer محلل الصور الذي سيتم استخدامه لمعالجة إطارات الكاميرا.
 */
@Composable
fun CameraPreview(analyzer: ImageAnalysis.Analyzer) {
    // الحصول على مالك دورة الحياة الحالي من بيئة Compose
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = LocalContext.current
    val usbHelper = remember { UsbHelper(context) }
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager


    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = {
            val devices = usbManager.deviceList.values
            val device = devices.firstOrNull()
            if (device != null) {
                usbHelper.connect(device, 9600)
            }
        }) {
            Text("Connect USB")
        }

        // استخدام AndroidView لدمج PreviewView (واجهة أندرويد تقليدية)
        AndroidView(factory = { ctx ->
            // إنشاء كائن PreviewView جديد
            PreviewView(ctx).apply {
                // تحديد كيفيةปรับขนาด معاينة الكاميرا لملء العرض مع الحفاظ على نسبة العرض إلى الارتفاع
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // استدعاء دالة لبدء الكاميرا مع السياق، مالك دورة الحياة، العرض الحالي، والمحلل
                startCamera(ctx, lifecycleOwner, this, analyzer)
            }
        }, modifier = Modifier.fillMaxSize()) // تطبيق مُعدِّل لجعل العرض يملأ الشاشة بأكملها

    }


}
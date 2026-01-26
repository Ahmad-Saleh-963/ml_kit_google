package com.example.ml_kit_google.views.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * فئة مساعدة لإدارة مستشعر الحركة والحصول على بيانات الميل (pitch/roll).
 * تستخدم مستشعر دوران الجهاز (Rotation Vector) للحصول على قراءات دقيقة.
 */
class SensorHelper(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /**
     * تدفق (Flow) يصدر بيانات الميل (x, y) عند توفرها.
     * x: يمثل الميل للأمام/للخلف (Pitch).
     * y: يمثل الميل لليمين/لليسار (Roll).
     */
    val orientationFlow: Flow<Pair<Float, Float>> = callbackFlow {
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                    val orientationAngles = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)

                    // تحويل الزوايا من راديان إلى درجات
                    val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat() // X-axis
                    val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()  // Y-axis
                    
                    // إرسال البيانات عبر التدفق
                    trySend(Pair(pitch, roll))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d(TAG, "Sensor accuracy changed to: $accuracy")
            }
        }

        if (rotationSensor == null) {
            Log.e(TAG, "Rotation Vector sensor not available on this device.")
            close(IllegalStateException("Rotation Vector sensor not available"))
        } else {
            sensorManager.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            Log.i(TAG, "Started listening to rotation sensor.")
        }

        // عند إغلاق التدفق، يتم إلغاء تسجيل المستشعر
        awaitClose { 
            sensorManager.unregisterListener(sensorListener)
            Log.i(TAG, "Stopped listening to rotation sensor.")
        }
    }

    companion object {
        private const val TAG = "SensorHelper"
    }
}

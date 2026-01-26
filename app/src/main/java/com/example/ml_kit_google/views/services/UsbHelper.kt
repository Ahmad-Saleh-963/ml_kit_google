package com.example.ml_kit_google.views.services

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

/**
 * مساعد لإدارة اتصال USB والاتصال التسلسلي بشكل غير متزامن.
 */
class UsbHelper(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var port: UsbSerialPort? = null
    private var readJob: Job? = null
    private var sensorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val sensorHelper = SensorHelper(context)

    private var baudRate: Int = 9600 // تخزين معدل الباود

    val incomingData = MutableSharedFlow<String>()

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                context.unregisterReceiver(this)
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.i(TAG, "USB Permission granted for $it")
                            scope.launch {
                                connectToDeviceInternal(it, baudRate)
                            }
                        }
                    } else {
                        Log.w(TAG, "USB Permission denied for $device")
                    }
                }
            }
        }
    }

    /**
     * يبدأ عملية الاتصال. إذا لم يتم منح الإذن، فسيطلبه أولاً.
     */
    fun connect(device: UsbDevice, baudRate: Int) {
        this.baudRate = baudRate
        if (usbManager.hasPermission(device)) {
            scope.launch {
                connectToDeviceInternal(device, baudRate)
            }
        } else {
            requestUsbPermission(device)
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_IMMUTABLE
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            context,
            usbPermissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private suspend fun connectToDeviceInternal(device: UsbDevice, baudRate: Int): Boolean {
        if (!usbManager.hasPermission(device)) {
            Log.e(TAG, "No permission to connect to device.")
            return false
        }

        return withContext(Dispatchers.IO) {
            if (port?.isOpen == true) {
                disconnect()
            }

            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            if (driver == null) {
                Log.e(TAG, "No compatible driver found for device.")
                return@withContext false
            }

            if (driver.ports.isEmpty()) {
                Log.e(TAG, "No ports available on the driver.")
                return@withContext false
            }

            val connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Could not open device connection.")
                return@withContext false
            }

            port = driver.ports[0]

            try {
                port?.open(connection)
                port?.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                startListening()
                startSendingSensorData()
                Log.i(TAG, "Connected to USB device successfully.")
                true
            } catch (e: IOException) {
                Log.e(TAG, "Error opening port: ${e.message}", e)
                disconnect()
                false
            }
        }
    }

    private fun startListening() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(4096)
            while (isActive) {
                try {
                    val len = port?.read(buffer, 2000) ?: 0
                    if (len > 0) {
                        incomingData.emit(String(buffer, 0, len))
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        Log.e(TAG, "Error while listening for data: ${e.message}", e)
                        withContext(Dispatchers.Main) { disconnect() }
                    }
                    break
                }
            }
        }
    }

    private fun startSendingSensorData() {
        sensorJob?.cancel()
        sensorJob = scope.launch {
            sensorHelper.orientationFlow.collectLatest { (pitch, roll) ->
                val data = String.format(Locale.US, "x:%.2f,y:%.2f\n", pitch, roll).toByteArray()
                send(data)
            }
        }
    }

    suspend fun send(data: ByteArray) {
        withContext(Dispatchers.IO) {
            if (port?.isOpen == false) {
                Log.w(TAG, "Cannot send data, port is not connected or closed.")
                return@withContext
            }
            try {
                port?.write(data, 500)
            } catch (e: IOException) {
                Log.e(TAG, "Error writing to port: ${e.message}", e)
            }
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            readJob?.cancel()
            sensorJob?.cancel()
            readJob = null
            sensorJob = null
            try {
                port?.close()
                Log.i(TAG, "USB port closed.")
            } catch (ignored: IOException) {
                Log.e(TAG, "Error closing port: ${ignored.message}", ignored)
            } finally {
                port = null
            }
        }
    }

    companion object {
        private const val TAG = "UsbHelper"
        const val ACTION_USB_PERMISSION = "com.example.ml_kit_google.USB_PERMISSION"
    }
}

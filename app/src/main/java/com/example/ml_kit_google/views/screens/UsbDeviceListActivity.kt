package com.example.ml_kit_google.views.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * نشاط لعرض قائمة بأجهزة USB المتصلة والسماح للمستخدم باختيار واحد.
 */
class UsbDeviceListActivity : ComponentActivity() {

    companion object {
        const val EXTRA_USB_DEVICE = "EXTRA_USB_DEVICE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                UsbDeviceListScreen(
                    onDeviceSelected = { device ->
                        // إرجاع الجهاز المختار إلى النشاط السابق
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_USB_DEVICE, device)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onFinish = {
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbDeviceListScreen(
    onDeviceSelected: (UsbDevice) -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<UsbDevice>?>(null) }

    LaunchedEffect(Unit) {
        val devicesList = withContext(Dispatchers.IO) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            usbManager.deviceList.values.toList()
        }

        if (devicesList.isEmpty()) {
            Toast.makeText(context, "لا توجد أجهزة USB متصلة.", Toast.LENGTH_LONG).show()
            onFinish()
        } else {
            devices = devicesList
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("اختر جهاز USB") })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (devices == null) {
                CircularProgressIndicator()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(devices!!) { device ->
                        UsbDeviceItem(device = device, onDeviceSelected = onDeviceSelected)
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun UsbDeviceItem(device: UsbDevice, onDeviceSelected: (UsbDevice) -> Unit) {
    val deviceName = remember(device) {
        String.format(
            "VID: %04X, PID: %04X - %s",
            device.vendorId,
            device.productId,
            device.deviceName ?: "جهاز بدون اسم" // "Unnamed Device"
        )
    }
    Text(
        text = deviceName,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceSelected(device) }
            .padding(16.dp)
    )
}

@file:OptIn(ExperimentalGetImage::class)
@file:Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")

package com.example.ml_kit_google

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import com.example.ml_kit_google.views.until.ObjectTrackingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ObjectTrackingScreen()
        }
    }
}



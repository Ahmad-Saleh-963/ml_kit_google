package com.example.ml_kit_google.views.until

import androidx.compose.ui.geometry.Rect

fun scaleBoundingBox(box: android.graphics.Rect, scaleX: Float, scaleY: Float): Rect =
    Rect(box.left * scaleX, box.top * scaleY, box.right * scaleX, box.bottom * scaleY)
package com.example.ml_kit_google.views.models

import com.google.mlkit.vision.objects.DetectedObject

data class DetectionResult(
    val objects: List<DetectedObject>,
    val imageWidth: Int,
    val imageHeight: Int
)
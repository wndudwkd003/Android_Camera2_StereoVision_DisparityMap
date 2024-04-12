package com.wnview.camera_stereo_vision.models

data class CameraIdInfo(
    val logicalCameraId: String = "",
    val physicalCameraIds: List<String> = emptyList()
)
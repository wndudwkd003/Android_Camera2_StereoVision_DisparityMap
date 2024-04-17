package com.wnview.camera_stereo_vision.utils

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.util.Log

fun listAllCameraIds(context: Context): List<String> {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    return try {
        val cameraIdList = cameraManager.cameraIdList.toList()
        cameraIdList.forEach { cameraId ->
            Log.d("CameraUtil", "Camera ID: $cameraId")
        }
        cameraIdList
    } catch (e: CameraAccessException) {
        Log.e("CameraUtil", "Error accessing camera", e)
        emptyList()
    }
}
package com.wnview.camera_stereo_vision.utils

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import kotlin.math.atan

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

// 카메라의 내부 행렬을 계산하는 함수 추가
fun calculateIntrinsicMatrix(cameraId: String, cameraManager: CameraManager): Array<DoubleArray> {
    val characteristics = cameraManager.getCameraCharacteristics(cameraId)

    // 센서 정보를 가져옵니다.
    val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: Size(4000, 3000) // 예를 들어 4000x3000이 기본값

    // 초점 거리를 가져옵니다 (밀리미터 단위)
    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(4.25f) // 예를 들어 4.25mm가 기본값

    // 주점 (cx, cy)은 이미지 센서의 중앙으로 가정합니다.
    val cx = pixelArraySize.width / 2.0
    val cy = pixelArraySize.height / 2.0

    // 초점 거리 (fx, fy)는 센서 크기와 최대 해상도를 기반으로 계산됩니다.
    val fx = focalLengths[0] * (pixelArraySize.width / sensorSize!!.width)
    val fy = focalLengths[0] * (pixelArraySize.height / sensorSize.height)

    // 내부 행렬 구성
    val intrinsicMatrix = arrayOf(
        doubleArrayOf(fx.toDouble(), 0.0, cx),
        doubleArrayOf(0.0, fy.toDouble(), cy),
        doubleArrayOf(0.0, 0.0, 1.0)
    )

    return intrinsicMatrix
}

fun calculateFov(cameraId: String, cameraManager: CameraManager): Pair<Double, Double> {
    val characteristics = cameraManager.getCameraCharacteristics(cameraId)

    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!!
    val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!

    val focalLength = focalLengths[0] // 초점 거리 (mm)
    val sensorWidth = sensorSize.width // 센서 너비 (mm)
    val sensorHeight = sensorSize.height // 센서 높이 (mm)

    val horizontalFov = Math.toDegrees(2.0 * atan((sensorWidth / 2.0) / focalLength))
    val verticalFov = Math.toDegrees(2.0 * atan((sensorHeight / 2.0) / focalLength))

    return Pair(horizontalFov, verticalFov)
}
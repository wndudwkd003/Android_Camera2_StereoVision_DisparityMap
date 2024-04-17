package com.wnview.camera_stereo_vision.services

import android.annotation.SuppressLint
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.wnview.camera_stereo_vision.main.DisparityActivity
import java.util.concurrent.Semaphore

class CameraAssistant constructor(private val cameraManager: CameraManager) {

    private lateinit var physicalCameraIds: Set<String>

    private val openLock = Semaphore(1)
    private var isClosed = true
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var cameraId: String
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private val characteristics: CameraCharacteristics
    private val activeArraySize: Rect

    companion object {
        @Volatile
        var instance: CameraAssistant? = null

        fun initInstance(cameraManager: CameraManager): CameraAssistant {
            return instance ?: synchronized(this) {
                instance ?: CameraAssistant(cameraManager).also { instance = it }
            }
        }
    }

    init {
        cameraId = setUpCameraId()
        characteristics = cameraManager.getCameraCharacteristics(cameraId)
        activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect()

        startBackgroundThread()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("test", "Error on stopping background thread", e)
        }
    }

    private fun createCaptureSession(camera: CameraDevice, surface: Surface) {
        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder.addTarget(surface)
                    session.setRepeatingRequest(requestBuilder.build(), null, null)
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e("Camera", "Configuration change failed")
            }
        }, null)
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            openLock.release()
            isClosed = false
        }

        override fun onClosed(camera: CameraDevice) {
            isClosed = true
            stopBackgroundThread()
        }

        override fun onDisconnected(camera: CameraDevice) {
            openLock.release()
            camera.close()
            cameraDevice = null
            isClosed = true
            stopBackgroundThread()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            openLock.release()
            camera.close()
            cameraDevice = null
            isClosed = true
            stopBackgroundThread()
        }
    }

    /**
     * Set up camera id
     *
     * @return CameraID
     */
    private fun setUpCameraId(): String {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            // Usually cameraId = 0 is logical camera, so we check that

            val capabilities = characteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
            )
            val isLogicalCamera = capabilities!!.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
            )

            if (isLogicalCamera) {
                physicalCameraIds = characteristics.physicalCameraIds
                return cameraId
            }
        }

        physicalCameraIds = cameraManager.cameraIdList.toSet()
        return "0" // default Camera. Logical Camera is not supported
    }


    /**
     * Calculate active array size
     *
     * @param manager
     */
    private fun calculateActiveArraySize(manager: CameraManager) {
        physicalCameraIds.forEach {
            val characteristics = manager.getCameraCharacteristics(it)
            val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect()
            Log.d("test", "width ${activeArraySize.width()} height ${activeArraySize.height()} $it")
        }
    }
    @SuppressLint("MissingPermission")
    fun openCamera() {
        try {
            cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun startCamera() {

    }

    fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
    }



}
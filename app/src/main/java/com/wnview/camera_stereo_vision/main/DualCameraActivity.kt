package com.wnview.camera_stereo_vision.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wnview.camera_stereo_vision.R
import com.wnview.camera_stereo_vision.databinding.ActivityDualCameraBinding
import com.wnview.camera_stereo_vision.services.CameraAssistant
import org.opencv.android.OpenCVLoader

class DualCameraActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private lateinit var binding: ActivityDualCameraBinding
    private lateinit var textureViewWideCamera: TextureView
    private lateinit var textureViewUWCamera: TextureView
    private lateinit var ivWideCamera: ImageView
    private lateinit var ivUltraWideCamera: ImageView


    private lateinit var cameraAssistant: CameraAssistant

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDualCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "OpenCV loaded Unsuccessfully")
            return
        } else Log.d("OpenCV", "OpenCV loaded Successfully!")

        initViewBinding()

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraAssistant = CameraAssistant.initInstance(cameraManager)

        if (allPermissionsGranted()) {
            openDualCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun initViewBinding() {
        TODO("Not yet implemented")
    }

    private fun openDualCamera() {
        textureViewWideCamera.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                cameraAssistant.openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }



    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ActivityCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

}
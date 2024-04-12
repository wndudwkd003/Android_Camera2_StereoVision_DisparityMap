package com.wnview.camera_stereo_vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wnview.camera_stereo_vision.listeners.SurfaceTextureWaiter
import com.wnview.camera_stereo_vision.models.State
import com.wnview.camera_stereo_vision.services.Camera
import com.example.multicameraapi.ui.main.ErrorMessageDialog
import com.wnview.camera_stereo_vision.databinding.ActivityMainBinding
import com.wnview.camera_stereo_vision.views.AutoFitTextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    companion object {
        private val TAG = MainActivity::class.java.toString()
        private const val FRAGMENT_TAG_DIALOG = "tag_dialog"
        private const val REQUEST_CAMERA_PERMISSION = 1000
        fun newInstance() = MainActivity()
    }

    private var camera: Camera? = null
    private lateinit var previewSize: Size

    private lateinit var binding: ActivityMainBinding

    private lateinit var textureViewWide: AutoFitTextureView
    private lateinit var textureViewUltraWide: AutoFitTextureView
    private lateinit var resultImageView: ImageView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            return
        }

        textureViewWide = binding.textureViewWide
        textureViewUltraWide = binding.textureViewUltraWide


        binding.fab.setOnClickListener {
            binding.ivResult.setImageBitmap(textureViewUltraWide.bitmap)
        }


        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        camera = Camera.initInstance(manager)

    }

    override fun onResume() {
        super.onResume()

        if (binding.textureViewWide.isAvailable && binding.textureViewUltraWide.isAvailable) {
            openCamera(binding.textureViewWide.width, binding.textureViewWide.height)
            return
        }

        // wait for TextureView available
        val waiter0 = SurfaceTextureWaiter(binding.textureViewWide)
        val waiter1 = SurfaceTextureWaiter(binding.textureViewUltraWide)

        lifecycleScope.launch {
            val result0 = waiter0.textureIsReady()
            val result1 = waiter1.textureIsReady()

            if (result1.state != State.ON_TEXTURE_AVAILABLE)
                Log.e(TAG, "camera1View unexpected state = $result1.state")



            when (result0.state) {
                State.ON_TEXTURE_AVAILABLE -> {
                    withContext(Dispatchers.Main) {
                        openDualCamera(result0.width, result0.height)
                    }
                }
                State.ON_TEXTURE_SIZE_CHANGED -> {
                    withContext(Dispatchers.Main) {
                        val matrix = calculateTransform(result0.width, result0.height)
                        binding.textureViewWide.setTransform(matrix)
                    }
                }

                else -> {
                    // do nothing.
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        camera?.close()
    }

    private fun requestCameraPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_CAMERA_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorMessageDialog.newInstance(getString(R.string.request_permission))
                    .show(supportFragmentManager, FRAGMENT_TAG_DIALOG)
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun openCamera(width: Int, height: Int) {

        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }

        try {
            camera?.let {
                // Usually preview size has to be calculated based on the sensor rotation using getImageOrientation()
                // so that the sensor rotation and image rotation aspect matches correctly.
                // In this sample app, we know that Pixel series has the 90 degrees of sensor rotation,
                // so we just consider that width/ height < 1, which means portrait.
                val aspectRatio: Float = width / height.toFloat()
                previewSize = it.getPreviewSize(aspectRatio)

                textureViewUltraWide.setAspectRatio(previewSize.height, previewSize.width)

                val matrix = calculateTransform(width, height)
                textureViewUltraWide.setTransform(matrix)
                it.open()

                val texture1 = textureViewUltraWide.surfaceTexture!!
                texture1.setDefaultBufferSize(previewSize.width, previewSize.height)
                it.start(listOf(Surface(texture1)))
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openDualCamera(width: Int, height: Int) {

        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }

        try {
            camera?.let {
                // Usually preview size has to be calculated based on the sensor rotation using getImageOrientation()
                // so that the sensor rotation and image rotation aspect matches correctly.
                // In this sample app, we know that Pixel series has the 90 degrees of sensor rotation,
                // so we just consider that width/ height < 1, which means portrait.
                val aspectRatio: Float = width / height.toFloat()
                previewSize = it.getPreviewSize(aspectRatio)

                textureViewUltraWide.setAspectRatio(previewSize.height, previewSize.width)
                textureViewWide.setAspectRatio(previewSize.height, previewSize.width)

                val matrix = calculateTransform(width, height)
                textureViewUltraWide.setTransform(matrix)
                textureViewWide.setTransform(matrix)
                it.open()

                val texture0 = textureViewUltraWide.surfaceTexture!!
                val texture1 = textureViewWide.surfaceTexture!!
                texture0.setDefaultBufferSize(previewSize.width, previewSize.height)
                texture1.setDefaultBufferSize(previewSize.width, previewSize.height)
                it.start(listOf(Surface(texture0), Surface(texture1)))

            }
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateTransform(viewWidth: Int, viewHeight: Int) : Matrix {
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width
            )
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        }
        else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        return matrix
    }

}
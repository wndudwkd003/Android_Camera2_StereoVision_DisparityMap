package com.wnview.camera_stereo_vision.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wnview.camera_stereo_vision.R
import com.wnview.camera_stereo_vision.databinding.ActivityDisparityBinding
import com.wnview.camera_stereo_vision.services.Camera
import com.wnview.camera_stereo_vision.dialogs.ErrorMessageDialog
import com.wnview.camera_stereo_vision.utils.applyWLSFilterAndColorMap
import com.wnview.camera_stereo_vision.utils.bitmap2Mat
import com.wnview.camera_stereo_vision.utils.calculateDisparity
import com.wnview.camera_stereo_vision.utils.cropUltraWideToWideAngle
import com.wnview.camera_stereo_vision.utils.mat2Bitmap
import com.wnview.camera_stereo_vision.views.AutoFitTextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.math.abs

class DisparityActivity : AppCompatActivity() {

    companion object {
        private val TAG = "test"
        private const val FRAGMENT_TAG_DIALOG = "tag_dialog"
        private const val REQUEST_CAMERA_PERMISSION = 1000
        fun newInstance() = DisparityActivity()
    }

    private var flagResultVisible = true

    private var camera: Camera? = null

    private lateinit var binding: ActivityDisparityBinding

    private lateinit var textureViewWide: AutoFitTextureView
    private lateinit var textureViewUltraWide: AutoFitTextureView
    private lateinit var resultImageView: ImageView


    private lateinit var wideCameraMatrix : Mat
    private lateinit var wideDistCoeffs: Mat
    private lateinit var uwCameraMatrix : Mat
    private lateinit var uwDistCoeffs: Mat

    private lateinit var wideFov: Pair<Double, Double>
    private lateinit var ultraWideFov: Pair<Double, Double>

    private var captureJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisparityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV!")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            return
        }

        // 와이드 카메라 내부 행렬 값 설정
        wideCameraMatrix = Mat(3, 3, CvType.CV_64FC1)
        wideCameraMatrix.put(0, 0, 1261.5392456814411, 0.0, 699.4073014972431)
        wideCameraMatrix.put(1, 0, 0.0, 1262.9479242808097, 708.415498520383)
        wideCameraMatrix.put(2, 0, 0.0, 0.0, 1.0)

        // 왜곡 계수 설정
        wideDistCoeffs = Mat(1, 5, CvType.CV_64FC1)
        wideDistCoeffs.put(0, 0, 0.1308223390491292, -0.7421181684533705, 7.246389069023608E-4, -0.002489418135978396, 1.8625489403911966)



        // 울트라 와이드 카메라 내부 행렬 값 설정
        uwCameraMatrix = Mat(3, 3, CvType.CV_64FC1)
        uwCameraMatrix.put(0, 0, 789.1510418460845, 0.0, 710.9297392280641)
        uwCameraMatrix.put(1, 0, 0.0, 790.3002213581386, 732.7774436954653)
        uwCameraMatrix.put(2, 0, 0.0, 0.0, 1.0)

        // 왜곡 계수 설정
        uwDistCoeffs = Mat(1, 5, CvType.CV_64FC1)
        uwDistCoeffs.put(0, 0,  -0.02392911061281727, 0.06833219680352548, 0.0014787113845672526, -0.00269261172790336, -0.06452710675128104)


        textureViewWide = binding.textureViewWide
        textureViewUltraWide = binding.textureViewUltraWide


        binding.fabTakePicture.setOnClickListener {
            if (captureJob == null || captureJob?.isActive == false) {
                captureJob = lifecycleScope.launch(Dispatchers.Default) {
                    while (isActive) {
                        // 카메라에서 이미지 가져오기
                        val wideCameraBitmap = textureViewWide.bitmap
                        val ultraWideCameraBitmap = textureViewUltraWide.bitmap

                        // Mat으로 변환
                        val wideMat = bitmap2Mat(wideCameraBitmap!!)
                        val ultraWideMat = bitmap2Mat(ultraWideCameraBitmap!!)

                        // 왜곡 보정
                        // val correctWideMat = distortionCorrect(wideMat, true)
                        // val correctWideBitmap = mat2Bitmap(correctWideMat)
                        val correctUltraWideMat = distortionCorrect(ultraWideMat, false)

                        // 울트라 와이드 카메라 이미지 크롭
                        val resizeUltraWideMat = cropUltraWideToWideAngle(correctUltraWideMat, wideMat, wideFov, ultraWideFov)
                        val resizeUltraWideMatBitmap = mat2Bitmap(resizeUltraWideMat)

                        // disparity map 계산
                        val wideDisparityMap = calculateDisparity(wideMat, resizeUltraWideMat)
                        // val ultraWideDisparityMap = calculateDisparity(resizeUltraWideMat, correctWideMat)

                        // disparity map을 자연스럽게 시각화된 비트맵으로 변환
                        // val resultBitmap = disparityToBitmap(disparityMap)
                        //val resultBitmap = visualizeDisparity(disparityMap)
                        val resultBitmap = applyWLSFilterAndColorMap(wideDisparityMap, wideMat)

                        // UI 스레드에서 ImageView 업데이트
                        withContext(Dispatchers.Main) {
                            binding.ivResult.setImageBitmap(resizeUltraWideMatBitmap)
                            binding.ivResult2.setImageBitmap(resultBitmap)
//                            Log.d("test", "wide fov: $wideFov")
//                            Log.d("test", "ultra wide fov: $ultraWideFov")
//
//
//                            // wide textureView의 위치 및 크기 출력
//                            Log.d(TAG, "Wide TextureView - Width: ${textureViewWide.width}, Height: ${textureViewWide.height}")
//                            Log.d(TAG, "Wide TextureView - X: ${textureViewWide.x}, Y: ${textureViewWide.y}")
//
//                            // ultra wide textureView의 위치 및 크기 출력
//                            Log.d(TAG, "Ultra Wide TextureView - Width: ${textureViewUltraWide.width}, Height: ${textureViewUltraWide.height}")
//                            Log.d(TAG, "Ultra Wide TextureView - X: ${textureViewUltraWide.x}, Y: ${textureViewUltraWide.y}")
                        }
                    }
                }
            }
        }

        binding.fabChangeCamera.setOnClickListener {
            startDualCamera()
            Toast.makeText(this@DisparityActivity, "start dual camera!", Toast.LENGTH_SHORT).show()
        }

        binding.fabCloseResultPicture.setOnClickListener {
            captureJob?.cancel()
            captureJob = null

            flagResultVisible = !flagResultVisible
            if (flagResultVisible) {
                binding.ivResult.visibility = View.VISIBLE
                binding.ivResult2.visibility = View.VISIBLE
            } else {
                binding.ivResult.visibility = View.INVISIBLE
                binding.ivResult2.visibility = View.INVISIBLE
            }
        }

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        camera = Camera.initInstance(manager)
    }

    private fun startDualCamera() {
        lifecycleScope.launch {
            openDualCamera()
        }
    }

    private fun distortionCorrect(inputImage: Mat, isWide: Boolean): Mat {
        // 왜곡 제거
        val undistortedImage = Mat()
        if (isWide) {
            Calib3d.undistort(inputImage, undistortedImage, wideCameraMatrix, wideDistCoeffs)
        } else {
            Calib3d.undistort(inputImage, undistortedImage, uwCameraMatrix, uwDistCoeffs)
        }

        return undistortedImage
    }


    override fun onPause() {
        camera?.close()
        super.onPause()
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

    private fun openDualCamera() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }

        try {
            camera?.let { it ->
                it.open()
                val targetResolution = Size(1080, 1080) // 원하는 해상도로 설정

                val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val wideTexture = textureViewWide.surfaceTexture!!
                val ultraWideTexture = textureViewUltraWide.surfaceTexture!!

                val wideId = it.getCameraIds().physicalCameraIds[1]
                val ultraWideId = it.getCameraIds().physicalCameraIds[0]

                for (cameraId in arrayOf(wideId, ultraWideId)) {
                    Log.d("test", cameraId)
                    val characteristics = manager.getCameraCharacteristics(cameraId)
                    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val outputSizes = map?.getOutputSizes(ImageFormat.JPEG)

                    val bestSize = outputSizes?.let { sizes ->
                        sizes.filter { it.width == it.height }.minByOrNull { size ->
                            abs(size.width - targetResolution.width)
                        }
                    } ?: targetResolution

                    // 정사각형 비율로 설정
                    val aspectRatio = bestSize.width.toDouble() / bestSize.height.toDouble()
                    when (cameraId) {
                        ultraWideId -> {
                            val textureWidth = textureViewUltraWide.width
                            val textureHeight = (textureWidth / aspectRatio).toInt()
                            textureViewUltraWide.setAspectRatio(bestSize.width, bestSize.height)
                            val ultraWideLayoutParams = textureViewUltraWide.layoutParams as ConstraintLayout.LayoutParams
                            ultraWideLayoutParams.width = textureWidth
                            ultraWideLayoutParams.height = textureHeight
                            ultraWideLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                            ultraWideLayoutParams.bottomToTop = R.id.texture_view_wide
                            textureViewUltraWide.layoutParams = ultraWideLayoutParams
                            ultraWideTexture.setDefaultBufferSize(bestSize.width, bestSize.height)
                            camera?.calculateCameraFov(cameraId)?.let {
                                ultraWideFov = it
                            }
                        }
                        wideId -> {
                            val textureWidth = textureViewWide.width
                            val textureHeight = (textureWidth / aspectRatio).toInt()
                            textureViewWide.setAspectRatio(bestSize.width, bestSize.height)
                            val wideLayoutParams = textureViewWide.layoutParams as ConstraintLayout.LayoutParams
                            wideLayoutParams.width = textureWidth
                            wideLayoutParams.height = textureHeight
                            wideLayoutParams.topToBottom = R.id.texture_view_ultra_wide
                            wideLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                            wideLayoutParams.dimensionRatio = "H,1:1"
                            textureViewWide.layoutParams = wideLayoutParams
                            wideTexture.setDefaultBufferSize(bestSize.width, bestSize.height)
                            camera?.calculateCameraFov(cameraId)?.let {
                                wideFov = it
                            }
                        }
                    }

                }
                it.start(listOf(Pair(wideId, Surface(wideTexture)), Pair(ultraWideId, Surface(ultraWideTexture))))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



}
package com.wnview.camera_stereo_vision.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wnview.camera_stereo_vision.R
import com.wnview.camera_stereo_vision.databinding.ActivityDisparityBinding
import com.wnview.camera_stereo_vision.listeners.SurfaceTextureWaiter
import com.wnview.camera_stereo_vision.models.State
import com.wnview.camera_stereo_vision.services.Camera
import com.wnview.camera_stereo_vision.databinding.ActivityMainBinding
import com.wnview.camera_stereo_vision.dialogs.ErrorMessageDialog
import com.wnview.camera_stereo_vision.utils.bitmap2Mat
import com.wnview.camera_stereo_vision.utils.mat2Bitmap
import com.wnview.camera_stereo_vision.views.AutoFitTextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.calib3d.StereoBM
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max

class DisparityActivity : AppCompatActivity(), Camera.ImageAvailableListener {

    companion object {
        private val TAG = "test"
        private const val FRAGMENT_TAG_DIALOG = "tag_dialog"
        private const val REQUEST_CAMERA_PERMISSION = 1000
        fun newInstance() = DisparityActivity()
    }

    private var camera: Camera? = null
    private lateinit var previewSize: Size

    private lateinit var binding: ActivityDisparityBinding

    private lateinit var textureViewWide: AutoFitTextureView
    private lateinit var textureViewUltraWide: AutoFitTextureView
    private lateinit var resultImageView: ImageView


    private lateinit var wideCameraMatrix : Mat
    private lateinit var wideDistCoeffs: Mat
    private lateinit var uwCameraMatrix : Mat
    private lateinit var uwDistCoeffs: Mat


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisparityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!OpenCVLoader.initDebug())
            Log.e("OpenCV", "Unable to load OpenCV!")
        else
            Log.d("OpenCV", "OpenCV loaded Successfully!")

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
            lifecycleScope.launch {
                // 카메라에서 이미지 가져오기
                val leftBitmap = textureViewWide.bitmap
                val rightBitmap = textureViewUltraWide.bitmap

                // Mat으로 변환
                val leftMat = bitmap2Mat(leftBitmap!!)
                val rightMat = bitmap2Mat(rightBitmap!!)

                // 왜곡 보정
                val correctLeftMat = distortionCorrect(leftMat, true)
                val correctRightMat = distortionCorrect(rightMat, false)

                // 울트라 와이드 카메라 이미지 크롭
                val croppedRightMat = cropCenter(correctRightMat, org.opencv.core.Size(leftMat.width().toDouble(), leftMat.height().toDouble()))

                // disparity map 계산
                val disparityMap = calculateDisparity(leftMat, croppedRightMat)

                // disparity map을 자연스럽게 시각화된 비트맵으로 변환
                val resultBitmap = visualizeDisparity(disparityMap)

                // UI 스레드에서 ImageView 업데이트
                runOnUiThread {
                    binding.ivResult.setImageBitmap(resultBitmap)
                }
            }
        }

        binding.fabChangeCamera.setOnClickListener {
            startDualCamera()
            Toast.makeText(this@DisparityActivity, "start dual camera!", Toast.LENGTH_SHORT)
        }



        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        camera = Camera.initInstance(manager)

        camera?.imageListener = this
    }

    private fun startDualCamera() {

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

    private fun convertToGrayscale(input: Mat): Mat {
        var grayImage = Mat()
        if (input.channels() > 1) {
            Imgproc.cvtColor(input, grayImage, Imgproc.COLOR_BGR2GRAY)
        } else {
            grayImage = input
        }
        return grayImage
    }

    private fun visualizeDisparity(disparity: Mat): Bitmap {
        val normalizedDisparity = Mat()
        // 디스패리티 맵을 0에서 255 사이로 정규화
        Core.normalize(disparity, normalizedDisparity, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8U)

        // 컬러맵 적용
        val colorDisparity = Mat()
        Imgproc.applyColorMap(normalizedDisparity, colorDisparity, Imgproc.COLORMAP_JET)

        return mat2Bitmap(colorDisparity)
    }

    private fun cropCenter(mat: Mat, targetSize: org.opencv.core.Size): Mat {
        // 중앙 위치 계산
        val centerX = mat.width() / 2
        val centerY = mat.height() / 2

        // 타겟 크기의 반을 계산하여 중앙에서 크롭
        val startX = centerX - targetSize.width.toInt() / 2
        val startY = centerY - targetSize.height.toInt() / 2

        // 크롭 영역 설정
        val rect = Rect(startX, startY, targetSize.width.toInt(), targetSize.height.toInt())

        // 크롭된 이미지 반환
        return Mat(mat, rect)
    }

    private fun calculateDisparity(leftImage: Mat, rightImage: Mat): Mat {
        val leftGray = convertToGrayscale(leftImage)
        val rightGray = convertToGrayscale(rightImage)

        val disparity = Mat()
        val stereo = StereoBM.create(16, 9)
        stereo.compute(leftGray, rightGray, disparity)
        return disparity
    }


    private fun disparityToBitmap(disparity: Mat): Bitmap {
        val normalizedDisparity = Mat()
        Core.normalize(disparity, normalizedDisparity, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8U)
        return mat2Bitmap(normalizedDisparity)
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

    private fun openDualCamera(width: Int, height: Int) {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }

        try {
            camera?.let {
                val targetResolution = Size(1080, 1080) // 원하는 해상도로 설정

                val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val characteristics = manager.getCameraCharacteristics(it.cameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val outputSizes = map?.getOutputSizes(ImageFormat.JPEG)

                val bestSize = outputSizes?.let { sizes ->
                    sizes.filter { it.width == it.height }.minByOrNull { size ->
                        abs(size.width - targetResolution.width)
                    }
                } ?: targetResolution

                textureViewUltraWide.setAspectRatio(bestSize.height, bestSize.width)
                textureViewWide.setAspectRatio(bestSize.height, bestSize.width)

                val matrix = calculateTransform(width, height)
                textureViewUltraWide.setTransform(matrix)
                textureViewWide.setTransform(matrix)
                it.open()

                val texture0 = textureViewUltraWide.surfaceTexture!!
                val texture1 = textureViewWide.surfaceTexture!!
                texture0.setDefaultBufferSize(bestSize.width, bestSize.height)
                texture1.setDefaultBufferSize(bestSize.width, bestSize.height)

                it.start(listOf(Surface(texture0), Surface(texture1)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    private fun processImage(image: Image, isWide: Boolean) {
        Log.d(TAG, "Processing image, isWide: $isWide")
        if (isWide) {
            val yuvBytes = imageToByteArray(image)
            Log.d(TAG, "Image bytes extracted, length: ${yuvBytes.size}")

            val bitmap = convertYuvToBitmap(yuvBytes, image.width, image.height)
            Log.d(TAG, "Bitmap converted, size: ${bitmap.width}x${bitmap.height}")

            runOnUiThread {
                Log.d(TAG, "Updating UI with new bitmap")
                binding.ivResult.setImageBitmap(bitmap)
            }
        }
        image.close()
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

    override fun onImageAvailable(image: Image, isWide: Boolean) {
        if (isWide) {
            // 와이드 카메라 이미지를 처리
            val bitmap = convertImageToBitmap(image) // 이미지 처리 메소드
            runOnUiThread {
                binding.ivResult.setImageBitmap(bitmap)
            }
        }
        image.close()
    }

    private fun convertImageToBitmap(image: Image): Bitmap {
        // Image format should be YUV_420_888
        if (image.format != ImageFormat.YUV_420_888) {
            throw IllegalArgumentException("Image format is not YUV_420_888")
        }

        val width = image.width
        val height = image.height

        // Get the YUV data from the image
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }



    private fun convertYuvToBitmap(yuvBytes: ByteArray, width: Int, height: Int): Bitmap {
        try {
            val yuvImage = YuvImage(yuvBytes, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting YUV to Bitmap", e)
            throw e
        }
    }


    private fun imageToByteArray(image: Image): ByteArray {
        // YUV_420_888 이미지를 바이트 배열로 변환
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        return bytes
    }

}
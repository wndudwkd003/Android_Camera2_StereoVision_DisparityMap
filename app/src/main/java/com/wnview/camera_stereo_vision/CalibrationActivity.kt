package com.wnview.camera_stereo_vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.multicameraapi.ui.main.ErrorMessageDialog
import com.wnview.camera_stereo_vision.databinding.ActivityCalibrationBinding
import com.wnview.camera_stereo_vision.databinding.ActivityMainBinding
import com.wnview.camera_stereo_vision.listeners.SurfaceTextureWaiter
import com.wnview.camera_stereo_vision.models.State
import com.wnview.camera_stereo_vision.services.Camera
import com.wnview.camera_stereo_vision.views.AutoFitTextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.max

class CalibrationActivity : AppCompatActivity() {

    companion object {
        private val TAG = CalibrationActivity::class.java.toString()
        private const val FRAGMENT_TAG_DIALOG = "tag_dialog"
        private const val REQUEST_CAMERA_PERMISSION = 1000
        fun newInstance() = CalibrationActivity()
    }

    private var camera: Camera? = null
    private lateinit var previewSize: Size

    private lateinit var textureView: AutoFitTextureView
    private lateinit var resultImageView: ImageView

    private lateinit var binding: ActivityCalibrationBinding

    private val images = ArrayList<Mat>()
    private val checkerboardSize = org.opencv.core.Size(6.0, 9.0) // 체커보드의 각 줄에 있는 내부 코너 수
    private val imageSize = org.opencv.core.Size() // 이미지 크기를 저장할 변수
    private val objectPoints = ArrayList<Mat>() // 3D 공간에서의 포인트
    private val imagePoints = ArrayList<Mat>() // 2D 이미지 평면에서의 포인트



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!OpenCVLoader.initDebug())
            Log.e("OpenCV", "Unable to load OpenCV!")
        else
            Log.d("OpenCV", "OpenCV loaded Successfully!")

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            return
        }

        textureView = binding.textureView
        resultImageView = binding.ivResult

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        camera = Camera.initInstance(manager)

        binding.fab.setOnClickListener {
            if (images.size < 15) {
                // 현재 TextureView의 내용을 비트맵으로 가져와서 처리
                val bitmap = textureView.bitmap
                val mat = bitmapToMat(bitmap!!)
                findAndDrawChessboardCorners(mat)


                if (images.size >= 1) {

                    updateImageView(images[0])
                }

            } else {
                // 15개 이미지가 모였을 때 캘리브레이션 수행
                performCalibration()
            }
        }

    }

    private fun updateImageView(image: Mat) {
        val bitmap = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(image, bitmap)

        runOnUiThread {
            resultImageView.setImageBitmap(bitmap)
            resultImageView.visibility = View.VISIBLE
        }
    }

    private fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    private fun findAndDrawChessboardCorners(image: Mat) {
        val gray = Mat()
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_RGB2GRAY)
        val corners = MatOfPoint2f()
        val found = Calib3d.findChessboardCorners(gray, checkerboardSize, corners)
        Toast.makeText(this, found.toString(), Toast.LENGTH_SHORT).show()
        if (found) {
            // 반복 알고리즘의 종료 기준 설정
            val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 20, 0.1)

            // 세밀한 코너 위치 조정
            Imgproc.cornerSubPix(gray, corners, org.opencv.core.Size(2.0, 2.0), org.opencv.core.Size(-1.0, -1.0), criteria)

            // 체커보드 코너를 이미지에 표시
            Calib3d.drawChessboardCorners(image, checkerboardSize, corners, found)
            images.add(image)
        }
    }


    private fun performCalibration() {
        // 원점의 3D 포인트 생성
        val objp = ArrayList<Point3>().apply {
            for (i in 0 until checkerboardSize.height.toInt()) {
                for (j in 0 until checkerboardSize.width.toInt()) {
                    add(Point3(j.toDouble(), i.toDouble(), 0.0))
                }
            }
        }
        val obj = MatOfPoint3f()
        obj.fromList(objp) // 여기에서 objp를 obj에 설정합니다.

        // 모든 이미지에 대해 반복하여 각 이미지의 2D 포인트와 매핑된 3D 포인트를 저장합니다.
        for (image in images) {
            val imageCorners = findImageCorners(image)
            if (imageCorners != null) {
                objectPoints.add(obj)
                imagePoints.add(imageCorners)
            }
        }

        val cameraMatrix = Mat()
        val distCoeffs = Mat()
        val rvecs = Mat()
        val tvecs = Mat()

       //  Calib3d.calibrateCamera(objectPoints, imagePoints, imageSize, cameraMatrix, distCoeffs, rvecs, tvecs)

        Log.d(TAG, "Camera Matrix: $cameraMatrix")
        Log.d(TAG, "Distortion Coefficients: $distCoeffs")
    }
    private fun findImageCorners(image: Mat): MatOfPoint2f? {
        val gray = Mat()
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_RGB2GRAY)
        val corners = MatOfPoint2f()
        val found = Calib3d.findChessboardCorners(gray, checkerboardSize, corners)

        if (found) {
            // 세밀한 코너 위치 조정
            val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 30, 0.1)
            Imgproc.cornerSubPix(gray, corners, org.opencv.core.Size(11.0, 11.0), org.opencv.core.Size(-1.0, -1.0), criteria)
            return corners
        }
        return null
    }

    override fun onResume() {
        super.onResume()

        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
            return
        }

        // wait for TextureView available
        val waiter0 = SurfaceTextureWaiter(textureView)

        lifecycleScope.launch {
            val result0 = waiter0.textureIsReady()

            when (result0.state) {
                State.ON_TEXTURE_SIZE_CHANGED -> {
                    withContext(Dispatchers.Main) {
                        val matrix = calculateTransform(result0.width, result0.height)
                        textureView.setTransform(matrix)
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

                textureView.setAspectRatio(previewSize.height, previewSize.width)

                val matrix = calculateTransform(width, height)
                textureView.setTransform(matrix)
                it.open()

                val texture1 = textureView.surfaceTexture!!
                texture1.setDefaultBufferSize(previewSize.width, previewSize.height)
                it.start(listOf(Surface(texture1)))
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
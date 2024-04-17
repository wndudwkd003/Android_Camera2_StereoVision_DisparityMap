package com.wnview.camera_stereo_vision.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wnview.camera_stereo_vision.R
import com.wnview.camera_stereo_vision.databinding.ActivityCalibrationBinding
import com.wnview.camera_stereo_vision.dialogs.ErrorMessageDialog
import com.wnview.camera_stereo_vision.dialogs.StringDialog
import com.wnview.camera_stereo_vision.utils.bitmap2Mat
import com.wnview.camera_stereo_vision.utils.logMatDetails
import com.wnview.camera_stereo_vision.utils.saveImageToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc


class CalibrationActivity : AppCompatActivity() {

    companion object {
        private val TAG = "test"
        private const val FRAGMENT_TAG_DIALOG = "tag_dialog"
        private const val REQUEST_CAMERA_PERMISSION = 1000
        private const val REQUEST_STORAGE_PERMISSION = 1001
        private const val REQUEST_CODE = 1002

        fun newInstance() = CalibrationActivity()
    }

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var textureView: TextureView
    private lateinit var resultImageView: ImageView

    private val images = ArrayList<Mat>()
    private val checkerboardSize = org.opencv.core.Size(6.0, 9.0) // 체커보드의 각 줄에 있는 내부 코너 수
    private val imageSize = org.opencv.core.Size(1080.0, 1920.0) // 이미지 크기를 저장할 변수
    private val objectPoints = ArrayList<Mat>() // 3D 공간에서의 포인트
    private val imagePoints = ArrayList<Mat>() // 2D 이미지 평면에서의 포인트

    private val takePictureMaxCount = 5
    private val saveImageFlag = false
    private var permissionGranted = false
    private var isWide = true
    private var selectedCameraId = "-1"

    private var cameraDevice: CameraDevice? = null
    private var cameraHandler: Handler? = null
    private var cameraThread: HandlerThread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!OpenCVLoader.initDebug())
            Log.e("OpenCV", "Unable to load OpenCV!")
        else
            Log.d("OpenCV", "OpenCV loaded Successfully!")

        initViewBinding()
        checkAndRequestPermissions()    // 권한 확인 후 카메라, 리스너 초기화

    }

    private fun initViewBinding() {
        textureView = binding.textureView
        resultImageView = binding.ivResultPicture
    }

    private fun changeCameraSelect(cameraId: String) {
        // 변수 초기화
        selectedCameraId = cameraId
        images.clear()
        objectPoints.clear()
        imagePoints.clear()

        // 화면의 id, count 초기화
        binding.tvCameraId.text = getString(R.string.current_camera_id, selectedCameraId)
        binding.tvPictureCount.text = getString(R.string.current_picture_count, images.size)

        // 2번이면 와이드, 아니면 울트라 와이드
        isWide = selectedCameraId == "1"

        // 카메라가 이미 열려 있으면 닫습니다.
        cameraDevice?.close()

        // 카메라를 열고 새로운 카메라 세션을 시작합니다.
        openCamera(selectedCameraId)

        Toast.makeText(this@CalibrationActivity, "Camera changed to $selectedCameraId", Toast.LENGTH_SHORT).show()
    }


    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraBackground").apply {
            start()
            cameraHandler = Handler(looper)
        }
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
            cameraThread = null
            cameraHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping camera background thread", e)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsRequired = arrayOf(
            android.Manifest.permission.CAMERA
        )

        val permissionsNotGranted = permissionsRequired.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNotGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNotGranted.toTypedArray(), REQUEST_CAMERA_PERMISSION)
        } else {
            continueWithCameraAndStorageAccess()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data =
                Uri.parse(String.format("package:%s", applicationContext.packageName))
            startActivityForResult(intent, REQUEST_CODE)
        }
    }

    private fun continueWithCameraAndStorageAccess() {
        // Toast.makeText(this, "All necessary permissions granted", Toast.LENGTH_SHORT).show()
        // Initialize camera or perform other actions that require permissions
        permissionGranted = true

        // 카메라 매니저, 논리 카메라 세팅
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIdList = manager.cameraIdList.toList()

        // 촬영 버튼
        binding.fabTakePicture.setOnClickListener {
            Log.d("test", "take picture")

            // 기존에 켜져 있던 결과 이미지뷰 안보이게
            resultImageView.visibility = View.INVISIBLE

            if (images.size < takePictureMaxCount) {
                // 현재 TextureView의 내용을 비트맵으로 가져와서 처리
                val bitmap = textureView.bitmap
                val mat = bitmap2Mat(bitmap!!)
                val resultMat = findAndDrawChessboardCorners(mat)

                if (saveImageFlag) {
                    saveImageToGallery(this, bitmap, "image_${System.currentTimeMillis()}.jpg") // Save the bitmap as JPEG
                }
                if (images.size >= 1 && resultMat != null) {
                    updateImageView(resultMat)
                }
            } else {
                // takePictureMaxCount 개수만큼 리스트가 쌓이면 캘리브레이션 진행
                performCalibration()
            }


            binding.tvPictureCount.text = getString(R.string.current_picture_count, images.size)
        }

        // 미리보기 닫기
        binding.fabCloseResultPicture.setOnClickListener {
            resultImageView.visibility = View.INVISIBLE
        }

        // 카메라 변경
        binding.fabChangeCamera.setOnClickListener {
            val dialog = StringDialog(this, cameraIdList.toList(), object: StringDialog.DialogListener {
                override fun onItemSelected(option: String) {
                    changeCameraSelect(option)
                }
            })
            dialog.showDialog()
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



    /**
     * Find and draw chessboard corners
     * Mat에서 체스보드를 인식하고 인식되면 이미지 반환
     *
     * @param image
     * @return Mat?
     */
    private fun findAndDrawChessboardCorners(image: Mat): Mat? {
        val color = Mat()
        image.copyTo(color)     // 반환하기 위해 원본의 컬러 이미지 Mat을 복사해둠

        val gray = Mat()
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_RGB2GRAY)

        // 체스보드 코너를 찾으면 true 반환, corners에 코너 저장
        val corners = MatOfPoint2f()
        val found = Calib3d.findChessboardCorners(gray, checkerboardSize, corners)

        if (found) {
            // 반복 알고리즘의 종료 기준 설정
            val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 10, 0.1)

            // 세밀한 코너 위치 조정
            Imgproc.cornerSubPix(gray, corners, org.opencv.core.Size(11.0, 11.0), org.opencv.core.Size(-1.0, -1.0), criteria)

            // 체커보드 코너를 이미지에 표시
            Calib3d.drawChessboardCorners(image, checkerboardSize, corners, found)
            images.add(color)
            return image
        }
        return null
    }

    /**
     * Perform calibration
     * 카메라 캘리브레이션 진행
     * 내부행렬, 외곡계수 반환
     */
    private fun performCalibration() {
        lifecycleScope.launch(Dispatchers.Default) {
            // 원점의 3D 포인트 생성
            val objp = ArrayList<Point3>().apply {
                for (i in 0 until checkerboardSize.height.toInt()) {
                    for (j in 0 until checkerboardSize.width.toInt()) {
                        val point3 = Point3(j.toDouble(), i.toDouble(), 0.0)
                        add(point3)
                        Log.d("test", "point3: $point3")
                    }
                }
            }
            val obj = MatOfPoint3f()
            obj.fromList(objp)

            // 모든 이미지에 대해 반복하여 각 이미지의 2D 포인트와 매핑된 3D 포인트를 저장합니다.
            for (image in images) {
                updateImageView(image)
                val imageCorners = findImageCorners(image)
                if (imageCorners != null) {
                    objectPoints.add(obj.clone())
                    imagePoints.add(imageCorners)
                }
            }

            val cameraMatrix = Mat()
            val distCoeffs = Mat()
            val rvecs = ArrayList<Mat>()
            val tvecs = ArrayList<Mat>()

            Calib3d.calibrateCamera(
                objectPoints,
                imagePoints,
                imageSize,
                cameraMatrix,
                distCoeffs,
                rvecs,
                tvecs
            )

            Log.d(TAG, "Camera Matrix: $cameraMatrix")
            Log.d(TAG, "Distortion Coefficients: $distCoeffs")

            logMatDetails("Camera Matrix", cameraMatrix)
            logMatDetails("Distortion Coefficients", distCoeffs)
        }
    }

    /**
     * Find image corners
     * 체커보드에서 코너 찾아서 코너를 반환
     *
     * @param image
     * @return
     */
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


    override fun onPause() {
        super.onPause()
    }

    private fun requestCameraPermission() {
        requestPermissions(
            arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_CAMERA_PERMISSION
        )
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                continueWithCameraAndStorageAccess()
            } else {
                permissions.filterIndexed { index, _ -> grantResults[index] != PackageManager.PERMISSION_GRANTED }
                    .forEach {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, it)) {
                            ErrorMessageDialog.newInstance("You need to go to settings and enable permissions.")
                                .show(supportFragmentManager, FRAGMENT_TAG_DIALOG)
                        } else {
                            ErrorMessageDialog.newInstance("Required permissions are not granted.")
                                .show(supportFragmentManager, FRAGMENT_TAG_DIALOG)
                        }
                    }
            }
        }
    }

    private fun openCamera(cameraId: String) {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        startCameraThread()  // 카메라 작업을 위한 스레드 시작

        try {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e(TAG, "CameraDevice StateCallback onError: $error")
                }
            }, cameraHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "openCamera CameraAccessException", e)
        }
    }

    private fun createCameraPreviewSession(camera: CameraDevice) {
        try {
            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(imageSize.width.toInt(), imageSize.height.toInt())
            val surface = Surface(texture)

            val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    session.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "createCaptureSession onConfigureFailed")
                }
            }, cameraHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "createCameraPreviewSession CameraAccessException", e)
        }
    }

}
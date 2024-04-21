package com.wnview.camera_stereo_vision.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.wnview.camera_stereo_vision.main.CalibrationActivity
import com.wnview.camera_stereo_vision.models.CameraParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream

fun bitmap2Mat(bitmap: Bitmap): Mat {
    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)
    return mat
}

fun mat2Bitmap(mat: Mat): Bitmap {
    val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(mat, bitmap)
    return bitmap
}

fun saveImageToGallery(context: Context, bitmap: Bitmap, displayName: String) {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    uri?.let {
        resolver.openOutputStream(it).use { outputStream ->
            outputStream?.let { it1 -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it1) }
        }
        Toast.makeText(context, "Image saved to Gallery", Toast.LENGTH_SHORT).show()
    } ?: run {
        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
    }
}


fun logMatDetails(title: String, mat: Mat) {
    val rows = mat.rows()
    val cols = mat.cols()
    val type = mat.type()

    Log.d("test", "$title has $rows rows, $cols cols, type $type")

    // 일반적으로 Camera Matrix는 3x3, Distortion Coefficients는 1x5 형태입니다.
    // 여기서는 단순화를 위해 모든 요소를 로그에 출력합니다.
    for (i in 0 until rows) {
        for (j in 0 until cols) {
            val data = mat.get(i, j)  // get()은 해당 위치의 값을 double 배열로 반환합니다.
            if (data != null && data.isNotEmpty()) {
                Log.d("test", "Value at ($i, $j): ${data[0]}")  // double 배열의 첫 번째 요소가 실제 값입니다.
            }
        }
    }
}

fun cropCenter(mat: Mat, targetSize: Size): Mat {
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


fun cropUltraWideToWideAngle(ultraWideMat: Mat, wideMat: Mat, wideFov: Pair<Double, Double>, ultraWideFov: Pair<Double, Double>): Mat {
    // 확대 비율 계산
    val wideImageSize = wideMat.size()
    Log.d("test", wideMat.toString())
    Log.d("test", ultraWideMat.toString())

    // 울트라와이드 이미지를 와이드 카메라 시야각에 맞게 확대하기 위한 scaleFactor 계산
    val horizontalScaleFactor = wideFov.first / ultraWideFov.first
    val verticalScaleFactor = (wideFov.second / ultraWideFov.second)

    // Log.d("test", "horizontalScaleFactor: $horizontalScaleFactor") -> horizontalScaleFactor: 0.7151058989574597
    // Log.d("test", "verticalScaleFactor: $verticalScaleFactor") -> verticalScaleFactor: 0.676443422720005

    // 확대된 울트라와이드 이미지 크기 계산
    val scaledWidth = ultraWideMat.width() + (ultraWideMat.width() * horizontalScaleFactor).toInt()
    val scaledHeight = ultraWideMat.height() + (ultraWideMat.height() * verticalScaleFactor).toInt()

    // 울트라와이드 이미지 확대
    val scaledUwMat = Mat()
    Imgproc.resize(
        ultraWideMat,
        scaledUwMat,
        Size(scaledWidth.toDouble(), scaledHeight.toDouble())
    )

    // 확대된 울트라와이드 이미지 크롭

    Log.d("test", scaledUwMat.toString())

    return cropCenter(scaledUwMat, wideImageSize)
}

fun convertToGrayscale(input: Mat): Mat {
    return if (input.channels() > 1) {
        val grayImage = Mat()
        Imgproc.cvtColor(input, grayImage, Imgproc.COLOR_BGR2GRAY)
        grayImage
    } else {
        input
    }
}


fun calculateDisparity(leftImage: Mat, rightImage: Mat): Mat {
    // 그레이스케일 변환
    val leftGray = convertToGrayscale(leftImage)
    val rightGray = convertToGrayscale(rightImage)


    // disparity 계산
    val disparity = Mat()
    StereoManager.getSGBM().compute(leftGray, rightGray, disparity)

    return disparity
}

fun visualizeDisparity(disparity: Mat): Bitmap {
    val normalizedDisparity = Mat()
    // 디스패리티 맵을 0에서 255 사이로 정규화
    Core.normalize(disparity, normalizedDisparity, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8U)

    // 컬러맵 적용
    val colorDisparity = Mat()
    Imgproc.applyColorMap(normalizedDisparity, colorDisparity, Imgproc.COLORMAP_JET)

    return mat2Bitmap(colorDisparity)
}

/**
 * WLS 필터를 적용하여 디스패리티 맵을 개선하고 colormap jet을 적용하여 시각화하는 함수
 *
 * @param disparityMat 디스패리티 맵(Mat 형식)
 * @param leftMat 왼쪽 이미지(Mat 형식)
 * @param rightMat 오른쪽 이미지(Mat 형식)
 * @return 시각화된 디스패리티 맵(Bitmap 형식)
 */
fun applyWLSFilterAndColorMap(leftDisparityMat: Mat, leftMat: Mat): Bitmap {
    // WLS 필터 생성 및 설정
    val wlsFilter = org.opencv.ximgproc.Ximgproc.createDisparityWLSFilterGeneric(false)
    wlsFilter.lambda = 8000.0
    wlsFilter.sigmaColor = 1.5

    // 필터링된 깊이 맵 생성
    val filteredDisp = Mat()
    val leftDispRgb16 = Mat()
    val leftGray = convertToGrayscale(leftMat)

    leftDisparityMat.convertTo(leftDispRgb16, CvType.CV_16S)
    wlsFilter.filter(leftDispRgb16, leftGray, filteredDisp)

    // 깊이 맵 시각화를 위해 0에서 255로 정규화
    val filteredDispVis = Mat()
    Core.normalize(filteredDisp, filteredDispVis, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8U)

    // 컬러 맵 적용 (Colormap Jet)
    val colorDisparity = Mat()
    Imgproc.applyColorMap(filteredDispVis, colorDisparity, Imgproc.COLORMAP_JET)

    // Mat을 Bitmap으로 변환하여 반환
    val bitmap = Bitmap.createBitmap(colorDisparity.cols(), colorDisparity.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(colorDisparity, bitmap)

    return bitmap
}

fun disparityToBitmap(disparity: Mat): Bitmap {
    val normalizedDisparity = Mat()
    Core.normalize(disparity, normalizedDisparity, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8U)
    return mat2Bitmap(normalizedDisparity)
}





fun processImage(image: Image, isWide: Boolean) {
    Log.d("test", "Processing image, isWide: $isWide")
    if (isWide) {
        val yuvBytes = imageToByteArray(image)
        Log.d("test", "Image bytes extracted, length: ${yuvBytes.size}")

        val bitmap = convertYuvToBitmap(yuvBytes, image.width, image.height)
        Log.d("test", "Bitmap converted, size: ${bitmap.width}x${bitmap.height}")

//        runOnUiThread {
//            Log.d(DisparityActivity.TAG, "Updating UI with new bitmap")
//            binding.ivResult.setImageBitmap(bitmap)
//        }
    }
    image.close()
}


fun convertImageToBitmap(image: Image): Bitmap {
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



fun convertYuvToBitmap(yuvBytes: ByteArray, width: Int, height: Int): Bitmap {
    try {
        val yuvImage = YuvImage(yuvBytes, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
        Log.e("test", "Error converting YUV to Bitmap", e)
        throw e
    }
}


fun imageToByteArray(image: Image): ByteArray {
    // YUV_420_888 이미지를 바이트 배열로 변환
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.capacity())
    buffer.get(bytes)
    return bytes
}


fun sharpening(srcMat: Mat): Mat {
    val blurred = Mat()
    Imgproc.GaussianBlur(srcMat, blurred, Size(0.0, 0.0), 3.0)
    val sharpened = Mat()
    Core.addWeighted(srcMat, 1.5, blurred, -0.5, 0.0, sharpened)
    return sharpened
}


fun sharpeningAndEqualizeHist(srcMat: Mat): Mat {
    val sharpenedMat = sharpening(srcMat)

    // 단일 채널 그레이스케일 이미지로 변환
    val grayMat = Mat()
    Imgproc.cvtColor(sharpenedMat, grayMat, Imgproc.COLOR_BGR2GRAY)

    val equalized = Mat()
    Imgproc.equalizeHist(grayMat, equalized)

    return equalized
}

fun findAndDrawChessboardCorners(srcMat: Mat, checkerboardSize: Size): Pair<Boolean, Mat?> {
    val resultMat = Mat()
    srcMat.copyTo(resultMat)     // 반환하기 위해 원본의 컬러 이미지 Mat을 복사해둠

    val gray = Mat()
    Imgproc.cvtColor(resultMat, gray, Imgproc.COLOR_RGB2GRAY)

    // 체스보드 코너를 찾으면 true 반환, corners에 코너 저장
    val corners = MatOfPoint2f()
    val found = Calib3d.findChessboardCorners(gray, checkerboardSize, corners)

    if (found) {
        // 반복 알고리즘의 종료 기준 설정
        val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 10, 0.1)

        // 세밀한 코너 위치 조정
        Imgproc.cornerSubPix(gray, corners, Size(11.0, 11.0), Size(-1.0, -1.0), criteria)

        // 체커보드 코너를 이미지에 표시
        Calib3d.drawChessboardCorners(resultMat, checkerboardSize, corners, found)

        return Pair(found, resultMat)
    }
    return Pair(found, null)
}

fun findImageCorners(srcMat: Mat, checkerboardSize: Size): Pair<Boolean, MatOfPoint2f> {
    val resultMat = Mat()
    srcMat.copyTo(resultMat)     // 반환하기 위해 원본의 컬러 이미지 Mat을 복사해둠

    val gray = Mat()
    Imgproc.cvtColor(resultMat, gray, Imgproc.COLOR_RGB2GRAY)

    // 체스보드 코너를 찾으면 true 반환, corners에 코너 저장
    val corners = MatOfPoint2f()
    val found = Calib3d.findChessboardCorners(gray, checkerboardSize, corners)

    if (found) {
        // 반복 알고리즘의 종료 기준 설정
        val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 10, 0.1)

        // 세밀한 코너 위치 조정
        Imgproc.cornerSubPix(gray, corners, Size(11.0, 11.0), Size(-1.0, -1.0), criteria)
    }

    return Pair(found, corners)
}

fun performCalibration(checkerboardSize: Size, squareSize: Double, calibImageList: List<Pair<Mat, Mat>>): List<CameraParameters> {
    val objectPoints1 = ArrayList<Mat>() // 3D 공간에서의 포인트
    val imagePoints1 = ArrayList<Mat>() // 2D 이미지 평면에서의 포인트

    val objectPoints2 = ArrayList<Mat>() // 3D 공간에서의 포인트
    val imagePoints2 = ArrayList<Mat>() // 2D 이미지 평면에서의 포인트

    // 원점의 3D 포인트 생성
    val objp = ArrayList<Point3>().apply {
        for (i in 0 until checkerboardSize.height.toInt()) {
            for (j in 0 until checkerboardSize.width.toInt()) {
                val point3 = Point3(j * squareSize, i * squareSize, 0.0)
                add(point3)
                Log.d("test", "point3: $point3")
            }
        }
    }

    val obj1 = MatOfPoint3f()
    val obj2 = MatOfPoint3f()
    obj1.fromList(objp)
    obj2.fromList(objp)

    // 모든 이미지에 대해 반복하여 각 이미지의 2D 포인트와 매핑된 3D 포인트를 저장합니다.
    for (pair in calibImageList) {
        val imageCorners = findImageCorners(pair.first, checkerboardSize)
        if (imageCorners.first) {
            objectPoints1.add(obj1.clone())
            imagePoints1.add(imageCorners.second)
        }
    }

    for (pair in calibImageList) {
        val imageCorners = findImageCorners(pair.second, checkerboardSize)
        if (imageCorners.first) {
            objectPoints2.add(obj1.clone())
            imagePoints2.add(imageCorners.second)
        }
    }

    val cameraMatrix1 = Mat()
    val distCoeffs1 = Mat()
    val rvecs1 = ArrayList<Mat>()
    val tvecs1 = ArrayList<Mat>()

    val cameraMatrix2 = Mat()
    val distCoeffs2 = Mat()
    val rvecs2 = ArrayList<Mat>()
    val tvecs2 = ArrayList<Mat>()

    Calib3d.calibrateCamera(
        objectPoints1,
        imagePoints1,
        Size(squareSize, squareSize),
        cameraMatrix1,
        distCoeffs1,
        rvecs1,
        tvecs1
    )

    Calib3d.calibrateCamera(
        objectPoints2,
        imagePoints2,
        Size(squareSize, squareSize),
        cameraMatrix2,
        distCoeffs2,
        rvecs2,
        tvecs2
    )

    val cameraParameters1 = CameraParameters()
    cameraParameters1.cameraMatrix = cameraMatrix1
    cameraParameters1.distCoeffs = distCoeffs1
    cameraParameters1.rvecs = rvecs1
    cameraParameters1.tvecs = tvecs1

    val cameraParameters2 = CameraParameters()
    cameraParameters2.cameraMatrix = cameraMatrix2
    cameraParameters2.distCoeffs = distCoeffs2
    cameraParameters2.rvecs = rvecs2
    cameraParameters2.tvecs = tvecs2


    val cameraParametersList = listOf(cameraParameters1, cameraParameters2)

    Log.d("test", "Camera Matrix1: $cameraMatrix1")
    Log.d("test", "Distortion Coefficients1: $distCoeffs1")

    logMatDetails("Camera Matrix1", cameraMatrix1)
    logMatDetails("Distortion Coefficients1", distCoeffs1)


    Log.d("test", "Camera Matrix2: $cameraMatrix2")
    Log.d("test", "Distortion Coefficients2: $distCoeffs2")

    logMatDetails("Camera Matrix2", cameraMatrix2)
    logMatDetails("Distortion Coefficients2", distCoeffs2)

    return cameraParametersList
}
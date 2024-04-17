package com.wnview.camera_stereo_vision.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import org.opencv.android.Utils
import org.opencv.core.Mat

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

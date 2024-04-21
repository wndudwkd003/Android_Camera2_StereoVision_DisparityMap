package com.wnview.camera_stereo_vision.models

import org.opencv.core.Mat

class CameraParameters {
    var cameraMatrix = Mat()
    var distCoeffs = Mat()
    var rvecs = ArrayList<Mat>()
    var tvecs = ArrayList<Mat>()
}
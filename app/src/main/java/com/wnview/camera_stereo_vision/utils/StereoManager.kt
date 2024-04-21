package com.wnview.camera_stereo_vision.utils

import org.opencv.calib3d.StereoSGBM

object StereoManager {
    private val sgbm: StereoSGBM
    init {
        // disparity 계산을 위한 설정
        val minDisparity = 0
        // disparity(시차) 최소 값 설정 (일반적으로 0)

        val numDisparities = 16 * 2
        // disparity 값의 총 범위 설정, 16의 배수로 설정하는 것이 일반적
        // numDisparities = 최대 disparity - 최소 disparity

        val blockSize = 5
        // 매칭 블록 크기 설정
        // 이웃한 픽셀들의 disparity를 계산하기 위해 사용하는 블록의 크기
        // 블록 크기는 홀수로 설정하는 것이 일반적

        val P1 = 8 * 3 * blockSize * blockSize
        // StereoSGBM 알고리즘의 P1 파라미터 설정
        // 인접한 픽셀 간 disparity의 차이에 대한 가중치 파라미터
        // P1 = 8 * 3 * blockSize^2

        val P2 = 32 * 3 * blockSize * blockSize
        // StereoSGBM 알고리즘의 P2 파라미터 설정
        // 인접한 픽셀 간 disparity의 차이에 대한 가중치 파라미터

        val disp12MaxDiff = 5
        // 유효 disparity 차이의 최대 값 설정
        // disp12MaxDiff는 이미지 쌍 간 disparity 맵에서 정합(matching)이 잘못된 픽셀의 최대 허용 값

        val uniquenessRatio = 3
        // 유일성 비율(uniqueness ratio) 설정
        // disparity가 가장 유일하다고 판단되는 픽셀의 비율
        // 높은 값은 더 엄격한 제약 조건을 의미하며, 낮은 값은 덜 엄격한 제약 조건을 의미

        val speckleWindowSize = 5
        // 스펙클 제거를 위한 윈도우 크기 설정
        // 스펙클 제거를 수행할 윈도우의 크기 (0이면 스펙클 제거가 비활성화됨)

        val speckleRange = 5
        // 스펙클 제거를 위한 픽셀 간 disparity 차이 설정
        // 스펙클 제거 시, 인접 픽셀 간 disparity 값의 차이에 대한 임계값

        val preFilterCap = 63
        // 프리 필터(capacity) 설정
        // disparity map을 계산하기 전에 이미지에 적용되는 전처리 필터의 강도

        val mode = StereoSGBM.MODE_SGBM_3WAY
        // StereoSGBM 알고리즘의 모드 설정

        // StereoSGBM 생성
        sgbm = StereoSGBM.create(
            minDisparity,
            numDisparities,
            blockSize,
            P1,
            P2,
            disp12MaxDiff,
            preFilterCap,
            uniquenessRatio,
            speckleWindowSize,
            speckleRange,
            mode
        )
    }

    fun getSGBM(): StereoSGBM = sgbm
}
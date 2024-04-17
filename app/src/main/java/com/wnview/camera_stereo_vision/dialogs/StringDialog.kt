package com.wnview.camera_stereo_vision.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog


class StringDialog(private val context: Context, private val options: List<String>, private val listener: DialogListener) {

    interface DialogListener {
        fun onItemSelected(option: String)
    }

    private var checkedItemIndex = -1 // 선택된 항목의 초기값은 -1 (선택 없음)

    // 다이얼로그 생성을 위한 메서드
    fun create(): AlertDialog {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("카메라 선택")

        // 라디오 버튼 추가
        builder.setSingleChoiceItems(options.toTypedArray(), checkedItemIndex) { _, which ->
            checkedItemIndex = which
        }

        // 'OK' 버튼 설정
        builder.setPositiveButton("OK") { _, _ ->
            if (checkedItemIndex != -1) {
                listener.onItemSelected(options[checkedItemIndex])
            }
        }

        // 'Cancel' 버튼 설정
        builder.setNegativeButton("Cancel", null)

        return builder.create()
    }

    // 다이얼로그 표시를 위한 메서드
    fun showDialog() {
        create().show()
    }
}

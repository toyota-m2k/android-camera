package io.github.toyota32k.secureCamera.settings

import android.os.Bundle
import android.view.View
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.secureCamera.R

class SettingDialog : UtDialogEx() {
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        return inflater.inflate(R.layout.dialog_setting)
    }
}
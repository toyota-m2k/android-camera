package io.github.toyota32k.secureCamera.utils

import android.view.Window
import android.view.WindowManager
import io.github.toyota32k.dialog.UtRadioSelectionBox
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask

fun Window.setSecureMode() {
    val current = (this.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
    UtImmortalSimpleTask.run("setSecureMode") {
        val next = showDialog(taskName) { UtRadioSelectionBox.create("Secure Mode", arrayOf("Allow Capture", "Disallow Capture"), if(current) 1 else 0) }.selectedIndex == 1
        if(current!=next) {
            if(next) {
                this@setSecureMode.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                this@setSecureMode.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        true
    }
}

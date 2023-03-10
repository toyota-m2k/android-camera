package io.github.toyota32k.lib.camera.gesture

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import kotlinx.coroutines.CoroutineScope

interface ICameraGestureOwner {
    val context: Context
    val gestureScope: CoroutineScope
    val previewView: PreviewView?
    val camera: Camera?
}

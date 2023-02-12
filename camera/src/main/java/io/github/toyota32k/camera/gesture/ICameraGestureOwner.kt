package io.github.toyota32k.camera.gesture

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import kotlinx.coroutines.CoroutineScope

interface ICameraGestureOwner {
    val context: Context
    val lifecycleScope: CoroutineScope
    val previewView: PreviewView?
    val camera: Camera?
}

package io.github.toyota32k.camera.gesture

class CameraGestureManager(
    cameraOwner: ICameraGestureOwner,
    enableZoom:Boolean=true,
    enableFocus:Boolean=true,
    longTapToFocus:Boolean=false,
    customAction:(()->Unit)?=null) {
    private val focusGesture = FocusGestureListener(cameraOwner, enableFocus, longTapToFocus,customAction)
    private val zoomGesture = if(enableZoom) ZoomGestureListener(cameraOwner) else null

    init {
        if(enableZoom||enableFocus) {
            cameraOwner.previewView?.setOnTouchListener { view, event ->
//                view.performClick()
                if(zoomGesture!=null) {
                    zoomGesture.onTouchEvent(event)
                    if(zoomGesture.isInProgress) {
                        return@setOnTouchListener true
                    }
                }
                if(focusGesture.onTouchEvent(event)) {
                    return@setOnTouchListener true
                }
                view.performClick()
            }
        }
    }

    fun setCustomAction(fn:(()->Unit)?) {
        focusGesture.customAction = fn
    }
}
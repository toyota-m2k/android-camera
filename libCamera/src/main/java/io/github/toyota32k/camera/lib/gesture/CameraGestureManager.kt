package io.github.toyota32k.camera.lib.gesture

class CameraGestureManager(
    cameraOwner: ICameraGestureOwner,
    enableZoom:Boolean=true,
    enableFocus:Boolean=true,
    longTapToFocus:Boolean=false,
    singleTapCustomAction:(()->Boolean)? = null,
    longTapCustomAction:(()->Boolean)? = null
) {
    private val focusGesture = FocusGestureListener(
        cameraOwner,
        enableFocus,
        longTapToFocus,
        singleTapCustomAction,
        longTapCustomAction
    )
    private val zoomGesture = if (enableZoom) ZoomGestureListener(cameraOwner) else null

    class Builder {
        private var enableZoom: Boolean = false
        private var enableFocus: Boolean = false
        private var longTapToFocus: Boolean = false
        private var singleTapCustomAction: (() -> Boolean)? = null
        private var longTapCustomAction: (() -> Boolean)? = null

        fun enableZoomGesture(): Builder {
            enableZoom = true
            return this
        }

        fun enableFocusGesture(withLongTap: Boolean = false): Builder {
            enableFocus = true
            longTapToFocus = withLongTap
            return this
        }

        fun singleTapCustomAction(fn: () -> Boolean): Builder {
            singleTapCustomAction = fn
            return this
        }

        fun longTapCustomAction(fn: () -> Boolean): Builder {
            longTapCustomAction = fn
            return this
        }

        fun build(cameraOwner: ICameraGestureOwner): CameraGestureManager {
            return CameraGestureManager(
                cameraOwner,
                enableZoom,
                enableFocus,
                longTapToFocus,
                singleTapCustomAction,
                longTapCustomAction
            )
        }
    }

    init {
        if (enableZoom || enableFocus) {
            cameraOwner.previewView?.apply {
                isClickable = true
                isLongClickable = true
                setOnTouchListener { view, event ->
                    if (zoomGesture != null) {
                        zoomGesture.onTouchEvent(event)
                        if (zoomGesture.isInProgress) {
                            return@setOnTouchListener true
                        }
                    }
                    if (focusGesture.onTouchEvent(event)) {
                        return@setOnTouchListener true
                    }
                    view.performClick()
                }
            }
        }
    }

    fun setSingleTapCustomAction(fn:(()->Boolean)?) {
        focusGesture.singleTapCustomAction = fn
    }
    fun setLongTapCustomAction(fn:(()->Boolean)?) {
        focusGesture.longTapCustomAction = fn
    }
}
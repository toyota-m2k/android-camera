package io.github.toyota32k.secureCamera.utils

import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.headlessNonnullBinding
import kotlinx.coroutines.flow.Flow

fun FragmentActivity.hideStatusBar() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView.rootView).let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun FragmentActivity.showStatusBar() {
    WindowCompat.setDecorFitsSystemWindows(window, true)
    WindowInsetsControllerCompat(window, window.decorView.rootView).let { controller ->
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

fun FragmentActivity.showStatusBar(flag:Boolean) {
    if(flag) {
        showStatusBar()
    } else {
        hideStatusBar()
    }
}
fun AppCompatActivity.hideActionBar() {
    supportActionBar?.hide()
}

fun AppCompatActivity.showActionBar() {
    supportActionBar?.show()
}

fun AppCompatActivity.showActionBar(flag:Boolean) {
    if(flag) {
        supportActionBar?.show()
    } else {
        supportActionBar?.hide()
    }
}

enum class ActivityOrientation(val value:Int) {
    AUTO(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    LANDSCAPE(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
}

fun FragmentActivity.setOrientation(orientation:ActivityOrientation) {
    requestedOrientation = orientation.value
}

class ActivityOptions(
    private var showActionBar:Boolean? = null,              // nullなら現状の値を変更しない
    private var showStatusBar:Boolean? = null,
    private var requestedOrientation:ActivityOrientation? = null
) {
    fun apply(activity: FragmentActivity) {
        showActionBar?.also { show ->
            if (activity is AppCompatActivity) {
                activity.showActionBar(show)
            }
        }
        showStatusBar?.also { show ->
            activity.showStatusBar(show)
        }
        requestedOrientation?.also { orientation ->
            activity.setOrientation(orientation)
        }
    }

    companion object {
        fun actionBar(showActionBar: Boolean, orientation: ActivityOrientation?=null):ActivityOptions
                = ActivityOptions(showActionBar, null, orientation)
        fun statusBar(showStatusBar: Boolean, orientation: ActivityOrientation):ActivityOptions
                = ActivityOptions(null, showStatusBar, orientation)
        fun actionAndStatusBar(showActionBar: Boolean, showStatusBar: Boolean, orientation: ActivityOrientation):ActivityOptions
                = ActivityOptions(showActionBar, showStatusBar, orientation)
    }
}

/**
 * Boolean値とStatusBar表示状態のバインディング
 */
fun Binder.activityStatusBarBinding(owner: LifecycleOwner, show:LiveData<Boolean>)
        = headlessNonnullBinding(owner, show) { (owner as FragmentActivity).showStatusBar(it) }
fun Binder.activityStatusBarBinding(owner: LifecycleOwner, show:Flow<Boolean>)
        = headlessNonnullBinding(owner, show) { (owner as FragmentActivity).showStatusBar(it) }
fun Binder.activityStatusBarBinding(show:LiveData<Boolean>)
        = activityStatusBarBinding(requireOwner, show)
fun Binder.activityStatusBarBinding(show:Flow<Boolean>)
        = activityStatusBarBinding(requireOwner, show)


/**
 * Boolean値とActionBar表示状態のバインディング
 */
fun Binder.activityActionBarBinding(owner: LifecycleOwner, show:LiveData<Boolean>)
        = headlessNonnullBinding(owner, show) { (owner as AppCompatActivity).showActionBar(it) }
fun Binder.activityActionBarBinding(owner: LifecycleOwner, show:Flow<Boolean>)
        = headlessNonnullBinding(owner, show) { (owner as AppCompatActivity).showActionBar(it) }
fun Binder.activityActionBarBinding(show:LiveData<Boolean>)
        = activityActionBarBinding(requireOwner, show)
fun Binder.activityActionBarBinding(show:Flow<Boolean>)
        = activityActionBarBinding(requireOwner, show)

/**
 * Orientationのバインディング
 */
fun Binder.activityOrientationBinding(owner: LifecycleOwner, show:LiveData<ActivityOrientation>)
        = headlessNonnullBinding(owner, show) { (owner as FragmentActivity).setOrientation(it) }
fun Binder.activityOrientationBinding(owner: LifecycleOwner, show:Flow<ActivityOrientation>)
        = headlessNonnullBinding(owner, show) { (owner as FragmentActivity).setOrientation(it) }
fun Binder.activityOrientationBinding(show:LiveData<ActivityOrientation>)
        = activityOrientationBinding(requireOwner, show)
fun Binder.activityOrientationBinding(show:Flow<ActivityOrientation>)
        = activityOrientationBinding(requireOwner, show)

/**
 * ActivityOption のバインディング
 */
fun Binder.activityOptionsBinding(owner:LifecycleOwner, options:LiveData<ActivityOptions>)
        = headlessNonnullBinding(owner, options) { it.apply(owner as FragmentActivity) }

fun Binder.activityOptionsBinding(owner:LifecycleOwner, options: Flow<ActivityOptions>)
        = headlessNonnullBinding(owner, options) { it.apply(owner as FragmentActivity) }

fun Binder.activityOptionsBinding(options:LiveData<ActivityOptions>)
        = activityOptionsBinding(requireOwner, options)

fun Binder.activityOptionsBinding(options: Flow<ActivityOptions>)
        = activityOptionsBinding(requireOwner, options)
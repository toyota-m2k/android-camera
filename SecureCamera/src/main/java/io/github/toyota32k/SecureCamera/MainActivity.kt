package io.github.toyota32k.SecureCamera

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.SecureCamera.databinding.ActivityMainBinding
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.camera.TcCamera
import io.github.toyota32k.camera.TcCameraManager
import io.github.toyota32k.camera.gesture.CameraGestureManager
import io.github.toyota32k.camera.gesture.ICameraGestureOwner
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.bindCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : UtMortalActivity() {
    override val logger = UtLog("MAIN")

    class MainViewModel : ViewModel() {
    }

    private val binder = Binder()
    private val viewModel by viewModels<MainViewModel>()
    private lateinit var controls:ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)
//        setContentView(R.layout.activity_main)

        binder.owner(this)
            .bindCommand(LiteUnitCommand {startActivity(Intent(this, CameraActivity::class.java))}, controls.cameraButton )
            .bindCommand(LiteUnitCommand {startActivity(Intent(this, PlayerActivity::class.java))}, controls.playerButton )
    }

}
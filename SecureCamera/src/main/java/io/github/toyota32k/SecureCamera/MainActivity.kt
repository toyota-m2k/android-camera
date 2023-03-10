package io.github.toyota32k.SecureCamera

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import io.github.toyota32k.SecureCamera.databinding.ActivityMainBinding
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.bindCommand

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
package io.github.toyota32k.secureCamera

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.longClickBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.mortal.UtMortalActivity
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.databinding.ActivityMainBinding
import io.github.toyota32k.secureCamera.dialog.BulseDialog
import io.github.toyota32k.secureCamera.dialog.ColorVariationDialog
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.dialog.SettingDialog
import io.github.toyota32k.secureCamera.dialog.SlotDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotSettings
import io.github.toyota32k.secureCamera.utils.PackageUtil
import io.github.toyota32k.utils.android.hideActionBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : UtMortalActivity() {
    override val logger = UtLog("MAIN")

    class MainViewModel : ViewModel() {
        val busy = MutableStateFlow<Boolean>(false)
    }

    private val binder = Binder()
    private val viewModel by viewModels<MainViewModel>()
    private lateinit var controls: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
//        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        Settings.Design.applyToActivity(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

//        setTheme(R.style.Theme_TryCamera_M3_DynamicColor)
//        setTheme(R.style.Theme_TryCamera_M3_Cherry_NoActionBar)

        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)

        setupWindowInsetsListener(controls.root)

        @SuppressLint("SetTextI18n")
        controls.appName.text = "${PackageUtil.appName(this)} v${PackageUtil.getVersion(this)} ${if(BuildConfig.DEBUG) "(d)" else ""}"

        hideActionBar()
        binder.owner(this)
            .bindCommand(LiteUnitCommand(::startCamera), controls.cameraButton )
            .bindCommand(LiteUnitCommand(::startPlayer), controls.playerButton )
            .bindCommand(LiteUnitCommand(::startServer), controls.serverButton )
            .bindCommand(LiteUnitCommand(::setting), controls.settingsButton)
            .longClickBinding(controls.settingsButton, this::settingMenu)
            .bindCommand(LiteUnitCommand(::setupSlots), controls.slotButton)
            .multiEnableBinding(arrayOf(controls.slotButton, controls.cameraButton, controls.playerButton, controls.serverButton, controls.settingsButton), viewModel.busy, boolConvert = BoolConvert.Inverse)
            .textBinding(controls.slotName, SlotSettings.currentSlotFlow.map { it.safeSlotName })
    }

    private fun setupSlots() {
        viewModel.busy.value = true
        lifecycleScope.launch {
            try {
                SlotDialog.show()
            } finally {
                viewModel.busy.value = false
            }
        }
    }

    private fun startCamera() {
        startActivity(Intent(this, CameraActivity::class.java))
    }

    private fun startPlayer() {
        viewModel.busy.value = true
        lifecycleScope.launch {
            if(PasswordDialog.checkPassword(SlotSettings.currentSlotIndex)) {
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
            } else {
                logger.error("Incorrect Password")
            }
            viewModel.busy.value = false
        }
    }

    private fun startServer() {
        viewModel.busy.value = true
        lifecycleScope.launch {
            if(PasswordDialog.checkPassword()) {
                startActivity(Intent(this@MainActivity, ServerActivity::class.java))
            }
            viewModel.busy.value = false
        }
    }
    private fun setting() {
        viewModel.busy.value = true
        lifecycleScope.launch {
            SettingDialog.show()
            viewModel.busy.value = false
        }
    }

    private fun settingMenu(anchor: View):Boolean {
        viewModel.busy.value = true
        val selection = MutableStateFlow<Int?>(null)
        PopupMenu(this, anchor).apply {
            setOnMenuItemClickListener {
                if (it.itemId != R.id.dangerous) {
                    selection.value = it.itemId
                    true
                } else false
            }
            setOnDismissListener {
                selection.value = -1
            }
            inflate(R.menu.menu_setting)
        }.show()
        lifecycleScope.launch {
            val sel = selection.first { it != null }
            when(sel) {
                R.id.settings -> SettingDialog.show()
                R.id.colors -> ColorVariationDialog.show()
                R.id.bulse -> BulseDialog.bulse()
                else -> {}
            }
            viewModel.busy.value = false
        }
        return true
    }

    private fun colorVariation() {
        ColorVariationDialog.show()
    }

    private fun bulse() {
        BulseDialog.bulse()
    }

}
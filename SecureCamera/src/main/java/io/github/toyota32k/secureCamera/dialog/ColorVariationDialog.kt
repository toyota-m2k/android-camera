package io.github.toyota32k.secureCamera.dialog

import android.content.Intent
import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.exposedDropdownMenuBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.MainActivity
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogColorVariationBinding
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.utils.ThemeSelector
import kotlinx.coroutines.flow.MutableStateFlow

class ColorVariationDialog : UtDialogEx() {
    class ColorVariationViewModel : UtDialogViewModel() {
        val dayNightMode = MutableStateFlow(Settings.Design.nightMode)
        val themeInfo = MutableStateFlow(Settings.Design.themeInfo)
        val contrastLevel = MutableStateFlow(Settings.Design.contrastLevel)

        fun save() {
            Settings.Design.nightMode = dayNightMode.value
            Settings.Design.themeInfo = themeInfo.value
            Settings.Design.contrastLevel = contrastLevel.value
        }
    }
    private val viewModel by lazy { getViewModel<ColorVariationViewModel>() }
    private lateinit var controls: DialogColorVariationBinding

    override fun preCreateBodyView() {
        title = getString(R.string.color_variation)
        heightOption = HeightOption.COMPACT
        widthOption = WidthOption.LIMIT(400)
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
        gravityOption = GravityOption.RIGHT_TOP
        draggable = true
    }

    override fun createBodyView(savedInstanceState: Bundle?,inflater: IViewInflater): View {
        controls = DialogColorVariationBinding.inflate(inflater.layoutInflater)
        binder
            .exposedDropdownMenuBinding(controls.dayNightDropdown, viewModel.dayNightMode, ThemeSelector.NightMode.entries)
            .exposedDropdownMenuBinding(controls.colorContrastDropdown, viewModel.contrastLevel, ThemeSelector.ContrastLevel.entries)
            .exposedDropdownMenuBinding(controls.themeDropdown, viewModel.themeInfo, Settings.ThemeList.themes) { toLabel { it.label } }
        return controls.root
    }

    override fun onPositive() {
        viewModel.save()
        super.onPositive()
    }

    companion object {
        fun show() {
            UtImmortalTask.launchTask(this::class.java.name) {
                createViewModel<ColorVariationViewModel>()
                if (showDialog(taskName) { ColorVariationDialog() }.status.ok ) {
                    withOwner {
                        val activity = it.asActivity() as? MainActivity ?: return@withOwner
                        if (ThemeSelector.defaultInstance.isThemeChanged(Settings.Design.themeInfo,Settings.Design.contrastLevel)) {
                            activity.startActivity(Intent(activity, MainActivity::class.java))
                            activity.finish()
                        } else {
                            ThemeSelector.defaultInstance.applyNightMode(Settings.Design.nightMode)
                        }
                    }
                }
            }
        }
    }
}
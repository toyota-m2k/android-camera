package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getActivity
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.databinding.DialogBulseBinding
import io.github.toyota32k.secureCamera.db.MetaDB
import kotlinx.coroutines.flow.MutableStateFlow

class BulseDialog : UtDialogEx() {
    class BulseViewModel : UtDialogViewModel() {
        val logger = UtLog("Bulse", null, BulseViewModel::class.java)
        val agree = MutableStateFlow(false)
    }

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.NONE
        rightButtonType = ButtonType.NONE
        noFooter = true
        noHeader = true
        cancellable = true
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
    }

    private val viewModel by lazy { getViewModel<BulseViewModel>() }
    private lateinit var controls: DialogBulseBinding
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogBulseBinding.inflate(inflater.layoutInflater)
        binder
            .clickBinding(controls.buttonReject) {
                onNegative()
            }
            .clickBinding(controls.buttonAccept) {
                logger.debug { "bulse" }
                onPositive()
            }
            .checkBinding(controls.checkAgree, viewModel.agree)
            .enableBinding(controls.buttonAccept, viewModel.agree)
        return controls.root
    }

    companion object {
        fun bulse() {
            UtImmortalTask.launchTask("Bulse") {
                createViewModel<BulseViewModel>()
                if (showDialog(taskName) { BulseDialog()}.status.ok) {
                    MetaDB.bulse()
                }
            }
        }
    }
}
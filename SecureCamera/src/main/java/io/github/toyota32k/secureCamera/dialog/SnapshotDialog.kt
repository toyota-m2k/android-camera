package io.github.toyota32k.secureCamera.dialog

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogSnapshotBinding

class SnapshotDialog : UtDialogEx() {
    class SnapshotViewModel : UtDialogViewModel() {
        lateinit var snapshot: Bitmap
        val sizeText get() = "${snapshot.width} x ${snapshot.height}"
    }
    override fun preCreateBodyView() {
        heightOption = HeightOption.FULL
        widthOption = WidthOption.FULL
        noHeader = true
        leftButtonType = ButtonType(getString(R.string.reject), positive=false)
        rightButtonType = ButtonType(getString(R.string.accept), positive=true)
    }

    private val viewModel: SnapshotViewModel by lazy { getViewModel() }
    private lateinit var controls: DialogSnapshotBinding

    override fun createBodyView(
        savedInstanceState: Bundle?,
        inflater: IViewInflater
    ): View {
        controls = DialogSnapshotBinding.inflate(inflater.layoutInflater, null, false)
        controls.imageView.setImageBitmap(viewModel.snapshot)
        controls.sizeText.text = viewModel.sizeText
        return controls.root
    }

    override fun onDialogClosing() {
        controls.imageView.setImageBitmap(null)
        super.onDialogClosing()
    }

    companion object {
        suspend fun showBitmap(snapshot: Bitmap):Boolean {
            return UtImmortalTask.awaitTaskResult(this::class.java.name) {
                val vm = createViewModel<SnapshotViewModel> { this.snapshot = snapshot }
                showDialog(taskName) { SnapshotDialog() }.status.ok
            }
        }
    }
}
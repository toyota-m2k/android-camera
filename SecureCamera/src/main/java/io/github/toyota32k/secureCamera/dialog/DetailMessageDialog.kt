package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.immortalTaskContext
import io.github.toyota32k.dialog.task.launchSubTask
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogDetailMessageBinding
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class DetailMessageDialog : UtDialogEx() {
    class DetailMessageViewModel : UtDialogViewModel() {
        val label = MutableStateFlow("")
        val message = MutableStateFlow("")
        val detailMessage = MutableStateFlow("")
        val showDetailMessage = MutableStateFlow(false)
        lateinit var inputFile: File
        var chapters: List<IChapter>? = null
        val commandPlay = LiteUnitCommand() {
            immortalTaskContext.launchSubTask {
                VideoPreviewDialog.show(inputFile.toUri().toString(), "preview", chapters) { builder ->
                    builder.enableSeekSmall(0,0)    // step by frame
                    builder.enableSeekMedium(1000, 1000)
                }
            }
        }

        companion object {
            fun create(taskName:String, label: String, message: String, detailMessage: String, inputFile:File, chapters: List<IChapter>?): DetailMessageViewModel {
                return UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel<DetailMessageViewModel>()?.also {
                    it.label.value = label
                    it.message.value = message
                    it.detailMessage.value = detailMessage
                    it.inputFile = inputFile
                    it.chapters = chapters
                } ?: throw IllegalStateException("no task")
            }
            fun instanceFor(dlg:DetailMessageDialog): DetailMessageViewModel {
                return ViewModelProvider(dlg.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[DetailMessageViewModel::class.java]
            }
        }
    }

    private val viewModel by lazy { DetailMessageViewModel.instanceFor(this) }
    private lateinit var controls : DialogDetailMessageBinding

    override fun preCreateBodyView() {
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.AUTO_SCROLL
        leftButtonType = ButtonType(getString(R.string.reject), positive=false)
        rightButtonType = ButtonType(getString(R.string.accept), positive=true)
        optionButtonType = ButtonType("Play", positive=true)
        optionButtonWithAccent = true
        cancellable = false
        noHeader = true
    }

    override fun createBodyView(
        savedInstanceState: Bundle?,
        inflater: IViewInflater
    ): View {
        controls = DialogDetailMessageBinding.inflate(inflater.layoutInflater).apply {
            binder
                .textBinding(label, viewModel.label)
                .textBinding(message, viewModel.message)
                .textBinding(detailMessage, viewModel.detailMessage)
                .checkBinding(checkShowDetail, viewModel.showDetailMessage)
                .visibilityBinding(detailMessage, viewModel.showDetailMessage)
                .dialogOptionButtonCommand(viewModel.commandPlay)
        }

        return controls.root
    }

    companion object {
        suspend fun showMessage(label:String, message:String, detailMessage:String, inputFile:File, chapters: List<IChapter>?):Boolean {
            return UtImmortalTask.awaitTaskResult(DetailMessageDialog::class.java.name) {
                DetailMessageViewModel.create(taskName, label, message, detailMessage, inputFile, chapters)
                showDialog(taskName) { DetailMessageDialog() }.status.ok
            }
        }
    }
}
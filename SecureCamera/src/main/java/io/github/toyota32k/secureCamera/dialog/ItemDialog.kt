package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.materialRadioUnSelectableButtonGroupBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.databinding.DialogItemBinding
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.Mark
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.Rating
import io.github.toyota32k.secureCamera.settings.SlotSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ItemDialog : UtDialogEx() {
    class ItemViewModel : UtDialogViewModel() {
        lateinit var item:MutableStateFlow<ItemEx>
        val metaDb = MetaDB[SlotSettings.currentSlotIndex]

        enum class NextAction {
            None,
            EditItem,
//            BackupItem,
            PurgeLocal,
            RestoreLocal,
            Repair,
        }

        var nextAction = NextAction.None
            private set
        val actionCommand = LiteCommand<NextAction> { action->
            nextAction = action
            completeCommand.invoke()
        }
        val backupCommand = LiteUnitCommand {
            viewModelScope.launch {
                item.value = metaDb.backupToCloud(item.value)
            }
        }

        val completeCommand = LiteUnitCommand()

        val rating = MutableStateFlow(Rating.RatingNone)
        val mark = MutableStateFlow(Mark.None)
//        val editCommand = LiteUnitCommand()
//        val backupCommand = LiteUnitCommand()
//        val removeLocalCommand = LiteUnitCommand()

        fun initFor(item:ItemEx) {
            this.item = MutableStateFlow(item)
            rating.value = item.rating
            mark.value = item.mark
        }

        suspend fun saveIfNeed():Boolean {
            return if(item.value.rating!=rating.value || item.value.mark != mark.value) {
                item.value = metaDb.updateMarkRating(item.value, mark.value, rating.value)
                true
            } else false
        }

        override fun onCleared() {
            super.onCleared()
            metaDb.close()
        }
    }

    private val viewModel: ItemViewModel by lazy { getViewModel() }
    private lateinit var controls: DialogItemBinding

    override fun preCreateBodyView() {
        draggable = true
        scrollable = true
        widthOption = WidthOption.COMPACT
        heightOption = HeightOption.AUTO_SCROLL
        gravityOption = GravityOption.CENTER
        noHeader = true
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogItemBinding.inflate(inflater.layoutInflater)
        binder
            .textBinding(controls.itemName, viewModel.item.map { it.nameForDisplay})
//            .visibilityBinding(controls.editVideoButton, ConstantLiveData(viewModel.item.isVideo && viewModel.item.cloud.isFileInLocal), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.backupButton, viewModel.item.map { it.cloud == CloudStatus.Local }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.removeLocalButton, viewModel.item.map { it.cloud == CloudStatus.Uploaded }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.restoreLocalButton, viewModel.item.map { it.cloud == CloudStatus.Cloud }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.repairButton, viewModel.item.map { it.cloud != CloudStatus.Cloud }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .enableBinding(controls.editVideoButton, viewModel.item.map { !it.isPhoto})
            .materialRadioUnSelectableButtonGroupBinding(controls.ratingSelector, viewModel.rating, Rating.idResolver, BindingMode.TwoWay)
            .materialRadioUnSelectableButtonGroupBinding(controls.markSelector, viewModel.mark, Mark.idResolver, BindingMode.TwoWay)
            .bindCommand(viewModel.actionCommand, controls.editVideoButton, ItemViewModel.NextAction.EditItem)
            .bindCommand(viewModel.backupCommand, controls.backupButton)
            .bindCommand(viewModel.actionCommand, controls.removeLocalButton, ItemViewModel.NextAction.PurgeLocal)
            .bindCommand(viewModel.actionCommand, controls.restoreLocalButton, ItemViewModel.NextAction.RestoreLocal)
            .bindCommand(viewModel.actionCommand, controls.repairButton, ItemViewModel.NextAction.Repair)
            .bindCommand(viewModel.completeCommand, ::onPositive)
        return controls.root
    }
}
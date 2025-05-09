package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.DPDate
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.datePickerBinding
import io.github.toyota32k.binder.materialRadioButtonGroupBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogPlaylistSettingBinding
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.IUtPropOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PlayListSettingDialog : UtDialogEx() {
    class PlayListSettingViewModel : UtDialogViewModel(), IUtPropOwner {
        enum class EditingMode {
            None, EditingStart, EditingEnd,
        }
        var minDate: DPDate = DPDate.Invalid
        var maxDate: DPDate = DPDate.Today
        val orderByDate = MutableStateFlow(false)   // ascendant:false / descendant:true
        val enableStartDate = MutableStateFlow(false)
        val enableEndDate = MutableStateFlow(false)
        val startDate = MutableStateFlow(DPDate.Today)
        val endDate = MutableStateFlow(DPDate.Today)
        val cloudTestMode = MutableStateFlow(false)
        val onlyUnBackedUpItems = MutableStateFlow(false)
        val editingMode = MutableStateFlow(EditingMode.None)
        val allowDelete = MutableStateFlow(false)
        val showStartDatePicker: Flow<Boolean> = combine(enableStartDate,editingMode) {enabled,mode-> enabled && mode==EditingMode.EditingStart }
        val showEndDatePicker: Flow<Boolean> = combine(enableEndDate, editingMode){enabled,mode-> enabled && mode==EditingMode.EditingEnd }

        val commandEditStartDate = LiteUnitCommand {
            if(editingMode.value==EditingMode.EditingStart) {
                editingMode.value = EditingMode.None
            } else {
                editingMode.value = EditingMode.EditingStart
            }
        }
        val commandEditEndDate = LiteUnitCommand {
            if(editingMode.value==EditingMode.EditingEnd) {
                editingMode.value = EditingMode.None
            } else {
                editingMode.value = EditingMode.EditingEnd
            }
        }
        val commandEndEdit = LiteCommand<DPDate> {
            editingMode.value = EditingMode.None
        }
        val commandReset = LiteUnitCommand {
            editingMode.value = EditingMode.None
            enableStartDate.value = false
            enableEndDate.value = false
            orderByDate.value = false
            cloudTestMode.value = false
            onlyUnBackedUpItems.value = false
            allowDelete.value = false
        }

        private fun normalizeDateRange() {
            if(startDate.value.isValid && endDate.value.isValid && startDate.value>endDate.value) {
                val d = startDate.value
                startDate.value = endDate.value
                endDate.value = d
            }
        }
        fun load(minDate: DPDate, maxDate: DPDate) {
            this.minDate = minDate
            this.maxDate = maxDate
            orderByDate.value = Settings.PlayListSetting.sortOrder
            enableStartDate.value = Settings.PlayListSetting.enableStartDate
            enableEndDate.value = Settings.PlayListSetting.enableEndDate
            startDate.value = if(Settings.PlayListSetting.startDate.isValid) {
                if(minDate>Settings.PlayListSetting.startDate) minDate else Settings.PlayListSetting.startDate
            } else minDate
            endDate.value = if(Settings.PlayListSetting.endDate.isValid) {
                if(maxDate<Settings.PlayListSetting.endDate) maxDate else Settings.PlayListSetting.endDate
            } else maxDate
            normalizeDateRange()
            onlyUnBackedUpItems.value = Settings.PlayListSetting.onlyUnBackedUpItems
            cloudTestMode.value = Settings.PlayListSetting.cloudTestMode
            allowDelete.value = Settings.PlayListSetting.allowDelete
        }

        fun save() {
            normalizeDateRange()
            Settings.PlayListSetting.sortOrder = orderByDate.value
            Settings.PlayListSetting.enableStartDate = enableStartDate.value
            Settings.PlayListSetting.enableEndDate = enableEndDate.value
            Settings.PlayListSetting.startDate = startDate.value
            Settings.PlayListSetting.endDate = endDate.value
            Settings.PlayListSetting.cloudTestMode = cloudTestMode.value
            Settings.PlayListSetting.onlyUnBackedUpItems = onlyUnBackedUpItems.value
            Settings.PlayListSetting.allowDelete = allowDelete.value
        }
        companion object {
//            fun createBy(task: IUtImmortalTask, minDate: DPDate, maxDate: DPDate): PlayListSettingViewModel {
//                return UtImmortalViewModelHelper.createBy(PlayListSettingViewModel::class.java,task) {
//                    it.load(minDate,maxDate)
//                }
//            }
//            fun instanceFor(dlg:PlayListSettingDialog): PlayListSettingViewModel {
//                return UtImmortalViewModelHelper.instanceFor(PlayListSettingViewModel::class.java, dlg)
//            }
        }

        object SortOrderResolver: IIDValueResolver<Boolean> {
            override fun id2value(id:Int) : Boolean {
                return id == R.id.radio_desc
            }
            override fun value2id(v: Boolean): Int {
                return if(v) R.id.radio_desc else R.id.radio_asc
            }
        }

    }

    lateinit var controls: DialogPlaylistSettingBinding
    val viewModel: PlayListSettingViewModel by lazy { getViewModel() }

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.AUTO_SCROLL
        title = requireActivity().getString(R.string.playlist_setting_title)
        enableFocusManagement()
    }
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogPlaylistSettingBinding.inflate(inflater.layoutInflater)

        binder
            .checkBinding(controls.checkStartDate, viewModel.enableStartDate)
            .checkBinding(controls.checkEndDate, viewModel.enableEndDate)
            .checkBinding(controls.checkCloudTestMode, viewModel.cloudTestMode)
            .checkBinding(controls.checkOnlyUnBackupItems, viewModel.onlyUnBackedUpItems)
            .checkBinding(controls.checkAllowDelete, viewModel.allowDelete)
            .textBinding(controls.startDateText, viewModel.startDate.map { it.toString() })
            .textBinding(controls.endDateText, viewModel.endDate.map { it.toString() })
            .materialRadioButtonGroupBinding(controls.sortOrderSelector, viewModel.orderByDate, PlayListSettingViewModel.SortOrderResolver)
            .bindCommand(viewModel.commandEditStartDate, controls.editStartDateButton)
            .bindCommand(viewModel.commandEditEndDate, controls.editEndDateButton)
            .datePickerBinding(controls.startDatePicker, viewModel.startDate, selectCommand = viewModel.commandEndEdit)
            .datePickerBinding(controls.endDatePicker, viewModel.endDate, selectCommand = viewModel.commandEndEdit)
            .visibilityBinding(controls.startDatePicker, viewModel.showStartDatePicker, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.endDatePicker, viewModel.showEndDatePicker, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .multiVisibilityBinding( arrayOf(controls.startDateText,controls.editStartDateButton), viewModel.enableStartDate)
            .multiVisibilityBinding(arrayOf(controls.endDateText, controls.editEndDateButton), viewModel.enableEndDate)

        return controls.root
    }

    override fun onPositive() {
        viewModel.save()
        super.onPositive()
    }

    companion object {
        suspend fun show(minDate:DPDate, maxDate:DPDate):Boolean {
            return UtImmortalTask.awaitTaskResult("playListSettingDialog.show") {
                createViewModel<PlayListSettingViewModel> { load(minDate, maxDate) }
                showDialog(taskName) { PlayListSettingDialog() }.status.ok
            }
        }
    }
}
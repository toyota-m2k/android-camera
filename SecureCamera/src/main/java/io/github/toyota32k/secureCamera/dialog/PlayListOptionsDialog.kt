package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.DPDate
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.datePickerBinding
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.materialRadioButtonGroupBinding
import io.github.toyota32k.binder.materialToggleButtonGroupBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.PlayerActivity
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogPlaylistOptionsBinding
import io.github.toyota32k.secureCamera.databinding.DialogPlaylistSettingBinding
import io.github.toyota32k.secureCamera.db.Mark
import io.github.toyota32k.secureCamera.db.Mark.Companion.toBitFlags
import io.github.toyota32k.secureCamera.db.Rating
import io.github.toyota32k.secureCamera.db.Rating.Companion.toBitFlags
import io.github.toyota32k.secureCamera.dialog.PlayListSettingDialog.PlayListSettingViewModel
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.IUtPropOwner
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.checkerframework.checker.units.qual.min
import kotlin.collections.toTypedArray
import kotlin.coroutines.coroutineContext

class PlayListOptionsDialog : UtDialogEx() {
    class PlayListOptionsViewModel : UtDialogViewModel(), IUtPropOwner {
        enum class EditingMode {
            None, EditingStart, EditingEnd,
        }
        var minDate: DPDate = DPDate.Invalid
        var maxDate: DPDate = DPDate.Today
        val sortOrder = MutableStateFlow( PlayerActivity.SortOptions.Order.Ascend)
        val sortKey = MutableStateFlow(PlayerActivity.SortOptions.Key.Date)
        val enableStartDate = MutableStateFlow(false)
        val enableEndDate = MutableStateFlow(false)
        val startDate = MutableStateFlow(DPDate.Today)
        val endDate = MutableStateFlow(DPDate.Today)
        val enableRatingFilter = MutableStateFlow(false)
        val enableMarkFilter = MutableStateFlow(false)
        val ratingsList = MutableStateFlow<List<Rating>>(listOf())
        val marksList = MutableStateFlow<List<Mark>>(listOf())
        val onlyUnBackedUpItems = MutableStateFlow(false)
        val onlyOfflineItems = MutableStateFlow(false)
        val editingMode = MutableStateFlow(EditingMode.None)
        val cloudTestMode = MutableStateFlow(false)
        val allowDelete = MutableStateFlow(false)
        val showStartDatePicker: Flow<Boolean> = combine(enableStartDate,editingMode) {enabled,mode-> enabled && mode==EditingMode.EditingStart }
        val showEndDatePicker: Flow<Boolean> = combine(enableEndDate, editingMode){enabled,mode-> enabled && mode==EditingMode.EditingEnd }

        val commandEditStartDate = LiteUnitCommand {
            editingMode.value = EditingMode.EditingStart
        }
        val commandEditEndDate = LiteUnitCommand {
            editingMode.value = EditingMode.EditingEnd
        }
        val commandDateSelected = LiteCommand<DPDate> {
            editingMode.value = EditingMode.None
        }
        val commandEndEdit = LiteUnitCommand {
            editingMode.value = EditingMode.None
        }

        private fun normalizeDateRange() {
            if(startDate.value.isValid && endDate.value.isValid && startDate.value>endDate.value) {
                val d = startDate.value
                startDate.value = endDate.value
                endDate.value = d
            }
        }
        lateinit var orgSortOption :PlayerActivity.SortOptions
        lateinit var orgFilterOption :PlayerActivity.FilterOptions

        fun load(playerViewModel: PlayerActivity.PlayerViewModel, minDate: DPDate, maxDate: DPDate) {
            val sortOptions = playerViewModel.playlist.sortOptions
            val filterOptions = playerViewModel.playlist.filterOptions
            this.minDate = minDate
            this.maxDate = maxDate
            orgSortOption = sortOptions
            orgFilterOption = filterOptions

            sortOrder.value = sortOptions.order
            sortKey.value = sortOptions.key

            enableStartDate.value = filterOptions.enableStartDate
            enableEndDate.value = filterOptions.enableEndDate
            startDate.value = filterOptions.startDate.coerceIn(minDate,maxDate)
            endDate.value = filterOptions.endDate.coerceIn(minDate, maxDate)
            normalizeDateRange()


            enableRatingFilter.value = filterOptions.enableRatingFilter
            enableMarkFilter.value = filterOptions.enableMarkFilter
            ratingsList.value = Rating.fromBitFlags(filterOptions.ratingFlags)
            marksList.value = Mark.fromBitFlags(filterOptions.markFlags)

            onlyOfflineItems.value = Settings.PlayListSetting.onlyOfflineItems
            onlyUnBackedUpItems.value = Settings.PlayListSetting.onlyUnBackedUpItems
            cloudTestMode.value = Settings.PlayListSetting.cloudTestMode
            allowDelete.value = Settings.PlayListSetting.allowDelete

            sortOptionsFlow.onEach {
                playerViewModel.playlist.updateSortOptions(it)
            }.launchIn(viewModelScope)
            filterOptionsFlow.onEach {
                playerViewModel.playlist.updateFilterOptions(it)
            }.launchIn(viewModelScope)
        }

        val sortOptionsFlow: StateFlow<PlayerActivity.SortOptions> by lazy {
            combine(sortOrder, sortKey) { order, key ->
                PlayerActivity.SortOptions(key, order)
            }.stateIn(viewModelScope, SharingStarted.Lazily, orgSortOption)
        }
        @Suppress("UNCHECKED_CAST")
        val filterOptionsFlow: StateFlow<PlayerActivity.FilterOptions> by lazy {
            combine(
                enableStartDate, startDate, enableEndDate, endDate, // 0-3
                enableRatingFilter, ratingsList, enableMarkFilter, marksList, // 4-7
                onlyOfflineItems, onlyUnBackedUpItems
            ) { // 8-9
                    args ->
                PlayerActivity.FilterOptions(
                    listMode = orgFilterOption.listMode,
                    args[0] as Boolean,
                    args[1] as DPDate,
                    args[2] as Boolean,
                    args[3] as DPDate,
                    args[4] as Boolean,
                    (args[5] as List<Rating>).toBitFlags(),
                    args[6] as Boolean,
                    (args[7] as List<Mark>).toBitFlags(),
                    args[8] as Boolean,
                    args[9] as Boolean
                )
            }.stateIn(viewModelScope, SharingStarted.Lazily, orgFilterOption)
        }

        fun save() {
            normalizeDateRange()
            Settings.PlayListSetting.set(
                sortKey.value,
                sortOrder.value,
                enableStartDate.value,
                enableEndDate.value,
                startDate.value,
                endDate.value,
                enableRatingFilter.value,
                ratingsList.value.toBitFlags(),
                enableMarkFilter.value,
                marksList.value.toBitFlags(),
                onlyOfflineItems.value,
                onlyUnBackedUpItems.value,
                allowDelete.value,
                cloudTestMode.value,
            )
        }

    }

    lateinit var controls: DialogPlaylistOptionsBinding
    val viewModel: PlayListOptionsViewModel by lazy { getViewModel() }

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.AUTO_SCROLL
        title = requireActivity().getString(R.string.playlist_option_title)
        enableFocusManagement()
    }

    override fun createBodyView(
        savedInstanceState: Bundle?,
        inflater: IViewInflater
    ): View {
        controls = DialogPlaylistOptionsBinding.inflate(inflater.layoutInflater)

        binder
            .checkBinding(controls.ratingCheck, viewModel.enableRatingFilter, mode= BindingMode.TwoWay)
            .checkBinding(controls.markCheck, viewModel.enableMarkFilter, mode= BindingMode.TwoWay)
            .checkBinding(controls.offlineItems, viewModel.onlyOfflineItems)
            .checkBinding(controls.checkOnlyUnBackupItems, viewModel.onlyUnBackedUpItems)
            .checkBinding(controls.checkStartDate, viewModel.enableStartDate)
            .checkBinding(controls.checkEndDate, viewModel.enableEndDate)
            .checkBinding(controls.checkAllowDelete, viewModel.allowDelete)
            .checkBinding(controls.checkCloudTestMode, viewModel.cloudTestMode)

            .textBinding(controls.startDateButton, viewModel.startDate.map { it.toString() })
            .textBinding(controls.endDateButton, viewModel.endDate.map { it.toString() })

            .materialRadioButtonGroupBinding(controls.sortKeySelector, viewModel.sortKey, PlayerActivity.SortOptions.Key.resolver)
            .materialRadioButtonGroupBinding(controls.sortOrderSelector, viewModel.sortOrder, PlayerActivity.SortOptions.Order.resolver)
            .materialToggleButtonGroupBinding(controls.ratingSelector, viewModel.ratingsList, Rating.idResolver, mode= BindingMode.TwoWay)
            .materialToggleButtonGroupBinding(controls.markSelector, viewModel.marksList, Mark.idResolver, mode= BindingMode.TwoWay)
            .enableBinding(controls.ratingSelector, viewModel.enableRatingFilter)
            .enableBinding(controls.markSelector, viewModel.enableMarkFilter)

            .datePickerBinding(controls.startDatePicker, viewModel.startDate, selectCommand = viewModel.commandDateSelected)
            .datePickerBinding(controls.endDatePicker, viewModel.endDate, selectCommand = viewModel.commandDateSelected)
            .visibilityBinding(controls.startDatePicker, viewModel.showStartDatePicker)
            .visibilityBinding(controls.endDatePicker, viewModel.showEndDatePicker)
            .visibilityBinding(controls.datePickerContainer, combine(viewModel.showStartDatePicker,viewModel.showEndDatePicker) {s,b-> s||b}, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .enableBinding(controls.startDateButton, viewModel.enableStartDate)
            .enableBinding(controls.endDateButton, viewModel.enableEndDate)

            .bindCommand(viewModel.commandEditStartDate, controls.startDateButton)
            .bindCommand(viewModel.commandEditEndDate, controls.endDateButton)
            .clickBinding(controls.datePickerContainer) {
                viewModel.commandEndEdit.invoke()
            }

        return controls.root
    }

    companion object {
        suspend fun show(playerViewModel: PlayerActivity.PlayerViewModel, minDate: DPDate, maxDate: DPDate) {
            return UtImmortalTask.awaitTaskResult("playListOptionsDialog.show") {
                val vm = createViewModel<PlayListOptionsDialog.PlayListOptionsViewModel> {
                    load(playerViewModel, minDate, maxDate)
                }
                if (showDialog(taskName) { PlayListOptionsDialog() }.status.ok) {
                    vm.save()
                } else {
                    // キャンセルされたら元に戻す
                    playerViewModel.playlist.updateOptions(vm.orgSortOption, vm.orgFilterOption)
                }
            }
        }
    }
}
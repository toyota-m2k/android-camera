package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogSlotBinding
import io.github.toyota32k.secureCamera.settings.SlotIndex
import io.github.toyota32k.secureCamera.settings.SlotInfo
import io.github.toyota32k.secureCamera.settings.SlotSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class SlotDialog : UtDialogEx() {
    class SlotDialogViewModel : UtDialogViewModel() {

        val defaultSlotName = MutableStateFlow("")
        val currentSlotIndex = MutableStateFlow(SlotSettings.currentSlotIndex)
        val isDefaultSlotSelected = MutableStateFlow<Boolean>(SlotSettings.currentSlotIndex == SlotIndex.DEFAULT)

        inner class SlotParams(slotInfo:SlotInfo) {
            val index = slotInfo.index
            val name = MutableStateFlow(slotInfo.slotName?:"")
            val inUse = MutableStateFlow(slotInfo.inUse)
            val sync = MutableStateFlow(slotInfo.sync)
            val secure = MutableStateFlow(slotInfo.secure)
            val isSelected = MutableStateFlow<Boolean>(SlotSettings.currentSlotIndex == index)
            init {
                isSelected.onEach {
                    if (it) {
                        currentSlotIndex.value = index
                    }
                }.launchIn(viewModelScope)
            }
        }
        val slotParams = listOf(
            SlotParams(SlotSettings[SlotIndex.SLOT1]),
            SlotParams(SlotSettings[SlotIndex.SLOT2]),
            SlotParams(SlotSettings[SlotIndex.SLOT3]),
            SlotParams(SlotSettings[SlotIndex.SLOT4]),
        )

        val selection = listOf(
            isDefaultSlotSelected,
            slotParams[0].isSelected,
            slotParams[1].isSelected,
            slotParams[2].isSelected,
            slotParams[3].isSelected
        )

        init {
            isDefaultSlotSelected.onEach {
                if (it) {
                    currentSlotIndex.value = SlotIndex.DEFAULT
                }
            }.launchIn(viewModelScope)
            currentSlotIndex.onEach {
                isDefaultSlotSelected.value = (it == SlotIndex.DEFAULT)
                for (i in 0..3) {
                    slotParams[i].isSelected.value = (it == slotParams[i].index)
                }
            }.launchIn(viewModelScope)
        }

        fun save() {
            SlotSettings.defaultSlotName = defaultSlotName.value
            SlotSettings.setCurrentSlot(currentSlotIndex.value)
            for (i in 0..3) {
                val params = slotParams[i]
                SlotSettings[slotParams[i].index] = SlotSettings[slotParams[i].index].update(
                    slotName = params.name.value,
                    inUse = params.inUse.value,
                    sync = params.sync.value,
                    secure = params.secure.value)
            }
        }
    }

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        gravityOption = GravityOption.CENTER
        cancellable = true
        draggable = true
        heightOption = HeightOption.AUTO_SCROLL
        widthOption = WidthOption.LIMIT(400)
        title = requireActivity().getString(R.string.slot_dialog_title)
        enableFocusManagement()
            .autoRegister()
    }

    private lateinit var controls : DialogSlotBinding
    private val viewModel: SlotDialogViewModel by viewModels()

    override fun createBodyView(
        savedInstanceState: Bundle?,
        inflater: IViewInflater
    ): View {
        controls = DialogSlotBinding.inflate(inflater.layoutInflater)
        val selectRadio = listOf(controls.slot1Name,controls.slot2Name,controls.slot3Name,controls.slot4Name)
        val nameEdits = listOf(controls.slot1NameInput, controls.slot2NameInput, controls.slot3NameInput, controls.slot4NameInput)
        val inUseCheckboxes = listOf(controls.slot1InUse, controls.slot2InUse, controls.slot3InUse, controls.slot4InUse)
        val syncCheckboxes = listOf(controls.slot1Sync, controls.slot2Sync, controls.slot3Sync, controls.slot4Sync)
        val secureCheckboxes = listOf(controls.slot1Secure, controls.slot2Secure, controls.slot3Secure, controls.slot4Secure)

        binder
            .owner(this)
            .editTextBinding(controls.defaultSlotNameInput, viewModel.defaultSlotName)
            .checkBinding(controls.defaultSlotName, viewModel.isDefaultSlotSelected)
            .apply {
                for (i in 1..4) {
                    val params = viewModel.slotParams[i-1]
                    editTextBinding(nameEdits[i-1], params.name)
                    checkBinding(inUseCheckboxes[i-1], params.inUse)
                    checkBinding(syncCheckboxes[i-1], params.sync)
                    checkBinding(secureCheckboxes[i-1], params.secure)
                    checkBinding(selectRadio[i-1], params.isSelected)
                    multiEnableBinding(arrayOf(nameEdits[i-1], selectRadio[i-1], syncCheckboxes[i-1], secureCheckboxes[i-1]), params.inUse)
                }
            }
        return controls.root
    }

    override fun onPositive() {
        viewModel.save()
        super.onPositive()
    }

    companion object {
        suspend fun show() {
            UtImmortalTask.awaitTask("slotDialog") {
                createViewModel<SlotDialogViewModel>()
                showDialog(taskName) { SlotDialog() }
            }
        }
    }
}
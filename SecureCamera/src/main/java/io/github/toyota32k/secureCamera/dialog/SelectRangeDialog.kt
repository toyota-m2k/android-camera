package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.sliderBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTaskBase
import io.github.toyota32k.dialog.task.UtImmortalViewModel
import io.github.toyota32k.dialog.task.UtImmortalViewModelHelper
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.secureCamera.databinding.DialogSelectRangeBinding
import io.github.toyota32k.secureCamera.dialog.SelectRangeDialog.RangeModeViewModel.Companion.MIN_PART_SPAN
import io.github.toyota32k.utils.TimeSpan
import io.github.toyota32k.utils.asConstantLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes

data class SplitParams(val enabled:Boolean, val duration:Long, val count:Long, val index:Int) {
    val isValid:Boolean
        get() = count>0 && index>=0 && duration>0
    private val span:Long = ceil(duration.toFloat()/count.coerceAtLeast(1)).toLong()

    val range: Range?
        get() = if(isValid && enabled) Range(span*index, min(duration,span*(index+1))) else null
}

class SelectRangeDialog: UtDialogEx() {
    class RangeModeViewModel: UtImmortalViewModel() {
        var naturalDuration: Long = 0L
            private set
        val maxPartCount: Long
            get() = ceil(naturalDuration.toFloat() / MIN_PART_SPAN.inWholeMilliseconds).toLong()

        val enablePartialMode = MutableStateFlow(false)
        val countOfPart = MutableStateFlow<Float>(2f)
        val selectedIndex = MutableStateFlow(1f)

        fun initWith(params:SplitParams) {
            naturalDuration = params.duration
            enablePartialMode.value = params.enabled
            countOfPart.value = max(2, params.count).toFloat()
            selectedIndex.value = params.index.toFloat()+1
        }

        companion object {
            val MIN_PART_SPAN = 2.minutes

            // 1 min
            fun createBy(task: IUtImmortalTask, currentParams: SplitParams): RangeModeViewModel {
                return UtImmortalViewModelHelper.createBy(
                    RangeModeViewModel::class.java,
                    task
                ).apply { initWith(currentParams) }
            }

            fun instanceFor(dlg: SelectRangeDialog): RangeModeViewModel {
                return UtImmortalViewModelHelper.instanceFor(RangeModeViewModel::class.java, dlg)
            }
        }
    }

    lateinit var viewModel: RangeModeViewModel
    lateinit var controls: DialogSelectRangeBinding

    override fun preCreateBodyView() {
        draggable = true
        scrollable = true
        heightOption = HeightOption.AUTO_SCROLL
        setLimitWidth(400)
        gravityOption = GravityOption.CENTER
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.OK)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        viewModel = RangeModeViewModel.instanceFor(this)
        controls = DialogSelectRangeBinding.inflate(inflater.layoutInflater)
        controls.countSlider.valueTo = viewModel.maxPartCount.toFloat()
        controls.countSlider.setLabelFormatter {
            "${it.toInt()}" // -- ${TimeSpan(ceil(viewModel.naturalDuration.toFloat()/it).toLong()).formatAuto()}"
        }
        controls.selectionSlider.setLabelFormatter {
            "${it.toInt()}"
        }

        binder
            .checkBinding(controls.checkEnablePartialMode, viewModel.enablePartialMode)
            .multiEnableBinding(arrayOf(controls.selectionSlider, controls.countSlider), viewModel.enablePartialMode)
            .textBinding(controls.textDurationValue, TimeSpan(viewModel.naturalDuration).formatAuto().asConstantLiveData())
            .textBinding(controls.countAndSpanValue, viewModel.countOfPart.map { "${it.toInt()} -- ${TimeSpan(ceil(viewModel.naturalDuration.toFloat()/it).toLong()).formatAuto()}" })
            .textBinding(controls.selectAtValue, combine(viewModel.selectedIndex, viewModel.countOfPart) { i,m-> "${i.toInt()}/${m.toInt()}" })
            .sliderBinding(controls.selectionSlider, viewModel.selectedIndex, max=viewModel.countOfPart)
            .sliderBinding(controls.countSlider, viewModel.countOfPart)


        return controls.root
    }

    data class Result(val enabled:Boolean, val span:Long, val index:Int)

    companion object {
        suspend fun show(task: UtImmortalTaskBase, currentParams: SplitParams): SplitParams? {
            return task.run {
                val vm = RangeModeViewModel.createBy(this, currentParams)
                if(showDialog(taskName) { SelectRangeDialog() }.status.ok) {
                    SplitParams(vm.enablePartialMode.value, vm.naturalDuration, vm.countOfPart.value.toLong(), vm.selectedIndex.value.toInt()-1)
                } else {
                    null
                }
            }
        }
    }
}
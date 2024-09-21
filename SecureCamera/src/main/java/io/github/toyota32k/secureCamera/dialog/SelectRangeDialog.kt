package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.radioGroupBinding
import io.github.toyota32k.binder.sliderBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTaskBase
import io.github.toyota32k.dialog.task.UtImmortalViewModel
import io.github.toyota32k.dialog.task.UtImmortalViewModelHelper
import io.github.toyota32k.lib.player.model.RangedPlayModel
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogSelectRangeBinding
import io.github.toyota32k.utils.TimeSpan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class SplitParams(
    val enabled:Boolean,
    val duration:Long,
    val span:Long) {
    companion object {
        fun fromModel(model: RangedPlayModel): SplitParams {
            return SplitParams(true, model.duration, model.spanLength)
        }
        fun create(duration: Long): SplitParams {
            return SplitParams(true, duration, 3)
        }
    }
    fun toModel(): RangedPlayModel? {
        return if(enabled) RangedPlayModel(duration, span) else null
    }
}

class SelectRangeDialog: UtDialogEx() {
    class RangeModeViewModel: UtImmortalViewModel() {
        object SpanResolver : IIDValueResolver<Int> {
            override fun id2value(id: Int): Int? {
                return when(id) {
                    R.id.radio_span_3min -> 3
                    R.id.radio_span_5min -> 5
                    R.id.radio_span_10min -> 10
                    else -> 0
                }
            }

            override fun value2id(v: Int): Int {
                return when(v) {
                    3 -> R.id.radio_span_3min
                    5 -> R.id.radio_span_5min
                    10 -> R.id.radio_span_10min
                    else -> R.id.radio_span_custom
                }
            }
            fun isCustom(v:Int):Boolean {
                return value2id(v) == R.id.radio_span_custom
            }
        }

        var naturalDuration: Long = 0L
            private set
        val minSpan = RangedPlayModel.MIN_SPAN_LENGTH/60000
        val maxSpan get() = ((naturalDuration - minSpan)/60000).toInt()
        val enablePartialMode = MutableStateFlow(true)
        val presetSpan = MutableStateFlow(3)
        val customSpan = MutableStateFlow(1f)

        fun initWith(params:SplitParams) {
            naturalDuration = params.duration
            enablePartialMode.value = params.enabled
            val spanInMin = (params.span/60000f).toInt()
            val id = SpanResolver.value2id(spanInMin)
            if(id == R.id.radio_span_custom) {
                customSpan.value = spanInMin.toFloat()
            } else {
                presetSpan.value = spanInMin.coerceIn(minSpan, maxSpan)
            }
        }

        fun toSplitParams(): SplitParams {
            return SplitParams(
                enablePartialMode.value,
                naturalDuration,
                if(SpanResolver.isCustom(presetSpan.value)) customSpan.value.roundToLong()*60000 else presetSpan.value*60000L)
        }

        companion object {
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
        title = "Partial Edit -- ${TimeSpan(viewModel.naturalDuration).formatAuto()}"
        controls = DialogSelectRangeBinding.inflate(inflater.layoutInflater)
        controls.spanSlider.valueFrom = viewModel.minSpan.toFloat()
        controls.spanSlider.valueTo = viewModel.maxSpan.toFloat()
        controls.spanSlider.setLabelFormatter {
            "${it.roundToInt()} min"
        }
        binder
            .checkBinding(controls.checkEnablePartialMode, viewModel.enablePartialMode)
            .multiEnableBinding(arrayOf(controls.radioSpan3min, controls.radioSpan5min, controls.radioSpan10min, controls.radioSpanCustom), viewModel.enablePartialMode)
            .radioGroupBinding(controls.radioSpanSelection, viewModel.presetSpan, RangeModeViewModel.SpanResolver)
            .enableBinding(controls.spanSlider, combine(viewModel.enablePartialMode, viewModel.presetSpan) { e,s-> e && s==0 })
            .textBinding(controls.spanValue, combine(viewModel.presetSpan, viewModel.customSpan) { p,c->
                if(p==0) "${c.roundToLong()} min" else "$p min"
            })
            .sliderBinding(controls.spanSlider, viewModel.customSpan)
        return controls.root
    }

    companion object {
        suspend fun show(task: UtImmortalTaskBase, currentParams: SplitParams): SplitParams? {
            return task.run {
                val vm = RangeModeViewModel.createBy(this, currentParams)
                if(showDialog(taskName) { SelectRangeDialog() }.status.ok) {
                    vm.toSplitParams()
                } else {
                    null
                }
            }
        }
    }
}
package io.github.toyota32k.secureCamera.utils.binding

import android.widget.DatePicker
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.toyota32k.binder.BaseBinding
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.command.ICommand
import io.github.toyota32k.utils.asMutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar
import java.util.Date

// io.github.toyota32k.binder に移行

/**
 * DatePicker が返してくる日付情報を１つにまとめるためのデータクラス
 */
data class DPDate(val intValue:Int) {
    constructor(year:Int, month:Int, day:Int):this(year*10000+month*100+day)
//    constructor(date:Date):this(date.year+1900, date.month, date.day)
    val year get() = intValue/10000
    val month get() = (intValue%10000)/100
    val day get() = intValue%100
    companion object {
        val Today: DPDate
            get() {
                val c = Calendar.getInstance()
                return DPDate(c[Calendar.YEAR], c[Calendar.MONTH], c[Calendar.DAY_OF_MONTH])
            }
        val Invalid: DPDate by lazy { DPDate(0) }
        val InvalidMin: DPDate get() = Invalid
        val InvalidMax: DPDate by lazy { DPDate(Int.MAX_VALUE) }
        fun fromInt(v: Int): DPDate {
            return DPDate(v / 10000, (v % 10000) / 100, v % 100)
        }
    }
    override fun toString(): String {
        return "$year / ${month+1} / $day"
    }


    val isValid:Boolean
        get() = intValue>0 && intValue!=Int.MAX_VALUE
    operator fun compareTo(d:DPDate) : Int {
        return intValue.compareTo(d.intValue)
    }
}

class DatePickerBinding private constructor(
    override val data: LiveData<DPDate>,
    mode: BindingMode
) : BaseBinding<DPDate>(mode) {
    private val datePicker : DatePicker?
        get() = view as? DatePicker

    private var command: ICommand<DPDate>? = null

    /**
     * 日付が選択されたときに実行するコマンド (optional)
     */
    fun setCommand(cmd:ICommand<DPDate>?) {
        command = cmd
    }

    fun connect(owner: LifecycleOwner, view: DatePicker) {
        super.connect(owner,view)
        if(mode!= BindingMode.OneWay) {
            view.setOnDateChangedListener { _, year, monthOfYear, dayOfMonth ->
                mutableData?.apply {
                    val d = DPDate(year,monthOfYear,dayOfMonth)
                    if(value!=d) {
                        value = d
                        command?.invoke(d)
                    }
                }
            }
        }
    }

    override fun onDataChanged(v: DPDate?) {
        v ?: return
        val view = datePicker?:return
        view.updateDate(v.year,v.month,v.day)
    }

    companion object {
        fun create(owner:LifecycleOwner, view: DatePicker, data: MutableLiveData<DPDate>, mode: BindingMode = BindingMode.TwoWay):DatePickerBinding {
            return DatePickerBinding(data,mode).apply { connect(owner,view) }
        }
        fun create(owner:LifecycleOwner, view: DatePicker, data: MutableStateFlow<DPDate>, mode: BindingMode = BindingMode.TwoWay):DatePickerBinding {
            return DatePickerBinding(data.asMutableLiveData(owner),mode).apply { connect(owner,view) }
        }
    }
}

fun Binder.datePickerBinding(owner:LifecycleOwner, view:DatePicker, data:MutableLiveData<DPDate>, mode: BindingMode = BindingMode.TwoWay, selectCommand:ICommand<DPDate>?=null): Binder
        = add(DatePickerBinding.create(owner, view, data, mode).apply { setCommand(selectCommand)})
fun Binder.datePickerBinding(owner:LifecycleOwner, view:DatePicker, data:MutableStateFlow<DPDate>, mode: BindingMode = BindingMode.TwoWay, selectCommand:ICommand<DPDate>?=null): Binder
        = add(DatePickerBinding.create(owner, view, data, mode).apply { setCommand(selectCommand)})
fun Binder.datePickerBinding(view:DatePicker, data:MutableLiveData<DPDate>, mode: BindingMode = BindingMode.TwoWay, selectCommand:ICommand<DPDate>?=null): Binder
        = add(DatePickerBinding.create(requireOwner, view, data, mode).apply { setCommand(selectCommand)})
fun Binder.datePickerBinding(view:DatePicker, data:MutableStateFlow<DPDate>, mode: BindingMode = BindingMode.TwoWay, selectCommand:ICommand<DPDate>?=null): Binder
        = add(DatePickerBinding.create(requireOwner, view, data, mode).apply { setCommand(selectCommand)})

package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.ReliableCommand
import io.github.toyota32k.binder.command.ReliableUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.materialRadioButtonGroupBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.IUtImmortalTaskContext
import io.github.toyota32k.dialog.task.IUtImmortalTaskMutableContextSource
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtImmortalViewModelHelper
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.secureCamera.databinding.DialogItemBinding
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.Mark
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.Rating
import io.github.toyota32k.secureCamera.utils.UtImmortalViewModel
import io.github.toyota32k.utils.ConstantLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ItemDialog : UtDialogEx() {
    class ItemViewModel : UtImmortalViewModel() {
        lateinit var item:ItemEx

        enum class NextAction {
            None,
            EditItem,
        }

        var nextAction = NextAction.None
        val rating = MutableStateFlow(Rating.RatingNone)
        val mark = MutableStateFlow(Mark.None)
        val editCommand = LiteUnitCommand()

        fun initFor(item:ItemEx) {
            this.item = item
            rating.value = item.rating
            mark.value = item.mark
        }

        suspend fun saveIfNeed() {
            if(item.rating!=rating.value || item.mark != mark.value) {
                item = MetaDB.updateMarkRating(item, mark.value, rating.value)
            }
        }
        companion object {
            fun createBy(task:IUtImmortalTask, item:ItemEx):ItemViewModel {
                return UtImmortalViewModelHelper.createBy(ItemViewModel::class.java, task) { it.initFor(item) }
            }
            fun instanceFor(dlg:ItemDialog):ItemViewModel {
                return UtImmortalViewModelHelper.instanceFor(ItemViewModel::class.java, dlg)
            }
        }
    }

    private val viewModel = ItemViewModel.instanceFor(this)
    private lateinit var controls: DialogItemBinding

    override fun preCreateBodyView() {
        draggable = true
        widthOption = WidthOption.COMPACT
        heightOption = HeightOption.COMPACT
        gravityOption = GravityOption.CENTER
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.OK)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogItemBinding.inflate(inflater.layoutInflater)
        binder
            .textBinding(controls.itemName, ConstantLiveData(viewModel.item.name))
            .materialRadioButtonGroupBinding(controls.ratingSelector, viewModel.rating, Rating.idResolver, BindingMode.TwoWay)
            .materialRadioButtonGroupBinding(controls.markSelector, viewModel.mark, Mark.idResolver, BindingMode.TwoWay)
            .bindCommand(viewModel.editCommand, controls.editVideoButton) {
                viewModel.nextAction = ItemViewModel.NextAction.EditItem
                onPositive()
            }
        return controls.root
    }
}
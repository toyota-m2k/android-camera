package io.github.toyota32k.secureCamera.dialog

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.RecyclerViewBinding
import io.github.toyota32k.binder.command.ICommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.LongClickUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.headlessNonnullBinding
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.binder.recyclerViewBindingEx
import io.github.toyota32k.lib.player.common.formatSize
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.ListItemBinding
import io.github.toyota32k.secureCamera.databinding.ListItemExBinding
import io.github.toyota32k.secureCamera.databinding.ViewItemListBinding
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.Mark
import io.github.toyota32k.secureCamera.db.Rating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class ItemListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger = UtLog("ItemListView")
    }
    interface IViewModel {
        val itemList: ObservableList<ItemEx>
        val currentItem: MutableStateFlow<ItemEx?>
        val enableReOrder: Boolean
        val deletionHandler:RecyclerViewBinding.IDeletionHandler<ItemEx>?
        val commandActionOnItem: ICommand<ItemEx>
    }
    interface IMultiSelectionViewModel: IViewModel {
        val selectedItemList: ObservableList<ItemEx>
    }

    val controls = ViewItemListBinding.inflate(LayoutInflater.from(context), this, true)
    lateinit var viewModel: IViewModel

    fun bindViewModel(viewModel: IViewModel, binder: Binder) {
        // アイテム毎にDrawableを作る。
        // １つのDrawableをアイテム間で共用していると、isSelected で tint を変更すると、意図せず、他のアイテムの表示も変わってしまう。
        fun icPhoto() = AppCompatResources.getDrawable(context, R.drawable.ic_type_photo)!!
        fun icVideo() = AppCompatResources.getDrawable(context, R.drawable.ic_type_video)!!
        fun icMarkStar() = AppCompatResources.getDrawable(context, Mark.Star.iconId)!!
        fun icMarkFlag() = AppCompatResources.getDrawable(context, Mark.Flag.iconId)!!
        fun icMarkCheck() = AppCompatResources.getDrawable(context, Mark.Check.iconId)!!
        fun icRating1() = AppCompatResources.getDrawable(context, Rating.Rating1.icon)!!
        fun icRating2() = AppCompatResources.getDrawable(context, Rating.Rating2.icon)!!
        fun icRating3() = AppCompatResources.getDrawable(context, Rating.Rating3.icon)!!
        fun icRating4() = AppCompatResources.getDrawable(context, Rating.Rating4.icon)!!
        fun icCloud() = AppCompatResources.getDrawable(context, R.drawable.ic_cloud)!!
        fun icCloudFull() = AppCompatResources.getDrawable(context, R.drawable.ic_cloud_full)!!

        controls.listView.addItemDecoration(DividerItemDecoration(context, LinearLayoutManager(context).orientation))
        binder.recyclerViewBindingEx<ItemEx,ListItemExBinding>(controls.listView) {
            list(viewModel.itemList)
            if (viewModel.deletionHandler != null) {
                gestureParams(RecyclerViewBinding.GestureParams(viewModel.enableReOrder, true, viewModel.deletionHandler))
                inflater(ListItemExBinding::inflate, null)
                bindView { ctrls, itemBinder, views, item ->
                    val multiViewModel = viewModel as? IMultiSelectionViewModel
                    val isVideo = item.isVideo
                    views.isSelected = false
                    views.tag = item
                    ctrls.textView.text = item.nameForDisplay
                    ctrls.sizeView.text = formatSize(item.size)
                    if (multiViewModel != null) {
                        ctrls.checkBox.isVisible = true
                        ctrls.checkBox.isChecked = multiViewModel.selectedItemList.contains(item)
                    } else {
                        ctrls.checkBox.isVisible = false
                    }
                    if (!isVideo) {
                        ctrls.durationView.visibility = View.GONE
                    } else {
                        ctrls.durationView.text = formatTime(item.duration, item.duration)
                        ctrls.durationView.visibility = View.VISIBLE
                    }
                    ctrls.iconView.setImageDrawable(if (isVideo) icVideo() else icPhoto())
                    val markIcon = when (item.mark) {
                        Mark.None -> null
                        Mark.Star -> icMarkStar()
                        Mark.Flag -> icMarkFlag()
                        Mark.Check -> icMarkCheck()
                    }
                    ctrls.iconMark.setImageDrawable(markIcon)
                    val ratingIcon = when (item.rating) {
                        Rating.RatingNone -> null
                        Rating.Rating1 -> icRating1()
                        Rating.Rating2 -> icRating2()
                        Rating.Rating3 -> icRating3()
                        Rating.Rating4 -> icRating4()
                    }
                    ctrls.iconRating.setImageDrawable(ratingIcon)

                    val cloudIcon = when (item.cloud) {
                        CloudStatus.Local -> null
                        CloudStatus.Uploaded -> icCloud()
                        CloudStatus.Cloud -> icCloudFull()
                    }
                    ctrls.iconCloud.setImageDrawable(cloudIcon)

                    itemBinder
                        .owner(binder.requireOwner)
                        .bindCommand(LiteUnitCommand {
                            if (multiViewModel != null) {
                                ctrls.checkBox.isChecked = !ctrls.checkBox.isChecked
                                if (ctrls.checkBox.isChecked) {
                                    multiViewModel.selectedItemList.add(item)
                                } else {
                                    multiViewModel.selectedItemList.remove(item)
                                }
                            } else {
                                val prev = viewModel.currentItem.value
                                viewModel.currentItem.value = item
                                if (prev == item) {
                                    viewModel.commandActionOnItem.invoke(item)
                                }
                            }
                        }, views)
                        .bindCommand(LongClickUnitCommand {
                            viewModel.currentItem.value = item
                            viewModel.commandActionOnItem.invoke(item)
                        }, views)
                        .headlessNonnullBinding(viewModel.currentItem.map { it?.id == item.id }) { hit ->
                            views.isSelected = hit
                        }
                }
            }
        }
    }

    fun ensureVisible(position:Int) {
        if (position in viewModel.itemList.indices) {
            controls.listView.scrollToPosition(position)
        }
    }
}
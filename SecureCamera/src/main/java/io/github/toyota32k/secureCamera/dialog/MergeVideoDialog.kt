package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.collection.ObjectList
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.binder.RecyclerViewBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.binder.observe
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.lib.media.editor.dialog.VideoPreviewDialog
import io.github.toyota32k.secureCamera.databinding.DialogMergeVideoBinding
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.ScDB
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MergeVideoDialog : UtDialogEx() {
    class ViewModel : UtDialogViewModel() {
        lateinit var metaDb: ScDB
        val targetItems = ObservableList<ItemEx>()
        val targetCount = MutableStateFlow(0)
        val sources = ObservableList<ItemEx>()
        var selectedForever: ItemEx? = null
        inner class SrcViewModel(): ItemListView.IMultiSelectionViewModel {
            override val itemList = sources
            override val currentItem = MutableStateFlow<ItemEx?>(null)
            override val enableReOrder: Boolean = true
            override val deletionHandler: RecyclerViewBinding.IDeletionHandler<ItemEx>? = null
            override val commandActionOnItem = null
            override val hasPreviewButton: Boolean = false
            override val noSelectionHighlights = true
            override val selectedForever: ItemEx? get() = this@ViewModel.selectedForever
            override val selectedItemList get() = targetItems
        }

        inner class DstViewModel(): ItemListView.IViewModel {
            override val itemList = targetItems
            override val currentItem = MutableStateFlow<ItemEx?>(null)
            override val enableReOrder: Boolean = true
            override val deletionHandler = DstDeletionHandler()
            override val commandActionOnItem = LiteCommand<ItemEx> { item->
                UtImmortalTask.launchTask {
                    VideoPreviewDialog.show(metaDb.urlOf(item), item.name, item.chapterList)
                }
            }
            override val hasPreviewButton: Boolean = true
            override val noSelectionHighlights: Boolean = false
        }

        inner class DstDeletionHandler : RecyclerViewBinding.IDeletionHandler<ItemEx> {
            override fun canDelete(item: ItemEx): Boolean {
                return item != selectedForever
            }

            override fun delete(item: ItemEx): RecyclerViewBinding.IDeletion {
                val index = sources.indexOf(item)
                if (index >= 0) {
                    sources[index] = item
                }
                return object : RecyclerViewBinding.IDeletion {
                    override fun commit() {
                    }
                }
            }
        }

        val srcViewModel: ItemListView.IMultiSelectionViewModel = SrcViewModel()
        val dstViewModel: ItemListView.IViewModel = DstViewModel()

        init {
            targetItems.addListenerForever { event->
                targetCount.value = targetItems.size
                if (event is ObservableList.InsertEventData && event.position in event.list.indices) {
                    val item = event.list[event.position]
                    dstViewModel.currentItem.value = item
                }
            }
        }
    }

    override fun preCreateBodyView() {
        draggable = false
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
        gravityOption = GravityOption.CENTER
        noHeader = true
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
    }

    val viewModel by lazy { getViewModel<ViewModel>() }
    lateinit var controls : DialogMergeVideoBinding
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogMergeVideoBinding.inflate(inflater.layoutInflater)
        binder.owner(requireActivity())
        controls.sourceList.bindViewModel(viewModel.srcViewModel, binder)
        controls.destinationList.bindViewModel(viewModel.dstViewModel, binder)
        binder.dialogRightButtonEnable(viewModel.targetCount.map { it>1 })
        return controls.root
    }

    companion object {
        suspend fun show(db:ScDB, target:ItemEx, videoList:List<ItemEx>): List<ItemEx>? {
            return UtImmortalTask.awaitTaskResultCatching<List<ItemEx>?>(this::class.java.name, null) {
                val vm = createViewModel<ViewModel> {
                    sources.replace(videoList)
                    selectedForever = target
                    targetItems.replace(listOf(target))
                    metaDb = db
                }
                if (showDialog(taskName) { MergeVideoDialog() }.status.ok) {
                    vm.targetItems.toList()
                } else null
            }
        }
    }
}
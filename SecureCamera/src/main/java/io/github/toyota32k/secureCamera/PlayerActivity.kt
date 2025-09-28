package io.github.toyota32k.secureCamera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.DPDate
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.RecyclerViewBinding
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.combinatorialVisibilityBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.LongClickUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.genericBinding
import io.github.toyota32k.binder.headlessBinding
import io.github.toyota32k.binder.headlessNonnullBinding
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.binder.materialRadioButtonGroupBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.recyclerViewBindingEx
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.mortal.UtMortalActivity
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTaskBase
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getActivity
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.formatSize
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMediaFeed
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.Rotation
import io.github.toyota32k.lib.player.model.chapter.ChapterList
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.ScDef.PHOTO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.PHOTO_PREFIX
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.databinding.ActivityPlayerBinding
import io.github.toyota32k.secureCamera.databinding.ListItemBinding
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.DBChange
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.Mark
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.MetaData
import io.github.toyota32k.secureCamera.db.Rating
import io.github.toyota32k.secureCamera.db.ScDB
import io.github.toyota32k.secureCamera.dialog.CropImageDialog
import io.github.toyota32k.secureCamera.dialog.ItemDialog
import io.github.toyota32k.secureCamera.dialog.MaskCoreParams
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.dialog.PlayListSettingDialog
import io.github.toyota32k.secureCamera.dialog.SnapshotDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotSettings
import io.github.toyota32k.secureCamera.utils.setSecureMode
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtSorter
import io.github.toyota32k.utils.android.CompatBackKeyDispatcher
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.hideActionBar
import io.github.toyota32k.utils.android.hideStatusBar
import io.github.toyota32k.utils.gesture.Direction
import io.github.toyota32k.utils.gesture.IUtManipulationTarget
import io.github.toyota32k.utils.gesture.Orientation
import io.github.toyota32k.utils.gesture.UtGestureInterpreter
import io.github.toyota32k.utils.gesture.UtManipulationAgent
import io.github.toyota32k.utils.lifecycle.disposableObserve
import io.github.toyota32k.utils.onTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.EnumSet
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min


class PlayerActivity : UtMortalActivity() {
    override val logger = UtLog("PlayerActivity")
    enum class ListMode(val resId:Int) {
        ALL(R.id.radio_all),
        PHOTO(R.id.radio_photos),
        VIDEO(R.id.radio_videos)
        ;
        object IDResolver: IIDValueResolver<ListMode> {
            override fun id2value(id:Int) : ListMode {
                return valueOf(id)
            }
            override fun value2id(v: ListMode): Int {
                return v.resId
            }
        }
        companion object {
            fun valueOf(resId: Int, def: ListMode = PHOTO): ListMode {
                return entries.find { it.resId == resId } ?: def
            }
            fun valueOf(name:String?):ListMode? {
                if(name==null) return null
                return try {
                    java.lang.Enum.valueOf(ListMode::class.java, name)
                } catch (_:Throwable) {
                    null
                }
            }
        }

    }

    class PlayerViewModel(application: Application): AndroidViewModel(application) {
        companion object {
            const val KEY_CURRENT_LIST_MODE = "ListMode"
            const val KEY_CURRENT_ITEM = "CurrentItem"
            var maskParams: MaskCoreParams? = null

            suspend fun saveSnapshot(db:ScDB, item: MetaData, pos:Long, snapshot: Bitmap):MetaData? {
                val bitmap = SnapshotDialog.showBitmap(snapshot, maskParams = maskParams)?.let {
                    maskParams = it.maskParams
                    it.bitmap
                } ?: return null
                return withContext(Dispatchers.IO) {
                    var file:File? = null
                    try {
                        val orgDate = item.date
                        val date = Date(orgDate + pos)
                        val filename = ITcUseCase.defaultFileName(PHOTO_PREFIX, PHOTO_EXTENSION, date)
                        file = File(db.filesDir, filename)
                        file.outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                            it.flush()
                        }
                        db.register(filename)
                    } catch(e:Throwable) {
                        TpLib.logger.error(e)
                        if(file!=null) {
                            try { file.delete() } catch (_:Throwable) {}
                        }
                        null
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
        val metaDb = MetaDB[SlotSettings.currentSlotIndex]

//        private val context: Application
//            get() = getApplication()
        val playlist = Playlist()
        val playerControllerModel = PlayerControllerModel.Builder(application, viewModelScope)
            .supportFullscreen()
            .supportPlaylist(playlist,autoPlay = false,continuousPlay = false)
            .supportSnapshot(::onSnapshot)
            .supportChapter(hideChapterViewIfEmpty = true)
            .enableRotateRight()
            .enableRotateLeft()
            .enableSeekMedium(Settings.Player.spanOfSkipBackward, Settings.Player.spanOfSkipForward)
            .snapshotSource(PlayerControllerModel.SnapshotSource.CAPTURE_PLAYER, selectable = true)
            .build()

        val fullscreenCommand = LiteCommand<Boolean> {
            playerControllerModel.setWindowMode( if(it) PlayerControllerModel.WindowMode.FULLSCREEN else PlayerControllerModel.WindowMode.NORMAL )
        }

        val rotateCommand = LiteCommand<Rotation>(playlist::rotateBitmap)
        val saveBitmapCommand = LiteUnitCommand(playlist::saveBitmap)
        val undoBitmapCommand = LiteUnitCommand(playlist::reloadBitmap)
//        val cropBitmapCommand = LiteUnitCommand()
        val ensureVisibleCommand = LiteUnitCommand()
        val playlistSettingCommand = LiteUnitCommand {
            viewModelScope.launch {
                data class MinMax(var min:DPDate,var max:DPDate)
                val list = metaDb.listEx(ListMode.ALL)
                val mm = list.fold(MinMax(DPDate.InvalidMax, DPDate.InvalidMin)) { acc, item->
                    val dp = item.dpDate
                    if(dp<acc.min) {
                        acc.min = dp
                    }
                    if(dp>acc.max) {
                        acc.max = dp
                    }
                    acc
                }
                if(PlayListSettingDialog.show(mm.min, mm.max)) {
                    playlist.onSettingChanged()
                }
                allowDelete.value = Settings.PlayListSetting.allowDelete
            }
        }

        val blockingAt = MutableStateFlow(System.currentTimeMillis())              // 画面ロックした時刻
        val playingBeforeBlocked = MutableStateFlow(false)  // 画面ロックされる前に再生中だった --> unlock時に再生を再開する。
        val allowDelete = MutableStateFlow(Settings.PlayListSetting.allowDelete)

        class DateRange {
            private var minDate:DPDate = DPDate.Invalid
            private var maxDate:DPDate = DPDate.Invalid
            init {
                updateBySetting()
            }
            fun updateBySetting() {
                val min = if(Settings.PlayListSetting.enableStartDate) Settings.PlayListSetting.startDate else DPDate.Invalid
                val max = if(Settings.PlayListSetting.enableEndDate) Settings.PlayListSetting.endDate else DPDate.Invalid
                setRange(min,max)
            }
            private fun setRange(min:DPDate, max:DPDate) {
                if (min.isValid && max.isValid && min > max) {
                    minDate = max
                    maxDate = min
                } else {
                    minDate = min
                    maxDate = max
                }
            }

            fun contained(date:DPDate):Boolean {
                if(minDate.isValid) {
                    if(date<minDate) return false
                }
                if(maxDate.isValid) {
                    if(date>maxDate) return false
                }
                return true
            }
        }

        inner class VideoSource(val item: ItemEx) : IMediaSourceWithChapter {
            //private val file: File = item.file
            override val name:String
                get() = item.name
            override val id: String
                get() = name
            override val uri: String
                get() = metaDb.urlOf(item)
            override val trimming: Range = Range.empty
            override val type: String
                get() = name.substringAfterLast(".", "")
            override var startPosition = AtomicLong()
//            override val chapterList: IChapterList
//                = if(item.chapterList!=null) ChapterList(item.chapterList.toMutableList()) else IChapterList.Empty

            override suspend fun getChapterList(): IChapterList {
                return if(item.chapterList!=null) ChapterList(item.chapterList.toMutableList()) else IChapterList.Empty
            }
        }
        inner class Playlist : IMediaFeed, IUtPropOwner {
            private var sortOrder:Int = 1
            private fun updateSortOrderBySettings() {
                sortOrder = if(Settings.PlayListSetting.sortOrder) -1 else 1
            }
            val collection = ObservableList<ItemEx>()
            val sorter = UtSorter(
                collection,
                actionOnDuplicate = UtSorter.ActionOnDuplicate.REPLACE
            ) { a, b ->
                val ta = a.creationDate // filename2date(a)?.time ?: 0L
                val tb = b.creationDate // filename2date(b)?.time ?: 0L
                val d = ta - tb
                sortOrder * (if(d<0) -1 else if(d>0) 1 else 0)
            }
            val photoBitmap: StateFlow<Bitmap?> = MutableStateFlow(null)

            fun setCroppedBitmap(bitmap: Bitmap) {
                photoBitmap.mutable.value = bitmap
                photoCropped.mutable.value = true
            }

            val currentSelection:StateFlow<ItemEx?> = MutableStateFlow<ItemEx?>(null)
            var photoSelection:ItemEx? = null
            var videoSelection:ItemEx? = null
            val isVideo: StateFlow<Boolean> = currentSelection.map { it?.isVideo==true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
            val isPhoto: StateFlow<Boolean> = currentSelection.map { it?.isPhoto==true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
            val listMode = MutableStateFlow(ListMode.ALL)

            override val currentSource = MutableStateFlow<IMediaSource?>(null)
            override val hasNext = MutableStateFlow(false)
            override val hasPrevious = MutableStateFlow(false)

            val photoRotation : StateFlow<Int> = MutableStateFlow(0)
            val photoCropped : StateFlow<Boolean> = MutableStateFlow(false)

            var dateRange = DateRange()
            private fun List<ItemEx>.filterByDateRange():List<ItemEx> {
                return this.filter { dateRange.contained(it.dpDate) }
            }

            suspend fun onSettingChanged() {
                updateSortOrderBySettings()
                dateRange.updateBySetting()
                refreshList()
            }


            init {
                updateSortOrderBySettings()
                listMode.onEach(::setListMode).launchIn(viewModelScope)
            }

            private fun resetPhoto(bitmap: Bitmap?=null) {
                photoRotation.mutable.value = 0
                photoBitmap.mutable.value = bitmap
                photoCropped.mutable.value = false
            }
            private fun loadPhotoFromItem(item:ItemEx?) {
                if(item==null) {
                    resetPhoto()
                    return
                }
                if(item.cloud.loadFromCloud) {
                    resetPhoto()
                    viewModelScope.launch {
                        photoBitmap.mutable.value = TcClient.getPhoto(metaDb, item)
                    }
                } else {
                    resetPhoto(BitmapFactory.decodeFile(metaDb.fileOf(item).path))
                }
            }


            fun select(item_:ItemEx?, force:Boolean=true) {
                if(!force && item_ == currentSelection.value) return
                if(collection.isEmpty()) {
                    hasNext.value = false
                    hasPrevious.value = false
                    currentSource.value = null
                    currentSelection.mutable.value = null
                    resetPhoto()
                    return
                }

                if(item_==null) {
                    currentSource.value = null
                    currentSelection.mutable.value = null
                    resetPhoto()
                    return
                }

                val index = collection.indexOfFirst{ it.id == item_.id }
                if(index<0) {
                    // 要求されたアイテムが存在しない
                    select(null, true)
                    return
                }

                val item = collection[index]
                currentSelection.mutable.value = item
                if(item.isVideo) {
                    videoSelection = item
                    resetPhoto()
                    playerControllerModel.playerModel.rotate(Rotation.NONE)
                    if(item.cloud.loadFromCloud) {
                        currentSource.value = null
                        viewModelScope.launch {
                            if(Authentication.authenticateAndMessage()) {
                                currentSource.value = VideoSource(item)
                            }
                        }
                    } else {
                        currentSource.value = VideoSource(item)
                    }
                } else {
                    photoSelection = item
                    currentSource.value = null
                    loadPhotoFromItem(item)
                }
                hasPrevious.mutable.value = index>0
                hasNext.mutable.value = index<collection.size-1
                ensureVisibleCommand.invoke()
            }

            fun currentIndex():Int {
                val item = currentSelection.value ?: return -1
                return collection.indexOf(item)
            }

            suspend fun refreshList() {
                setListMode(listMode.value)
            }

            fun addItem(item:ItemEx) {
                if(when(listMode.value){
                    ListMode.ALL-> true
                    ListMode.PHOTO->item.isPhoto
                    ListMode.VIDEO->item.isVideo
                }) {
                    sorter.add(item)
                    select(item)
                }
            }

            fun removeItem(item:ItemEx) {
                val index = collection.indexOfFirst { it.id == item.id }
                if(index>=0) {
                    collection.removeAt(index)
                }
            }

            fun replaceItem(item:ItemEx) {
                val index = collection.indexOfFirst { it.id == item.id }
                if(index>=0) {
                    sorter.add(item)
                    select(item)
                }
            }

            private suspend fun setListMode(mode:ListMode) {
                val newList = metaDb.listEx(mode).filterByDateRange().run {
                    if(Settings.PlayListSetting.onlyUnBackedUpItems) {
                        filter { !it.cloud.isFileInCloud }
                    } else {
                        this
                    }
                }
                setFileList(newList, mode)
            }

            private fun setFileList(list:Collection<ItemEx>, newMode:ListMode) {
                val current = currentSource.value as VideoSource?
                sorter.replace(list)
//                listMode.value = newMode
                when(newMode) {
                    ListMode.ALL->  select(current?.item)
                    ListMode.VIDEO -> select(videoSelection)
                    ListMode.PHOTO -> select(photoSelection)
                }
            }

            override fun next() {
                if(collection.isEmpty()) return
                val current = currentSelection.value
                if(current==null) {
                    select(collection[0])
                } else {
                    val index = collection.indexOf(current) + 1
                    if(0<=index && index<collection.size) {
                        select(collection[index])
                    }
                }
            }

            override fun previous() {
                if(collection.isEmpty()) return
                val current = currentSelection.value
                if(current==null) {
                    select(collection[collection.size-1])
                } else {
                    val index = collection.indexOf(current) - 1
                    if(0<=index && index<collection.size) {
                        select(collection[index])
                    }
                }
            }

            fun rotateBitmap(rotation: Rotation) {
                playlist.photoBitmap.mutable.value = playlist.photoBitmap.value?.run {
                    if(rotation!=Rotation.NONE) {
                        photoRotation.mutable.value = Rotation.normalize(photoRotation.mutable.value + rotation.degree)
                        Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(rotation.degree.toFloat()) }, true)
                    } else this
                } ?: return
            }

            fun saveBitmap() {
                CoroutineScope(Dispatchers.IO).launch {
                    val item = currentSelection.value ?: return@launch
                    val bitmap = photoBitmap.value ?: return@launch
                    val file = metaDb.fileOf(item)
                    file.outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                        it.flush()
                    }
                    photoRotation.mutable.value = 0
                    photoCropped.mutable.value = false
                    metaDb.updateFile(item,null)
                }
            }

            // 回転/トリミングを無効化して元に戻す
            fun reloadBitmap() {
                loadPhotoFromItem(currentSelection.value)
            }
        }

        init {
            viewModelScope.launch {
                val mode = ListMode.valueOf(metaDb.KV.get(KEY_CURRENT_LIST_MODE))
                if(mode!=null) {
                    playlist.listMode.value = mode
                }
                val name = metaDb.KV.get(KEY_CURRENT_ITEM) ?: return@launch
                val item = metaDb.itemExOf(name) ?: return@launch
                playlist.select(item)
            }
        }

        val currentIndex:Int
            get() = playlist.currentIndex()

        private fun onSnapshot(pos:Long, bitmap: Bitmap) {
            CoroutineScope(Dispatchers.IO).launch {
                val source = playlist.currentSelection.value ?: return@launch
                val newItem = saveSnapshot(metaDb, source.data, pos, bitmap) ?: return@launch
                if (playlist.listMode.value != ListMode.VIDEO) {
                    withContext(Dispatchers.Main) { playlist.sorter.add(ItemEx(newItem,metaDb.slotIndex.index,null)) }
                }
            }
        }

        fun saveListModeAndSelection() {
            val listMode = playlist.listMode.value
            val currentItem = playlist.currentSelection.value

            // onCleared で metaDb.close() されるので、MetaDB を新たに取得しておく
            val db = MetaDB[SlotSettings.currentSlotIndex]
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    metaDb.KV.put(KEY_CURRENT_LIST_MODE, listMode.toString())
                    if (currentItem != null) {
                        metaDb.KV.put(KEY_CURRENT_ITEM, currentItem.name)
                    }
                } finally {
                    db.close()
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            saveListModeAndSelection()
            metaDb.close()
            playerControllerModel.close()
        }
    }

    private val editorActivityBroker = EditorActivity.Broker(this)
    private val viewModel by viewModels<PlayerViewModel>()
    lateinit var controls: ActivityPlayerBinding
    val binder = Binder()

    // Manipulation Handling
    private val gestureInterpreter: UtGestureInterpreter = UtGestureInterpreter(SCApplication.instance, enableScaleEvent = true)
    private val manipulator: ManipulationTarget by lazy { ManipulationTarget() }

    private val compatBackKeyDispatcher = CompatBackKeyDispatcher()
    override fun onCreate(savedInstanceState: Bundle?) {
        Settings.Design.applyToActivity(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Settings.initialize(application)

//        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//        setTheme(R.style.Theme_TryCamera_M3_DynamicColor_NoActionBar)
//        setTheme(R.style.Theme_TryCamera_M3_Cherry_NoActionBar)

        controls = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(controls.root)
        setupWindowInsetsListener(controls.player, UtDialogConfig.SystemZone.SYSTEM_BARS)
        hideActionBar()
        hideStatusBar()

        // アイテム毎にDrawableを作る。
        // １つのDrawableをアイテム間で共用していると、isSelected で tint を変更すると、意図せず、他のアイテムの表示も変わってしまう。
        fun icPhoto() = AppCompatResources.getDrawable(this, R.drawable.ic_type_photo)!!
        fun icVideo() = AppCompatResources.getDrawable(this, R.drawable.ic_type_video)!!
        fun icMarkStar() = AppCompatResources.getDrawable(this, Mark.Star.iconId)!!
        fun icMarkFlag() = AppCompatResources.getDrawable(this, Mark.Flag.iconId)!!
        fun icMarkCheck() = AppCompatResources.getDrawable(this, Mark.Check.iconId)!!
        fun icRating1() = AppCompatResources.getDrawable(this, Rating.Rating1.icon)!!
        fun icRating2() = AppCompatResources.getDrawable(this, Rating.Rating2.icon)!!
        fun icRating3() = AppCompatResources.getDrawable(this, Rating.Rating3.icon)!!
        fun icRating4() = AppCompatResources.getDrawable(this, Rating.Rating4.icon)!!
        fun icCloud() = AppCompatResources.getDrawable(this, R.drawable.ic_cloud)!!
        fun icCloudFull() = AppCompatResources.getDrawable(this, R.drawable.ic_cloud_full)!!

        controls.listView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager(this).getOrientation()))
        binder.owner(this)
            .materialRadioButtonGroupBinding(controls.listMode, viewModel.playlist.listMode, ListMode.IDResolver)
            .visibilityBinding(controls.videoViewer, viewModel.playlist.isVideo)
            .visibilityBinding(controls.photoViewer, viewModel.playlist.isPhoto)
            .combinatorialVisibilityBinding(viewModel.playerControllerModel.windowMode.map { it==PlayerControllerModel.WindowMode.FULLSCREEN}) {
                straightGone(controls.collapseButton)
                inverseGone(controls.expandButton)
            }
            .visibilityBinding(controls.photoButtonPanel, viewModel.playerControllerModel.showControlPanel, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .multiVisibilityBinding(arrayOf(controls.photoSaveButton, controls.photoUndoButton), combine(viewModel.playlist.photoRotation, viewModel.playlist.photoCropped) { r,c -> r!=0 || c }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.safeGuard, viewModel.blockingAt.map { it>0 }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .bindCommand(viewModel.fullscreenCommand, controls.expandButton, true)
            .bindCommand(viewModel.fullscreenCommand, controls.collapseButton, false)
            .bindCommand(viewModel.rotateCommand, Pair(controls.imageRotateLeftButton,Rotation.LEFT), Pair(controls.imageRotateRightButton, Rotation.RIGHT)) // { onRotate(it) }
            .bindCommand(viewModel.saveBitmapCommand, controls.photoSaveButton)
            .bindCommand(viewModel.undoBitmapCommand, controls.photoUndoButton)
            .clickBinding(controls.photoCropButton, ::cropBitmap)
//            .longClickBinding(controls.photoCropButton, ::applyPreviousCropParams)
            .bindCommand(viewModel.ensureVisibleCommand,this::ensureVisible)
            .bindCommand(viewModel.playlistSettingCommand, controls.listSettingButton)
            .genericBinding(controls.imageView,viewModel.playlist.photoBitmap) { view, bitmap->
                view.setImageBitmap(bitmap)
            }
            .headlessBinding(viewModel.playlist.currentSelection) {
                manipulator.agent.resetScrollAndScale()
            }
            .headlessBinding(viewModel.playerControllerModel.playerModel.rotation) {
                manipulator.agent.resetScroll()
            }
            .headlessBinding(viewModel.playlist.photoRotation) {
                manipulator.agent.resetScroll()
            }
            .add {
                viewModel.playerControllerModel.windowMode.disposableObserve(this, ::onWindowModeChanged)
            }
            .recyclerViewBindingEx(controls.listView) {
                list(viewModel.playlist.collection)
                gestureParams(viewModel.allowDelete.map { RecyclerViewBinding.GestureParams(false,it,::onDeletingItem)})
                inflate { ListItemBinding.inflate(layoutInflater, it, false) }
                bindView { ctrls, itemBinder, views, item->
                    val isVideo = item.isVideo
                    views.isSelected = false
                    views.tag = item
                    ctrls.textView.text = item.nameForDisplay
                    ctrls.sizeView.text = formatSize(item.size)
                    if(!isVideo) {
                        ctrls.durationView.visibility = View.GONE
                    } else {
                        ctrls.durationView.text = formatTime(item.duration,item.duration)
                        ctrls.durationView.visibility = View.VISIBLE
                    }
                    ctrls.iconView.setImageDrawable(if(isVideo) icVideo() else icPhoto())
                    val markIcon = when(item.mark) {
                        Mark.None -> null
                        Mark.Star -> icMarkStar()
                        Mark.Flag -> icMarkFlag()
                        Mark.Check -> icMarkCheck()
                    }
                    ctrls.iconMark.setImageDrawable(markIcon)
                    val ratingIcon = when(item.rating) {
                        Rating.RatingNone -> null
                        Rating.Rating1 -> icRating1()
                        Rating.Rating2 -> icRating2()
                        Rating.Rating3 -> icRating3()
                        Rating.Rating4 -> icRating4()
                    }
                    ctrls.iconRating.setImageDrawable(ratingIcon)

                    val cloudIcon = when(item.cloud) {
                        CloudStatus.Local-> null
                        CloudStatus.Uploaded->icCloud()
                        CloudStatus.Cloud->icCloudFull()
                    }
                    ctrls.iconCloud.setImageDrawable(cloudIcon)

                    itemBinder
                        .owner(this@PlayerActivity)
                        .bindCommand(LiteUnitCommand {
                            if(item==viewModel.playlist.currentSelection.value) {
                                startEditing(item)
                            } else {
                                viewModel.playlist.select(item, false)
                            }
                        }, views)
                        .bindCommand(LongClickUnitCommand {
                            viewModel.playlist.select(item, false)
                            startEditing(item)
                        }, views )
                        .headlessNonnullBinding(viewModel.playlist.currentSelection.map { it?.id == item.id }) { hit->
                            views.isSelected = hit
                        }

                }
            }

        controls.videoViewer.bindViewModel(viewModel.playerControllerModel, binder)

        gestureInterpreter.setup(this, controls.viewerArea) {
            onScroll(manipulator::onScroll)
            onScale(manipulator::onScale)
            onTap(manipulator::onTap)
            onDoubleTap(manipulator::onDoubleTap)
            onLongTap { _ -> window.setSecureMode() }
            onFlickVertical(manipulator::onFlick)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON  // スリープしない
            or WindowManager.LayoutParams.FLAG_SECURE  // キャプチャーを禁止（タスクマネージャで見えないようにする）
        )

        ensureVisible()

        DBChange.observable.onEach(::onDataChanged).launchIn(lifecycleScope)

        // Back Key の動作をカスタマイズ
        compatBackKeyDispatcher.register(this) {
            if (viewModel.playerControllerModel.windowMode.value == PlayerControllerModel.WindowMode.FULLSCREEN) {
                viewModel.playerControllerModel.setWindowMode(PlayerControllerModel.WindowMode.NORMAL)
            } else {
                finish()
            }
        }
    }

    /**
     * 前回のトリミング情報（scale/translation）を復元して、トリミングを実行する
     */
//    private fun applyPreviousCropParams(v: View): Boolean {
//        viewModel.cropParams?.apply {
//            controls.imageView.scaleX = scale
//            controls.imageView.scaleY = scale
//            controls.imageView.translationX = tx
//            controls.imageView.translationY = ty
//        } ?: return false
//        cropBitmap(v)
//        return true
//    }

    private fun getMaskParams(sourceWidth:Int, sourceHeight:Int): MaskCoreParams? {
//        val bitmap = viewModel.playlist.photoBitmap.value ?: return null

        val scale = controls.imageView.scaleX               // x,y 方向のscaleは同じ
        if (scale == 1f) return PlayerViewModel.maskParams

        val rtx = controls.imageView.translationX
        val rty = controls.imageView.translationY
        if (rtx==0f && rty==0f) return PlayerViewModel.maskParams

        val tx = rtx / scale
        val ty = rty / scale

        val bw = sourceWidth
        val bh = sourceHeight
        val vw = controls.imageView.width                   // imageView のサイズ
        val vh = controls.imageView.height
        val fitter = UtFitter(FitMode.Inside, vw, vh)
        fitter.fit(bw, bh)
        val iw = fitter.resultWidth                         // imageView内での bitmapの表示サイズ
        val ih = fitter.resultHeight
        val mx = (vw-iw)/2                                  // imageView と bitmap のマージン
        val my = (vh-ih)/2

        // scale: 画面中央をピボットとする拡大率
        // translation：中心座標の移動距離 x scale
        val sw = vw / scale                                 // scaleを補正した表示サイズ
        val sh = vh / scale
        val cx = vw/2f - tx                                 // 現在表示されている画面の中央の座標（scale前の元の座標系）
        val cy = vh/2f - ty
        val sx = max(cx - sw/2 - mx, 0f)              // 表示されている画像の座標（表示画像内の座標系）
        val sy = max(cy - sh/2 - my, 0f)
        val ex = min(cx + sw/2 - mx, iw)
        val ey = min(cy + sh/2 - my, ih)

        val bs = bw.toFloat()/iw                            // 画像の拡大率を補正して、元画像座標系に変換
        val x = sx * bs
        val y = sy * bs
        val w = (ex - sx) * bs
        val h = (ey - sy) * bs

        return MaskCoreParams.fromSize(bw, bh, x, y, w, h)

//        val newBitmap = Bitmap.createBitmap(bitmap, x.roundToInt(), y.roundToInt(), w.roundToInt(), h.roundToInt())
//        viewModel.playlist.setCroppedBitmap(newBitmap)
//        manipulator.agent.resetScrollAndScale()
    }

    /**
     * 表示中のビットマップを可視範囲でトリミングする
     */
    private fun cropBitmap(@Suppress("UNUSED_PARAMETER") v:View) {
        val bitmap = viewModel.playlist.photoBitmap.value ?: return
        UtImmortalTask.launchTask("cropBitmap") {
            val cropped = CropImageDialog.cropBitmap(bitmap, getMaskParams(bitmap.width,bitmap.height))
            if (cropped != null) {
                PlayerViewModel.maskParams = cropped.maskParams
                viewModel.playlist.setCroppedBitmap(cropped.bitmap)
                manipulator.agent.resetScrollAndScale()
            }
        }
        return

        // ずいぶん苦労して実装したので、削除してしまうのが忍びないｗｗｗ
//        val scale = controls.imageView.scaleX               // x,y 方向のscaleは同じ
//        val rtx = controls.imageView.translationX
//        val rty = controls.imageView.translationY
//        if (scale ==1f && rtx==0f && rty==0f) return
//        viewModel.cropParams = PlayerViewModel.CropParams(rtx, rty, scale)
//        val tx = rtx / scale
//        val ty = rty / scale
//
//        val bitmap = viewModel.playlist.photoBitmap.value ?: return
//        val bw = bitmap.width                               // bitmap のサイズ
//        val bh = bitmap.height
//        val vw = controls.imageView.width                   // imageView のサイズ
//        val vh = controls.imageView.height
//        val fitter = UtFitter(FitMode.Inside, vw, vh)
//        fitter.fit(bw, bh)
//        val iw = fitter.resultWidth                         // imageView内での bitmapの表示サイズ
//        val ih = fitter.resultHeight
//        val mx = (vw-iw)/2                                  // imageView と bitmap のマージン
//        val my = (vh-ih)/2
//
//        // scale: 画面中央をピボットとする拡大率
//        // translation：中心座標の移動距離 x scale
//        val sw = vw / scale                                 // scaleを補正した表示サイズ
//        val sh = vh / scale
//        val cx = vw/2f - tx                                 // 現在表示されている画面の中央の座標（scale前の元の座標系）
//        val cy = vh/2f - ty
//        val sx = max(cx - sw/2 - mx, 0f)              // 表示されている画像の座標（表示画像内の座標系）
//        val sy = max(cy - sh/2 - my, 0f)
//        val ex = min(cx + sw/2 - mx, iw)
//        val ey = min(cy + sh/2 - my, ih)
//
//        val bs = bw.toFloat()/iw                            // 画像の拡大率を補正して、元画像座標系に変換
//        val x = sx * bs
//        val y = sy * bs
//        val w = (ex - sx) * bs
//        val h = (ey - sy) * bs
//
//        val newBitmap = Bitmap.createBitmap(bitmap, x.roundToInt(), y.roundToInt(), w.roundToInt(), h.roundToInt())
//        viewModel.playlist.setCroppedBitmap(newBitmap)
//        manipulator.agent.resetScrollAndScale()
    }


    private suspend fun onDataChanged(c:DBChange) {
        when(c.type) {
            DBChange.Type.Add -> {
                val item = viewModel.metaDb.itemExAt(c.itemId) ?: return
                viewModel.playlist.addItem(item)
            }
            DBChange.Type.Update -> {
                val item = viewModel.metaDb.itemExAt(c.itemId) ?: return
                viewModel.playlist.replaceItem(item)
            }
            DBChange.Type.Delete -> {
                val item = viewModel.metaDb.itemExAt(c.itemId) ?: return
                viewModel.playlist.removeItem(item)
            }
            DBChange.Type.Refresh -> {
                viewModel.playlist.refreshList()
            }
        }
    }

    private fun ensureVisible() {
        val index = viewModel.currentIndex
        if(index>=0) {
            controls.listView.scrollToPosition(index)
        }
    }

    private fun sizeInKb(size: Long): String {
        return String.format(Locale.US, "%,d KB", size / 1000L)
    }

    private suspend fun ensureSelectItem(name:String, update:Boolean=false) {
        val item = viewModel.metaDb.itemExOf(name) ?: return
        if(update) {
            viewModel.playlist.replaceItem(item)
        }
        viewModel.playlist.select(item)
    }

    private fun startEditing(item:ItemEx) {
        UtImmortalTask.launchTask("dealItem") {
            viewModel.playerControllerModel.commandPause.invoke()
            val vm = createViewModel<ItemDialog.ItemViewModel> { initFor(item) }
            if(showDialog(taskName) { ItemDialog() }.status.ok) {
                vm.saveIfNeed()
                val item2 = vm.item.value
                when(vm.nextAction) {
                    ItemDialog.ItemViewModel.NextAction.EditItem -> editItem(item2)
                    ItemDialog.ItemViewModel.NextAction.PurgeLocal -> viewModel.metaDb.purgeLocalFile(item2)
                    ItemDialog.ItemViewModel.NextAction.RestoreLocal -> {
                        if(viewModel.playlist.isVideo.value) {
                            viewModel.playlist.select(null,true)
                        }
                        viewModel.metaDb.restoreFromCloud(item2)
                    }
                    ItemDialog.ItemViewModel.NextAction.Repair -> {
                        viewModel.metaDb.recoverFromCloud(item2)
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun UtImmortalTaskBase.editItem(item:ItemEx) {
        viewModel.saveListModeAndSelection()
        viewModel.playlist.select(null)
        viewModel.playerControllerModel.playerModel.killPlayer()
        controls.videoViewer.dissociatePlayer()
        val name = editorActivityBroker.invoke(item.name)
        (getActivity() as? PlayerActivity)?.let { _ ->
            ensureSelectItem(item.name, name != null)
        }
    }

    inner class ManipulationTarget : IUtManipulationTarget {
        val agent = UtManipulationAgent(this)
        fun onScroll(event: UtGestureInterpreter.IScrollEvent) {
            agent.onScroll(event)
        }

        fun onScale(event: UtGestureInterpreter.IScaleEvent) {
            agent.onScale(event)
        }
        fun onTap(@Suppress("UNUSED_PARAMETER") event: UtGestureInterpreter.IPositionalEvent) {
            if(viewModel.playlist.isVideo.value) {
                viewModel.playerControllerModel.playerModel.togglePlay()
            }
        }
        fun onDoubleTap(@Suppress("UNUSED_PARAMETER") event: UtGestureInterpreter.IPositionalEvent) {
            agent.resetScrollAndScale()
        }

        fun onFlick(eventIFlickEvent: UtGestureInterpreter.IFlickEvent) {
            if(viewModel.playerControllerModel.windowMode.value == PlayerControllerModel.WindowMode.FULLSCREEN) {
                viewModel.playerControllerModel.showControlPanel.value = eventIFlickEvent.direction == Direction.Start
            }
        }

        override val parentView: View
            get() = controls.viewerArea

        override val contentView: View
            get() = if(viewModel.playlist.isVideo.value) controls.videoViewer.controls.player else  controls.imageView
        override val overScrollX: Float
            get() = 0.3f
        override val overScrollY: Float
            get() = 0f
        override val pageOrientation:EnumSet<Orientation> = EnumSet.of(Orientation.Horizontal)
        override fun changePage(orientation: Orientation, dir: Direction): Boolean {
            return if(orientation== Orientation.Horizontal) {
                when(dir) {
                    Direction.Start-> viewModel.playlist.hasPrevious.value.onTrue { viewModel.playlist.previous() }
                    Direction.End-> viewModel.playlist.hasNext.value.onTrue { viewModel.playlist.next() }
                }
            } else false
        }

        override fun hasNextPage(orientation: Orientation, dir: Direction): Boolean {
            return if(orientation== Orientation.Horizontal) {
                when(dir) {
                    Direction.Start-> viewModel.playlist.hasPrevious.value
                    Direction.End-> viewModel.playlist.hasNext.value
                }
            } else false
        }
    }

    private fun onDeletingItem(item:ItemEx): RecyclerViewBinding.IPendingDeletion {
        if(item == viewModel.playlist.currentSelection.value) {
            viewModel.playlist.select(null)
        }
        return object:RecyclerViewBinding.IPendingDeletion {
            override val itemLabel: String get() = item.name
            override val undoButtonLabel: String? = null  // default で ok

            override fun commit() {
                try {
                    TpLib.logger.debug("deleted $item")
                    lifecycleScope.launch { viewModel.metaDb.deleteFile(item) }
                } catch(e:Throwable) {
                    TpLib.logger.error(e)
                }
            }

            override fun rollback() {
                viewModel.playlist.select(item)
            }
        }
    }

    private fun onWindowModeChanged(mode:PlayerControllerModel.WindowMode) {
        logger.debug("windowMode=$mode")
        when(mode) {
            PlayerControllerModel.WindowMode.FULLSCREEN -> {
                controls.listPanel.visibility = View.GONE
                viewModel.playerControllerModel.showControlPanel.value = false
//                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            else-> {
                controls.listPanel.visibility = View.VISIBLE
                viewModel.playerControllerModel.showControlPanel.value = true
//                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    override fun onPause() {
        viewModel.playingBeforeBlocked.value = viewModel.playerControllerModel.playerModel.isPlaying.value
        viewModel.blockingAt.value = System.currentTimeMillis()
        viewModel.playerControllerModel.playerModel.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        fun afterUnblocked() {
            viewModel.blockingAt.value = 0
            if(viewModel.playerControllerModel.playerModel.revivePlayer()) {
                controls.videoViewer.associatePlayer()
                lifecycleScope.launch { viewModel.playlist.refreshList() }
            } else if(viewModel.playingBeforeBlocked.value) {
                viewModel.playingBeforeBlocked.value = false
                viewModel.playerControllerModel.playerModel.play()
            }
        }
        if(System.currentTimeMillis() - viewModel.blockingAt.value>3000) {
            lifecycleScope.launch {
                if(PasswordDialog.checkPassword(SlotSettings.currentSlotIndex)) {
                    afterUnblocked()
                } else {
                    logger.error("Incorrect Password")
                    finish()
                }
            }
        } else {
            afterUnblocked()
        }

    }

//    override fun onDestroy() {
//        super.onDestroy()
//        if(isFinishing) {
//            metaDb.close()
//        }
//    }

//    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
//        if(keyCode == KeyEvent.KEYCODE_BACK && viewModel.playerControllerModel.windowMode.value == PlayerControllerModel.WindowMode.FULLSCREEN) {
//            viewModel.playerControllerModel.setWindowMode(PlayerControllerModel.WindowMode.NORMAL)
//            return true
//        }
//        return super.onKeyDown(keyCode, event)
//    }
}
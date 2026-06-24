package io.github.toyota32k.secureCamera

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.DPDate
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.RecyclerViewBinding
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.LongClickUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.headlessBinding
import io.github.toyota32k.binder.headlessNonnullBinding
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.binder.materialRadioButtonGroupBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.recyclerViewBindingEx
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.mortal.UtMortalActivity
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTaskBase
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getActivity
import io.github.toyota32k.lib.media.editor.dialog.SliderPartition
import io.github.toyota32k.lib.media.editor.dialog.SliderPartitionDialog
import io.github.toyota32k.lib.media.editor.model.MaskCoreParams
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.formatSize
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.model.BitmapInfo
import io.github.toyota32k.lib.player.model.IBitmapInfo
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMediaFeed
import io.github.toyota32k.lib.player.model.IMediaMetadataRetrieverSource
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.IMediaSourcePreparer
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.IPhotoLoader
import io.github.toyota32k.lib.player.model.PhotoSizeOption
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.Rotation
import io.github.toyota32k.lib.player.model.VisibleAreaParams
import io.github.toyota32k.lib.player.model.VisibleAreaParams.Companion.IDENTITY
import io.github.toyota32k.lib.player.model.chapter.ChapterList
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.io.toAndroidFile
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.secureCamera.client.OkHttpInputFile
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
import io.github.toyota32k.secureCamera.dialog.ItemDialog
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.dialog.PlayListOptionsDialog
import io.github.toyota32k.secureCamera.dialog.SettingDialog
import io.github.toyota32k.secureCamera.dialog.SnapshotDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotSettings
import io.github.toyota32k.secureCamera.utils.FileUtil.safeDelete
import io.github.toyota32k.secureCamera.utils.setSecureMode
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtSorter
import io.github.toyota32k.utils.android.CompatBackKeyDispatcher
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.RefBitmap
import io.github.toyota32k.utils.android.RefBitmap.Companion.toRef
import io.github.toyota32k.utils.android.RefBitmapHolder
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration


class PlayerActivity : UtMortalActivity() {
    companion object {
        val logger = UtLog("PlayerActivity")
    }
    override val logger:UtLog = PlayerActivity.logger

    enum class ListMode(val resId:Int, val key:Int) {
        ALL(R.id.radio_all, 0),
        PHOTO(R.id.radio_photos, 2),
        VIDEO(R.id.radio_videos, 1)
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
            fun valueOf(name:String?):ListMode {
                if(name==null) return ListMode.ALL
                return try {
                    java.lang.Enum.valueOf(ListMode::class.java, name)
                } catch (_:Throwable) {
                    ListMode.ALL
                }
            }
            fun fromKey(key:Int, def:ListMode=ALL): ListMode {
                return entries.find { it.key == key } ?: def
            }
        }
    }

    data class SortOptions(
        val key:Key,
        val order:Order,
    ) {
        constructor(src: SortOptions, key: Key = src.key, order: Order = src.order) : this(
            key,
            order
        )
        // region Types

        enum class Key(val v:Int, val resId:Int) {
            Date(0, R.id.radio_sort_by_date),
            Size(1, R.id.radio_sort_by_size),
            ;
            private class IDResolver : IIDValueResolver<Key> {
                override fun id2value(@IdRes id: Int): Key = entries.find { it.resId == id } ?: Date
                override fun value2id(v: Key): Int = v.resId
            }
            companion object {
                val resolver: IIDValueResolver<Key> by lazy { IDResolver() }
                fun fromValue(v:Int, def:Key = Date):Key {
                    return entries.find { it.v == v } ?: def
                }
            }
        }

        enum class Order(val v:Boolean, val resId:Int, val eff:Int) {
            Ascend(false, R.id.radio_ascend, 1),
            Descend(true, R.id.radio_descend, -1),
            ;
            private class IDResolver : IIDValueResolver<Order> {
                override fun id2value(@IdRes id: Int): Order = Order.entries.find { it.resId == id } ?: Order.Ascend
                override fun value2id(v: Order): Int = v.resId
            }
            companion object {
                fun fromValue(v: Boolean, def:Order = Ascend):Order {
                    return entries.find { it.v == v } ?: def
                }
                val resolver: IIDValueResolver<Order> by lazy { IDResolver() }
            }
        }

        // endregion

        // sort
        fun compare(a: ItemEx, b: ItemEx): Int {
            return when (key) {
                Key.Date -> a.creationDate.compareTo(b.creationDate)
                Key.Size -> a.size.compareTo(b.size)
            } * order.eff
        }

        companion object {
            val initial = SortOptions(
                key = Key.fromValue(Settings.PlayListSetting.sortKey),
                order = Order.fromValue(Settings.PlayListSetting.sortOrder),
            )
        }
    }

    data class FilterOptions(
        val enableStartDate:Boolean,
        val startDate:DPDate,
        val enableEndDate:Boolean,
        val endDate:DPDate,
        val enableRatingFilter:Boolean,
        val ratingFlags:Int,
        val enableMarkFilter:Boolean,
        val markFlags:Int,
        val offline:Boolean,
        val unbackedUp:Boolean,
        ) {

        // filter
        fun filter(item: ItemEx):Boolean {
//            if (!when(listMode) {
//                ListMode.ALL-> true
//                ListMode.VIDEO-> item.isVideo
//                ListMode.PHOTO-> item.isPhoto
//            }) return false

            if (unbackedUp) {
                if (item.cloud.isFileInCloud) return false
            }
            if (offline) {
                if (!item.cloud.isFileInLocal) return false
            }

            if (enableRatingFilter && ratingFlags!=0) {
                if ((item.rating.flag and ratingFlags) == 0) return false
            }
            if (enableMarkFilter && markFlags!=0) {
                if ((item.mark.flag and markFlags) == 0) return false
            }

            val dpDate = item.dpDate
            if(enableStartDate && startDate.isValid && dpDate<startDate) return false
            if(enableEndDate && endDate.isValid && dpDate>endDate) return false

            return true
        }

        companion object {
            val initial = FilterOptions(
                enableStartDate = Settings.PlayListSetting.enableStartDate,
                startDate = Settings.PlayListSetting.startDate,
                enableEndDate = Settings.PlayListSetting.enableEndDate,
                endDate = Settings.PlayListSetting.endDate,
                enableRatingFilter = Settings.PlayListSetting.enableRatingFilter,
                ratingFlags = Settings.PlayListSetting.ratingFlags,
                enableMarkFilter = Settings.PlayListSetting.enableMarkFilter,
                markFlags = Settings.PlayListSetting.markFlags,
                offline = Settings.PlayListSetting.onlyOfflineItems,
                unbackedUp = Settings.PlayListSetting.onlyUnBackedUpItems
            )
        }
    }

    class PlayerViewModel(application: Application): AndroidViewModel(application) {
        companion object {
//            const val KEY_CURRENT_LIST_MODE = "ListMode"
            const val KEY_CURRENT_ITEM = "CurrentItem"
            var maskParams: MaskCoreParams? = null

            suspend fun saveSnapshot(db:ScDB, item: MetaData, pos:Long, snapshot: RefBitmap):MetaData? {
                val bitmapHolder = RefBitmapHolder(snapshot)
                val resolution = Settings.Player.snapshotResolution
                if (resolution!=SettingDialog.SettingViewModel.Resolution.HIGH) {
                    // 必要に応じて解像度を落とす
                    val longSide = max(snapshot.width, snapshot.height)
                    val shortSide = min(snapshot.width, snapshot.height)
                    val ratio = longSide.toFloat() / shortSide.toFloat()
                    val reqSize = if (ratio>1.4) {
                        // 19:6
                        resolution.wide.size
                    } else {
                        // 4:3
                        resolution.narrow.size
                    }
                    if (longSide>reqSize.width || shortSide>reqSize.height) {
                        val result = UtFitter(FitMode.Inside, reqSize).fit(longSide, shortSide).result
                        val size = if (snapshot.width > snapshot.height) {
                            result.asSize
                        } else {
                            Size(result.height.roundToInt(), result.width.roundToInt())
                        }
                        bitmapHolder.set(snapshot.scale(size.width, size.height))
                    }
                }
                val sourceBitmap = bitmapHolder.getOrNull() ?: return null

                val ref = SnapshotDialog.showBitmap(sourceBitmap, maskParams = maskParams)?.let {
                        maskParams = it.maskParams
                        it.bitmap
                } ?: return null

                return withContext(Dispatchers.IO) {
                    ref.use { bitmap ->
                        var file: File? = null
                        try {
                            val orgDate = item.date
                            val date = Date(orgDate + pos)
                            file = db.createPhotoFile(date)
                            file.outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                                it.flush()
                            }
                            db.register(file.name)
                        } catch (e: Throwable) {
                            TpLib.logger.error(e)
                            file?.safeDelete()
                            null
                        }
                    }
                }
            }
        }
        val metaDb = MetaDB[SlotSettings.currentSlotIndex]

        val playlist = Playlist()
        // ExoPlayer の HTTP/HTTPS 取得を NetClient と同じ OkHttpClient 経由にする。
        // これで自己署名 BooTube への HTTPS リクエストが NetClient の TrustManager で
        // 検証され、`Trust anchor not found` を回避できる。
        val okhttpDataSource = OkHttpDataSource.Factory(NetClient.motherClient)
        val mediaDataSourceFactory = DefaultDataSource.Factory(application, okhttpDataSource)
        val playerControllerModel = PlayerControllerModel.Builder(application, viewModelScope)
            .dataSourceFactory(mediaDataSourceFactory)
            .customOkHttpClient(NetClient.motherClient)
            .supportFullscreen()
            .supportPlaylist(playlist,autoPlay = false,continuousPlay = false)
            .supportChapter(hideChapterViewIfEmpty = true)
            .supportSnapshot(::onSnapshot)
            .snapshotSource(PlayerControllerModel.SnapshotSource.CAPTURE_PLAYER, selectable = true)
            .supportMagnifySlider { orgModel, duration->
                val sp = SliderPartitionDialog.show(SliderPartition.fromModel(orgModel, duration))
                if (sp==null) orgModel else sp.toModel()
            }
            .enableRotateRight()
            .enableRotateLeft()
            .enableSeekMedium(Settings.Player.spanOfSkipBackward, Settings.Player.spanOfSkipForward)
            .enablePhotoViewer(Duration.INFINITE, photoSizeOption = PhotoSizeOption.LimitByScreen)
            .customPhotoLoader(object: IPhotoLoader {
                override suspend fun loadBitmap(src: IMediaSource): IBitmapInfo? {
                    val source = src as? MediaSource ?: return null
                    if(source.item.cloud.loadFromCloud) {
                        val bmp = TcClient.getPhoto(metaDb, source.item)?.toRef()
                        if (bmp==null) {
                            return BitmapInfo.asError
                        } else {
                            return BitmapInfo.withBitmap(bmp)
                        }
                    } else {
                        // file の sha1 hash を cacheヒントとして使う場合はこちら
//                        return BitmapInfo.useGlide(src.uri)
                        // file のタイムスタンプをヒントとして使う場合はこちら
                        return BitmapInfo.useGlideWithCustomHint(src.item.date)
                    }
                }
            })
            .supportFullscreen()
            .build()
        val ensureVisibleCommand = LiteUnitCommand()
        val playlistSettingCommand = LiteUnitCommand {
            viewModelScope.launch {
                data class MinMax(var min:DPDate,var max:DPDate)
                val list = metaDb.listEx(ListMode.ALL)
                val current = playlist.currentSelection.value?.dpDate ?: DPDate.Today
                val mm = MinMax(current,current)
                list.fold(mm) { acc, item->
                    val dp = item.dpDate
                    if(dp<acc.min) {
                        acc.min = dp
                    }
                    if(dp>acc.max) {
                        acc.max = dp
                    }
                    acc
                }
                PlayListOptionsDialog.show(this@PlayerViewModel,mm.min, mm.max)
                allowDelete.value = Settings.PlayListSetting.allowDelete
            }
        }
        val editPhotoCommand = LiteCommand<RefBitmap>()

        val gotoTopCommand = LiteUnitCommand {
            val item = playlist.collection.firstOrNull() ?: return@LiteUnitCommand
            playlist.select(item)
        }
        val gotoBottomCommand = LiteUnitCommand {
            val item = playlist.collection.lastOrNull() ?: return@LiteUnitCommand
            playlist.select(item)
        }

        val blockingAt = MutableStateFlow(System.currentTimeMillis())              // 画面ロックした時刻
        val playingBeforeBlocked = MutableStateFlow(false)  // 画面ロックされる前に再生中だった --> unlock時に再生を再開する。
        val allowDelete = MutableStateFlow(Settings.PlayListSetting.allowDelete)
        val listMode = MutableStateFlow<ListMode>(Settings.PlayListSetting.listMode)

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

        inner class MediaSource(val item: ItemEx) : IMediaSourceWithChapter, IMediaMetadataRetrieverSource, IMediaSourcePreparer {
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
            override suspend fun getChapterList(): IChapterList {
                return if(item.chapterList!=null) ChapterList(item.chapterList.toMutableList()) else IChapterList.Empty
            }

            override suspend fun <T> withMediaMetadataRetriever(fn: suspend (MediaMetadataRetriever) -> T): T {
                val inFile = if(item.cloud.isFileInLocal) {
                    metaDb.fileOf(item).toAndroidFile()
                } else {
                    OkHttpInputFile(SCApplication.instance, metaDb.urlOf(item))
                }
                return inFile.openMetadataRetriever().useObj {
                    fn (it)
                }
            }

            /**
             * Playlist から選択されたメディアをプレーヤーにセットする直前に呼ばれる。
             * 認証して、
             */
            override suspend fun onSourceLoading(): Boolean {
                if (item.cloud.loadFromCloud) {
                    if (Authentication.authAndMessage()==null) return false
                }
                return true
            }

            override suspend fun onSourceLoaded() {}
        }

        inner class Playlist : IMediaFeed, IUtPropOwner {
//            private var sortOrder:Int = 1
//            private fun updateSortOrderBySettings() {
//                sortOrder = if(Settings.PlayListSetting.sortOrder) -1 else 1
//            }
            val collection = ObservableList<ItemEx>()
//            val sorter = UtSorter(
//                collection,
//                actionOnDuplicate = UtSorter.ActionOnDuplicate.REPLACE
//            ) { a, b ->
//                val ta = a.creationDate // filename2date(a)?.time ?: 0L
//                val tb = b.creationDate // filename2date(b)?.time ?: 0L
//                val d = ta - tb
//                sortOrder * (if(d<0) -1 else if(d>0) 1 else 0)
//            }

            val currentSelection:StateFlow<ItemEx?> = MutableStateFlow<ItemEx?>(null)
            private var photoSelection:ItemEx? = null
            private var videoSelection:ItemEx? = null
            val isCurrentVideo: Boolean = currentSelection.value?.isVideo==true
//            val listMode = MutableStateFlow(ListMode.ALL)

//            val listOptionsFlow = MutableStateFlow(ListOptions.initial)
            private var mSortOptions:SortOptions = SortOptions.initial
            val sortOptions: SortOptions get() = mSortOptions
            suspend fun updateSortOptions(
                sortOptions: SortOptions
            ) {
                if (sortOptions!=mSortOptions) {
                    mSortOptions = sortOptions
                    updateList()
                }
            }

            private val sorter = UtSorter(collection, actionOnDuplicate = UtSorter.ActionOnDuplicate.REPLACE ) { a,b->
                mSortOptions.compare(a,b)
            }

            private var mFilterOptions:FilterOptions = FilterOptions.initial
            val filterOptions:FilterOptions get() = mFilterOptions
            suspend fun updateFilterOptions(filterOptions: FilterOptions) {
                if (filterOptions!=mFilterOptions) {
                    mFilterOptions = filterOptions
                    updateList()
                }
            }

//            suspend fun updateListMode(listMode: ListMode) {
//                if (listMode!=this.listMode.value) {
//                    Settings.PlayListSetting.listMode = listMode
//                    mFilterOptions = mFilterOptions.copy(listMode = listMode)
//                    updateList()
//                }
//            }
//            val listMode:ListMode get() = mFilterOptions.listMode
            fun filter(item: ItemEx):Boolean {
                return mFilterOptions.filter(item)
            }

            suspend fun updateOptions(sortOptions: SortOptions, filterOptions: FilterOptions) {
                var modified = false
                if (sortOptions!=mSortOptions) {
                    mSortOptions = sortOptions
                    modified = true
                }
                if (filterOptions!=mFilterOptions) {
                    mFilterOptions = filterOptions
                    modified = true
                }
                if (modified) {
                    updateList()
                }
            }

            suspend fun updateList() {
                val current = currentSource.value as MediaSource?
                val listMode = listMode.value
                metaDb.listEx(listMode).filter {
                    filter(it)
                }.run {
                    sorter.replace(this)
                }
                when(listMode) {
                    ListMode.ALL->  select(current?.item)
                    ListMode.VIDEO -> select(videoSelection)
                    ListMode.PHOTO -> select(photoSelection)
                }
            }

            override val currentSource = MutableStateFlow<IMediaSource?>(null)
            override val hasNext = MutableStateFlow(false)
            override val hasPrevious = MutableStateFlow(false)

            fun select(requestItem:ItemEx?, force:Boolean=false) {
                if(!force && requestItem == currentSelection.value) return
                if(collection.isEmpty()) {
                    hasNext.value = false
                    hasPrevious.value = false
                    currentSource.value = null
                    currentSelection.mutable.value = null
                    return
                }

                if(requestItem==null) {
                    currentSource.value = null
                    currentSelection.mutable.value = null
                    return
                }

                val index = collection.indexOfFirst{ it.id == requestItem.id }
                if(index<0) {
                    // 要求されたアイテムが存在しない
                    select(null, true)
                    return
                }

                val item = collection[index]
                currentSelection.mutable.value = item
                if(item.isVideo) {
                    videoSelection = item
                } else if (item.isPhoto) {
                    photoSelection = item
                } else {
                    // invalid item
                    logger.error("${item.type}")
                    select(null, true)
                    return
                }
                playerControllerModel.playerModel.rotate(Rotation.NONE)
//                if(item.cloud.loadFromCloud) {
//                    currentSource.value = null
//                    viewModelScope.launch {
//                        Authentication.authAndMessage() ?: return@launch
//                        currentSource.value = MediaSource(item)
//                    }
//                } else {
//                    currentSource.value = MediaSource(item)
//                }
                currentSource.value = MediaSource(item)
                hasPrevious.mutable.value = index>0
                hasNext.mutable.value = index<collection.size-1
                ensureVisibleCommand.invoke()
            }

            fun currentIndex():Int {
                val item = currentSelection.value ?: return -1
                return collection.indexOf(item)
            }

            fun addItem(item:ItemEx) {
                if (mFilterOptions.filter(item)) {
                    sorter.add(item)
                    select(item)
                }
            }
            fun addItemNotSelect(item:ItemEx) {
                if (mFilterOptions.filter(item)) {
                    sorter.add(item)
                }
            }

            fun removeItem(itemId:Int) {
                val index = collection.indexOfFirst { it.id == itemId }
                if(index>=0) {
                    collection.removeAt(index)
                }
            }

            fun replaceItem(item:ItemEx) {
                val index = collection.indexOfFirst { it.id == item.id }
                if (index>=0) {
                    collection.set(index, item)
                } else {
                    addItem(item)
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

            fun updateCurrentPhoto(photoBitmap: RefBitmap) {
                photoBitmap.use { bitmap ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val item = currentSelection.value ?: return@launch
                        val file = metaDb.fileOf(item)
                        file.outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                            it.flush()
                        }
                        metaDb.updateFile(item, null)
                    }
                }
            }
        }

        init {
            viewModelScope.launch {
                val name = metaDb.KV.get(KEY_CURRENT_ITEM) ?: return@launch
                val item = metaDb.itemExOf(name) ?: return@launch
                playlist.select(item)
            }
        }

        val currentIndex:Int
            get() = playlist.currentIndex()

        private fun onSnapshot(pos:Long, bitmap: RefBitmap) {
            CoroutineScope(Dispatchers.IO).launch {
                val source = playlist.currentSelection.value ?: return@launch
                if (source.isVideo) {
                    val newMetaData = saveSnapshot(metaDb, source.data, pos, bitmap) ?: return@launch
                    val newItem = ItemEx(newMetaData,metaDb.slotIndex.index,null)
                    withContext(Dispatchers.Main) {
                        playlist.addItemNotSelect(newItem)
                    }
                } else {
                    editPhotoCommand.invoke(bitmap)
                }
            }
        }

        fun saveListModeAndSelection() {
            val listMode = this.listMode.value
            val currentItem = playlist.currentSelection.value

            // onCleared で metaDb.close() されるので、MetaDB を新たに取得しておく
            val db = MetaDB[SlotSettings.currentSlotIndex]
            CoroutineScope(Dispatchers.IO).launch {
                db.use { _ ->
//                    metaDb.KV.put(KEY_CURRENT_LIST_MODE, listMode.toString())
                    if (currentItem != null) {
                        metaDb.KV.put(KEY_CURRENT_ITEM, currentItem.name)
                    }
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
    private val manipulationTarget by lazy { ManipulationTargetImpl(controls.mediaPlayerView.manipulationTarget) }
    private val manipulationAgent by lazy { UtManipulationAgent(manipulationTarget) }

    private val compatBackKeyDispatcher = CompatBackKeyDispatcher()
    override fun onCreate(savedInstanceState: Bundle?) {
        Settings.Design.applyToActivity(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Settings.initialize(application)

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

        controls.listView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager(this).orientation))
        binder.owner(this)
            .materialRadioButtonGroupBinding(controls.listMode, viewModel.listMode, ListMode.IDResolver)
            .visibilityBinding(controls.safeGuard, viewModel.blockingAt.map { it>0 }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .bindCommand(viewModel.ensureVisibleCommand,this::ensureVisible)
            .bindCommand(viewModel.gotoTopCommand, controls.gotoTopButton)
            .bindCommand(viewModel.gotoBottomCommand, controls.gotoBottomButton)
            .bindCommand(viewModel.playlistSettingCommand, controls.listSettingButton)
            .bindCommand(viewModel.editPhotoCommand, this::editPhoto)
            .observe(viewModel.playlist.currentSelection) { item->
                manipulationAgent.resetScrollAndScale()
            }
            .headlessBinding(viewModel.playerControllerModel.playerModel.rotation) {
                manipulationAgent.resetScroll()
            }
            .add {
                viewModel.playerControllerModel.windowMode.disposableObserve(this, ::onWindowModeChanged)
            }
            .observe(viewModel.listMode) { listMode->
                if (Settings.PlayListSetting.listMode != listMode) {
                    Settings.PlayListSetting.listMode = listMode
                }
                viewModel.playlist.updateList()
            }
            .recyclerViewBindingEx<ItemEx,ListItemBinding>(controls.listView) {
                list(viewModel.playlist.collection)
                gestureParams(viewModel.allowDelete.map { RecyclerViewBinding.GestureParams(false,it,itemDeletionHandler)})
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

        controls.mediaPlayerView.bindViewModel(viewModel.playerControllerModel, binder)

        gestureInterpreter.setup(this, controls.viewerArea) {
            onScroll(manipulationAgent::onScroll)
            onScale(manipulationAgent::onScale)
            onTap { viewModel.playerControllerModel.playerModel.togglePlay() }
            onDoubleTap { manipulationAgent.resetScrollAndScale() }
            onLongTap { window.setSecureMode() }
            onFlickVertical { event->
                if(viewModel.playerControllerModel.windowMode.value == PlayerControllerModel.WindowMode.FULLSCREEN) {
                    viewModel.playerControllerModel.showControlPanel.value = event.direction == Direction.Start
                }
            }
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
                viewModel.playlist.removeItem(c.itemId)
            }
            DBChange.Type.Refresh -> {
                viewModel.playlist.updateList()
            }
        }
    }

    private fun ensureVisible() {
        val index = viewModel.currentIndex
        if(index>=0) {
            controls.listView.scrollToPosition(index)
        }
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
                        if (viewModel.playlist.isCurrentVideo) {
                            viewModel.playlist.select(null, true)
                        }
                        val host = Authentication.authAndMessage()
                        if (host != null) {
                            viewModel.metaDb.restoreFromCloud(item2, host)
                        }
                    }
                    ItemDialog.ItemViewModel.NextAction.Repair -> {
                        val host = Authentication.authAndMessage()
                        if (host != null) {
                            viewModel.metaDb.recoverFromCloud(item2, host)
                        }
                    }

                    ItemDialog.ItemViewModel.NextAction.Delete -> {
                        // Item Dialog で確認済み
                        if(item2 == viewModel.playlist.currentSelection.value) {
                            viewModel.playlist.select(null)
                        }
                        viewModel.metaDb.deleteFile(item)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun editPhoto(bitmap:RefBitmap, visibleAreaParams: VisibleAreaParams?=null) {
        UtImmortalTask.launchTask("editPhoto") {
            bitmap.addRef()
            try {
                SnapshotDialog.showBitmap(bitmap, visibleAreaParams ?: controls.mediaPlayerView.visibleAreaParams)?.use { cropped ->
                    val bmp = cropped.bitmap?.takeIf { it.hasBitmap } ?: return@use
                    PlayerViewModel.maskParams = cropped.maskParams
//                manipulationAgent.resetScrollAndScale()
                    viewModel.playlist.updateCurrentPhoto(bmp)
                }
            } finally {
                bitmap.release()
            }
        }
    }

    private suspend fun UtImmortalTaskBase.editItem(item:ItemEx) {
        if (item.isVideo) {
            viewModel.saveListModeAndSelection()
            viewModel.playlist.select(null)
            viewModel.playerControllerModel.playerModel.killPlayer()
            controls.mediaPlayerView.dissociatePlayer()
            val name = editorActivityBroker.invoke(item.name)
            (getActivity() as? PlayerActivity)?.let { _ ->
                ensureSelectItem(item.name, name != null)
            }
        } else {
            val bitmap = viewModel.playerControllerModel.playerModel.shownBitmap.value?.takeIf { it.hasBitmap } ?: return
            editPhoto(bitmap, PlayerViewModel.maskParams ?: IDENTITY)
        }
    }

    /**
     * SimpleManipulationTarget（VideoPlayerView#manipulationTarget） に、paging用のメンバー実装を追加するクラス
     */
    inner class ManipulationTargetImpl(val playerManipulationTarget: IUtManipulationTarget) : IUtManipulationTarget by playerManipulationTarget {
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

    /**
     * スワイプによるアイテム削除用i/fの実装
     */
    private inner class ItemDeletionHandler : RecyclerViewBinding.IDeletionHandler<ItemEx> {
        override fun canDelete(item: ItemEx):Boolean {
            return viewModel.allowDelete.value
        }
        override fun delete(item: ItemEx):RecyclerViewBinding.IPendingDeletion {
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


    }
    private val itemDeletionHandler = ItemDeletionHandler()

    private fun onWindowModeChanged(mode:PlayerControllerModel.WindowMode) {
        logger.debug("windowMode=$mode")
        when(mode) {
            PlayerControllerModel.WindowMode.FULLSCREEN -> {
                controls.listPanel.visibility = View.GONE
                viewModel.playerControllerModel.showControlPanel.value = false
            }
            else-> {
                controls.listPanel.visibility = View.VISIBLE
                viewModel.playerControllerModel.showControlPanel.value = true
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
                controls.mediaPlayerView.associatePlayer()
                lifecycleScope.launch { viewModel.playlist.updateList() }
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
}
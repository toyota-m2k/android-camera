package io.github.toyota32k.secureCamera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.RecyclerViewBinding
import io.github.toyota32k.binder.VisibilityBinding
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
import io.github.toyota32k.binder.recyclerViewGestureBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.common.getAttrColorAsDrawable
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.dialog.task.getActivity
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.formatSize
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.model.*
import io.github.toyota32k.lib.player.model.chapter.ChapterList
import io.github.toyota32k.secureCamera.ScDef.PHOTO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.PHOTO_PREFIX
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.databinding.ActivityPlayerBinding
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.DBChange
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.Mark
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.MetaDB.toItemEx
import io.github.toyota32k.secureCamera.db.MetaData
import io.github.toyota32k.secureCamera.db.Rating
import io.github.toyota32k.secureCamera.dialog.ItemDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.utils.*
import io.github.toyota32k.shared.gesture.Direction
import io.github.toyota32k.shared.gesture.Orientation
import io.github.toyota32k.shared.gesture.UtGestureInterpreter
import io.github.toyota32k.shared.UtSorter
import io.github.toyota32k.shared.gesture.IUtManipulationTarget
import io.github.toyota32k.shared.gesture.UtManipulationAgent
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.disposableObserve
import io.github.toyota32k.utils.onTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicLong


class PlayerActivity : UtMortalActivity() {
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
                return values().find { it.resId == resId } ?: def
            }
            fun valueOf(name:String?):ListMode? {
                if(name==null) return null
                return try {
                    java.lang.Enum.valueOf(ListMode::class.java, name)
                } catch (e:Throwable) {
                    null
                }
            }
        }

    }

    class PlayerViewModel(application: Application): AndroidViewModel(application) {
        companion object {
            const val KEY_CURRENT_LIST_MODE = "ListMode"
            const val KEY_CURRENT_ITEM = "CurrentItem"
            suspend fun takeSnapshot(item: MetaData, pos:Long, bitmap: Bitmap):MetaData? {
                return withContext(Dispatchers.IO) {
                    var file:File? = null
                    try {
//                    val current = playlist.currentSelection.value ?: return
                        val orgDate = item.date
                        val date = Date(orgDate + pos)
                        val filename = ITcUseCase.defaultFileName(PHOTO_PREFIX, PHOTO_EXTENSION, date)
                        file = File(UtImmortalTaskManager.application.filesDir, filename)
                        file.outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                            it.flush()
                        }
                        MetaDB.register(filename)
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

        private val context: Application
            get() = getApplication()
        val playlist = Playlist()
        val playerControllerModel = PlayerControllerModel.Builder(application, viewModelScope)
            .supportFullscreen()
            .supportPlaylist(playlist,autoPlay = false,continuousPlay = false)
            .supportSnapshot(::onSnapshot)
            .supportChapter(hideChapterViewIfEmpty = true)
            .enableRotateRight()
            .enableRotateLeft()
            .relativeSeekDuration(Settings.Player.spanOfSkipForward, Settings.Player.spanOfSkipBackward)
            .build()
        //val playerModel get() = playerControllerModel.playerModel

        val fullscreenCommand = LiteCommand<Boolean> {
            playerControllerModel.setWindowMode( if(it) PlayerControllerModel.WindowMode.FULLSCREEN else PlayerControllerModel.WindowMode.NORMAL )
        }
        val rotateCommand = LiteCommand<Rotation>(playlist::rotateBitmap)
        val saveBitmapCommand = LiteUnitCommand(playlist::saveBitmap)
        val ensureVisibleCommand = LiteUnitCommand()

        inner class VideoSource(val item: ItemEx) : IMediaSourceWithChapter {
            private val file: File = item.file
            override val name:String
                get() = item.name
            override val id: String
                get() = name
            override val uri: String
                get() = item.uri
            override val trimming: Range = Range.empty
            override val type: String
                get() = name.substringAfterLast(".", "")
            override var startPosition = AtomicLong()
//            override val disabledRanges: List<Range> = emptyList()
            override val chapterList: IChapterList
                = if(item.chapterList!=null) ChapterList(item.chapterList.toMutableList()) else IChapterList.Empty
        }
        inner class Playlist : IMediaFeed, IUtPropOwner {
            val collection = ObservableList<ItemEx>()
            val sorter = UtSorter(
                collection,
                actionOnDuplicate = UtSorter.ActionOnDuplicate.REPLACE
            ) { a, b ->
                val ta = a.creationDate // filename2date(a)?.time ?: 0L
                val tb = b.creationDate // filename2date(b)?.time ?: 0L
//                if(ta==tb) {
//                    TpLib.logger.debug("same value")
//                }
//                TpLib.logger.debug("compare: $ta with $tb = ${ta-tb} (${(ta-tb).toInt()}")
//                ((filename2date(a)?.time ?: 0) - (filename2date(b)?.time ?: 0)).toInt()
                val d = ta - tb
                if(d<0) -1 else if(d>0) 1 else 0
            }
//            val isVideo: StateFlow<Boolean> = MutableStateFlow(false)
            val photoBitmap: StateFlow<Bitmap?> = MutableStateFlow(null)

            val currentSelection:StateFlow<ItemEx?> = MutableStateFlow<ItemEx?>(null)
            var photoSelection:ItemEx? = null
            var videoSelection:ItemEx? = null
            val isVideo: StateFlow<Boolean> = currentSelection.map { it?.isVideo==true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
            val isPhoto: StateFlow<Boolean> = currentSelection.map { it?.isPhoto==true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
            val listMode = MutableStateFlow(ListMode.ALL)

            override val currentSource = MutableStateFlow<IMediaSource?>(null)
            override val hasNext = MutableStateFlow(false)
            override val hasPrevious = MutableStateFlow(false)

//            val commandNext = LiteUnitCommand(::next)
//            val commandPrev = LiteUnitCommand(::previous)
            val photoRotation : StateFlow<Int> = MutableStateFlow(0)

            init {
                listMode.onEach(::setListMode).launchIn(viewModelScope)
            }
            fun select(item_:ItemEx?, force:Boolean=true) {
                if(!force && item_ == currentSelection.value) return
                if(collection.isEmpty()) {
                    hasNext.value = false
                    hasPrevious.value = false
                    currentSource.value = null
                    photoRotation.mutable.value = 0
                    photoBitmap.mutable.value = null
//                    playerControllerModel.playerModel.rotate(Rotation.NONE)
                    currentSelection.mutable.value = null
                    return
                }

                if(item_==null) {
                    currentSource.value = null
                    photoRotation.mutable.value = 0
                    photoBitmap.mutable.value = null
                    currentSelection.mutable.value = null
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
                    photoRotation.mutable.value = 0
                    photoBitmap.mutable.value = null
                    playerControllerModel.playerModel.rotate(Rotation.NONE)
                    if(!item.cloud.isFileInLocal) {
                        viewModelScope.launch {
                            if(Authentication.authentication()) {
                                currentSource.value = VideoSource(item)
                            }
                        }
                    } else {
                        currentSource.value = VideoSource(item)
                    }
                } else {
                    photoSelection = item
                    currentSource.value = null
                    photoRotation.mutable.value = 0
                    if(!item.cloud.isFileInLocal) {
                        viewModelScope.launch {
                            photoBitmap.mutable.value = TcClient.getPhoto(item)
                        }
                    } else {
                        photoBitmap.mutable.value = BitmapFactory.decodeFile(item.file.path)
                    }
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
                sorter.add(item)
                select(item)
            }

            fun removeItem(item:ItemEx) {
                var index = collection.indexOfFirst { it.id == item.id }
                if(index>=0) {
                    collection.removeAt(index)
                }
            }

            fun replaceItem(item:ItemEx) {
                val current = currentSelection.value?.id == item.id
                sorter.add(item)
                if(current) {
                    select(item)
                }
            }

            private suspend fun setListMode(mode:ListMode) {
                val newList = MetaDB.listEx(mode)
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
                    val file = item.file
                    file.outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                        it.flush()
                    }
                    photoRotation.mutable.value = 0
                    MetaDB.updateFile(item,null)
//                    if (playlist.listMode.value != ListMode.VIDEO) {
//                        withContext(Dispatchers.Main) { playlist.replaceItem(newItem) }
//                    }
                }
            }
        }

        init {
            viewModelScope.launch {
                val mode = ListMode.valueOf(MetaDB.KV.get(KEY_CURRENT_LIST_MODE))
                if(mode!=null) {
                    playlist.listMode.value = mode
                }
                val name = MetaDB.KV.get(KEY_CURRENT_ITEM) ?: return@launch
                val item = MetaDB.itemExOf(name) ?: return@launch
                playlist.select(item)
            }
        }

        val currentIndex:Int
            get() = playlist.currentIndex()

        private fun onSnapshot(pos:Long, bitmap: Bitmap) {
            CoroutineScope(Dispatchers.IO).launch {
                val source = playlist.currentSelection.value ?: return@launch
                val newItem = takeSnapshot(source.data, pos, bitmap) ?: return@launch
                if (playlist.listMode.value != ListMode.VIDEO) {
                    withContext(Dispatchers.Main) { playlist.sorter.add(ItemEx(newItem,null)) }
                }
            }
        }

//        fun updateItem(itemNew:ItemEx) {
//            val index = playlist.sorter.find(itemNew)
//            if(index>=0) {
//                playlist.collection[index] = itemNew
//            }
//        }

        override fun onCleared() {
            val listMode = playlist.listMode.value.toString()
            val currentItem = playlist.currentSelection.value?.name
            CoroutineScope(Dispatchers.IO).launch {
                MetaDB.KV.put(KEY_CURRENT_LIST_MODE, listMode)
                if(currentItem!=null) {
                    MetaDB.KV.put(KEY_CURRENT_ITEM, currentItem)
                }
            }
            super.onCleared()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.initialize(application)
        controls = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(controls.root)
        hideActionBar()

        val normalColor: Drawable
//        val normalTextColor: Int
        val selectedColor: Drawable
//        val selectedTextColor: Int
//
        theme!!.apply {
            normalColor = getAttrColorAsDrawable(com.google.android.material.R.attr.colorSurface, Color.WHITE)
//            normalTextColor = getAttrColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
            selectedColor = getAttrColorAsDrawable(com.google.android.material.R.attr.colorSecondary, Color.BLUE)
//            selectedTextColor = getAttrColor(com.google.android.material.R.attr.colorOnSecondary, Color.WHITE)
        }


        val icPhoto = AppCompatResources.getDrawable(this, R.drawable.ic_type_photo)!!
        val icVideo = AppCompatResources.getDrawable(this, R.drawable.ic_type_video)!!
        val icMarkStar = AppCompatResources.getDrawable(this, Mark.Star.iconId)!!
        val icMarkFlag = AppCompatResources.getDrawable(this, Mark.Flag.iconId)!!
        val icMarkCheck = AppCompatResources.getDrawable(this, Mark.Check.iconId)!!
        val icRating1 = AppCompatResources.getDrawable(this, Rating.Rating1.icon)!!
        val icRating2 = AppCompatResources.getDrawable(this, Rating.Rating2.icon)!!
        val icRating3 = AppCompatResources.getDrawable(this, Rating.Rating3.icon)!!
        val icRating4 = AppCompatResources.getDrawable(this, Rating.Rating4.icon)!!
        val icCloud = AppCompatResources.getDrawable(this, R.drawable.ic_cloud)!!
        val icCloudFull = AppCompatResources.getDrawable(this, R.drawable.ic_cloud_full)!!
//        val icPhotoSel = TintDrawable.tint(icPhoto, selectedTextColor)
//        val icVideoSel = TintDrawable.tint(icVideo, selectedTextColor)
//        val icPhotoSel = TintDrawable.tint(AppCompatResources.getDrawable(this, R.drawable.ic_type_photo)!!, selectedTextColor)
//        val icVideoSel = TintDrawable.tint(AppCompatResources.getDrawable(this, R.drawable.ic_type_video)!!, selectedTextColor)
        controls.listView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager(this).getOrientation()))
        binder.owner(this)
            .materialRadioButtonGroupBinding(controls.listMode, viewModel.playlist.listMode, ListMode.IDResolver)
            .visibilityBinding(controls.videoViewer, viewModel.playlist.isVideo)
            .visibilityBinding(controls.photoViewer, viewModel.playlist.isPhoto)
//            .enableBinding(controls.imageNextButton, viewModel.playlist.hasNext)
//            .enableBinding(controls.imagePrevButton, viewModel.playlist.hasPrevious)
            .combinatorialVisibilityBinding(viewModel.playerControllerModel.windowMode.map {it==PlayerControllerModel.WindowMode.FULLSCREEN}) {
                straightGone(controls.collapseButton)
                inverseGone(controls.expandButton)
            }
            .visibilityBinding(controls.photoButtonPanel, viewModel.playerControllerModel.showControlPanel, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
//            .headlessBinding(viewModel.playerControllerModel.showControlPanel) {
//                val params = controls.videoViewer.controls.player.layoutParams as FrameLayout.LayoutParams
//                if(it==true) {
//                    controls.photoButtonPanel.visibility = View.VISIBLE
//                    params.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
//                } else {
//                    controls.photoButtonPanel.visibility = View.GONE
//                    params.gravity = Gravity.CENTER
//                }
//                controls.videoViewer.controls.player.layoutParams = params
//            }
            .visibilityBinding(controls.photoSaveButton, viewModel.playlist.photoRotation.map { it!=0 }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
//            .bindCommand(viewModel.playlist.commandNext, controls.imageNextButton)
//            .bindCommand(viewModel.playlist.commandPrev, controls.imagePrevButton)
            .bindCommand(viewModel.fullscreenCommand, controls.expandButton, true)
            .bindCommand(viewModel.fullscreenCommand, controls.collapseButton, false)
//            .bindCommand(viewModel.rotateCommand, controls.imageRotateLeftButton, Rotation.LEFT)
//            .bindCommand(viewModel.rotateCommand, controls.imageRotateRightButton, Rotation.RIGHT)
//            .bindCommand(viewModel.rotateCommand, this::onRotate)
            .bindCommand(viewModel.rotateCommand, Pair(controls.imageRotateLeftButton,Rotation.LEFT), Pair(controls.imageRotateRightButton, Rotation.RIGHT)) // { onRotate(it) }
            .bindCommand(viewModel.saveBitmapCommand, controls.photoSaveButton)
            .bindCommand(viewModel.ensureVisibleCommand,this::ensureVisible)
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
//            .bindCommand(viewModel.playerControllerModel.commandPlayerTapped, ::onPlayerTapped)
            .add {
                viewModel.playerControllerModel.windowMode.disposableObserve(this, ::onWindowModeChanged)
            }
            .recyclerViewGestureBinding(controls.listView, viewModel.playlist.collection, R.layout.list_item, dragToMove = false, swipeToDelete = true, deletionHandler = ::onDeletingItem) { itemBinder, views, item->
                val textView = views.findViewById<TextView>(R.id.text_view)
                val sizeView = views.findViewById<TextView>(R.id.size_view)
                val durationView = views.findViewById<TextView>(R.id.duration_view)
                val iconView = views.findViewById<ImageView>(R.id.icon_view)
                val markView = views.findViewById<ImageView>(R.id.icon_mark)
                val ratingView = views.findViewById<ImageView>(R.id.icon_rating)
                val cloudView = views.findViewById<ImageView>(R.id.icon_cloud)
                val isVideo = item.isVideo
                views.isSelected = false
                views.tag = item
                textView.text = item.nameForDisplay
                sizeView.text = formatSize(item.size)
                if(!isVideo) {
                    durationView.visibility = View.GONE
                } else {
                    durationView.text = formatTime(item.duration,item.duration)
                    durationView.visibility = View.VISIBLE
                }
                iconView.setImageDrawable(if(isVideo) icVideo else icPhoto)
                val markIcon = when(item.mark) {
                    Mark.None -> null
                    Mark.Star -> icMarkStar
                    Mark.Flag -> icMarkFlag
                    Mark.Check -> icMarkCheck
                }
                markView.setImageDrawable(markIcon)
                val ratingIcon = when(item.rating) {
                    Rating.RatingNone -> null
                    Rating.Rating1 -> icRating1
                    Rating.Rating2 -> icRating2
                    Rating.Rating3 -> icRating3
                    Rating.Rating4 -> icRating4
                }
                ratingView.setImageDrawable(ratingIcon)

                val cloudIcon = when(item.cloud) {
                    CloudStatus.Local-> null
                    CloudStatus.Uploaded->icCloud
                    CloudStatus.Cloud->icCloudFull
                }
                cloudView.setImageDrawable(cloudIcon)
//                val tint = AppCompatResources.getColorStateList(this@PlayerActivity, R.color.color_icon_primary)

                itemBinder
                    .owner(this)
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
                        logger.debug("${item.name} isSelected = $hit")
                        views.background = if(hit) selectedColor else normalColor

//                        iconView.isSelected = hit
//                        assert(views.tag === item)
//                        if(views.isSelected!=hit) {
//                            lifecycleScope.launch {
//                                if (hit) {
//                                    delay(10)
//                                    views.isSelected = hit
//                                    logger.debug("select: ${item.name}")
//                                } else {
//                                    delay(50)
//                                    views.isSelected = hit
//                                    logger.debug("unselect: ${item.name}")
//                                }
//                            }
//                        }
                    }

            }

        controls.videoViewer.bindViewModel(viewModel.playerControllerModel, binder)

        gestureInterpreter.setup(this, controls.viewerArea) {
            onScroll(manipulator::onScroll)
            onScale(manipulator::onScale)
            onTap(manipulator::onTap)
            onDoubleTap(manipulator::onDoubleTap)
            onFlickVertical(manipulator::onFlick)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON  // スリープしない
              or WindowManager.LayoutParams.FLAG_SECURE)         // タスクマネージャに表示させない、キャプチャー禁止

        ensureVisible()

        DBChange.observable.onEach(::onDataChanged).launchIn(lifecycleScope)

//        val wm = getSystemService(WIFI_SERVICE) as WifiManager
//        val ip = wm.connectionInfo

//        lifecycleScope.launch {
//            val ip = NetworkUtils.getIpAddress(applicationContext)
//            logger.info(ip)
//            server.start()
//        }
    }

    private suspend fun onDataChanged(c:DBChange) {
        when(c.type) {
            DBChange.Type.Add -> {
                val item = MetaDB.itemAt(c.itemId)?.toItemEx() ?: return
                viewModel.playlist.addItem(item)
            }
            DBChange.Type.Update -> {
                val item = MetaDB.itemAt(c.itemId)?.toItemEx() ?: return
                viewModel.playlist.replaceItem(item)
            }
            DBChange.Type.Delete -> {
                val item = MetaDB.itemAt(c.itemId)?.toItemEx() ?: return
                viewModel.playlist.removeItem(item)
            }
            DBChange.Type.Refresh -> {
                viewModel.playlist.refreshList()
            }
        }
    }

    fun ensureVisible() {
        val index = viewModel.currentIndex
        if(index>=0) {
            controls.listView.scrollToPosition(index)
        }
    }

    private fun sizeInKb(size: Long): String {
        return String.format("%,d KB", size / 1000L)
    }

    private suspend fun ensureSelectItem(name:String, update:Boolean=false) {
        val item = MetaDB.itemExOf(name) ?: return
        if(update) {
            viewModel.playlist.replaceItem(item)
        }
        viewModel.playlist.select(item)
    }

    private fun startEditing(item:ItemEx) {
        UtImmortalSimpleTask.run("dealItem") {
            viewModel.playerControllerModel.commandPause.invoke()
            val vm = ItemDialog.ItemViewModel.createBy(this, item)
            if(showDialog(taskName) { ItemDialog() }.status.ok) {
                vm.saveIfNeed()
                when(vm.nextAction) {
                    ItemDialog.ItemViewModel.NextAction.EditItem -> editItem(item)
                    ItemDialog.ItemViewModel.NextAction.BackupItem -> MetaDB.backupToCloud(item)
                    ItemDialog.ItemViewModel.NextAction.RemoveLocal -> MetaDB.removeUploadedFile(item)
                    ItemDialog.ItemViewModel.NextAction.RestoreLocal -> MetaDB.restoreFromCloud(item)
                    else -> {}
                }
            }
            true
        }
    }

    private suspend fun UtImmortalSimpleTask.editItem(item:ItemEx) {
        viewModel.playlist.select(null)
        viewModel.playerControllerModel.playerModel.killPlayer()
        controls.videoViewer.dissociatePlayer()
        val name = editorActivityBroker.invoke(item.name)
        (getActivity() as? PlayerActivity)?.let { activity ->
            ensureSelectItem(item.name, name != null)
        }
    }

//    private suspend fun UtImmortalSimpleTask.removeLocalFile(item:ItemEx) {
//        if(item.cloud != CloudStatus.Uploaded) return
//        MetaDB.removeUploadedFile(item)
//        (getActivity() as? PlayerActivity)?.itemUpdated(item.name)
//    }

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
                    lifecycleScope.launch { MetaDB.deleteFile(item) }
                } catch(e:Throwable) {
                    TpLib.logger.error(e)
                }
            }

            override fun rollback() {
                viewModel.playlist.select(item)
            }
        }
    }

//    private fun onPlayerTapped() {
//        when(viewModel.playerControllerModel.windowMode.value) {
//            PlayerControllerModel.WindowMode.FULLSCREEN -> {
//                viewModel.playerControllerModel.showControlPanel.toggle()
//            }
//            else -> {
//                viewModel.playerControllerModel.playerModel.togglePlay()
//            }
//        }
//    }

    private fun onWindowModeChanged(mode:PlayerControllerModel.WindowMode) {
        when(mode) {
            PlayerControllerModel.WindowMode.FULLSCREEN -> {
                controls.listPanel.visibility = View.GONE
                viewModel.playerControllerModel.showControlPanel.value = false
                hideStatusBar()
            }
            else-> {
                controls.listPanel.visibility = View.VISIBLE
                viewModel.playerControllerModel.showControlPanel.value = true
                showStatusBar()
            }
        }
    }

//    override fun onGestureFling(dir: FlingDirection): Boolean {
//        when(dir) {
//            FlingDirection.Left->viewModel.playlist.commandNext.invoke()
//            FlingDirection.Right->viewModel.playlist.commandPrev.invoke()
//            else -> return false
//        }
//        return true
//    }
//    fun onGestureSwipe(dx: Float, dy: Float, end:Boolean): Boolean {
//        controls.imageView.translationX  -= dx
//        controls.imageView.translationY  -= dy
//        return true
//    }
//
//    fun onGestureZoom(scale: Float, end:Boolean): Boolean {
//        val newScale = (controls.imageView.scaleX * scale).run {
//            max(1f, min(10f, this))
//        }
//        controls.imageView.scaleX = newScale
//        controls.imageView.scaleY = newScale
//        return true
//    }

//    override fun onPause() {
//        controls.videoViewer.visibility = View.INVISIBLE
//        super.onPause()
//    }

    override fun onResume() {
        super.onResume()
//        controls.videoViewer.visibility = View.VISIBLE
        if(viewModel.playerControllerModel.playerModel.revivePlayer()) {
            controls.videoViewer.associatePlayer()
            lifecycleScope.launch { viewModel.playlist.refreshList() }
        }
    }

//    override fun onDestroy() {
//        super.onDestroy()
//    }

    override fun handleKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
        // return super.handleKeyEvent(keyCode, event)
        if(keyCode == KeyEvent.KEYCODE_BACK && viewModel.playerControllerModel.windowMode.value == PlayerControllerModel.WindowMode.FULLSCREEN) {
            viewModel.playerControllerModel.setWindowMode(PlayerControllerModel.WindowMode.NORMAL)
            return true
        }
        return false
    }

    companion object {
        val logger = UtLog("PlayerActivity", null, PlayerActivity::class.java)
    }
}
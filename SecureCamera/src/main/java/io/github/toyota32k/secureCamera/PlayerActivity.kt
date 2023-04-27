package io.github.toyota32k.secureCamera

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.toyota32k.bindit.*
import io.github.toyota32k.bindit.command.LongClickCommand
import io.github.toyota32k.bindit.command.LongClickUnitCommand
import io.github.toyota32k.bindit.list.ObservableList
import io.github.toyota32k.boodroid.common.getAttrColor
import io.github.toyota32k.boodroid.common.getAttrColorAsDrawable
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.formatSize
import io.github.toyota32k.lib.player.model.*
import io.github.toyota32k.secureCamera.ScDef.PHOTO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.PHOTO_PREFIX
import io.github.toyota32k.secureCamera.ScDef.VIDEO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.VIDEO_PREFIX
import io.github.toyota32k.secureCamera.databinding.ActivityPlayerBinding
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.utils.*
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.bindCommand
import io.github.toyota32k.utils.disposableObserve
import io.github.toyota32k.utils.onTrue
import kotlinx.coroutines.flow.*
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
        }

    }

    class PlayerViewModel(application: Application): AndroidViewModel(application) {
        companion object {
            fun filename2date(filename:String): Date? {
                val dateString = when {
                    filename.startsWith(PHOTO_PREFIX)-> filename.substringAfter(PHOTO_PREFIX).substringBefore(PHOTO_EXTENSION)
                    filename.startsWith(VIDEO_PREFIX)-> filename.substringAfter(VIDEO_PREFIX).substringBefore(VIDEO_EXTENSION)
                    else -> return null
                }
                return try { ITcUseCase.dateFormatForFilename.parse(dateString) } catch(e:Throwable) { Date() }
            }
        }

        private val context: Application
            get() = getApplication()
        val playlist = Playlist()
        val playerControllerModel = PlayerControllerModel.Builder(application, viewModelScope)
            .supportFullscreen()
            .supportPlaylist(playlist,autoPlay = false,continuousPlay = false)
            .supportSnapshot(::onSnapshot)
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

        inner class VideoSource(override val name:String) : IMediaSource {
            val file: File = File(context.filesDir, name)
            override val id: String
                get() = name
            override val uri: String
                get() = file.toUri().toString()
            override val trimming: Range = Range.empty
            override val type: String
                get() = name.substringAfterLast(".", "")
            override var startPosition = AtomicLong()
//            override val disabledRanges: List<Range> = emptyList()
        }
        inner class Playlist : IMediaFeed, IUtPropOwner {
            val collection = ObservableList<String>()
            val sorter = io.github.toyota32k.shared.UtSorter(
                collection,
                allowDuplication = true
            ) { a, b ->
                val ta = filename2date(a)?.time ?: 0L
                val tb = filename2date(b)?.time ?: 0L
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

            val currentSelection:StateFlow<String?> = MutableStateFlow<String?>(null)
            var photoSelection:String? = null
            var videoSelection:String? = null
            val isVideo: StateFlow<Boolean> = currentSelection.map { it?.endsWith(VIDEO_EXTENSION) == true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
            val isPhoto: StateFlow<Boolean> = currentSelection.map { it?.endsWith(PHOTO_EXTENSION) == true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
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
            fun select(name:String?) {
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

                if(name==null) {
                    currentSource.value = null
                    photoRotation.mutable.value = 0
                    photoBitmap.mutable.value = null
                    currentSelection.mutable.value = null
                    return
                }

                val index = collection.indexOf(name).takeIf { it>=0 } ?: 0
                val item = collection[index]
                currentSelection.mutable.value = item
                if(item.endsWith(VIDEO_EXTENSION)) {
                    videoSelection = item
                    photoRotation.mutable.value = 0
                    photoBitmap.mutable.value = null
                    playerControllerModel.playerModel.rotate(Rotation.NONE)
                    currentSource.value = VideoSource(item)
                } else {
                    photoSelection = item
                    currentSource.value = null
                    photoRotation.mutable.value = 0
                    photoBitmap.mutable.value = BitmapFactory.decodeFile(File(context.filesDir, item).path)
                }
                hasPrevious.mutable.value = index>0
                hasNext.mutable.value = index<collection.size-1
            }

            private fun setListMode(mode:ListMode) {
                val newList = when(mode) {
                    ListMode.VIDEO->context.fileList().filter {it.endsWith(VIDEO_EXTENSION) }
                    ListMode.PHOTO->context.fileList().filter {it.endsWith(PHOTO_EXTENSION) }
                    ListMode.ALL->context.fileList().filter { it.endsWith(VIDEO_EXTENSION) || it.endsWith(PHOTO_EXTENSION)}
                }
                setFileList(newList, mode)
            }

            private fun setFileList(list:Collection<String>, newMode:ListMode) {
                val current = currentSource.value
                sorter.replace(list)
                listMode.value = newMode
                when(newMode) {
                    ListMode.ALL->  select(current?.name)
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
                val filename = currentSelection.value ?: return
                val bitmap = photoBitmap.value ?: return
                val file = File(context.filesDir, filename)
                file.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                    it.flush()
                }
                photoRotation.mutable.value = 0
            }
        }
        
        private fun onSnapshot(pos:Long, bitmap: Bitmap) {
            try {
                val current = playlist.currentSelection.value ?: return
                val orgDate = filename2date(current) ?: return
                val date = Date(orgDate.time + pos)
                val filename = ITcUseCase.defaultFileName(PHOTO_PREFIX, PHOTO_EXTENSION, date)
                val file = File(context.filesDir, filename)
                file.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                    it.flush()
                }
                if(playlist.listMode.value!=ListMode.VIDEO) {
                    playlist.sorter.add(filename)
                }
            } catch(e:Throwable) {
                TpLib.logger.error(e)
            } finally {
                bitmap.recycle()
            }
        }

        override fun onCleared() {
            super.onCleared()
            playerControllerModel.close()
        }
    }

    private val viewModel by viewModels<PlayerViewModel>()
    lateinit var controls: ActivityPlayerBinding
    val binder = Binder()
    val gestureInterpreter:UtGestureInterpreter by lazy { UtGestureInterpreter(this@PlayerActivity, enableScaleEvent = true) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controls = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(controls.root)
        hideActionBar()

        val normalColor: Drawable
        val normalTextColor: Int
        val selectedColor: Drawable
        val selectedTextColor: Int

        theme!!.apply {
            normalColor = getAttrColorAsDrawable(com.google.android.material.R.attr.colorSurface, Color.WHITE)
            normalTextColor = getAttrColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
            selectedColor = getAttrColorAsDrawable(com.google.android.material.R.attr.colorSecondary, Color.BLUE)
            selectedTextColor = getAttrColor(com.google.android.material.R.attr.colorOnSecondary, Color.WHITE)
        }
        val icPhoto = AppCompatResources.getDrawable(this, R.drawable.ic_type_photo)!!
        val icVideo = AppCompatResources.getDrawable(this, R.drawable.ic_type_video)!!
        val icPhotoSel = TintDrawable.tint(icPhoto, selectedTextColor)
        val icVideoSel = TintDrawable.tint(icVideo, selectedTextColor)
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
            .genericBinding(controls.imageView,viewModel.playlist.photoBitmap) { view, bitmap->
                view.setImageBitmap(bitmap)
            }
            .headlessBinding(viewModel.playlist.currentSelection) {
                manipulator.agent.resetScrollAndScale()
            }
//            .bindCommand(viewModel.playerControllerModel.commandPlayerTapped, ::onPlayerTapped)
            .add {
                viewModel.playerControllerModel.windowMode.disposableObserve(this, ::onWindowModeChanged)
            }
            .recyclerViewGestureBinding(controls.listView, viewModel.playlist.collection, R.layout.list_item, dragToMove = false, swipeToDelete = true, deletionHandler = ::onDeletingItem) { itemBinder, views, name->
                val textView = views.findViewById<TextView>(R.id.text_view)
                val sizeView = views.findViewById<TextView>(R.id.size_view)
                val iconView = views.findViewById<ImageView>(R.id.icon_view)
                val isVideo = name.endsWith(VIDEO_EXTENSION)
                textView.text = name
                sizeView.text = "${formatSize(File(filesDir, name).length())}"
                itemBinder
                    .owner(this)
                    .bindCommand(LiteUnitCommand { viewModel.playlist.select(name)}, views)
                    .bindCommand(LongClickUnitCommand { startEditing(views, name) }, views )
                    .headlessNonnullBinding(viewModel.playlist.currentSelection.map { it == name }) { hit->
                        if(hit) {
                            views.background = selectedColor
                            textView.setTextColor(selectedTextColor)
                            iconView.setImageDrawable(if(isVideo) icVideoSel else icPhotoSel)
                        } else {
                            views.background = normalColor
                            textView.setTextColor(normalTextColor)
                            iconView.setImageDrawable(if(isVideo) icVideo else icPhoto)
                        }
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
    }

    private fun startEditing(anchor:View, name:String) {
        PopupMenu(this, anchor).apply {
            menu.add(R.string.start_editing)
            setOnMenuItemClickListener {
                viewModel.playlist.select(null)
                viewModel.playerControllerModel.playerModel.killPlayer()
                controls.videoViewer.dissociatePlayer()
                val intent = Intent(this@PlayerActivity, EditorActivity::class.java).apply { putExtra(EditorActivity.KEY_FILE_NAME, name) }
                startActivity(intent)
                true
            }
            show()
        }

    }

    inner class ViewerManipulator : IUtManipulationTarget {
        val agent = UtManipulationAgent(this)
        fun onScroll(event: UtGestureInterpreter.IScrollEvent) {
            agent.onScroll(event)
//            controls.imageView.translationX  -= event.dx
//            controls.imageView.translationY  -= event.dy
        }

        fun onScale(event: UtGestureInterpreter.IScaleEvent) {
            agent.onScale(event)
//            val newScale = (controls.imageView.scaleX * event.scale).run {
//                max(1f, min(10f, this))
//            }
//            controls.imageView.scaleX = newScale
//            controls.imageView.scaleY = newScale
        }
        fun onTap(@Suppress("UNUSED_PARAMETER") event:UtGestureInterpreter.IPositionalEvent) {
            if(viewModel.playlist.isVideo.value) {
                viewModel.playerControllerModel.playerModel.togglePlay()
            }
        }
        fun onDoubleTap(@Suppress("UNUSED_PARAMETER") event:UtGestureInterpreter.IPositionalEvent) {
            contentView.translationX = 0f
            contentView.translationY = 0f
            contentView.scaleX = 1f
            contentView.scaleY = 1f
        }
//        fun onLongTap() {
//            if(viewModel.playerControllerModel.windowMode.value == PlayerControllerModel.WindowMode.FULLSCREEN) {
//                viewModel.playerControllerModel.showControlPanel.toggle()
//            }
//        }

        fun onFlick(eventIFlickEvent: UtGestureInterpreter.IFlickEvent) {
            if(viewModel.playerControllerModel.windowMode.value == PlayerControllerModel.WindowMode.FULLSCREEN) {
                viewModel.playerControllerModel.showControlPanel.value =
                    eventIFlickEvent.direction == Direction.Start
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
            return if(orientation==Orientation.Horizontal) {
                when(dir) {
                    Direction.Start-> viewModel.playlist.hasPrevious.value.onTrue { viewModel.playlist.previous() }
                    Direction.End-> viewModel.playlist.hasNext.value.onTrue { viewModel.playlist.next() }
                }
            } else false
        }

        override fun hasNextPage(orientation: Orientation, dir: Direction): Boolean {
            return if(orientation==Orientation.Horizontal) {
                when(dir) {
                    Direction.Start-> viewModel.playlist.hasPrevious.value
                    Direction.End-> viewModel.playlist.hasNext.value
                }
            } else false
        }
    }
    val manipulator = ViewerManipulator()
    
    private fun onDeletingItem(item:String):RecyclerViewBinding.IPendingDeletion {
        if(item == viewModel.playlist.currentSelection.value) {
            viewModel.playlist.select(null)
        }
        return object:RecyclerViewBinding.IPendingDeletion {
            override val itemLabel: String get() = item
            override val undoButtonLabel: String? = null  // default で ok

            override fun commit() {
                try {
                    TpLib.logger.debug("deleted $item")
                    deleteFile(item)
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
            }
            else-> {
                controls.listPanel.visibility = View.VISIBLE
                viewModel.playerControllerModel.showControlPanel.value = true
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
    override fun onResume() {
        super.onResume()
        if(viewModel.playerControllerModel.playerModel.revivePlayer()) {
            controls.videoViewer.associatePlayer()
        }
    }

    override fun handleKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
        // return super.handleKeyEvent(keyCode, event)
        if(keyCode == KeyEvent.KEYCODE_BACK && viewModel.playerControllerModel.windowMode.value == PlayerControllerModel.WindowMode.FULLSCREEN) {
            viewModel.playerControllerModel.setWindowMode(PlayerControllerModel.WindowMode.NORMAL)
            return true
        }
        return false
    }
}
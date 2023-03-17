package io.github.toyota32k.secureCamera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.bindit.*
import io.github.toyota32k.bindit.list.ObservableList
import io.github.toyota32k.boodroid.common.getAttrColor
import io.github.toyota32k.boodroid.common.getAttrColorAsDrawable
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.model.*
import io.github.toyota32k.secureCamera.ScDef.PHOTO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.PHOTO_PREFIX
import io.github.toyota32k.secureCamera.ScDef.VIDEO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.VIDEO_PREFIX
import io.github.toyota32k.secureCamera.databinding.ActivityPlayerBinding
import io.github.toyota32k.secureCamera.utils.*
import io.github.toyota32k.utils.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicLong

fun <T> Binder.bindCommandXxx(cmd:ICommand<T>, vararg views:Pair<View,T>): Binder {
    views.forEach {  pair->
        add(cmd.attachView(pair.first, pair.second))
    }
    return this
}

fun <T> Binder.bindCommandXxx(cmd:ICommand<T>, view:View, param:T): Binder
        = bindCommandXxx(cmd, Pair(view,param))

fun <T> Binder.bindCommandXxx(owner: LifecycleOwner, cmd:ICommand<T>, callback:(T)->Unit): Binder {
    add(cmd.bind(owner,callback))
    return this
}

fun <T> Binder.bindCommandXxx(owner: LifecycleOwner, cmd:ICommand<T>, vararg views:Pair<View,T>, callback:(T)->Unit): Binder {
    bindCommandXxx(cmd, *views)
    add(cmd.bind(owner,callback))
    return this
}

fun <T> Binder.bindCommandXxx(cmd:ICommand<T>, callback:(T)->Unit): Binder
        = bindCommandXxx(requireOwner, cmd, callback)

fun <T> Binder.bindCommandXxx(cmd:ICommand<T>, vararg views:Pair<View,T>, callback:(T)->Unit): Binder
        = bindCommandXxx(requireOwner, cmd, views=views, callback)

fun <T> Binder.bindCommandXxx(cmd:ICommand<T>, view:View, param:T, callback:(T)->Unit): Binder
        = bindCommandXxx(requireOwner, cmd, Pair(view,param), callback=callback)


fun Binder.bindCommandXxx(cmd:IUnitCommand, vararg views:View): Binder {
    views.forEach { view->
        add(cmd.attachView(view))
    }
    return this
}

fun Binder.bindCommandXxx(owner: LifecycleOwner, cmd:IUnitCommand, callback:()->Unit): Binder
        = add(cmd.bind(owner,callback))

fun Binder.bindCommandXxx(owner: LifecycleOwner, cmd:IUnitCommand, vararg views:View, callback:()->Unit): Binder
        = bindCommandXxx(cmd,*views).add(cmd.bind(owner,callback))

fun Binder.bindCommandXxx(cmd:IUnitCommand, callback:()->Unit): Binder
        = add(cmd.bind(requireOwner,callback))

fun Binder.bindCommandXxx(cmd:IUnitCommand, vararg views:View, callback:()->Unit): Binder
        = bindCommandXxx(requireOwner, cmd, views=views, callback)









//fun Binder.bindCommandXxx(cmd:IUnitCommand, vararg views:View): Binder {
//    views.forEach { view->
//        add(cmd.attachView(view))
//    }
//    return this
//}
//fun Binder.bindCommandXxx(owner: LifecycleOwner, cmd:IUnitCommand, vararg views:View, callback:()->Unit): Binder
//        = bindCommandXxx(cmd,*views).add(cmd.bind(owner,callback))
//
//fun Binder.bindCommandXxx(cmd:IUnitCommand, vararg views:View, callback:()->Unit): Binder
//        = bindCommandXxx(requireOwner, cmd, views=views, callback)

class PlayerActivity : AppCompatActivity() {

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
                return ITcUseCase.dateFormatForFilename.parse(dateString)
            }
        }

        private val context: Application
            get() = getApplication()
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
            override val disabledRanges: List<Range> = emptyList()
        }
        inner class Playlist : IMediaFeed, IUtPropOwner {
            val collection = ObservableList<String>()
            val sorter = Sorter(collection, allowDuplication = true) { a,b-> ((filename2date(a)?.time?:0) - (filename2date(b)?.time?:0)).toInt() }
            val isVideo: StateFlow<Boolean> = MutableStateFlow(false)
            val photoBitmap: StateFlow<Bitmap?> = MutableStateFlow(null)

            val currentSelection:StateFlow<String?> = MutableStateFlow<String?>(null)
            var photoSelection:String? = null
            var videoSelection:String? = null
            val listMode = MutableStateFlow(ListMode.ALL)

            override val currentSource = MutableStateFlow<IMediaSource?>(null)
            override val hasNext = MutableStateFlow(false)
            override val hasPrevious = MutableStateFlow(false)

            val commandNext = LiteUnitCommand(::next)
            val commandPrev = LiteUnitCommand(::previous)
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
                    isVideo.mutable.value = false
                    currentSelection.mutable.value = null
                    return
                }

                if(name==null) {
                    currentSource.value = null
                    photoRotation.mutable.value = 0
                    photoBitmap.mutable.value = null
                    isVideo.mutable.value = false
                    currentSelection.mutable.value = null
                    return
                }

                val index = collection.indexOf(name).takeIf { it>=0 } ?: 0
                val item = collection[index]
                currentSelection.mutable.value = item
                if(item.endsWith(VIDEO_EXTENSION)) {
                    videoSelection = item
                    isVideo.mutable.value = true
                    photoRotation.mutable.value = 0
                    photoBitmap.mutable.value = null
                    currentSource.value = VideoSource(item)
                } else {
                    photoSelection = item
                    currentSource.value = null
                    isVideo.mutable.value = false
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

        val playlist = Playlist()
        val playerControllerModel = PlayerControllerModel.Builder(application)
            .supportFullscreen()
            .supportPlaylist(playlist,autoPlay = false,continuousPlay = false)
            .supportSnapshot(::onSnapshot)
            .enableRotateRight()
            .enableRotateLeft()
            .build()
        //val playerModel get() = playerControllerModel.playerModel

        val fullscreenCommand = LiteCommand<Boolean> {
            playerControllerModel.setWindowMode( if(it) PlayerControllerModel.WindowMode.FULLSCREEN else PlayerControllerModel.WindowMode.NORMAL )
        }
        val rotateCommand = LiteCommand<Rotation>(playlist::rotateBitmap)
        val saveBitmapCommand = LiteUnitCommand(playlist::saveBitmap)

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

        binder.owner(this)
            .materialRadioButtonGroupBinding(controls.listMode, viewModel.playlist.listMode, ListMode.IDResolver)
            .combinatorialVisibilityBinding(viewModel.playlist.isVideo) {
                straightInvisible(controls.videoViewer)
                inverseInvisible(controls.photoViewer)
            }
            .enableBinding(controls.imageNextButton, viewModel.playlist.hasNext)
            .enableBinding(controls.imagePrevButton, viewModel.playlist.hasPrevious)
            .combinatorialVisibilityBinding(viewModel.playerControllerModel.windowMode.map {it==PlayerControllerModel.WindowMode.FULLSCREEN}) {
                straightGone(controls.collapseButton)
                inverseGone(controls.expandButton)
            }
            .visibilityBinding(controls.photoButtonPanel, viewModel.playlist.currentSelection.map {it!=null}, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.photoSaveButton, viewModel.playlist.photoRotation.map { it!=0 }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .bindCommandXxx(viewModel.playlist.commandNext, controls.imageNextButton)
            .bindCommandXxx(viewModel.playlist.commandPrev, controls.imagePrevButton)
            .bindCommandXxx(viewModel.fullscreenCommand, controls.expandButton, true)
            .bindCommandXxx(viewModel.fullscreenCommand, controls.collapseButton, false)
//            .bindCommandXxx(viewModel.rotateCommand, controls.imageRotateLeftButton, Rotation.LEFT)
//            .bindCommandXxx(viewModel.rotateCommand, controls.imageRotateRightButton, Rotation.RIGHT)
//            .bindCommandXxx(viewModel.rotateCommand, this::onRotate)
            .bindCommandXxx(viewModel.rotateCommand, Pair(controls.imageRotateLeftButton,Rotation.LEFT), Pair(controls.imageRotateRightButton, Rotation.RIGHT)) // { onRotate(it) }
            .bindCommand(viewModel.saveBitmapCommand, controls.photoSaveButton)
            .genericBinding(controls.imageView,viewModel.playlist.photoBitmap) { view, bitmap->
                view.setImageBitmap(bitmap)
            }
            .bindCommandXxx(viewModel.playerControllerModel.commandPlayerTapped, ::onPlayerTapped)
            .add {
                viewModel.playerControllerModel.windowMode.disposableObserve(this, ::onWindowModeChanged)
            }
            .recyclerViewGestureBinding(controls.listView, viewModel.playlist.collection, R.layout.list_item, dragToMove = false, swipeToDelete = true, deletionHandler = ::onDeletingItem) { itemBinder, views, name->
                val textView = views.findViewById<TextView>(R.id.text_view)
                val iconView = views.findViewById<ImageView>(R.id.icon_view)
                val isVideo = name.endsWith(VIDEO_EXTENSION)
                textView.text = name
                itemBinder
                    .owner(this)
                    .bindCommandXxx(LiteUnitCommand { viewModel.playlist.select(name)}, views)
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




    }
    
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

    fun MutableStateFlow<Boolean>.toggle() {
        value = !value
    }

    private fun onPlayerTapped() {
        when(viewModel.playerControllerModel.windowMode.value) {
            PlayerControllerModel.WindowMode.FULLSCREEN -> {
                viewModel.playerControllerModel.showControlPanel.toggle()
            }
            else -> {
                viewModel.playerControllerModel.playerModel.togglePlay()
            }
        }
    }

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
}
package io.github.toyota32k.secureCamera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.bindit.*
import io.github.toyota32k.bindit.list.ObservableList
import io.github.toyota32k.boodroid.common.getAttrColor
import io.github.toyota32k.boodroid.common.getAttrColorAsDrawable
import io.github.toyota32k.lib.player.model.IMediaFeed
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.secureCamera.databinding.ActivityPlayerBinding
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.bindCommand
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicLong

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
            val isVideo: StateFlow<Boolean> = MutableStateFlow(false)
            val photoBitmap: StateFlow<Bitmap?> = MutableStateFlow(null)

//            val currentIndex:Int
//                get() = collection.indexOfFirst { it==currentSource.value?.name }
//            val isVideo
//                get() = currentSource.value?.name?.endsWith(".mp4")?:false
//            val isPhoto:Boolean
//                get() = !isVideo

            val currentSelection:StateFlow<String?> = MutableStateFlow<String?>(null)
            var photoSelection:String? = null
            var videoSelection:String? = null
            val listMode = MutableStateFlow(ListMode.ALL)

            override val currentSource = MutableStateFlow<IMediaSource?>(null)
            override val hasNext = MutableStateFlow(false)
            override val hasPrevious = MutableStateFlow(false)

            init {
                listMode.onEach(::setListMode).launchIn(viewModelScope)
            }

            fun select(name:String?) {
                if(collection.isEmpty()) {
                    hasNext.value = false
                    hasPrevious.value = false
                    currentSource.value = null
                    photoBitmap.mutable.value = null
                    isVideo.mutable.value = false
                    currentSelection.mutable.value = null
                    return
                }

                if(name==null) {
                    currentSource.value = null
                    photoBitmap.mutable.value = null
                    isVideo.mutable.value = false
                    currentSelection.mutable.value = null
                    return
                }

                val index = collection.indexOf(name).takeIf { it>=0 } ?: 0
                val item = collection[index]
                currentSelection.mutable.value = item
                if(item.endsWith(".mp4")) {
                    videoSelection = item
                    isVideo.mutable.value = true
                    photoBitmap.mutable.value = null
                    currentSource.value = VideoSource(item)
                } else {
                    photoSelection = item
                    currentSource.value = null
                    isVideo.mutable.value = false
                    photoBitmap.mutable.value = BitmapFactory.decodeFile(File(context.filesDir, item).path)
                }
                hasPrevious.mutable.value = index>0
                hasNext.mutable.value = index<collection.size-1
            }

            private fun setListMode(mode:ListMode) {
                val newList = when(mode) {
                    ListMode.VIDEO->context.fileList().filter {it.endsWith(".mp4") }
                    ListMode.PHOTO->context.fileList().filter {it.endsWith(".jpeg") }
                    ListMode.ALL->context.fileList().toList()
                }
                setFileList(newList, mode)
            }

            private fun setFileList(list:Collection<String>, newMode:ListMode) {
                val current = currentSource.value
                collection.replace(list)
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

            val commandNext = LiteUnitCommand(::next)
            val commandPrev = LiteUnitCommand(::previous)
        }

        val playlist = Playlist()
        val playerControllerModel = PlayerControllerModel.Builder(application)
            .supportFullscreen()
            .supportPlaylist(playlist,autoPlay = false,continuousPlay = false)
            .build()
        //val playerModel get() = playerControllerModel.playerModel

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
            .bindCommand(viewModel.playlist.commandNext, controls.imageNextButton)
            .bindCommand(viewModel.playlist.commandPrev, controls.imagePrevButton)
            .genericBinding(controls.imageView,viewModel.playlist.photoBitmap) { view, bitmap->
                view.setImageBitmap(bitmap)
            }
            .recyclerViewBinding(controls.listView, viewModel.playlist.collection, R.layout.list_item) { itemBinder, views, name->
                val textView = views.findViewById<TextView>(R.id.text_view)
                val iconView = views.findViewById<ImageView>(R.id.icon_view)
                val isVideo = name.endsWith(".mp4")
                textView.text = name
                itemBinder
                    .owner(this)
                    .bindCommand(LiteUnitCommand { viewModel.playlist.select(name)}, views)
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
}
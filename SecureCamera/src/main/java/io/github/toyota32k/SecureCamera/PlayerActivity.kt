package io.github.toyota32k.SecureCamera

import android.app.Application
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.liveData
import io.github.toyota32k.SecureCamera.databinding.ActivityPlayerBinding
import io.github.toyota32k.bindit.*
import io.github.toyota32k.bindit.list.ObservableList
import io.github.toyota32k.lib.player.model.ControlPanelModel
import io.github.toyota32k.utils.Disposer
import io.github.toyota32k.utils.disposableObserve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File

class PlayerActivity : AppCompatActivity() {

    enum class ListMode(val resId:Int) {
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
        val controlPanelModel = ControlPanelModel.create(application, supportFullscreen = true)
        val playerModel get() = controlPanelModel.playerModel
        val fileList:List<File> = application.fileList().map { File(application.filesDir, it) }
        val listMode = MutableStateFlow(ListMode.VIDEO)
        val collection = ObservableList<String>()
        val disposer = Disposer()
        val currentIndex = MutableStateFlow(0)
        var photoSelection:String? = null
        var videoSelection:String? = null

        init {
            disposer.register(
                listMode.disposableObserve(Dispatchers.Main) {mode->
                    when(mode) {
                        ListMode.VIDEO->{
                            collection.replace(application.fileList().filter { it.endsWith(".mp4") })
                            restoreSelection(videoSelection)
                        }
                        ListMode.PHOTO -> {
                            playerModel.pause()
                            collection.replace(application.fileList().filter { it.endsWith(".jpeg") })
                            restoreSelection(photoSelection)
                        }
                    }
                },
                currentIndex.disposableObserve(Dispatchers.Main) { i ->
                    when(listMode.value) {
                        ListMode.VIDEO->{
                            videoSelection = collection[i]
                        }
                        ListMode.PHOTO -> {
                            photoSelection = collection[i]
                        }
                    }
                },
            )
        }

        private fun restoreSelection(name:String?) {
            if(name==null) return
            currentIndex.value = collection.indexOfFirst { it == name }.takeIf {0<=it && it<collection.size} ?: return
        }
        override fun onCleared() {
            super.onCleared()
            controlPanelModel.close()
            disposer.dispose()
        }
    }

    val viewModel by viewModels<PlayerViewModel>()
    lateinit var controls:ActivityPlayerBinding
    val binder = Binder()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controls = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(controls.root)

        binder.owner(this)
            .materialRadioButtonGroupBinding(controls.listMode, viewModel.listMode, ListMode.IDResolver)
            .combinatorialVisibilityBinding(viewModel.listMode.map { it==ListMode.VIDEO}) {
                straightInvisible(controls.playerView)
                inverseInvisible(controls.photoViewer)
            }
            .headlessNonnullBinding(viewModel.listMode) {
                when(it) {
                    ListMode.VIDEO -> {

                    }
                    ListMode.PHOTO -> {
                        viewModel.playerModel.pause()

                    }
                }
                if(it!=ListMode.VIDEO) {
                }
            }
            .visibilityBinding(controls.playerView, viewModel.listMode.map { it==ListMode.VIDEO}, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)
            .visibilityBinding(controls.photoViewer, viewModel.listMode.map { it==ListMode.PHOTO}, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)

        controls.playerView.bindViewModel(viewModel.controlPanelModel, binder)
    }
}
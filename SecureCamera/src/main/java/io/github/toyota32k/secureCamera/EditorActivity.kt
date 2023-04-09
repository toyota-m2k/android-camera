package io.github.toyota32k.secureCamera

import android.app.Application
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.lib.player.model.*
import io.github.toyota32k.lib.player.model.chapter.MutableChapterList
import io.github.toyota32k.secureCamera.databinding.ActivityEditorBinding
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.bindCommand
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class EditorActivity : UtMortalActivity() {
    class EditorViewModel(application: Application) : AndroidViewModel(application) {
        val playerControllerModel = PlayerControllerModel.Builder(application)
            .supportFullscreen()
            .supportChapter()
            .relativeSeekDuration(Settings.Player.spanOfSkipForward, Settings.Player.spanOfSkipBackward)
            .build()
        val playerModel get() = playerControllerModel.playerModel
        val chapterList get() = (playerModel.currentSource.value as? IMediaSourceWithChapter)?.chapterList as? IMutableChapterList
        val commandAddChapter = LiteUnitCommand {
            chapterList?.addChapter(playerModel.currentPosition, "", null)
        }
        val commandRemoveChapter = LiteUnitCommand {
            val neighbor = chapterList?.getNeighborChapters(playerModel.currentPosition) ?: return@LiteUnitCommand
            chapterList?.removeChapterAt(neighbor.next)
        }
        val commandToggleSkip = LiteUnitCommand {
            val chapter = chapterList?.getChapterAround(playerModel.currentPosition) ?: return@LiteUnitCommand
            chapterList?.skipChapter(chapter, !chapter.skip)
        }
        val commandSave = LiteUnitCommand()

        fun setSource(name: String) {
            playerModel.setSource(VideoSource(name), false)
        }

        fun reset() {
            playerModel.reset()
        }

        override fun onCleared() {
            super.onCleared()
            playerControllerModel.close()
        }

        inner class VideoSource(override val name:String) : IMediaSourceWithChapter {
            private val file: File = File(getApplication<Application>().filesDir, name)
            override val id: String
                get() = name
            override val uri: String
                get() = file.toUri().toString()
            override val trimming: Range = Range.empty
            override val type: String
                get() = name.substringAfterLast(".", "")
            override var startPosition = AtomicLong()
            override val chapterList: IChapterList = MutableChapterList()
        }

    }

    private val binder = Binder()
    private val viewModel by viewModels<EditorViewModel>()
    private lateinit var controls: ActivityEditorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_editor)
        controls = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(controls.root)
        binder
            .owner(this)
            .bindCommand(viewModel.commandAddChapter, controls.makeChapter)
            .bindCommand(viewModel.commandRemoveChapter, controls.removeNextChapter)
            .bindCommand(viewModel.commandToggleSkip, controls.makeRegionSkip)
            .bindCommand(viewModel.commandSave, controls.saveVideo)
        controls.videoViewer.bindViewModel(viewModel.playerControllerModel, binder)
        if(savedInstanceState==null) {
            val name = intent.extras?.getString(KEY_FILE_NAME) ?: throw IllegalStateException("no source")
            viewModel.setSource(name)
        }
    }

    override fun onPause() {
        super.onPause()
        if(isFinishing) {
            viewModel.reset()
        }
    }

    companion object {
        const val KEY_FILE_NAME = "video_source"
    }
}
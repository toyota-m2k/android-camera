package io.github.toyota32k.secureCamera

import android.app.Application
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.lib.player.model.*
import io.github.toyota32k.lib.player.model.chapter.MutableChapterList
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IProgress
import io.github.toyota32k.media.lib.format.NoConvertAudioStrategy
import io.github.toyota32k.media.lib.format.NoConvertVideoStrategy
import io.github.toyota32k.secureCamera.databinding.ActivityEditorBinding
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.utils.TimeSpan
import io.github.toyota32k.secureCamera.utils.hideActionBar
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.bindCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class EditorActivity : UtMortalActivity() {
    class EditorViewModel(application: Application) : AndroidViewModel(application) {
        val playerControllerModel = PlayerControllerModel.Builder(application, viewModelScope)
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

//        fun reset() {
//            playerModel.reset()
//        }

        override fun onCleared() {
            super.onCleared()
            logger.debug()
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

        companion object {
            val logger = UtLog("VM", EditorActivity.logger)
        }
    }

    private val binder = Binder()
    private val viewModel by viewModels<EditorViewModel>()
    private lateinit var controls: ActivityEditorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideActionBar()
        //setContentView(R.layout.activity_editor)
        controls = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(controls.root)
        binder
            .owner(this)
            .bindCommand(viewModel.commandAddChapter, controls.makeChapter)
            .bindCommand(viewModel.commandRemoveChapter, controls.removeNextChapter)
            .bindCommand(viewModel.commandToggleSkip, controls.makeRegionSkip)
            .bindCommand(viewModel.commandSave, controls.saveVideo, callback = ::trimmingAndSave)
        controls.videoViewer.bindViewModel(viewModel.playerControllerModel, binder)
        if(savedInstanceState==null) {
            val name = intent.extras?.getString(KEY_FILE_NAME) ?: throw IllegalStateException("no source")
            viewModel.setSource(name)
        }
    }

    private fun formatTime(time:Long, duration:Long) : String {
        val v = TimeSpan(time/1000)
        val t = TimeSpan(duration/1000)
        return when {
            t.hours>0 -> v.formatH()
            t.minutes>0 -> v.formatM()
            else -> v.formatS()
        }
    }

    private fun IProgress.format():String {
        val remaining = if(remainingTime>0) {
            val r = TimeSpan(remainingTime)
            when {
                r.hours>0 -> r.formatH()
                r.minutes>0 -> r.formatM()
                else -> "${remainingTime/1000}\""
            }
        } else null
        return if(remaining!=null) {
            "${percentage} % (${formatTime(current, total)}/${formatTime(total,total)}) -- $remaining left."
        } else "${percentage} % (${formatTime(current, total)}/${formatTime(total,total)})"
    }

    fun stringInKb(size: Long): String {
        return String.format("%,d KB", size / 1000L)
    }

    fun safeDelete(file:File) {
        try {
            file.delete()
        } catch (e:Throwable) {
            logger.error(e)
        }
    }
    fun trimmingAndSave() {
        val srcFile = File(application.filesDir ?: return, (viewModel.playerModel.currentSource.value as? EditorViewModel.VideoSource)?.name ?: return)
        val dstFile = File(application.cacheDir ?: return, "trimming")
        val ranges = viewModel.chapterList?.enabledRanges(Range.empty) ?: return

        UtImmortalSimpleTask.run("trimming") {
            val vm = ProgressDialog.ProgressViewModel.create(taskName)
            vm.message.value = "Trimming Now..."
            val converter = Converter.Factory()
                .input(srcFile)
                .output(dstFile)
                .audioStrategy(NoConvertAudioStrategy)
                .videoStrategy(NoConvertVideoStrategy)
                .addTrimmingRanges(*ranges.map { Converter.Factory.RangeMs(it.start, it.end) }.toTypedArray())
                .setProgressHandler {
                    vm.progress.value = it.percentage
                    vm.progressText.value = it.format()
                }
                .build()
            val awaiter = converter.executeAsync()
            vm.cancelCommand.bindForever { awaiter.cancel() }
            CoroutineScope(Dispatchers.IO).launch {
                val r = awaiter.await()
                try {
                    if (r.succeeded && dstFile.length()>0) {
                        logger.debug("${stringInKb(srcFile.length())} --> ${stringInKb(dstFile.length())}")
                        UtImmortalSimpleTask.run("completeMessage") {
                            showConfirmMessageBox("Completed.", "${stringInKb(srcFile.length())} → ${stringInKb(dstFile.length())}")
                            finish()
                            true
                        }
                        viewModel.playerModel.reset()
                        safeDelete(srcFile)
                        dstFile.renameTo(srcFile)
                    }
                } catch(e:Throwable) {
                    logger.error(e)
                } finally {
                    safeDelete(dstFile)
                }

                withContext(Dispatchers.Main) { vm.closeCommand.invoke(r.succeeded) }
            }
            showDialog(taskName) { ProgressDialog() }.status.ok
        }
    }

    override fun onPause() {
        super.onPause()
        if(isFinishing) {
            viewModel.playerControllerModel.close()
        }
    }

    companion object {
        const val KEY_FILE_NAME = "video_source"
        val logger = UtLog("Editor", null, this::class.java)
    }
}
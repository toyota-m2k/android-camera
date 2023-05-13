package io.github.toyota32k.secureCamera

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.bindit.enableBinding
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.lib.player.model.*
import io.github.toyota32k.lib.player.model.chapter.ChapterEditor
import io.github.toyota32k.lib.player.model.chapter.MutableChapterList
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IProgress
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.secureCamera.databinding.ActivityEditorBinding
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.MetaData
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.utils.TimeSpan
import io.github.toyota32k.secureCamera.utils.hideActionBar
import io.github.toyota32k.secureCamera.utils.hideStatusBar
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
            .supportChapter()
            .supportSnapshot(this::onSnapshot)
            .relativeSeekDuration(Settings.Player.spanOfSkipForward, Settings.Player.spanOfSkipBackward)
            .build()
        val playerModel get() = playerControllerModel.playerModel
        val videoSource get() = playerModel.currentSource.value as VideoSource
        val chapterList by lazy {
            ChapterEditor(videoSource.chapterList as IMutableChapterList)
        }
        val commandAddChapter = LiteUnitCommand {
            chapterList.addChapter(playerModel.currentPosition, "", null)
        }
        val commandRemoveChapter = LiteUnitCommand {
            val neighbor = chapterList.getNeighborChapters(playerModel.currentPosition)
            chapterList.removeChapterAt(neighbor.next)
        }
        val commandToggleSkip = LiteUnitCommand {
            val chapter = chapterList.getChapterAround(playerModel.currentPosition) ?: return@LiteUnitCommand
            chapterList.skipChapter(chapter, !chapter.skip)
        }
        val commandSave = LiteUnitCommand()
        fun setSource(item: MetaData, chapters:List<IChapter>) {
            playerModel.setSource(VideoSource(item), false)
            chapterList.initChapters(chapters)
        }

        val commandUndo = LiteUnitCommand {
            chapterList.undo()
        }
        val commandRedo = LiteUnitCommand {
            chapterList.redo()
        }

        private fun onSnapshot(pos:Long, bitmap: Bitmap) {
            val source = (playerModel.currentSource.value as? VideoSource)?.item ?: return
            CoroutineScope(Dispatchers.IO).launch {
                PlayerActivity.PlayerViewModel.takeSnapshot(source, pos, bitmap)
            }
        }

        override fun onCleared() {
            super.onCleared()
            logger.debug()
            playerControllerModel.close()
        }

        inner class VideoSource(val item: MetaData) : IMediaSourceWithChapter {
            override val name:String
                get() = item.name
            private val file: File = item.file(getApplication())
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
        hideStatusBar()

        controls = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(controls.root)

        binder
            .owner(this)
            .bindCommand(viewModel.commandAddChapter, controls.makeChapter)
            .bindCommand(viewModel.commandRemoveChapter, controls.removeNextChapter)
            .bindCommand(viewModel.commandToggleSkip, controls.makeRegionSkip)
            .bindCommand(viewModel.commandSave, controls.saveVideo, callback = ::trimmingAndSave)
            .bindCommand(viewModel.commandUndo, controls.undo)
            .bindCommand(viewModel.commandRedo, controls.redo)

        if(savedInstanceState==null) {
            lifecycleScope.launch {
                val name = intent.extras?.getString(KEY_FILE_NAME) ?: throw IllegalStateException("no source")
                val item = MetaDB.itemOf(name) ?: throw IllegalStateException("no item")
                val chapters = MetaDB.getChaptersFor(item)
                viewModel.setSource(item, chapters)
                binder
                    .enableBinding(controls.redo, viewModel.chapterList.canRedo)
                    .enableBinding(controls.undo, viewModel.chapterList.canUndo)
            }
        } else {
            binder
                .enableBinding(controls.redo, viewModel.chapterList.canRedo)
                .enableBinding(controls.undo, viewModel.chapterList.canUndo)
        }

        controls.videoViewer.bindViewModel(viewModel.playerControllerModel, binder)
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
            "$percentage % (${formatTime(current, total)}/${formatTime(total,total)}) -- $remaining left."
        } else "$percentage % (${formatTime(current, total)}/${formatTime(total,total)})"
    }

    private fun stringInKb(size: Long): String {
        return String.format("%,d KB", size / 1000L)
    }

    private fun safeDelete(file:File) {
        try {
            file.delete()
        } catch (e:Throwable) {
            logger.error(e)
        }
    }
    private fun trimmingAndSave() {
        val targetItem = (viewModel.playerModel.currentSource.value as? EditorViewModel.VideoSource)?.item ?: return
        val srcFile = targetItem.file(application)
        val dstFile = File(application.cacheDir ?: return, "trimming")
        val ranges = viewModel.chapterList.enabledRanges(Range.empty)

        UtImmortalSimpleTask.run("trimming") {
            val vm = ProgressDialog.ProgressViewModel.create(taskName)
            vm.message.value = "Trimming Now..."
            val converter = Converter.Factory()
                .input(srcFile)
                .output(dstFile)
                .audioStrategy(PresetAudioStrategies.AACDefault)
                .videoStrategy(PresetVideoStrategies.HEVC1080Profile)
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
                    val srcLen = srcFile.length()
                    val dstLen = dstFile.length()
                    if (r.succeeded && dstLen>0) {
                        logger.debug("${stringInKb(srcLen)} --> ${stringInKb(dstLen)}")
                        withContext(Dispatchers.Main) { viewModel.playerModel.reset() }
                        safeDelete(srcFile)
                        dstFile.renameTo(srcFile)
                        MetaDB.updateFile(ItemEx(targetItem, viewModel.chapterList.defrag()))
//                        val testFile = File(filesDir, "mov-2030.01.01-00:00:00.mp4")
//                        safeDelete(testFile)
//                        dstFile.renameTo(testFile)
                        UtImmortalSimpleTask.run("completeMessage") {
                            showConfirmMessageBox("Completed.", "${stringInKb(srcLen)} → ${stringInKb(dstLen)}")
                            getActivity()?.finish()
                            true
                        }
                    } else if(!r.cancelled) {
                        val msg = r.errorMessage ?: r.exception?.message ?: "unknown error"
                        UtImmortalSimpleTask.run("errorMessage") {
                            showConfirmMessageBox("Error.", msg)
                            true
                        }
                    }
                } catch(e:Throwable) {
                    logger.error(e)
                    UtImmortalSimpleTask.run("errorMessage") {
                        showConfirmMessageBox("Something Wrong.", e.localizedMessage ?: e.message ?: "")
                        true
                    }
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

    override fun handleKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
        if(keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
            if(viewModel.chapterList.isDirty) {
                UtImmortalSimpleTask.run {
                    if(showYesNoMessageBox(null, "Chapters are editing. Save changes?")) {
                        MetaDB.setChaptersFor(viewModel.videoSource.item, viewModel.chapterList.chapters)
                    }
                    getActivity()?.finish()
                    true
                }
                return true
            }
        }
        return super.handleKeyEvent(keyCode, event)
    }
    companion object {
        const val KEY_FILE_NAME = "video_source"
        val logger = UtLog("Editor", null, this::class.java)
    }
}
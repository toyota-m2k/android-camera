package io.github.toyota32k.secureCamera

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.longClickBinding
import io.github.toyota32k.dialog.broker.UtActivityBroker
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.dialog.task.getActivity
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.dialog.task.showOkCancelMessageBox
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.chapter.ChapterEditor
import io.github.toyota32k.lib.player.model.chapter.MutableChapterList
import io.github.toyota32k.lib.player.model.skipChapter
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.FastStart
import io.github.toyota32k.media.lib.converter.HttpFile
import io.github.toyota32k.media.lib.converter.HttpInputFile
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.converter.IProgress
import io.github.toyota32k.media.lib.converter.Rotation
import io.github.toyota32k.media.lib.converter.toAndroidFile
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.secureCamera.client.OkHttpStreamSource
import io.github.toyota32k.secureCamera.databinding.ActivityEditorBinding
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.dialog.ReportTextDialog
import io.github.toyota32k.secureCamera.dialog.SelectQualityDialog
import io.github.toyota32k.shared.gesture.UtGestureInterpreter
import io.github.toyota32k.shared.gesture.UtManipulationAgent
import io.github.toyota32k.shared.gesture.UtSimpleManipulationTarget
import io.github.toyota32k.utils.TimeSpan
import io.github.toyota32k.utils.UtLazyResetableValue
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.hideActionBar
import io.github.toyota32k.utils.hideStatusBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class EditorActivity : UtMortalActivity() {
    class EditorViewModel(application: Application) : AndroidViewModel(application) {
        val playerControllerModel = PlayerControllerModel.Builder(application, viewModelScope)
            .supportChapter()
            .supportSnapshot(this::onSnapshot)
            .enableRotateLeft()
            .enableRotateRight()
//            .relativeSeekDuration(Settings.Player.spanOfSkipForward, Settings.Player.spanOfSkipBackward)
            .enableSeekSmall(0,0)
            .enableSeekMedium(1000, 3000)
            .enableSeekLarge(5000, 10000)
            .enableSliderLock(true)
            .counterInMs()
            .build()
        val playerModel get() = playerControllerModel.playerModel
        private val videoSource get() = playerModel.currentSource.value as VideoSource
        val targetItem:ItemEx get() = videoSource.item

        val chapterList by lazy {
            ChapterEditor(videoSource.chapterList as IMutableChapterList)
        }
        val commandAddChapter = LiteUnitCommand {
            chapterList.addChapter(playerModel.currentPosition, "", null)
        }
        val commandAddSkippingChapter = LiteUnitCommand {
            val neighbor = chapterList.getNeighborChapters(playerModel.currentPosition)
            val prev = neighbor.getPrevChapter(chapterList)
            if(neighbor.hit<0) {
                // 現在位置にチャプターがなければ追加する
                if(!chapterList.addChapter(playerModel.currentPosition, "", null)) {
                    return@LiteUnitCommand
                }
            }
            // ひとつ前のチャプターを無効化する
            if(prev!=null) {
                chapterList.skipChapter(prev, true)
            }
        }
        val commandRemoveChapter = LiteUnitCommand {
            val neighbor = chapterList.getNeighborChapters(playerModel.currentPosition)
            chapterList.removeChapterAt(neighbor.next)
        }
        val commandRemoveChapterPrev = LiteUnitCommand {
            val neighbor = chapterList.getNeighborChapters(playerModel.currentPosition)
            chapterList.removeChapterAt(neighbor.prev)
        }
        val commandToggleSkip = LiteUnitCommand {
            val chapter = chapterList.getChapterAround(playerModel.currentPosition) ?: return@LiteUnitCommand
            chapterList.skipChapter(chapter, !chapter.skip)
        }
//        val commandSave = LiteUnitCommand()
        fun setSource(item: ItemEx, chapters:List<IChapter>) {
            resetInputFile()
            playerModel.setSource(VideoSource(item), false)
            chapterList.initChapters(chapters)
        }

        private val resetableInputFile: UtLazyResetableValue<IInputMediaFile> = UtLazyResetableValue {
            if(targetItem.cloud.isFileInLocal) {
                targetItem.file.toAndroidFile()
            } else {
                HttpInputFile(application, OkHttpStreamSource(targetItem.uri)).apply { addRef() }
            }
        }

        private fun resetInputFile() {
            resetableInputFile.reset {
                if(it is HttpInputFile) {
                    it.release()
                }
            }
        }
        val inputFile:IInputMediaFile get() = resetableInputFile.value

        val commandUndo = LiteUnitCommand {
            chapterList.undo()
        }
        val commandRedo = LiteUnitCommand {
            chapterList.redo()
        }
        val blocking = MutableStateFlow(false)

        private fun onSnapshot(pos:Long, bitmap: Bitmap) {
            val source = (playerModel.currentSource.value as? VideoSource)?.item ?: return
            if(!source.cloud.isFileInLocal) return
            CoroutineScope(Dispatchers.IO).launch {
                PlayerActivity.PlayerViewModel.takeSnapshot(source.data, pos, bitmap)
            }
        }

        override fun onCleared() {
            super.onCleared()
            logger.debug()
            resetInputFile()
            playerControllerModel.close()
            HttpInputFile.deleteAllTempFile(getApplication())
        }

        inner class VideoSource(val item: ItemEx) : IMediaSourceWithChapter {
            override val name:String
                get() = item.name
//            private val file: File = item.file
            override val id: String
                get() = name
            override val uri: String
                get() = item.uri
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

    // Scale/Scroll
    private val gestureInterpreter = UtGestureInterpreter(SCApplication.instance, enableScaleEvent = true)
    private val manipulationAgent by lazy { UtManipulationAgent(UtSimpleManipulationTarget(controls.videoViewer,controls.videoViewer.controls.player)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.debug()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setTheme(R.style.Theme_TryCamera_M3_DynamicColor_NoActionBar)

        controls = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(controls.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.editor)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        hideActionBar()
        hideStatusBar()

        binder.owner(this)

        lifecycleScope.launch {
            if(savedInstanceState==null) {
                val name = intent.extras?.getString(KEY_FILE_NAME) ?: throw IllegalStateException("no source")
                val item = MetaDB.itemExOf(name) ?: throw IllegalStateException("no item")
                val chapters = MetaDB.getChaptersFor(item.data)
                viewModel.setSource(item, chapters)
            }
            binder
                .enableBinding(controls.redo, viewModel.chapterList.canRedo)
                .enableBinding(controls.undo, viewModel.chapterList.canUndo)
//                .visibilityBinding(controls.saveVideo, ConstantLiveData(viewModel.targetItem.cloud.isFileInLocal), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .bindCommand(viewModel.commandAddChapter, controls.makeChapter)
                .bindCommand(viewModel.commandAddSkippingChapter, controls.makeChapterAndSkip)
                .bindCommand(viewModel.commandRemoveChapter, controls.removeNextChapter)
                .bindCommand(viewModel.commandRemoveChapterPrev, controls.removePrevChapter)
                .bindCommand(viewModel.commandToggleSkip, controls.makeRegionSkip)
                .clickBinding(controls.saveVideo) { selectQualityAndSave() }
                .longClickBinding(controls.saveVideo) { showVideoProperties() }
                .bindCommand(viewModel.commandUndo, controls.undo)
                .bindCommand(viewModel.commandRedo, controls.redo)
            controls.videoViewer.bindViewModel(viewModel.playerControllerModel, binder)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON  // スリープしない
              or WindowManager.LayoutParams.FLAG_SECURE)         // タスクマネージャに表示させない、キャプチャー禁止

        gestureInterpreter.setup(this, manipulationAgent.parentView) {
            onScale(manipulationAgent::onScale)
            onScroll(manipulationAgent::onScroll)
            // タップによる再生開始・停止は誤操作の原因になって逆に不便だったのでやめる。
//            onTap {
//                viewModel.playerModel.togglePlay()
//            }
            onDoubleTap {
                manipulationAgent.resetScrollAndScale()
            }
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

        fun formatPercent(permillage:Int):String {
            return "${permillage/10}.${permillage%10} %"
        }
        return if(remaining!=null) {
            "${formatPercent(permillage)} (${formatTime(current, total)}/${formatTime(total,total)}) -- $remaining left."
        } else "${formatPercent(permillage)} (${formatTime(current, total)}/${formatTime(total,total)})"
    }

    private fun stringInKb(size: Long): String {
        return String.format(Locale.US, "%,d KB", size / 1000L)
    }

    private fun safeDelete(file:File) {
        try {
            file.delete()
        } catch (e:Throwable) {
            logger.error(e)
        }
    }

    private fun showVideoProperties():Boolean {
        val inFile = if(viewModel.targetItem.cloud.isFileInLocal) {
            viewModel.targetItem.file.toAndroidFile()
        } else {
            HttpFile(viewModel.targetItem.uri)
        }
        val s = Converter.analyze(inFile)
        ReportTextDialog.show(viewModel.targetItem.name, s.toString())
        return true
    }

    private fun selectQualityAndSave():Boolean {
        logger.debug()
        lifecycleScope.launch {
            val quality = SelectQualityDialog.show() ?: return@launch
            trimmingAndSave(quality)
        }
        return true
    }

    private fun trimmingAndSave(reqQuality: SelectQualityDialog.VideoQuality?=null) {
        val quality = reqQuality ?: SelectQualityDialog.VideoQuality.High
        val targetItem = viewModel.targetItem
//        if(!targetItem.cloud.isFileInLocal) return
//        val srcFile = targetItem.file
        val trimFile = File(application.cacheDir ?: return, "trimming")
        val optFile = File(application.cacheDir ?: return, "optimized")
        val ranges = viewModel.chapterList.enabledRanges(Range.empty)
        val strategy = when(quality) {
            SelectQualityDialog.VideoQuality.High -> PresetVideoStrategies.HEVC1080LowProfile
            SelectQualityDialog.VideoQuality.Middle -> PresetVideoStrategies.HEVC720Profile
            SelectQualityDialog.VideoQuality.Low -> PresetVideoStrategies.HEVC720LowProfile
        }
        val srcFile = viewModel.inputFile

//        val s = Converter.analyze(srcFile)
//        logger.debug("input:\n$s")


        UtImmortalSimpleTask.run("trimming") {
            val vm = ProgressDialog.ProgressViewModel.create(taskName)
            vm.message.value = "Trimming Now..."
            val rotation = if(viewModel.playerModel.rotation.value!=0) Rotation(viewModel.playerModel.rotation.value, relative = true) else Rotation.nop
            val converter = Converter.Factory()
                .input(srcFile)
                .output(trimFile)
                .audioStrategy(PresetAudioStrategies.AACDefault)
                .videoStrategy(strategy)
                .rotate(rotation)
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
                vm.message.value = "Optimizing Now..."
                val dstFile:File = if(FastStart.process(trimFile.toUri(), optFile.toUri(), applicationContext) {
                    vm.progress.value = it.percentage
                    vm.progressText.value = it.format()
                }) {
                    safeDelete(trimFile)
                    optFile
                } else {
                    safeDelete(optFile)
                    trimFile
                }
                try {
                    val srcLen = srcFile.getLength().let { if(it<0) targetItem.size else it }
                    val dstLen = dstFile.length()
                    var confirmed = false
                    if (r.succeeded && dstLen>0) {
                        logger.debug("${stringInKb(srcLen)} --> ${stringInKb(dstLen)}")
                        if(quality.compact) {
//                            val s = Converter.analyze(srcFile.toAndroidFile())
//                            logger.debug("input:\n$s")
//                            val d = Converter.analyze(dstFile.toAndroidFile())
//                            logger.debug("output:\n$d")
                            if(!UtImmortalSimpleTask.runAsync("low quality") {
                                showOkCancelMessageBox("${quality.name} Quality Conversion", "${stringInKb(srcLen)} → ${stringInKb(dstLen)}")
                            }) { throw CancellationException("cancelled") }
                            confirmed = true
                        }

                        withContext(Dispatchers.Main) { viewModel.playerModel.reset() }
                        val testOnly = false     // false: 通常の動作（元のファイルに上書き） / true: テストファイルに出力して、元のファイルは変更しない
                        if(testOnly) {
                            MetaDB.withTestFile { testFile ->
                                dstFile.renameTo(testFile)
                            }
                        } else {
                            safeDelete(targetItem.file)
                            dstFile.renameTo(targetItem.file)
                            val adjustedEnabledRange = r.adjustedTrimmingRangeList?.list?.map { Range(it.startUs/1000L, it.endUs/1000L) }
                            val newChapterList = if(!adjustedEnabledRange.isNullOrEmpty()) {
                                viewModel.chapterList.adjustWithEnabledRanges(adjustedEnabledRange)
                            } else {
                                viewModel.chapterList.chapters
                            }
                            MetaDB.updateFile(targetItem, newChapterList)
                        }
                        UtImmortalSimpleTask.run("completeMessage") {
                            if(!confirmed) {
                                showConfirmMessageBox("Completed.","${stringInKb(srcLen)} → ${stringInKb(dstLen)}")
                            }
                            setResultAndFinish(true, targetItem)
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
                    if(e !is CancellationException) {
                        logger.error(e)
                        UtImmortalSimpleTask.run("errorMessage") {
                            showConfirmMessageBox(
                                "Something Wrong.",
                                e.localizedMessage ?: e.message ?: ""
                            )
                            true
                        }
                    }
                } finally {
                    safeDelete(trimFile)
                    safeDelete(optFile)
                }

                withContext(Dispatchers.Main) { vm.closeCommand.invoke(r.succeeded) }
            }
            showDialog(taskName) { ProgressDialog() }.status.ok
        }
    }

    override fun onPause() {
        logger.debug()
        super.onPause()
        viewModel.blocking.value = true
        saveChapters()  // viewModel.playerControllerModel.close()でviewModel.videoSourceがクリアされるので、そのまえに保存する。
        if(isFinishing) {
            logger.debug("finishing")
            viewModel.playerControllerModel.close()
        }
    }

    override fun onResume() {
        logger.debug()
        super.onResume()
        if(viewModel.blocking.value) {
            lifecycleScope.launch {
                if(PasswordDialog.checkPassword()) {
                    viewModel.blocking.value = false
                } else {
                    logger.error("Incorrect Password")
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        logger.debug()
        super.onDestroy()
    }

    private fun saveChapters() {
        if(viewModel.chapterList.isDirty) {
            val source = viewModel.playerModel.currentSource.value as? EditorViewModel.VideoSource ?: return    // 動画コンバート成功後にcurrentSourceはリセットされるが、Chapterは保存済みのはず。
            val target = source.item.data
            val list = viewModel.chapterList.chapters.run {
                // 先頭の不要なチャプターは削除する
                if (size == 1 && this[0].run {position == 0L && !skip && label.isEmpty()}) {
                    emptyList()
                } else {
                    this
                }
            }
            viewModel.chapterList.clearDirty()
            CoroutineScope(Dispatchers.IO).launch {
                MetaDB.setChaptersFor(target, list)
            }
        }
    }

    override fun handleKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
        if(keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
                UtImmortalSimpleTask.run {
//                    saveChapters()
                    setResultAndFinish(true, viewModel.targetItem)
                    true
                }
                return true
//            if(viewModel.chapterList.isDirty) {
//                UtImmortalSimpleTask.run {
//                    if(showYesNoMessageBox(null, "Chapters are editing. Save changes?")) {
//                        setResult(RESULT_OK,)
//                        MetaDB.setChaptersFor(viewModel.videoSource.item, viewModel.chapterList.chapters)
//                    }
//                    setResultAndFinish(true, viewModel.targetItem)
//                    true
//                }
//                return true
//            }
        }
        return super.handleKeyEvent(keyCode, event)
    }

    class Broker(activity:FragmentActivity) : UtActivityBroker<String,String?>() {
        init{
            register(activity)
        }
        class Contract:ActivityResultContract<String,String?>() {
            override fun createIntent(context: Context, input: String): Intent {
                return Intent(context.applicationContext, EditorActivity::class.java).apply { putExtra(KEY_FILE_NAME, input) }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): String? {
                return if(resultCode == RESULT_OK) intent?.getStringExtra(KEY_FILE_NAME) else null
            }
        }

        override val contract: ActivityResultContract<String, String?>
            get() = Contract()
    }

    companion object {
        const val KEY_FILE_NAME = "video_source"
        val logger = UtLog("Editor", null, this::class.java)

        private suspend fun UtImmortalSimpleTask.setResultAndFinish(ok:Boolean,item:ItemEx) {
            (getActivity() as? EditorActivity)?.apply {
                setResult(if(ok) RESULT_OK else RESULT_CANCELED, Intent().apply { putExtra(KEY_FILE_NAME, item.name) })
                finish()
            }
        }
    }
}
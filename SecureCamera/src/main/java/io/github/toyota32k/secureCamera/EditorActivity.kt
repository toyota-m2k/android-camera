package io.github.toyota32k.secureCamera

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.broker.UtActivityBroker
import io.github.toyota32k.dialog.mortal.UtMortalActivity
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTaskBase
import io.github.toyota32k.dialog.task.getActivity
import io.github.toyota32k.dialog.task.showOkCancelMessageBox
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.media.editor.dialog.SliderPartition
import io.github.toyota32k.lib.media.editor.dialog.SliderPartitionDialog
import io.github.toyota32k.lib.media.editor.handler.save.GenericSaveFileHandler
import io.github.toyota32k.lib.media.editor.handler.split.GenericSplitHandler
import io.github.toyota32k.lib.media.editor.model.IMediaSourceWithMutableChapterList
import io.github.toyota32k.lib.media.editor.model.IMultiOutputFileSelector
import io.github.toyota32k.lib.media.editor.model.IMultiSplitResult
import io.github.toyota32k.lib.media.editor.model.IOutputFileProvider
import io.github.toyota32k.lib.media.editor.model.ISaveResult
import io.github.toyota32k.lib.media.editor.model.IVideoSaveResult
import io.github.toyota32k.lib.media.editor.model.MediaEditorModel
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.chapter.MutableChapterList
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.io.AndroidFile
import io.github.toyota32k.media.lib.io.HttpFile
import io.github.toyota32k.media.lib.io.HttpInputFile
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.IOutputMediaFile
import io.github.toyota32k.media.lib.io.toAndroidFile
import io.github.toyota32k.media.lib.processor.Analyzer
import io.github.toyota32k.media.lib.processor.contract.IProgress
import io.github.toyota32k.media.lib.report.Summary
import io.github.toyota32k.media.lib.types.RangeMs
import io.github.toyota32k.media.lib.types.RangeUs.Companion.us2ms
import io.github.toyota32k.secureCamera.ScDef.VIDEO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.VIDEO_PREFIX
import io.github.toyota32k.secureCamera.client.OkHttpStreamSource
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.databinding.ActivityEditorBinding
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.dialog.DetailMessageDialog
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.dialog.ReportTextDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotSettings
import io.github.toyota32k.secureCamera.utils.FileUtil.safeDeleteFile
import io.github.toyota32k.utils.TimeSpan
import io.github.toyota32k.utils.UtLazyResetableValue
import io.github.toyota32k.utils.android.CompatBackKeyDispatcher
import io.github.toyota32k.utils.android.RefBitmap
import io.github.toyota32k.utils.android.hideActionBar
import io.github.toyota32k.utils.android.hideStatusBar
import io.github.toyota32k.utils.gesture.UtScaleGestureManager
import io.github.toyota32k.utils.onTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class EditorActivity : UtMortalActivity() {
    class EditorViewModel(application: Application) : AndroidViewModel(application) {
        val metaDb = MetaDB[SlotSettings.currentSlotIndex]
        val editorModel = MediaEditorModel.Builder(application, viewModelScope) {
            supportChapter()
            .supportSnapshot(this@EditorViewModel::onSnapshot)
            .enableRotateLeft()
            .enableRotateRight()
            .enableSeekSmall(0,0)
            .enableSeekMedium(1000, 3000)
            .enableSeekLarge(5000, 10000)
            .enableSliderLock(true)
            .counterInMs()
            .snapshotSource(PlayerControllerModel.SnapshotSource.CAPTURE_PLAYER, selectable = true)
            .supportMagnifySlider { orgModel, duration->
                val sp = SliderPartitionDialog.show(SliderPartition.fromModel(orgModel, duration))
                if (sp==null) orgModel else sp.toModel()
            }
        }
        .supportChapterEditor()
        .supportCrop()
        .setSaveFileHandler( GenericSaveFileHandler(application, true))
        .supportSplit(GenericSplitHandler(application, true))
        .setOutputFileProvider(FileProvider())
        .setOutputFileSelector(FileSelector())
        .enableBuiltInMagnifySlider()
        .build()
        private fun stringInKb(size: Long): String {
            return if (size<0) "uav" else String.format(Locale.US, "%,d KB", size / 1000L)
        }

        fun safeOverwrite(srcFile: File, dstFile:File):Boolean {
            if (!dstFile.exists()) {
                srcFile.renameTo(dstFile)
            }
            // dstをバックアップ
            val bakFile = File(application.cacheDir ?: throw java.lang.IllegalStateException("no cacheDir"), "tc-backup").apply { safeDeleteFile(this) }
            try { dstFile.renameTo(bakFile) } catch (e:Throwable) { return false }
            safeDeleteFile(dstFile) // すでに存在しないはずだが念のため

            // src を dstにリネーム
            try {
                srcFile.renameTo(dstFile)
                return true
            } catch(e:Throwable) {
                logger.error(e)
                try {
                    // リネームに失敗したらコピーも試す
                    srcFile.copyTo(dstFile)
                    return true
                } catch (e:Throwable) {
                    // リネームもコピーも失敗したら、backup ファイルから復元する。
                    bakFile.renameTo(dstFile)
                    return false
                }
            } finally {
                safeDeleteFile(bakFile)
            }
        }

        val finishEditing = MutableStateFlow<ItemEx?>(null)

        fun onVideoSaved(result:ISaveResult) {
            CoroutineScope(Dispatchers.IO).launch {
                val result = result as IVideoSaveResult
                val outFile = result.outputFile as AndroidFile
                val actualSoughtMap = result.convertResult.actualSoughtMap
                val newChapterList = if (actualSoughtMap!=null) {
                    editorModel.chapterEditorHandler.correctChapterList(actualSoughtMap)
                } else null
                try {
                    // 確認ダイアログ
                    val srcLen = result.convertResult.report?.input?.size ?: -1
                    val dstLen = result.convertResult.report?.output?.size ?: -1
                    if (DetailMessageDialog.showMessage(
                            "Completed.",
                            "${stringInKb(srcLen)} → ${stringInKb(dstLen)}",
                            result.convertResult.report?.toString() ?: "no information",
                            outFile.path!!,
                            newChapterList
                        )
                    ) {
                        // OKなら上書き保存＆DB更新 --> Editor終了
                        val targetFile = metaDb.fileOf(targetItem)
                        val newFile = outFile.path!!
                        safeOverwrite(newFile, targetFile).onTrue {
                            finishEditing.value = metaDb.updateFile(targetItem, newChapterList)
                        }
                    }
                } finally {
                    outFile.safeDelete()
                }
            }
        }

        fun onVideoSplit(result:IMultiSplitResult, files:MutableMap<AndroidFile,Long>) {
            CoroutineScope(Dispatchers.IO).launch {
                val count = result.results.size
                try {
                    if (result.succeeded && UtImmortalTask.awaitTaskResult { showOkCancelMessageBox("Split File","Split into $count files.\nAre you sure to replace these files?") }) {
                        var firstItem:ItemEx? = null
                        for(i in 0 until count) {
                            val r = result.results[i]
                            val output = r.outputFile as AndroidFile
                            val (name,file) = if (i == 0) {
                                // 先頭は上書き
                                targetItem.name to targetFile
                            } else {
                                val pos = files[output] ?: throw IllegalStateException("no position")
                                val date = targetItem.creationDate + pos
                                val name = ITcUseCase.defaultFileName(VIDEO_PREFIX, VIDEO_EXTENSION, Date(date))
                                name to File(metaDb.filesDir, name)
                            }
                            safeOverwrite(output.path!!, file)
                            val actualSoughtMap = r.actualSoughtMap
                            val newChapterList = if (actualSoughtMap!=null) {
                                editorModel.chapterEditorHandler.correctChapterList(actualSoughtMap)
                            } else null

                            if (firstItem==null) {
                                // 先頭アイテムはリプレース
                                firstItem = metaDb.updateFile(targetItem, newChapterList)
                            } else {
                                val newItem = metaDb.register(name)!!
                                metaDb.setChaptersFor(newItem, newChapterList)
                            }
                        }
                        // 先頭itemを選択した状態でエディタを終了
                        finishEditing.value = firstItem
                    }
                } finally {
                    for(f in files.keys) {
                        f.safeDelete()
                    }
                }
            }
        }

        val playerControllerModel get() = editorModel.playerControllerModel
        val playerModel get() = playerControllerModel.playerModel
        private val videoSource get() = playerModel.currentSource.value as VideoSource
        val targetItem:ItemEx get() = videoSource.item
        val targetFile:File get() = metaDb.fileOf(targetItem)
        val targetUri:String get() = metaDb.urlOf(targetItem)

        private inner class FileProvider: IOutputFileProvider {
            override suspend fun getOutputFile(
                mimeType: String,
                inputFile: AndroidFile
            ): AndroidFile {
                return File(application.cacheDir ?: throw java.lang.IllegalStateException("no cacheDir"), "tc-output").toAndroidFile()
            }

            override suspend fun finalize(result: ISaveResult) {
                onVideoSaved(result)
            }
        }


        private inner class FileSelector: IMultiOutputFileSelector {
            val files = mutableMapOf<AndroidFile,Long>()
            override suspend fun initialize(trimmedRangeMsList: List<RangeMs>): Boolean = true

            override suspend fun selectOutputFile(
                index: Int,
                positionMs: Long
            ): IOutputMediaFile {
                return File(application.cacheDir ?: throw java.lang.IllegalStateException("no cacheDir"), "tc-split${index}").toAndroidFile().apply {
                    files[this] = positionMs
                }
            }

            override suspend fun finalize(result: IMultiSplitResult) {
                onVideoSplit(result, files)
            }
        }

//        val chapterList by lazy {
//            ChapterEditor(videoSource.chapterList as IMutableChapterList)   // videoSource.chapterList は空のリスト ... setSourceでリストは初期化される。
//        }
//        val commandAddChapter = LiteUnitCommand {
//            chapterList.addChapter(playerModel.currentPosition, "", null)
//        }
//        val commandAddSkippingChapter = LiteUnitCommand {
//            val neighbor = chapterList.getNeighborChapters(playerModel.currentPosition)
//            val prev = neighbor.getPrevChapter(chapterList)
//            if(neighbor.hit<0) {
//                // 現在位置にチャプターがなければ追加する
//                if(!chapterList.addChapter(playerModel.currentPosition, "", null)) {
//                    return@LiteUnitCommand
//                }
//            }
//            // ひとつ前のチャプターを無効化する
//            if(prev!=null) {
//                chapterList.skipChapter(prev, true)
//            }
//        }
//        val commandRemoveChapter = LiteUnitCommand {
//            val neighbor = chapterList.getNeighborChapters(playerModel.currentPosition)
//            chapterList.removeChapterAt(neighbor.next)
//        }
//        val commandRemoveChapterPrev = LiteUnitCommand {
//            val neighbor = chapterList.getNeighborChapters(playerModel.currentPosition)
//            chapterList.removeChapterAt(neighbor.prev)
//        }
//        val commandToggleSkip = LiteUnitCommand {
//            val chapter = chapterList.getChapterAround(playerModel.currentPosition) ?: return@LiteUnitCommand
//            chapterList.skipChapter(chapter, !chapter.skip)
//        }
//        val commandSave = LiteUnitCommand()
        fun setSource(item: ItemEx, chapters:List<IChapter>) {
            resetInputFile()
            playerModel.setSource(VideoSource(item))
//            chapterList.initChapters(chapters)
//            if(chapterList.chapterAt(0)?.position!=0L) {
//                // 動画先頭位置が暗黙のチャプターとして登録されていることを前提に動作する。
//                chapterList.addChapter(0, "", null)
//            }
        }

        fun saveChapters() {
            val source = playerModel.currentSource.value as? EditorViewModel.VideoSource ?: return    // 動画コンバート成功後にcurrentSourceはリセットされるが、Chapterは保存済みのはず。
            if(editorModel.chapterEditorHandler.isDirty) {
                val target = source.item.data
                val chapterList = editorModel.chapterEditorHandler.getChapterList()
                val list = chapterList.chapters.run {
                    // 先頭の不要なチャプターは削除する
                    if (size == 1 && this[0].run {position == 0L && !skip && label.isEmpty()}) {
                        emptyList()
                    } else {
                        this
                    }
                }
                editorModel.chapterEditorHandler.clearDirty()
                CoroutineScope(Dispatchers.IO).launch {
                    metaDb.setChaptersFor(target, list)
                }
            }
        }


        private val resetableInputFile: UtLazyResetableValue<IInputMediaFile> = UtLazyResetableValue {
            if(targetItem.cloud.isFileInLocal) {
                targetFile.toAndroidFile()
            } else {
                HttpInputFile(application, OkHttpStreamSource(metaDb.urlOf(targetItem))).apply {
                    addRef()
                }
            }
        }

        private fun resetInputFile() {
            resetableInputFile.reset {
                if(it is HttpInputFile) {
                    it.release()
                }
            }
        }
//        val inputFile:IInputMediaFile get() = resetableInputFile.value

//        val commandUndo = LiteUnitCommand {
//            chapterList.undo()
//        }
//        val commandRedo = LiteUnitCommand {
//            chapterList.redo()
//        }
//        val commandSplit = LiteUnitCommand {
//            if(playerModel.naturalDuration.value < SelectRangeDialog.MIN_DURATION) return@LiteUnitCommand
//            UtImmortalTask.launchTask("split") {
//                val current = playerControllerModel.rangePlayModel.value
//                val params = if(current!=null) {
//                    SplitParams.fromModel(current)
//                } else {
//                    SplitParams.create(playerModel.naturalDuration.value)
//                }
//                val result = SelectRangeDialog.show(this, params)
//                if(result!=null) {
//                    playerControllerModel.setRangePlayModel(result.toModel())
//                }
//            }
//        }
        val playingBeforeBlocked = MutableStateFlow(false)
        val blocking = MutableStateFlow(false)

        private fun onSnapshot(pos:Long, bitmap: RefBitmap) {
            val source = (playerModel.currentSource.value as? VideoSource)?.item ?: return
            if(!source.cloud.isFileInLocal) return
            CoroutineScope(Dispatchers.IO).launch {
                PlayerActivity.PlayerViewModel.saveSnapshot(metaDb, source.data, pos, bitmap)
            }
        }

        override fun onCleared() {
            super.onCleared()
            logger.debug()
            resetInputFile()
            playerControllerModel.close()
            HttpInputFile.deleteAllTempFile(getApplication())
        }

        inner class VideoSource(val item: ItemEx) : IMediaSourceWithMutableChapterList {
            override val name:String
                get() = item.name
//            private val file: File = item.file
            override val id: String
                get() = name
            override val uri: String
                get() = metaDb.urlOf(item)
            override val trimming: Range = Range.empty
            override val type: String
                get() = name.substringAfterLast(".", "")
            override var startPosition = AtomicLong()
            val chapterList: IMutableChapterList = MutableChapterList(item.chapterList ?: emptyList())
            override suspend fun getChapterList(): IMutableChapterList {
                return chapterList
            }
        }

//        fun createConvertHelper(quality:SelectQualityDialog.VideoQuality=SelectQualityDialog.VideoQuality.High, keepHdr:Boolean=false): ConvertHelper {
//            return ConvertHelper(
//                inputFile,
//                quality.strategy,
//                keepHdr,
//                if(playerModel.rotation.value!=0) Rotation(playerModel.rotation.value,relative = true) else Rotation.nop,
//                chapterList.enabledRanges(Range.empty).map { RangeMs(it.start, it.end) }.toTypedArray(),
//                playerModel.naturalDuration.value
//            )
//        }

        companion object {
            val logger = UtLog("VM", EditorActivity.logger)
        }
    }

    override val logger = EditorActivity.logger
    private val binder = Binder()
    private val viewModel by viewModels<EditorViewModel>()
    private lateinit var controls: ActivityEditorBinding

    // Scale/Scroll
    private lateinit var gestureManager: UtScaleGestureManager
//
//    private val gestureInterpreter = UtGestureInterpreter(SCApplication.instance, enableScaleEvent = true)
//    private val manipulationAgent by lazy { UtManipulationAgent(UtSimpleManipulationTarget(controls.videoViewer,controls.videoViewer.controls.player)) }
    private val compatBackKeyDispatcher = CompatBackKeyDispatcher()

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.debug()
        Settings.Design.applyToActivity(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

//        setTheme(R.style.Theme_TryCamera_M3_DynamicColor_NoActionBar)
//        setTheme(R.style.Theme_TryCamera_M3_Cherry_NoActionBar)
//        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        controls = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(controls.root)
        setupWindowInsetsListener(controls.editor, UtDialogConfig.SystemZone.SYSTEM_BARS) // cutout はあえて除けない
        hideActionBar()
        hideStatusBar()

        binder.owner(this)

        lifecycleScope.launch {
            val name = intent.extras?.getString(KEY_FILE_NAME) ?: throw IllegalStateException("no source")
            val item = viewModel.metaDb.itemExOf(name) ?: throw IllegalStateException("no item")
            val chapters = viewModel.metaDb.getChaptersFor(item.data)
            if(item.cloud.loadFromCloud && !Authentication.authenticateAndMessage()) {
                UtImmortalTask.launchTask {
                    setResultAndFinish(false, item)
                }
                return@launch
            }
            viewModel.setSource(item, chapters)

            binder
                .visibilityBinding(controls.safeGuard, viewModel.blocking, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
//                .visibilityBinding(controls.saveVideo, ConstantLiveData(viewModel.targetItem.cloud.isFileInLocal), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
//                .longClickBinding(controls.saveVideo) { showVideoProperties() }
//                .clickBinding(controls.chop) { chopAt(viewModel.playerModel.currentPosition) }
//                .enableBinding(controls.splitMode, viewModel.playerModel.naturalDuration.map { it>SelectRangeDialog.MIN_DURATION })
//                .bindCommand(viewModel.commandUndo, controls.undo)
//                .bindCommand(viewModel.commandRedo, controls.redo)
//                .bindCommand(viewModel.commandSplit, controls.splitMode)
                .observe(viewModel.editorModel.cropHandler.croppingNow) {
                    if (it) {
                        gestureManager.agent.resetScrollAndScale()
                    }
                    enableGestureManager(!it)
                }
                .observe(viewModel.finishEditing) { item->
                    if (item!=null) {
                        UtImmortalTask.launchTask {
                            setResultAndFinish(false, item)
                        }
                    }
                }

            controls.editorPlayerView.bindViewModel(viewModel.editorModel, binder)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON  // スリープしない
              or WindowManager.LayoutParams.FLAG_SECURE)         // タスクマネージャに表示させない、キャプチャー禁止


        gestureManager = UtScaleGestureManager(this.applicationContext, enableDoubleTap = true, controls.editorPlayerView.manipulationTarget, minScale = 1f)
            .setup(this) {
                onTap {
                    viewModel.editorModel.playerModel.togglePlay()
                }
                onDoubleTap {
                    gestureManager.agent.resetScrollAndScale()
                }
            }

        compatBackKeyDispatcher.register(this) {
            UtImmortalTask.launchTask {
                setResultAndFinish(true, viewModel.targetItem)
            }
        }
    }

    private fun enableGestureManager(sw:Boolean) {
        val view = gestureManager.manipulationTarget.parentView
        if (sw) {
            gestureManager.gestureInterpreter.attachView(view)
        } else {
            gestureManager.gestureInterpreter.detachView(view)
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

    private fun sourceVideoProperties(): Summary {
        val inFile = if(viewModel.targetItem.cloud.isFileInLocal) {
            viewModel.targetFile.toAndroidFile()
        } else {
            HttpFile(viewModel.targetUri)
        }
        return Analyzer.analyze(inFile)
    }
    private fun showVideoProperties():Boolean {
        ReportTextDialog.show(viewModel.targetItem.name, sourceVideoProperties().toString())
        return true
    }

//    private fun selectQualityAndSave():Boolean {
//        logger.debug()
//        viewModel.playerControllerModel.commandPause.invoke()
//        lifecycleScope.launch {
//            val sourceHdr = sourceVideoProperties().videoSummary?.profile?.isHDR() == true
//            val helper = viewModel.createConvertHelper()
//            val pos = viewModel.playerModel.currentPosition
//            val result = SelectQualityDialog.show(sourceHdr, helper, pos ) ?: return@launch
//            withContext(Dispatchers.IO) {
//                trimmingAndSave(result.quality, sourceHdr && result.keepHdr)
//            }
//        }
//        return true
//    }

//    private fun chopAt(pos:Long) {
//        viewModel.playerControllerModel.playerModel.pause()
//        val duration = viewModel.playerModel.naturalDuration.value
//        val targetItem = viewModel.targetItem
//        val helper = SplitHelper(viewModel.inputFile)
//        UtImmortalTask.launchTask {
//            // トリミング開始前に編集内容を一旦セーブ // ..トリミング中に強制終了したとき（主にデバッグ中）に編集内容が消えてしまうのを回避
//            withContext(Dispatchers.IO) {
//                saveChapters()
//            }
//
//            val result = helper.chop(this@EditorActivity, pos)
//            if (result!=null) {
//                // 分割成功
//                // chapter list を分割
//                val chapters = viewModel.chapterList.chapters
//                val firstChapterList = MutableChapterList()
//                val lastChapterList = MutableChapterList()
//                var lastOfFirst: IChapter? = null
//                for(c in chapters) {
//                    if (c.position < 0 || duration < c.position) continue   // invalid position
//                    if (c.position <= result.actualSplitPosMs) {
//                        firstChapterList.addChapter(c.position, c.label, c.skip)
//                        lastOfFirst = c
//                    } else {
//                        val corrPos = c.position - result.actualSplitPosMs
//                        if (lastOfFirst!=null && corrPos!=0L) {
//                            lastChapterList.addChapter(0, lastOfFirst.label, lastOfFirst.skip)
//                        }
//                        lastChapterList.addChapter(corrPos, c.label, c.skip)
//                    }
//                }
//
//                // 後半ファイルを追加
//                val date = targetItem.creationDate + pos
//                val name = ITcUseCase.defaultFileName(VIDEO_PREFIX, VIDEO_EXTENSION, Date(date))
//                val file = File(viewModel.metaDb.filesDir, name)
//                safeDeleteFile(file)
//                result.lastFile.renameTo(file)
//                val newItem = viewModel.metaDb.register(name)!!
//                viewModel.metaDb.setChaptersFor(newItem, lastChapterList.chapters)
//
//                // 前半ファイルで、ターゲットをリプレース
//                safeDeleteFile(viewModel.metaDb.fileOf(targetItem))
//                result.firstFile.renameTo(viewModel.metaDb.fileOf(targetItem))
//                val replacedItem = viewModel.metaDb.updateFile(targetItem, firstChapterList.chapters)
//
//                // 編集画面を終了してプレーヤー画面に戻る
//                setResultAndFinish(true, replacedItem)
//            } else {
//                if (!helper.cancelled) {
//                    showConfirmMessageBox("Split File","Error: ${helper.error?.message ?: "unknown"}")
//                }
//            }
//        }
//    }
//
//    private fun trimmingAndSave(reqQuality: SelectQualityDialog.VideoQuality?=null, keepHdr:Boolean) {
//        val quality = reqQuality ?: SelectQualityDialog.VideoQuality.High
//        val targetItem = viewModel.targetItem
//        val srcFile = viewModel.inputFile
//
//        UtImmortalTask.launchTask("trimming") {
//            // トリミング開始前に編集内容を一旦セーブ
//            // ..トリミング中に強制終了したとき（主にデバッグ中）に編集内容が消えてしまうのを回避
//            withContext(Dispatchers.IO) {
//                saveChapters()
//            }
//            val helper = viewModel.createConvertHelper(quality, keepHdr)
//            val dstFile = helper.convertAndOptimize(applicationContext)
//            if (dstFile != null) {
//                val result = helper.result
//                val report = helper.report
//                val srcLen = srcFile.getLength().let { if(it<0) targetItem.size else it }
//                val dstLen = dstFile.length()
//                logger.debug("${stringInKb(srcLen)} --> ${stringInKb(dstLen)}")
//                // トリミングによるchapterListの調整
//                val adjustedEnabledRange = result.actualSoughtMap?.adjustedRangeList(helper.trimmingRanges.toList())?.list?.map { Range(it.startUs / 1000L, it.endUs / 1000L) }
//                val newChapterList = if (!adjustedEnabledRange.isNullOrEmpty()) {
//                    viewModel.chapterList.adjustWithEnabledRanges(adjustedEnabledRange)
//                } else {
//                    null
//                }
//                // 確認ダイアログ
//                if (DetailMessageDialog.showMessage("Completed.", "${stringInKb(srcLen)} → ${stringInKb(dstLen)}", report?.toString() ?: "no information", dstFile, newChapterList)) {
//                    // OKなら上書き保存＆DB更新
//                    withContext(Dispatchers.Main) { viewModel.playerModel.reset() }
//                    val testOnly = false     // false: 通常の動作（元のファイルに上書き） / true: テストファイルに出力して、元のファイルは変更しない
//                    if (testOnly) {
//                        viewModel.metaDb.withTestFile { testFile ->
//                            dstFile.renameTo(testFile)
//                        }
//                    } else {
//                        safeDeleteFile(viewModel.metaDb.fileOf(targetItem))
//                        dstFile.renameTo(viewModel.metaDb.fileOf(targetItem))
//                        viewModel.metaDb.updateFile(targetItem, newChapterList)
//                    }
//                    setResultAndFinish(true, targetItem)
//                }
//            }
//        }
//    }

    override fun onPause() {
        logger.debug()
        super.onPause()
        viewModel.saveChapters()  // viewModel.playerControllerModel.close()でviewModel.videoSourceがクリアされるので、そのまえに保存する。
        viewModel.playingBeforeBlocked.value = viewModel.playerControllerModel.playerModel.isPlaying.value
        viewModel.blocking.value = true
        viewModel.playerControllerModel.playerModel.pause()
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
                if(PasswordDialog.checkPassword(SlotSettings.currentSlotIndex)) {
                    viewModel.blocking.value = false
                    if(viewModel.playingBeforeBlocked.value) {
                        viewModel.playingBeforeBlocked.value = false
                        viewModel.playerControllerModel.playerModel.play()
                    }
                } else {
                    logger.error("Incorrect Password")
                    UtImmortalTask.launchTask {
                        setResultAndFinish(false, viewModel.targetItem)
                    }
                }
            }
        }
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        logger.debug("${newConfig.orientation}")
//    }

    override fun onDestroy() {
        logger.debug()
        super.onDestroy()
    }

//    private fun saveChapters() {
//        val source = viewModel.playerModel.currentSource.value as? EditorViewModel.VideoSource ?: return    // 動画コンバート成功後にcurrentSourceはリセットされるが、Chapterは保存済みのはず。
//        if(viewModel.editorModel.chapterEditorHandler.isDirty) {
//            val target = source.item.data
//            val chapterList = viewModel.editorModel.chapterEditorHandler.getChapterList()
//            val list = chapterList.chapters.run {
//                // 先頭の不要なチャプターは削除する
//                if (size == 1 && this[0].run {position == 0L && !skip && label.isEmpty()}) {
//                    emptyList()
//                } else {
//                    this
//                }
//            }
//            viewModel.editorModel.chapterEditorHandler.clearDirty()
//            CoroutineScope(Dispatchers.IO).launch {
//                viewModel.metaDb.setChaptersFor(target, list)
//            }
//        }
//    }


//    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
//        if(keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
//            UtImmortalTask.launchTask {
////                    saveChapters()
//                setResultAndFinish(true, viewModel.targetItem)
//            }
//            return true
////            if(viewModel.chapterList.isDirty) {
////                UtImmortalSimpleTask.run {
////                    if(showYesNoMessageBox(null, "Chapters are editing. Save changes?")) {
////                        setResult(RESULT_OK,)
////                        MetaDB.setChaptersFor(viewModel.videoSource.item, viewModel.chapterList.chapters)
////                    }
////                    setResultAndFinish(true, viewModel.targetItem)
////                    true
////                }
////                return true
////            }
//        }
//        return super.onKeyDown(keyCode, event)
//    }

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

        private suspend fun UtImmortalTaskBase.setResultAndFinish(ok:Boolean,item:ItemEx) {
            (getActivity() as? EditorActivity)?.apply {
                setResult(if(ok) RESULT_OK else RESULT_CANCELED, Intent().apply { putExtra(KEY_FILE_NAME, item.name) })
                finish()
            }
        }
    }
}
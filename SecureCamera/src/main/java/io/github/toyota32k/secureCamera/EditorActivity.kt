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
                // 出力ファイルが存在しなければ、単なるリネームでok
                srcFile.renameTo(dstFile)
                return true
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
            if (!result.succeeded) return
            CoroutineScope(Dispatchers.IO).launch {
                val result = result as IVideoSaveResult
                val outFile = result.outputFile as AndroidFile
                val soughtMap = result.convertResult.soughtMap ?: throw IllegalStateException("no sought map")
                val newChapterList = editorModel.chapterEditorHandler.correctChapterList(soughtMap)
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
                            val soughtMap = r.soughtMap ?: throw IllegalStateException("no sought map")
                            val newChapterList = editorModel.chapterEditorHandler.correctChapterList(soughtMap)

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

        fun setSource(item: ItemEx, chapters:List<IChapter>) {
            resetInputFile()
            playerModel.setSource(VideoSource(item))
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
    private val compatBackKeyDispatcher = CompatBackKeyDispatcher()

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.debug()
        Settings.Design.applyToActivity(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

    override fun onDestroy() {
        logger.debug()
        super.onDestroy()
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

        private suspend fun UtImmortalTaskBase.setResultAndFinish(ok:Boolean,item:ItemEx) {
            (getActivity() as? EditorActivity)?.apply {
                setResult(if(ok) RESULT_OK else RESULT_CANCELED, Intent().apply { putExtra(KEY_FILE_NAME, item.name) })
                finish()
            }
        }
    }
}
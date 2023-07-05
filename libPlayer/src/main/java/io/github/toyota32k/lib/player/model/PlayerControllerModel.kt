package io.github.toyota32k.lib.player.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.TpFrameExtractor
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable

/**
 * コントローラーの共通実装
 * ControlPanelModel              基本実装（AmvFrameSelectorViewで使用）
 *   + FullControlPanelModel      フル機能プレイヤー（AmvPlayerUnitView）用
 *   + TrimmingControlPanelModel  トリミング画面(AMvTrimmingPlayerView)用
 */
open class PlayerControllerModel(
    val playerModel: IPlayerModel,
    val supportFullscreen:Boolean,
    val supportPinP:Boolean,
    val snapshotHandler:((Long,Bitmap)->Unit)?,
    val enableRotateRight:Boolean,
    val enableRotateLeft:Boolean,
    val showNextPreviousButton:Boolean,
//    var seekRelativeForward:Long,
//    var seekRelativeBackword:Long,
    val seekSmall:RelativeSeek?,
    val seekMedium:RelativeSeek?,
    val seekLarge:RelativeSeek?,

) : Closeable, IUtPropOwner {
    companion object {
        val logger by lazy { UtLog("CPM", TpLib.logger) }
    }
    data class RelativeSeek(val backward:Long, val forward:Long)

    class Builder(val context:Context, val coroutineScope: CoroutineScope) {
        private var mSupportChapter:Boolean = false
        private var mPlaylist:IMediaFeed? = null
        private var mAutoPlay:Boolean = false
        private var mContinuousPlay:Boolean = false
        private var mSupportFullscreen:Boolean = false
        private var mSupportPinP:Boolean = false
        private var mSnapshotHandler:((Long,Bitmap)->Unit)? = null
        private var mEnableRotateRight:Boolean = false
        private var mEnableRotateLeft:Boolean = false
        private var mShowNextPreviousButton:Boolean = false
//        private var mSeekForward:Long = 1000L
//        private var mSeekBackword:Long = 500L
        private var mSeekSmall:RelativeSeek? = null
        private var mSeekMedium:RelativeSeek? = null
        private var mSeekLarge:RelativeSeek? = null

        private var mHideChapterViewIfEmpty = false
//        private var mScope:CoroutineScope? = null

        fun supportChapter(hideChapterViewIfEmpty:Boolean=false):Builder {
            mSupportChapter = true
            mHideChapterViewIfEmpty = hideChapterViewIfEmpty
            return this
        }
        fun supportPlaylist(playlist:IMediaFeed, autoPlay:Boolean, continuousPlay:Boolean):Builder {
            mPlaylist = playlist
            mAutoPlay = autoPlay
            mContinuousPlay = continuousPlay
            return this
        }
        fun supportFullscreen():Builder {
            mSupportFullscreen = true
            return this
        }
        fun supportPiP():Builder {
            mSupportPinP = true
            return this
        }

        fun supportSnapshot(snapshotHandler:(Long,Bitmap)->Unit):Builder {
            mSnapshotHandler = snapshotHandler
            return this
        }

        fun showNextPreviousButton():Builder {
            mShowNextPreviousButton = true
            return this
        }

        fun enableRotateRight():Builder {
            mEnableRotateRight = true
            return this
        }
        fun enableRotateLeft():Builder {
            mEnableRotateLeft = true
            return this
        }
//        fun relativeSeekDuration(forward:Long, backward:Long):Builder {
//            mSeekForward = forward
//            mSeekBackword = backward
//            return this
//        }

        fun enableSeekSmall(backward:Long, forward:Long):Builder {
            mSeekSmall = RelativeSeek(backward, forward)
            return this
        }
        fun enableSeekMedium(backward:Long, forward:Long):Builder {
            mSeekMedium = RelativeSeek(backward, forward)
            return this
        }
        fun enableSeekLarge(backward:Long, forward:Long):Builder {
            mSeekLarge = RelativeSeek(backward, forward)
            return this
        }

//        private val scope:CoroutineScope by lazy { CoroutineScope(Dispatchers.Main+ SupervisorJob()) }

        fun build():PlayerControllerModel {
            val playerModel = when {
                mSupportChapter && mPlaylist!=null -> PlaylistChapterPlayerModel(context, coroutineScope, mPlaylist!!, mAutoPlay, mContinuousPlay, mHideChapterViewIfEmpty)
                mSupportChapter -> ChapterPlayerModel(context, coroutineScope, mHideChapterViewIfEmpty)
                mPlaylist!=null -> PlaylistPlayerModel(context, coroutineScope, mPlaylist!!, mAutoPlay, mContinuousPlay)
                else -> BasicPlayerModel(context, coroutineScope)
            }
            return PlayerControllerModel(
                playerModel,
                supportFullscreen = mSupportFullscreen,
                supportPinP = mSupportPinP,
                snapshotHandler = mSnapshotHandler,
                enableRotateRight = mEnableRotateRight,
                enableRotateLeft = mEnableRotateLeft,
                showNextPreviousButton = mShowNextPreviousButton,
                seekSmall = mSeekSmall,
                seekMedium = mSeekMedium,
                seekLarge = mSeekLarge,
//                seekRelativeForward = mSeekForward,
//                seekRelativeBackword = mSeekBackword
            )
        }
    }

    /**
     * コントローラーのCoroutineScope
     * playerModel.scope を継承するが、ライフサイクルが異なるので、新しいインスタンスにしておく。
     */
//    val scope:CoroutineScope = CoroutineScope(playerModel.scope.coroutineContext)
//    private val resetableScope = UtManualIncarnateResetableValue { CoroutineScope(playerModel.scope.coroutineContext) }

    /**
     * ApplicationContext参照用
     */
    val context: Context get() = playerModel.context

    /**
     * AmvExoVideoPlayerのbindViewModelで、playerをplayerView.playerに設定するか？
     * 通常は true。ただし、FullControlPanelのように、PinP/FullScreenモードに対応する場合は、
     * どのビューに関連付けるかを個別に分岐するため、falseにする。
     */
    open val autoAssociatePlayer:Boolean = true

    val showControlPanel = MutableStateFlow(true)

    val canSnapshot:StateFlow<Boolean> = playerModel.currentSource.map {
        it?.uri?.startsWith("http") == false
    }.stateIn(playerModel.scope, SharingStarted.Eagerly, false)
    // region Commands

    private fun seekRelative(forward:Boolean, s:RelativeSeek?) {
        if(s==null) return
        playerModel.seekRelative(if(forward) s.forward else -s.backward)
    }

    val commandPlay = LiteUnitCommand(playerModel::play)
    val commandPause = LiteUnitCommand(playerModel::pause)
//    val commandTogglePlay = LiteUnitCommand { playerModel.togglePlay() }
//    val commandNext = LiteUnitCommand { playerModel.next() }
//    val commandPrev = LiteUnitCommand { playerModel.previous() }
//    val commandNextChapter = LiteUnitCommand { playerModel.nextChapter() }
//    val commandPrevChapter = LiteUnitCommand { playerModel.prevChapter() }
//    val commandSeek = LiteCommand<Long?> { if(it!=null) playerModel.seekRelative(it) }
    val commandSeekLarge = LiteCommand<Boolean> { seekRelative(it, seekLarge) }
    val commandSeekMedium = LiteCommand<Boolean> { seekRelative(it, seekMedium) }
    val commandSeekSmall = LiteCommand<Boolean> { seekRelative(it, seekSmall) }
    val commandFullscreen = LiteUnitCommand { setWindowMode(WindowMode.FULLSCREEN) }
    val commandPinP = LiteUnitCommand { setWindowMode(WindowMode.PINP) }
    val commandCollapse = LiteUnitCommand { setWindowMode(WindowMode.NORMAL) }
    val commandSnapshot = LiteUnitCommand(::snapshot)
//    val commandPlayerTapped = if(playerTapToPlay) LiteUnitCommand { playerModel.togglePlay() } else LiteUnitCommand()
    val commandRotate = LiteCommand<Rotation> { playerModel.rotate(it) }

    // endregion

    // region Fullscreen/PinP

    enum class WindowMode {
        NORMAL,
        FULLSCREEN,
        PINP
    }
    val windowMode : StateFlow<WindowMode> = MutableStateFlow(WindowMode.NORMAL)
    fun setWindowMode(mode:WindowMode) {
        logger.debug("mode=${windowMode.value} --> $mode")
        windowMode.mutable.value = mode
    }

    private fun snapshot() {
        val handler = snapshotHandler ?: return
        val src = playerModel.currentSource.value ?: return
        if(src.uri.startsWith("http")) return

        val pos = playerModel.currentPosition
        val rotation = Rotation.normalize(playerModel.rotation.value)

        CoroutineScope(Dispatchers.IO).launch {
            TpFrameExtractor(playerModel.context, Uri.parse(src.uri)).use { extractor->
                val bitmap = extractor.extractFrame(pos)?.run {
                    if(rotation!=0) {
                        Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                    } else this
                } ?: return@use
                withContext(Dispatchers.Main) {
                    handler(pos, bitmap)
                }
            }
        }
    }
    // endregion

    // region Slider

    /**
     * スライダーのトラッカー位置
     */
//    val sliderPosition = MutableStateFlow(0L)

    /**
     * プレーヤーの再生位置
     * 通常は、sliderPosition == presentingPosition だが、トリミングスライダーの場合は、左右トリミング用トラッカーも候補となる。
     * （最後に操作したトラッカーの位置が、presentingPosition となる。）
     */
//    open val presentingPosition:Flow<Long> = sliderPosition

//    fun seekAndSetSlider(pos:Long) {
//        val clipped = playerModel.clipPosition(pos)
////        sliderPosition.value = clipped
//        playerModel.seekTo(clipped)
//    }
    /**
     * スライダーのカウンター表示文字列
     */
    val counterText:Flow<String> = combine(playerModel.playerSeekPosition, playerModel.naturalDuration) { pos, duration->
        "${formatTime(pos,duration)} / ${formatTime(duration,duration)}"
    }

    // endregion

//    init {
//        playerModel.playerSeekPosition.onEach(this::onPlayerSeekPositionChanged).launchIn(scope)
//    }

    /**
     * タイマーによって監視されるプレーヤーの再生位置（playerModel.playerSeekPosition）に応じて、スライダーのシーク位置を合わせる。
     */
//    open fun onPlayerSeekPositionChanged(pos:Long) {
//        sliderPosition.value = pos
//    }

    override fun close() {
        playerModel.close()
    }
}

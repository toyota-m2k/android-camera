package io.github.toyota32k.lib.player.model

import android.content.Context
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.lib.player.TpLib
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
    var seekRelativeForward:Long,
    var seekRelativeBackword:Long,
) : Closeable, IUtPropOwner {
    companion object {
        val logger by lazy { UtLog("CPM", TpLib.logger) }
    }

    class Builder(val context:Context) {
        private var mSupportChapter:Boolean = false
        private var mPlaylist:IMediaFeed? = null
        private var mAutoPlay:Boolean = false
        private var mContinuousPlay:Boolean = false
        private var mSupportFullscreen:Boolean = false
        private var mSupportPinP:Boolean = false
        private var mSeekForward:Long = 1000L
        private var mSeekBackword:Long = 500L
        private var mScope:CoroutineScope? = null

        fun supportChapter():Builder {
            mSupportChapter = true
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

        fun relativeSeekDuration(forward:Long, backward:Long):Builder {
            mSeekForward = forward
            mSeekBackword = backward
            return this
        }

        fun coroutineScope(scope: CoroutineScope):Builder {
            mScope = scope + SupervisorJob()
            return this
        }

        private val scope:CoroutineScope
            get() = mScope ?: CoroutineScope(Dispatchers.Main+ SupervisorJob())

        fun build():PlayerControllerModel {
            val playerModel = when {
                mSupportChapter && mPlaylist!=null -> PlaylistChapterPlayerModel(context, scope, mPlaylist!!, mAutoPlay, mContinuousPlay)
                mSupportChapter -> ChapterPlayerModel(context, scope)
                mPlaylist!=null -> PlaylistPlayerModel(context, scope, mPlaylist!!, mAutoPlay, mContinuousPlay)
                else -> BasicPlayerModel(context, scope)
            }
            return PlayerControllerModel(playerModel, mSupportFullscreen, mSupportPinP, mSeekForward, mSeekBackword)
        }
    }

    /**
     * コントローラーのCoroutineScope
     * playerModel.scope を継承するが、ライフサイクルが異なるので、新しいインスタンスにしておく。
     */
    val scope:CoroutineScope = CoroutineScope(playerModel.scope.coroutineContext)

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

    // region Commands

    val commandPlay = LiteUnitCommand { playerModel.play() }
    val commandPause = LiteUnitCommand { playerModel.pause() }
//    val commandTogglePlay = LiteUnitCommand { playerModel.togglePlay() }
//    val commandNext = LiteUnitCommand { playerModel.next() }
//    val commandPrev = LiteUnitCommand { playerModel.previous() }
//    val commandNextChapter = LiteUnitCommand { playerModel.nextChapter() }
//    val commandPrevChapter = LiteUnitCommand { playerModel.prevChapter() }
    val commandSeekForward = LiteUnitCommand { playerModel.seekRelative(seekRelativeForward) }
    val commandSeekBackward = LiteUnitCommand { playerModel.seekRelative(-seekRelativeBackword) }
    val commandFullscreen = LiteUnitCommand { setWindowMode(WindowMode.FULLSCREEN) }
    val commandPinP = LiteUnitCommand { setWindowMode(WindowMode.PINP) }
    val commandCollapse = LiteUnitCommand { setWindowMode(WindowMode.NORMAL) }
//    val commandPlayerTapped = LiteUnitCommand()

    // endregion

    // region Fullscreen/PinP

    enum class WindowMode {
        NORMAL,
        FULLSCREEN,
        PINP
    }
    val windowMode : StateFlow<WindowMode> = MutableStateFlow(WindowMode.NORMAL)
    private fun setWindowMode(mode:WindowMode) {
        logger.debug("mode=${windowMode.value} --> $mode")
        windowMode.mutable.value = mode
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
        scope.cancel()
        playerModel.close()
    }

}

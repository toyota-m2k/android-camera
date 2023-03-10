package io.github.toyota32k.lib.player.model

import android.content.Context
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import java.io.Closeable

/**
 * コントローラーの共通実装
 * ControlPanelModel              基本実装（AmvFrameSelectorViewで使用）
 *   + FullControlPanelModel      フル機能プレイヤー（AmvPlayerUnitView）用
 *   + TrimmingControlPanelModel  トリミング画面(AMvTrimmingPlayerView)用
 */
open class ControlPanelModel(
    val playerModel: IPlayerModel,
    var supportChapter:Boolean,
    var supportFullscreen:Boolean,
    var supportPinP:Boolean
) : Closeable, IUtPropOwner {
    companion object {
        val logger by lazy { UtLog("CPM", TpLib.logger) }
        fun create(context: Context, supportChapter:Boolean=false, supportFullscreen:Boolean=false, supportPinP:Boolean=false, playerModel:IPlayerModel): ControlPanelModel {
//            val playerViewModel = IPlayerModel(context)
            return ControlPanelModel(playerModel, supportChapter, supportFullscreen, supportPinP)
        }
    }

    var seekRelativeForward:Long = 1000
    var seekRelativeBackword:Long = 500

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
    val commandNext = LiteUnitCommand { playerModel.next() }
    val commandPrev = LiteUnitCommand { playerModel.previous() }
    val commandNextChapter = LiteUnitCommand { playerModel.nextChapter() }
    val commandPrevChapter = LiteUnitCommand { playerModel.prevChapter() }
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

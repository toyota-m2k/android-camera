package io.github.toyota32k.lib.player.model

import android.app.Application
import android.content.Context
import android.util.Size
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.video.VideoSize
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.player.lib.R
import io.github.toyota32k.shared.UtManualIncarnateResetableValue
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.SuspendableEvent
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

open class BasicPlayerModel(
    context: Context,
    coroutineScope: CoroutineScope
) : IPlayerModel, IUtPropOwner {
    companion object {
        val logger by lazy { UtLog("PM", TpLib.logger) }
    }

    // region Properties / Status
    final override val context: Application = context.applicationContext as Application           // ApplicationContextならViewModelが持っていても大丈夫だと思う。
    final override val isPlaying = MutableStateFlow(false)
    /**
     * エラーメッセージ
     */
    final override val errorMessage: StateFlow<String?> = MutableStateFlow<String?>(null)

    enum class PlayerState {
        None,       // 初期状態
        Loading,
        Ready,
        Error,
    }
    protected val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState.None)
    protected val ended = MutableStateFlow(false)                   // 次回再生開始時に先頭に戻すため、最後まで再生したことを覚えておくフラグ
    private val watchPositionEvent = SuspendableEvent(signal = false, autoReset = false)    // スライダー位置監視を止めたり、再開したりするためのイベント

    // ExoPlayerのリスナー
    private val listener =  PlayerListener()

//    private val resetables = ManualResetables()
    val resetablePlayer = UtManualIncarnateResetableValue(
        onIncarnate = {
            ExoPlayer.Builder(context).build().apply {addListener(listener)}
        },
        onReset = { player->
            player.removeListener(listener)
            player.release()
        }
    )


    final override val scope = CoroutineScope( coroutineScope.coroutineContext + SupervisorJob() )
    final override val isLoading = state.map { it == PlayerState.Loading }.stateIn(scope, SharingStarted.Eagerly, false)
    final override val isReady = state.map { it== PlayerState.Ready }.stateIn(scope, SharingStarted.Eagerly, false)
    final override val isError = errorMessage.map { !it.isNullOrBlank() }.stateIn(scope, SharingStarted.Lazily, false)

    // ExoPlayer
    private val isDisposed:Boolean get() = !resetablePlayer.hasValue      // close済みフラグ
    protected val player: ExoPlayer? get() = if(resetablePlayer.hasValue) resetablePlayer.value else null

//    fun requirePlayer():ExoPlayer {
//        return player ?: throw IllegalStateException("ExoPlayer has been killed.")
//    }
//
//    protected inline fun <T> withPlayer(def:T, fn:(ExoPlayer)->T):T {
//        return player?.run {
//            fn(this)
//        } ?: def
//    }

    protected inline fun withPlayer(fn:(ExoPlayer)->Unit) {
        player?.apply{ fn(this) } ?: logger.error("no exoPlayer now")
    }
    protected inline fun <T> runOnPlayer(def:T, fn:ExoPlayer.()->T):T {
        return player?.run {
            fn()
        } ?: def
    }
    protected inline fun runOnPlayer(fn:ExoPlayer.()->Unit) {
        player?.apply {
            fn()
        }
    }

    /**
     * 現在再生中の動画のソース
     */
    override val currentSource = MutableStateFlow<IMediaSource?>(null)

    /**
     * 動画の全再生時間
     */
    override val naturalDuration: StateFlow<Long> = MutableStateFlow(0L)

    /**
     * 動画の画面サイズ情報
     * ExoPlayerの動画読み込みが成功したとき onVideoSizeChanged()イベントから設定される。
     */
    override val videoSize: StateFlow<Size?> = MutableStateFlow(null)

    /**
     * （外部から）エラーメッセージを設定する
     */
    fun setErrorMessage(msg:String?) {
        errorMessage.mutable.value = msg
    }

    // endregion
//    /**
//     * VideoSizeはExoPlayerの持ち物なので、ライブラリ利用者が明示的にexoplayerをリンクしていないとアクセスできない。
//     * そのような不憫な人のために中身を開示してあげる。
//     */
//    val videoWidth:Int? get() = videoSize.value?.width
//    val videoHeight:Int? get() = videoSize.value?.height

    /**
     * 動画プレーヤーを配置するルートビューのサイズ
     * AmvExoVideoPlayerビュークラスのonSizeChanged()からonRootViewSizeChanged()経由で設定される。
     * このルートビューの中に収まるよう、動画プレーヤーのサイズが調整される。
     */
//    private val rootViewSize: StateFlow<Size?> = MutableStateFlow<Size?>(null)

    /**
     * ルートビューに動画プレーヤーを配置する方法を指定
     *  true: ルートビューにぴったりフィット（Aspectは無視）
     *  false: ルートビューの中に収まるサイズ（Aspect維持）
     */
    // var stretchVideoToView = false
//    override val stretchVideoToView = MutableStateFlow(false)
    final override val rotation = MutableStateFlow(0)
    override fun rotate(value: Rotation) {
        if(value == Rotation.NONE) {
            rotation.value = 0
        } else {
            rotation.value = Rotation.normalize(rotation.value + value.degree)
        }
    }

//    private val mFitter = UtFitter(FitMode.Inside)
//    override val playerSize: StateFlow<Size> = combine(rotation, videoSize.filterNotNull(),rootViewSize.filterNotNull()) { rotation, videoSize, rootViewSize->
//        logger.debug("rotation=$rotation, videoSize=(${videoSize.width} x ${videoSize.height}), rootViewSize=(${rootViewSize.width} x ${rootViewSize.height})")
//        val size = Rotation.transposeSize(rotation, Size(videoSize.width, videoSize.height))
//        Rotation.transposeSize(rotation,mFitter
//            .setLayoutWidth(rootViewSize.width)
//            .setLayoutHeight(rootViewSize.height)
//            .fit(size.width, size.height)
//            .resultSize)
//            .apply { logger.debug("result playerSize = (${width} x ${height})") }
//    }.stateIn(scope, SharingStarted.Eagerly, Size(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

    /**
     * １つのアプリで、同時に、ExoPlayer を１つ以上インスタンス化できないようなので、
     * 複数のActivityでExoPlayerを使う場合に、ビューモデルを閉じて(close)、開き直す(openIfNeed) ことを可能にする。
     */
    // region About Current Movie

    // endregion

    // region Initialize / Termination

    init {
        isPlaying.onEach {
            if (it) {
                watchPositionEvent.set()
            }
        }.launchIn(scope)

        ended.onEach {
            if (it) {
                onPlaybackCompleted()
            }
        }.launchIn(scope)

        scope.launch {
            while (!isDisposed) {
                watchPositionEvent.waitOne()
                if (isPlaying.value) {
                    withPlayer { player ->
                        val src = currentSource.value as? IMediaSourceWithChapter
                        val pos = player.currentPosition
                        playerSeekPosition.mutable.value = pos
                        // 無効区間、トリミングによる再生スキップの処理
                        if (src is IMediaSourceWithChapter && src.chapterList.isNotEmpty) {
                            val dr = src.chapterList.disabledRanges(src.trimming)
                            val hit = dr.firstOrNull { it.contains(pos) }
                            if (hit != null) {
                                if (hit.end == 0L || hit.end >= naturalDuration.value) {
                                    ended.value = true
                                } else {
                                    player.seekTo(hit.end)
                                }
                            }
                        }
                    }
                } else {
                    watchPositionEvent.reset()
                }
                delay(50)
            }
        }
    }

    /**
     * 解放
     */
    override fun close() {
        logger.debug()
        currentSource.value = null
        resetablePlayer.reset()
        scope.cancel()
    }

    override fun killPlayer() {
        logger.debug()
        resetablePlayer.reset()
    }

    override fun revivePlayer():Boolean {
        logger.debug()
        return resetablePlayer.incarnate()
    }
    // endregion

    // region Seeking

    inner class SeekManagerEx : ISeekManager {
        override val requestedPositionFromSlider = MutableStateFlow(-1L)
        var lastOperationTick:Long = 0L
        var fastSync = false
        var running = false
        init {
            run()
        }

        private fun run() {
            if(running) return
            running = true
            requestedPositionFromSlider.onEach {
                val tick = System.currentTimeMillis()
                if(0<=it && it<=naturalDuration.value) {
                    if(tick-lastOperationTick<500) {
                        setFastSeek()
                    } else {
                        setExactSync()
                    }
                    clippingSeekTo(it, false)
                }
                delay(200L)
            }.onCompletion {
                logger.debug("SeekManager stopped.")
                running = false
            }.launchIn(scope)
        }

        private fun setFastSeek() {
            if(!fastSync) {
                runOnPlayer {
                    setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    fastSync = true
                }
            }
        }
        private fun setExactSync() {
            if(fastSync) {
                runOnPlayer {
                    setSeekParameters(SeekParameters.EXACT)
                    fastSync = false
                }
            }
        }
        fun reset() {
            setExactSync()
            lastOperationTick = 0L
            requestedPositionFromSlider.value = -1L
        }
    }
    override val seekManager = SeekManagerEx()

    /**
     * 0-durationで　引数 pos をクリップして返す。
     */
    protected fun clipPosition(pos:Long, trimming: Range?):Long {
        val duration = naturalDuration.value
        val s:Long
        val e:Long
        if(trimming==null) {
            s = 0L
            e = duration
        } else {
            s = max(0, trimming.start)
            e = if(trimming.end in (s + 1) until duration) trimming.end else duration
        }
        return max(s, min(pos,e))
    }

    /**
     * pseudoClippingを考慮したシーク
     */
    protected fun clippingSeekTo(pos:Long, awareTrimming:Boolean) {
        withPlayer { player ->
            val clippedPos = clipPosition(pos, if (awareTrimming) currentSource.value?.trimming else null)
            player.seekTo(clippedPos)
            playerSeekPosition.mutable.value = clippedPos
        }
    }

    override fun seekRelative(seek:Long) {
        if(!isReady.value) return
        withPlayer { player ->
            clippingSeekTo(player.currentPosition + seek, true)
        }
    }

    override fun seekTo(seek:Long) {
        if(!isReady.value) return
        clippingSeekTo(seek, true)
    }

    /**
     * プレーヤー内の再生位置
     * 動画再生中は、タイマーで再生位置(player.currentPosition)を監視して、このFlowにセットする。
     * スライダーは、これをcollectして、シーク位置を同期する。
     */
    override val playerSeekPosition: StateFlow<Long> =  MutableStateFlow(0L)

    /**
     * 再生中に EOS に達したときの処理
     * デフォルト： 再生を止めて先頭にシークする
     */
    override fun onPlaybackCompleted() {
        pause()
        clippingSeekTo(0, true)
    }

    // endregion

    // region ExoPlayer Video Player

    /**
     * ExoPlayerのイベントリスナークラス
     */
    inner class PlayerListener :  Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            this@BasicPlayerModel.videoSize.mutable.value = Size(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.stackTrace(error)
            if(!isReady.value) {
                state.mutable.value = PlayerState.Error
                errorMessage.mutable.value = context.getString(R.string.video_player_error)
            } else {
                logger.warn("ignoring exo error.")
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            isPlaying.value = playWhenReady
        }

        override fun onPlaybackStateChanged(playbackState:Int) {
            super.onPlaybackStateChanged(playbackState)
            when(playbackState) {
                Player.STATE_IDLE -> {
                    state.mutable.value = PlayerState.None
                }
                Player.STATE_BUFFERING -> {
                    if(state.value == PlayerState.None) {
                        state.mutable.value = PlayerState.Loading
                    } else {
//                        scope.launch {
//                            for (i in 0..20) {
//                                delay(100)
//                                if (runOnPlayer(PlayerState.None) { playbackState } != Player.STATE_BUFFERING) {
//                                    break
//                                }
//                            }
//                            if (runOnPlayer(PlayerState.None) { playbackState } == Player.STATE_BUFFERING) {
//                                // ２秒以上bufferingならロード中に戻す
//                                logger.debug("buffering more than 2 sec")
//                                state.mutable.value = PlayerState.Loading
//                            }
//                        }
                    }

                }
                Player.STATE_READY ->  {
                    ended.value = false
                    state.mutable.value = PlayerState.Ready
                    naturalDuration.mutable.value = runOnPlayer(0L) { duration }
                }
                Player.STATE_ENDED -> {
//                    player.playWhenReady = false
                    ended.value = true
                }
                else -> {}
            }
        }

//        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
//            if(useExoPlayList) {
//                state.mutable.value = PlayerState.None
//                videoSize.mutable.value = null
//                errorMessage.mutable.value = null
//                naturalDuration.mutable.value = 0L
//
//                currentSource.mutable.value = mediaItem?.getAmvSource()
//                hasNext.value = player.hasNextMediaItem()
//                hasPrevious.value = player.hasPreviousMediaItem()
//            }
//        }
    }

    override val currentPosition: Long
        get() = runOnPlayer(0L) { currentPosition }


    /**
     * View （StyledPlayerView）に Playerを関連付ける
     */
    override fun associatePlayerView(playerView: StyledPlayerView) {
        withPlayer { player ->
            playerView.player = player
        }
    }

    override fun dissociatePlayerView(playerView: StyledPlayerView) {
        playerView.player = null
    }

    /**
     * バックグラウンド再生（PlayerNotificationManager）対応用
     */
    fun associateNotificationManager(manager: PlayerNotificationManager) {
        withPlayer { player ->
            manager.setPlayer(player)
        }
    }

    // endregion

    // region Sizing of Player View

    /**
     * ルートビューサイズ変更のお知らせ
     */
//    override fun onRootViewSizeChanged(size: Size) {
//        rootViewSize.mutable.value = size
//    }

    // endregion

    // region Handling Media Sources

    fun MediaItem.getAmvSource(): IMediaSource {
        return this.localConfiguration!!.tag as IMediaSource
    }

    fun makeMediaSource(item:IMediaSource) : MediaSource {
        return ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(MediaItem.Builder().setUri(item.uri).setTag(item).build())
    }

    override fun setSource(src:IMediaSource?, autoPlay:Boolean) {
        reset()
        if(src==null) return
        currentSource.value = src
        val pos = max(src.trimming.start, src.startPosition.getAndSet(0L))

        runOnPlayer {
            setMediaSource(makeMediaSource(src), pos)
            prepare()
        }
        if(autoPlay) {
            play()
        }
    }

    // endregion

    // region Controlling Player

    /**
     * 再初期化
     */
    override fun reset() {
        logger.debug()
        pause()
        currentSource.value = null
        seekManager.reset()
        playerSeekPosition.mutable.value = 0L
        errorMessage.mutable.value = null
    }

    /**
     * Play / Pauseをトグル
     */
    override fun togglePlay() {
        if(isDisposed) return
        if(runOnPlayer(false) { playWhenReady} ) {
            pause()
        } else {
            play()
        }
    }

    /**
     * （再生中でなければ）再生を開始する
     */
    override fun play() {
        logger.debug()
        if(isDisposed) return
        errorMessage.mutable.value = null
        runOnPlayer {playWhenReady = true }
    }

    /**
     * 再生を中断する
     */
    override fun pause() {
        logger.debug()
        if(isDisposed) return
        runOnPlayer {playWhenReady = false }
    }

    // endregion
}
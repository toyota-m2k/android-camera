package io.github.toyota32k.lib.player.model

import android.app.Application
import android.content.Context
import android.util.Size
import android.view.ViewGroup
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.video.VideoSize
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.UtFitter
import io.github.toyota32k.lib.player.common.FitMode
import io.github.toyota32k.player.lib.R
import io.github.toyota32k.shared.UtManualIncarnateResetableValue
import io.github.toyota32k.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.max
import kotlin.math.min

open class BasicPlayerModel(
    context: Context,
) : IPlayerModel, IUtPropOwner {
    companion object {
        val logger by lazy { UtLog("PM", TpLib.logger) }
    }

    // region Properties / Status
    final override val context: Application = context.applicationContext as Application           // ApplicationContextならViewModelが持っていても大丈夫だと思う。

    enum class PlayerState {
        None,       // 初期状態
        Loading,
        Ready,
        Error,
    }
    protected val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState.None)

    private inner class DependsOnScope {
        val scope = UtManualIncarnateResetableValue { CoroutineScope(Dispatchers.Main+ SupervisorJob()) }
        val isLoading = UtManualIncarnateResetableValue { state.map { it == PlayerState.Loading }.stateIn(scope.value, SharingStarted.Eagerly, false) }
        val isReady = UtManualIncarnateResetableValue { state.map { it== PlayerState.Ready }.stateIn(scope.value, SharingStarted.Eagerly, false) }
        val isError = UtManualIncarnateResetableValue { errorMessage.map { !it.isNullOrBlank() }.stateIn(scope.value, SharingStarted.Lazily, false) }
        private val resetables = arrayOf(isLoading, isReady, isError)
        init {
            openIfNeed()
        }
        fun openIfNeed():Boolean {
            if(scope.hasValue) {
                return false
            } else {
                scope.incarnate()
                resetables.forEach {
                    it.incarnate()
                }

                isPlaying.onEach {
                    if(it) {
                        watchPositionEvent.set()
                    }
                }.launchIn(scope.value)

                ended.onEach {
                    if(it) {
                        onPlaybackCompleted()
                    }
                }.launchIn(scope.value)

                scope.value.launch {
                    while(!isDisposed) {
                        watchPositionEvent.waitOne()
                        if(isPlaying.value) {
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
                return true
            }
        }

        fun close() {
            scope.reset { scope->
                scope.cancel()
                resetables.forEach {
                    it.reset()
                }
            }
        }
    }
    private val dependsOnScope = DependsOnScope()

    final override val scope: CoroutineScope
        get() = dependsOnScope.scope.value
    final override val isLoading
        get() = dependsOnScope.isLoading.value
    final override val isReady
        get() = dependsOnScope.isReady.value
    final override val isPlaying = MutableStateFlow(false)


    protected val ended = MutableStateFlow(false)                   // 次回再生開始時に先頭に戻すため、最後まで再生したことを覚えておくフラグ
    private val watchPositionEvent = SuspendableEvent(signal = false, autoReset = false)    // スライダー位置監視を止めたり、再開したりするためのイベント


    /**
     * エラーメッセージ
     */
    final override val errorMessage: StateFlow<String?> = MutableStateFlow<String?>(null)
    final override val isError = errorMessage.map { !it.isNullOrBlank() }.stateIn(scope, SharingStarted.Lazily, false)

    /**
     * （外部から）エラーメッセージを設定する
     */
    fun setErrorMessage(msg:String?) {
        errorMessage.mutable.value = msg
    }

    // endregion

    // region About Current Movie

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
    val videoSize: StateFlow<VideoSize?> = MutableStateFlow<VideoSize?>(null)

    // endregion

    // region Initialize / Termination

    init {
        dependsOnScope.openIfNeed()
//        isPlaying.onEach {
//            if(it) {
//                watchPositionEvent.set()
//            }
//        }.launchIn(scope)
//
//        ended.onEach {
//            if(it) {
//                onPlaybackCompleted()
//            }
//        }.launchIn(scope)
//
//
//        scope.launch {
//            while(!isDisposed) {
//                watchPositionEvent.waitOne()
//                if(isPlaying.value) {
//                    withPlayer { player ->
//                        val src = currentSource.value as? IMediaSourceWithChapter
//                        val pos = player.currentPosition
//                        playerSeekPosition.mutable.value = pos
//                        // 無効区間、トリミングによる再生スキップの処理
//                        if (src is IMediaSourceWithChapter && src.chapterList.isNotEmpty) {
//                            val dr = src.chapterList.disabledRanges(src.trimming)
//                            val hit = dr.firstOrNull { it.contains(pos) }
//                            if (hit != null) {
//                                if (hit.end == 0L || hit.end >= naturalDuration.value) {
//                                    ended.value = true
//                                } else {
//                                    player.seekTo(hit.end)
//                                }
//                            }
//                        }
//                    }
//                } else {
//                    watchPositionEvent.reset()
//                }
//                delay(50)
//            }
//        }
    }

    /**
     * 解放
     */
    override fun close() {
        logger.debug()
        currentSource.value = null
        resetablePlayer.reset { player->
            player.removeListener(listener)
            player.release()
        }
        dependsOnScope.close()
    }

    override fun openIfNeed(): Boolean {
        logger.debug()
        return dependsOnScope.openIfNeed()
    }

    // endregion

    // region Seeking

    inner class SeekManagerEx : ISeekManager {
        override val requestedPositionFromSlider = MutableStateFlow<Long>(-1L)
        var lastOperationTick:Long = 0L
        var fastSync = false
        init {
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
            }.launchIn(scope)
        }

        fun setFastSeek() {
            if(!fastSync) {
                runOnPlayer {
                    setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    fastSync = true
                }
            }
        }
        fun setExactSync() {
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
            this@BasicPlayerModel.videoSize.mutable.value = videoSize
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

    // ExoPlayerのリスナー
    private val listener =  PlayerListener()

    // ExoPlayer
    private val resetablePlayer = UtManualIncarnateResetableValue<ExoPlayer> { ExoPlayer.Builder(context).build().apply {addListener(listener)} }
    private val isDisposed:Boolean get() = !resetablePlayer.hasValue      // close済みフラグ
    protected val player: ExoPlayer? = if(resetablePlayer.hasValue) resetablePlayer.value else null
    protected inline fun <T> withPlayer(def:T, fn:(ExoPlayer)->T):T {
        return player?.run {
            fn(this)
        } ?: def
    }
    protected inline fun withPlayer(fn:(ExoPlayer)->Unit) {
        player?.apply{ fn(this) }
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

    override val currentPosition: Long
        get() = runOnPlayer(0L) { currentPosition }


    /**
     * View （StyledPlayerView）に Playerを関連付ける
     */
    override fun associatePlayerView(playerView: StyledPlayerView) {
        playerView.player = player
    }

    /**
     * バックグラウンド再生（PlayerNotificationManager）対応用
     */
    fun associateNotificationManager(manager: PlayerNotificationManager) {
        manager.setPlayer(player)
    }

    // endregion

    // region Sizing of Player View

    /**
     * VideoSizeはExoPlayerの持ち物なので、ライブラリ利用者が明示的にexoplayerをリンクしていないとアクセスできない。
     * そのような不憫な人のために中身を開示してあげる。
     */
    val videoWidth:Int? get() = videoSize.value?.width
    val videoHeight:Int? get() = videoSize.value?.height

    /**
     * 動画プレーヤーを配置するルートビューのサイズ
     * AmvExoVideoPlayerビュークラスのonSizeChanged()からonRootViewSizeChanged()経由で設定される。
     * このルートビューの中に収まるよう、動画プレーヤーのサイズが調整される。
     */
    private val rootViewSize: StateFlow<Size?> = MutableStateFlow<Size?>(null)

    /**
     * ルートビューサイズ変更のお知らせ
     */
    override fun onRootViewSizeChanged(size: Size) {
        rootViewSize.mutable.value = size
    }

    /**
     * ルートビューに動画プレーヤーを配置する方法を指定
     *  true: ルートビューにぴったりフィット（Aspectは無視）
     *  false: ルートビューの中に収まるサイズ（Aspect維持）
     */
    // var stretchVideoToView = false
    override val stretchVideoToView = MutableStateFlow(false)
    final override val rotation = MutableStateFlow(0)
    override fun rotate(value: Rotation) {
        if(value == Rotation.NONE) {
            rotation.value = 0
        } else {
            rotation.value = Rotation.normalize(rotation.value + value.degree)
        }
    }

    private val mFitter = UtFitter(FitMode.Inside)
    override val playerSize = combine(rotation, videoSize.filterNotNull(),rootViewSize.filterNotNull()) { rotation, videoSize, rootViewSize->
        logger.debug("rotation=$rotation, videoSize=(${videoSize.width} x ${videoSize.height}), rootViewSize=(${rootViewSize.width} x ${rootViewSize.height})")
        val size = Rotation.transposeSize(rotation, Size(videoSize.width, videoSize.height))
        Rotation.transposeSize(rotation,mFitter
            .setLayoutWidth(rootViewSize.width)
            .setLayoutHeight(rootViewSize.height)
            .fit(size.width, size.height)
            .resultSize)
            .apply { logger.debug("result playerSize = (${width} x ${height})") }
    }.stateIn(scope, SharingStarted.Eagerly, Size(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

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
        seekManager.reset()
        errorMessage.mutable.value = null    }

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
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
import io.github.toyota32k.lib.player.common.AmvFitterEx
import io.github.toyota32k.lib.player.common.FitMode
import io.github.toyota32k.player.lib.R
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.SuspendableEvent
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.max
import kotlin.math.min

open class BasicPlayerModel(
    context: Context,
    final override val scope: CoroutineScope // dispose()まで有効なコルーチンスコープ)
) : IPlayerModel, IUtPropOwner {
    companion object {
        val logger by lazy { UtLog("PM", TpLib.logger) }
    }

    // region Properties / Status
    final override val context: Application = context.applicationContext as Application           // ApplicationContextならViewModelが持っていても大丈夫だと思う。

    /**
     * プレーヤーの状態
     */

    enum class PlayerState {
        None,       // 初期状態
        Loading,
        Ready,
        Error,
    }
    protected val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState.None)

    final override val isLoading = state.map { it == PlayerState.Loading }.stateIn(scope, SharingStarted.Eagerly, false)
    final override val isReady = state.map { it== PlayerState.Ready }.stateIn(scope, SharingStarted.Eagerly, false)
    final override val isPlaying = MutableStateFlow<Boolean>(false)

    protected val ended = MutableStateFlow(false)                   // 次回再生開始時に先頭に戻すため、最後まで再生したことを覚えておくフラグ
    private val watchPositionEvent = SuspendableEvent(signal = false, autoReset = false)    // スライダー位置監視を止めたり、再開したりするためのイベント

    private var isDisposed:Boolean = false      // close済みフラグ
        private set

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
        isPlaying.onEach {
            if(it) {
                watchPositionEvent.set()
            }
        }.launchIn(scope)

        ended.onEach {
            if(it) {
                onPlaybackCompleted()
            }
        }.launchIn(scope)


        scope.launch {
            while(!isDisposed) {
                watchPositionEvent.waitOne()
                if(isPlaying.value) {
                    val src = currentSource.value as? IMediaSourceWithChapter
                    val pos = player.currentPosition
                    playerSeekPosition.mutable.value = pos
                    // 無効区間、トリミングによる再生スキップの処理
                    val dr = src?.disabledRanges
                    if(dr!=null) {
                        val hit = dr.firstOrNull { it.contains(pos) }
                        if (hit != null) {
                            if (hit.end == 0L || hit.end >= naturalDuration.value) {
                                ended.value = true
                            } else {
                                player.seekTo(hit.end)
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
        player.removeListener(listener)
        player.release()
        scope.cancel()
        isDisposed = true
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
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                fastSync = true
            }
        }
        fun setExactSync() {
            if(fastSync) {
                player.setSeekParameters(SeekParameters.EXACT)
                fastSync = false
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
        val clippedPos = clipPosition(pos, if(awareTrimming) currentSource.value?.trimming else null )
        player.seekTo(clippedPos)
        playerSeekPosition.mutable.value = clippedPos
    }

    override fun seekRelative(seek:Long) {
        if(!isReady.value) return
        clippingSeekTo(player.currentPosition + seek, true )
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
                        scope.launch {
                            for (i in 0..20) {
                                delay(100)
                                if (player.playbackState != Player.STATE_BUFFERING) {
                                    break
                                }
                            }
                            if (player.playbackState == Player.STATE_BUFFERING) {
                                // ２秒以上bufferingならロード中に戻す
                                logger.debug("buffering more than 2 sec")
                                state.mutable.value = PlayerState.Loading
                            }
                        }
                    }

                }
                Player.STATE_READY ->  {
                    ended.value = false
                    state.mutable.value = PlayerState.Ready
                    naturalDuration.mutable.value = player.duration
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
    protected val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(listener)
    }

    override val currentPosition: Long
        get() = player.currentPosition


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

    private val mFitter = AmvFitterEx(FitMode.Inside)
    override val playerSize = combine(videoSize.filterNotNull(),rootViewSize.filterNotNull()) { videoSize, rootViewSize->
        logger.debug("videoSize=(${videoSize.height} x ${videoSize.height}), rootViewSize=(${rootViewSize.width} x ${rootViewSize.height})")
        mFitter
            .setLayoutWidth(rootViewSize.width)
            .setLayoutHeight(rootViewSize.height)
            .fit(videoSize.width, videoSize.height)
            .resultSize
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

        player.setMediaSource(makeMediaSource(src), pos)
        player.prepare()
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
    }

    /**
     * Play / Pauseをトグル
     */
    override fun togglePlay() {
        if(player.playWhenReady) {
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
        player.playWhenReady = true
    }

    /**
     * 再生を中断する
     */
    override fun pause() {
        logger.debug()
        if(isDisposed) return
        player.playWhenReady = false
    }

    // endregion
}
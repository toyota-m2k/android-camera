package io.github.toyota32k.boodroid.data

import android.Manifest
import android.os.Build
import io.github.toyota32k.dialog.UtDialogBase
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.withActivity
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.MainActivity
import io.github.toyota32k.secureCamera.client.BooTubeDiscovery
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.secureCamera.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * アクティブホスト (active host) の IP/port を mDNS-SD で常時監視し、
 * 変動があれば自動で `Settings` の `activeHost.address` を更新するトラッカ。
 *
 * 動作概要:
 *   - [BooTubeDiscovery] のシングルトン的なインスタンスをアプリ起動中ずっと走らせる
 *   - `discovery.services` (StateFlow) を購読
 *   - 現在の `AppViewModel.instance.settings.activeHost.serviceName` と一致するエントリの
 *     IP/port が変わった瞬間、settings を更新して永続化する
 *
 * 利点:
 *   - DHCP リース更新等で BooTube PC の IP が変わっても、ユーザが何もしなくても追従できる
 *   - API 34+ 端末では `NsdManager.registerServiceInfoCallback` 経由で push 通知的に更新される
 *
 * 注意:
 *   - `AppViewModel.instance.settings = newSettings` の setter は `refreshCommand` を発火するため、
 *     IP 変動時は動画リスト再取得が走る (UX 上は再表示で済むので許容範囲)
 *   - 並行して [HostAddressDialog] や [BooTubeDiscovery.resolveOnce] が独自に discovery を起動する
 *     ことがあるが、`NsdManager` は複数 listener を許容するので衝突しない
 */
object ActiveHostTracker {
    private val logger = UtLog("HostTracker", NetClient.logger)

    private var discovery: BooTubeDiscovery? = null
    private var collectorJob: Job? = null

    fun start() {
        UtImmortalTask.launchTask("ActiveHostTracker") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                val permitted = withActivity<MainActivity, Boolean> { activity ->
                    activity.activityBrokers.permissionBroker.requestPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }
                if (!permitted) {
                    UtDialogBase.logger.warn("ACCESS_LOCAL_NETWORK permission denied.")
                    return@launchTask
                }
            }

            if (discovery != null) return@launchTask
            discovery = BooTubeDiscovery().apply {
                start()
                collectorJob = CoroutineScope(Dispatchers.IO).launch {
                    services.collect { servers ->
                        handleServerUpdates(servers)
                    }
                }
                logger.debug("ActiveHostTracker started")
            }
        }
    }

    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
        discovery?.stop()
        discovery = null
        logger.debug("ActiveHostTracker stopped")
    }

    private fun handleServerUpdates(servers: List<BooTubeDiscovery.DiscoveredServer>) {
        for (svr in servers) {
            if (svr.app == "SA") {
                val oldHost = Settings.SecureArchive.getPairedHost(svr.serviceName) ?: continue
                val newAddr = "${svr.host}:${svr.port}"
                val newFp = svr.host
                if (newAddr != oldHost.address || newFp != oldHost.fingerprint) {
                    Settings.SecureArchive.updateHost(oldHost, svr.toHost())
                }
            }
        }
    }
}

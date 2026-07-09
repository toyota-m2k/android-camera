package io.github.toyota32k.secureCamera

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.combinatorialVisibilityBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.mortal.UtMortalActivity
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.dialog.task.showOkCancelMessageBox
import io.github.toyota32k.dialog.task.showRadioSelectionBox
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.client.auth.AuthHost
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.databinding.ActivityServerBinding
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.MetaData
import io.github.toyota32k.secureCamera.db.ScDB
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.server.NetworkUtils
import io.github.toyota32k.secureCamera.server.TcServer
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotIndex
import io.github.toyota32k.secureCamera.settings.SlotSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerActivity : UtMortalActivity() {
    companion object {
        val logger = UtLog("SERVER")
    }
    class ServerViewModel(application: Application) : AndroidViewModel(application) {
        private val metaDb:ScDB = MetaDB[SlotSettings.currentSlotIndex]
        val port = Settings.Server.myPort
        val ssl = Settings.Server.ssl
        val server = TcServer(port)
        lateinit var address: String
        val myAddressText = MutableStateFlow("unknown")
        val statusString = MutableStateFlow("Starting...")
        val backupCommand = LiteUnitCommand(::backup)
        val purgeCommand = LiteUnitCommand(::purge)
        val repairCommand = LiteUnitCommand(::repair)
        val migrateCommand = LiteUnitCommand(::migrate)
        val backupDbCommand = LiteUnitCommand(::backupDB)
        val selectHostCommand = LiteUnitCommand {
            viewModelScope.launch {
                if (isBusy.value) return@launch
                isBusy.value = true
                try {
                    selectHost(silent = false)
                } finally {
                    isBusy.value = false
                }
            }
        }
        val isBusy = MutableStateFlow(false)
        val isMaintenanceMode = MutableStateFlow(false)

        val selectedHost = MutableStateFlow<AuthHost?>(null)
        init {
            try {
                logger.debug("start server")
                isBusy.value = true
                server.start()
                statusString.value = "Ready"
                viewModelScope.launch {
                    address = NetworkUtils.getIpAddress(application)
                    myAddressText.value = "${if(ssl) "HTTPS" else "HTTP"} - $address:$port"
                    selectHost(silent=true)
                    isBusy.value = false
                }
            } catch(e:Throwable) {
                statusString.value = "Error"
                logger.error(e)
            }
        }

        private suspend fun selectHostCore(silent:Boolean):AuthHost? {
            if (Authentication.hosts.isEmpty()) {
                if (!silent) {
                    UtImmortalTask.awaitTask { showConfirmMessageBox(null, "No host registered.") }
                }
                return null
            }
            val connectables = Authentication.connectableHosts()
            if (connectables.isEmpty()) {
                if (!silent) {
                    UtImmortalTask.awaitTask { showConfirmMessageBox(null, "No active host found.") }
                }
                return null
            }
            if (connectables.size == 1) {
                val host = connectables.first()
                if (!silent) {
                    UtImmortalTask.awaitTask { showConfirmMessageBox(null, "${host.displayName}: single host") }
                }
                return host
            }
            return UtImmortalTask.awaitTaskResult {
                val items = connectables.map { it.displayName }.toTypedArray()
                val sel = showRadioSelectionBox("Select Host", items, 0)
                if (sel<0||connectables.size<=sel) {
                    if (!silent) {
                        showConfirmMessageBox(null, "No host selected.")
                    }
                    null
                } else connectables[sel]
            }
        }
        private suspend fun selectHost(silent:Boolean):AuthHost? {
            selectedHost.value = selectHostCore(silent)
            return selectedHost.value
        }
        private suspend fun authenticatedHost():AuthHost? {
            val sel = selectedHost.value ?: selectHost(silent=false) ?: return null
            if (!Authentication.authenticate(sel).message()) {
                return null
            }
            return sel
        }

        private suspend fun backupCore(title:String, message:String, backupReq:suspend (String)->Unit) {
            if (isBusy.value) return
            isBusy.value = true
            try {
                val decision = UtImmortalTask.awaitTaskResult("Server.backup") {
                    showOkCancelMessageBox(
                        title,
                        message,
                        "OK",
                        "Cancel"
                    )
                }
                if (!decision) {
                    return
                }
                backupReq("${address}:${port}")
            } finally {
                isBusy.value = false
            }
        }

        /**
         * SAに対してバックアップの開始を要求
         * - すべての（sync==trueの）MetaDBが対象
         */
        private fun backup() {
            viewModelScope.launch {
                val host = authenticatedHost() ?: return@launch
                backupCore(
                    "Backup Media Files",
                    "Backup all media files to ${host.displayName}",
                ) { TcClient.requestBackupData(host, it) }
            }
        }

        /**
         * すべてのScDBを(SAが稼働している)PCにバックアップする
         * SAのDBも同じフォルダにバックアップされる。
         * ただし、DBのリストアは未実装。
         */
        private fun backupDB() {
            viewModelScope.launch {
                val host = authenticatedHost() ?: return@launch
                backupCore(
                    "Backup Databases",
                    "Backup databases to ${host.displayName}",
                ) { TcClient.requestBackupDB(host,it)}
            }
        }

        /**
         * バックアップ済みアイテムのローカルファイルを一括削除
         * - カレントMetaDBだけが対象
         */
        private fun purge() {
            isBusy.value = true
            viewModelScope.launch {
                try {
                    metaDb.purgeAllLocalFiles()
                } catch(_:Throwable) {
                } finally {
                    isBusy.value = false
                }
            }
        }

        /**
         * SAにバックアップ後、ローカルで削除してしまったファイルを、SAから取得しなおして復元する。
         * - カレントMetaDBだけが対象
         */
        private fun repair() {
            isBusy.value = true
            viewModelScope.launch {
                try {
                    val host = authenticatedHost() ?: return@launch
                    val itemsOnServer = TcClient.getListForRepair(host, SlotSettings.currentSlotIndex) ?: return@launch
                    val itemsOnLocal = metaDb.list(PlayerActivity.ListMode.ALL).fold(mutableMapOf<Int, MetaData>()) { map, item -> map.apply { put(item.id, item)} }
                    var count = 0
                    for(item in itemsOnServer) {
                        if(!itemsOnLocal.contains(item.originalId)) {
                            // サーバーにのみ存在するレコード
                            logger.debug("found target item: ${item.name} / ${item.id}")
                            metaDb.repairWithBackup(SCApplication.instance, item, host)
                            count++
                        }
                    }
                    logger.debug("$count items has been repaired.")
                } finally {
                    isBusy.value = false
                }
            }
        }

        /**
         * 新しいデバイスから、SAに古いデバイスの移行を依頼する。
         * SAは、登録されているデバイスの一覧を返してくるので、Private Camera側でターゲットデバイスを選択すれば、
         * そのデバイスで登録されたメディアファイルのオーナーが、このデバイスに書き変わり、PlayerActivityの一覧に列挙される。
         * 移行したメディアファイルは、「アップロード＆パージ済み」になっており、必要ならRestoreすることができる。
         */
        private fun migrate() {
            logger.debug()

            UtImmortalTask.launchTask("migrate") {
                isBusy.value = true
                try {
                    val host = authenticatedHost() ?: return@launchTask
                    // ターゲットデバイスを選択し、サーバーに対してマイグレーションの開始を宣言する。
                    val migration = try {
                        prepareMigration(host, this)
                    } catch(e:Throwable) {
                        logger.error(e, "migration cannot be started")
                        showConfirmMessageBox(
                            "Migration",
                            message = "${e.javaClass.simpleName} ${e.message ?: "unknown exception."}"
                        )
                        null
                    } ?: return@launchTask

                    // プログレスダイアログの準備
                    val pvm = createViewModel<ProgressDialog.ProgressViewModel>()
                    pvm.message.value = "Migrating..."

                    // マイグレーション本体
                    var succeeded = false
                    try {
                        succeeded = migrateCore(this, migration, pvm)
                    } catch (e: Throwable) {
                        logger.error(e, "migration failed")
                        showConfirmMessageBox(
                            "Migration",
                            message = "${e.javaClass.simpleName} ${e.message ?: "unknown exception."}"
                        )
                    } finally {
                        // プログレスダイアログを閉じる
                        pvm.closeCommand.invoke(succeeded)
                        // サーバーに対して、マイグレーションの終了を宣言する
                        TcClient.endMigration(migration)
                    }
                } finally {
                    isBusy.value = false
                }
            }
        }

        // 長い文字列（UUID）を XXXXX...XXXXX の書式に短縮する。
        private fun shorten(text:String, head:Int=5, tail:Int=5):String {
            return if(text.length>head+tail) {
                "${text.substring(0, head)}...${text.substring(text.length-tail)}"
            } else {
                text
            }
        }

        private suspend fun prepareMigration(host: AuthHost, task: IUtImmortalTask) : TcClient.MigrationInfo? {
            val devices = TcClient.getDeviceListForMigration(host)
            var message:String? = null
            while (true) {
                if (devices.isNullOrEmpty()) {
                    message = "No devices found."
                    break
                }
                val index = task.showRadioSelectionBox(
                    "Select Device",
                    devices.map { "${it.name} - ${shorten(it.clientId)}" }.toTypedArray(),
                    0
                )
                if (index<0) {
                    // cancelled
                    break
                }
                if (!task.showOkCancelMessageBox(
                        "Migration",
                        "Are you sure to migrate data from ${devices[index].name}?"
                    )
                ) {
                    // cancelled
                    break
                }

                val device = devices[index]

                val migration = TcClient.startMigration(host,device)
                if (migration == null) {
                    message = "Failed to start migration."
                    break
                }
                if (migration.list.isEmpty()) {
                    // サーバーの仕様上、空のリストを返すことはないが、念のためチェック
                    message = "No data to migrate from ${device.name}."
                    break
                }
                return migration
            }
            if (message!=null) {
                task.showConfirmMessageBox("Migration", message)
            }
            return null
        }

        private suspend fun migrateCore(task: IUtImmortalTask, migration:TcClient.MigrationInfo, pvm:ProgressDialog.ProgressViewModel):Boolean {
            var cancelled = false
            pvm.message.value = "Migrating..."
            pvm.cancelCommand.bindForever { cancelled = true }
            // プログレスダイアログをモーダル表示
            task.subTask().launchTask {
                showDialog(ProgressDialog())
            }

            return withContext(Dispatchers.IO) {
                MetaDB.dbCache().use { dbCache ->
                    var imported = 0
                    var invalid = 0
                    for (entry in migration.list) {
                        if (cancelled) {
                            logger.debug("migration cancelled at $imported/$invalid/${migration.list.size}")
                            return@use false
                        }
                        imported++
                        pvm.progress.value = imported * 100 / migration.list.size
                        pvm.progressText.value = "$imported / ${migration.list.size}"
                        // 端末側DBに移行データのエントリーを作る
                        val data = dbCache[SlotIndex.fromIndex(entry.slot)].migrateOne(migration.handle, entry)
                        // DB的に移行が出来たことをサーバーに知らせる
                        val result = TcClient.reportMigratedOne(migration, entry, data.id)
                        if (!result) {
                            // この状態になったら
                            // - この端末内に、エントリーが追加されている
                            // - サーバー上での移行が失敗しているので、参照先のないエントリーになる。
                            // つまり、表示できないエントリーができてしまう。
                            // ロールバックしようかとも考えたが、万が一、サーバーのDB書き換えが成功しているのに通信エラーなどで、
                            // 応答が得られず、ここに入ってくる可能性があり、その場合は、存在するのに、どの端末からも見えないエントリーに
                            // なってしまうリスクがあるので、ここは無視して、見えないだけの余分なエントリーが追加されてしまうほうが安全サイドと考えた。
                            invalid++
                            logger.error("failed to update item in SecureArchive: ${data.name} / ${data.id}")
                        }
                    }
                    logger.debug("selected device: ${migration.deviceInfo.name} / ${migration.deviceInfo.clientId}")
                    task.showConfirmMessageBox("Migration", "Migration completed.\nImported: $imported (Invalid: $invalid) / Total: ${migration.list.size}")
                    true
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            server.close()
            metaDb.close()
            logger.debug("stop server")
        }
    }

    private val viewModel by viewModels<ServerViewModel>()
    private lateinit var controls: ActivityServerBinding
    private val binder = Binder()

    override fun onCreate(savedInstanceState: Bundle?) {
        Settings.Design.applyToActivity(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()  // 最近(2024/3/28現在)のAndroid Studioのテンプレートが書き出すコード（１）。。。タブレットでステータスバーなどによってクライアント領域が不正になる現象が回避できるっぽい。、

        controls = ActivityServerBinding.inflate(layoutInflater)
        setContentView(controls.root)

        // 最近(2024/3/28現在)のAndroid Studioのテンプレートが書き出すコード（２）
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.server)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binder.owner(this)
            .textBinding(controls.serverStatus, viewModel.statusString.map { "Status: $it"})
            .textBinding(controls.serverAddress, viewModel.selectedHost.map { it?.displayName ?: "Host is not selected." })
            .bindCommand(viewModel.backupCommand, controls.backupButton)
            .bindCommand(viewModel.purgeCommand, controls.purgeButton)
            .bindCommand(viewModel.repairCommand, controls.repairButton)
            .bindCommand(viewModel.migrateCommand, controls.migrateButton)
            .bindCommand(viewModel.backupDbCommand, controls.backupDbButton)
            .bindCommand(viewModel.selectHostCommand, controls.hostButton)
            .checkBinding(controls.maintenanceCheckbox, viewModel.isMaintenanceMode)
            .multiEnableBinding(arrayOf(controls.purgeButton,controls.backupButton, controls.repairButton, controls.migrateButton), viewModel.isBusy, boolConvert = BoolConvert.Inverse)
            .combinatorialVisibilityBinding(viewModel.isMaintenanceMode) {
                inverseGone(controls.backupButton, controls.purgeButton)
                straightGone(controls.repairButton, controls.migrateButton, controls.backupDbButton)
            }
            .visibilityBinding(controls.progressRing, viewModel.isBusy, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)
    }
}
package io.github.toyota32k.secureCamera

import android.app.Application
import android.os.Build
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
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.dialog.task.showOkCancelMessageBox
import io.github.toyota32k.dialog.task.showRadioSelectionBox
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.databinding.ActivityServerBinding
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.MetaData
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.server.NetworkUtils
import io.github.toyota32k.secureCamera.server.TcServer
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ServerActivity : UtMortalActivity() {
    companion object {
        val logger = UtLog("SERVER")
    }
    class ServerViewModel(application: Application) : AndroidViewModel(application) {
        private var dbOpened:Boolean = MetaDB.open()
        val port = Settings.SecureArchive.myPort
        val server = TcServer(port)
        val ipAddress = MutableStateFlow("unknown")
        val statusString = MutableStateFlow("Starting...")
        val backupCommand = LiteUnitCommand(::backup)
        val purgeCommand = LiteUnitCommand(::purge)
        val repairCommand = LiteUnitCommand(::repair)
        val migrateCommand = LiteUnitCommand(::migrate)
        val isBusy = MutableStateFlow(false)
        val isMaintenanceMode = MutableStateFlow(false)

        init {
            try {
                logger.debug("start server")
                server.start()
                statusString.value = "Running"
                viewModelScope.launch {
                    val addr = NetworkUtils.getIpAddress(application)
                    val index = addr.indexOf( "/")
                    ipAddress.value = if(index>0) {
                        addr.substring(0, index)
                    } else {
                        addr
                    }
                }
            } catch(e:Throwable) {
                statusString.value = "Error"
                logger.error(e)
            }
        }

        private fun backup() {
            viewModelScope.launch {
                if(!Authentication.authenticateAndMessage()) {
                    return@launch
                }
                val decision = UtImmortalTask.awaitTaskResult("Server.backup") {
                    showOkCancelMessageBox(
                        "Backup",
                        "Backup to ${Authentication.activeHostLabel}",
                        "OK",
                        "Cancel"
                    )
                }
                if(!decision) {
                    return@launch
                }

                val json = JSONObject()
                    .put("id", Settings.SecureArchive.clientId)
                    .put("name", Build.MODEL)
                    .put("type", "SecureCamera")
                    .put("token", TcServer.updateAuthToken())
                    .put("address", "${ipAddress.value}:${Settings.SecureArchive.myPort}")
                    .toString()
                val request = Request.Builder()
                    .url("http://${Authentication.activeHostAddress}/backup/request")
                    .put(json.toRequestBody("application/json".toMediaType()))
                    .build()
                try {
                    NetClient.executeAndGetJsonAsync(request)
                    logger.info("backup started.")
                } catch (e:Throwable) {
                    logger.error(e)
                }
            }
        }

        private fun purge() {
            isBusy.value = true
            viewModelScope.launch {
                try {
                    MetaDB.purgeAllLocalFiles()
                } catch(_:Throwable) {
                } finally {
                    isBusy.value = false
                }
            }
        }

        private fun repair() {
            isBusy.value = true
            viewModelScope.launch {
                try {
                    val itemsOnServer = TcClient.getListForRepair() ?: return@launch
                    val itemsOnLocal = MetaDB.list(PlayerActivity.ListMode.ALL).fold(mutableMapOf<Int, MetaData>()) { map, item -> map.apply { put(item.id, item)} }
                    var count = 0
                    for(item in itemsOnServer) {
                        if(!itemsOnLocal.contains(item.originalId)) {
                            // サーバーにのみ存在するレコード
                            logger.debug("found target item: ${item.name} / ${item.id}")
                            MetaDB.repairWithBackup(SCApplication.instance, item)
                            count++
                        }
                    }
                    logger.debug("$count items has been repaired.")
                } finally {
                    isBusy.value = false
                }
            }
        }

        private fun migrate() {
            isBusy.value = true
            UtImmortalTask.launchTask("migrate") {
                try {
                    fun shorten(text:String, head:Int=5, tail:Int=5):String {
                        return if(text.length>head+tail) {
                            "${text.substring(0, head)}...${text.substring(text.length-tail)}"
                        } else {
                            text
                        }
                    }
                    val devices = TcClient.getDeviceListForMigration() ?: return@launchTask
                    if(devices.isEmpty()) {
                        showConfirmMessageBox("Migration", "No devices found.")
                        return@launchTask
                    }
                    var index = showRadioSelectionBox("Select Device", devices.map { "${it.name} - ${shorten(it.clientId)}" }.toTypedArray(), 0)
                    var device = if(index<0 || !showOkCancelMessageBox("Migration", "Are you sure to migrate data from ${devices[index].name}?")) {
                        return@launchTask
                    } else devices[index]

                    val migration = TcClient.startMigration(device.clientId)
                    if(migration==null) {
                        showConfirmMessageBox("Migration", "Migration failed.")
                        return@launchTask
                    }
                    if(migration.list.isEmpty()) {
                        showConfirmMessageBox("Migration", "No data to migrate.")
                        return@launchTask
                    }
                    var cancelled = false
                    val pvm = createViewModel<ProgressDialog.ProgressViewModel>()
                    pvm.message.value = "Migrating..."
                    pvm.cancelCommand.bindForever { cancelled = true }
                    var count = 0
                    withContext(Dispatchers.IO) {
                        for (entry in migration.list) {
                            if (cancelled) {
                                break
                            }
                            count++
                            pvm.progress.value = (count * 100 / migration.list.size).toInt()
                            pvm.progressText.value = "$count / ${migration.list.size}"
                            // 端末側DBに移行データのエントリーを作る
                            val data = MetaDB.migrateOne(migration.handle, entry)
                            if (data != null) {
                                // DB的に移行が出来たことをサーバーに知らせる
                                var result = TcClient.reportMigratedOne(migration.handle, entry, data.id)
                                if (!result) {
                                    // この状態になったら
                                    // - この端末内に、エントリーが追加されている
                                    // - サーバー上での移行が失敗しているので、参照先のないエントリーになる。
                                    // つまり、表示できないエントリーができてしまう。
                                    // ロールバックしようかとも考えたが、万が一、サーバーのDB書き換えが成功しているのに通信エラーなどで、
                                    // 応答が得られず、ここに入ってくる可能性があり、その場合は、存在するのに、どの端末からも見えないエントリーに
                                    // なってしまうリスクがあるので、ここは無視して、見えないだけの余分なエントリーが追加されてしまうほうが安全サイドと考えた。
                                    logger.error("failed to update item in SecureArchive: ${data.name} / ${data.id}")
                                }
                            } else {
                                logger.error("failed to update item in MetaDB: ${entry.name} / ${entry.originalId}")
                            }
                        }
                    }

                    TcClient.endMigration(migration.handle)

                    logger.debug("selected device: ${device.name} / ${device.clientId}")
                } finally {
                    isBusy.value = false
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            server.close()
            if (dbOpened) {
                MetaDB.close()
            }
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
//            .bindCommand(LiteUnitCommand(),controls.closeButton) { finish() }
            .textBinding(controls.serverStatus, viewModel.statusString.map { "Server Status: $it"})
            .textBinding(controls.serverAddress, viewModel.ipAddress)
            .bindCommand(viewModel.backupCommand, controls.backupButton)
            .bindCommand(viewModel.purgeCommand, controls.purgeButton)
            .bindCommand(viewModel.repairCommand, controls.repairButton)
            .bindCommand(viewModel.migrateCommand, controls.migrateButton)
            .checkBinding(controls.maintenanceCheckbox, viewModel.isMaintenanceMode)
            .multiEnableBinding(arrayOf(controls.purgeButton,controls.backupButton, controls.repairButton, controls.migrateButton), viewModel.isBusy, boolConvert = BoolConvert.Inverse)
            .combinatorialVisibilityBinding(viewModel.isMaintenanceMode) {
                inverseGone(controls.backupButton, controls.purgeButton)
                straightGone(controls.repairButton, controls.migrateButton)
            }
            .visibilityBinding(controls.progressRing, viewModel.isBusy, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)
    }
}
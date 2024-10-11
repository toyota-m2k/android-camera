package io.github.toyota32k.secureCamera

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
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
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtMessageBox
import io.github.toyota32k.dialog.UtSingleSelectionBox
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.dialog.task.showOkCancelMessageBox
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.databinding.ActivityServerBinding
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.MetaData
import io.github.toyota32k.secureCamera.server.NetworkUtils
import io.github.toyota32k.secureCamera.server.TcServer
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
                val decision = UtImmortalSimpleTask.runAsync {
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
                    var count:Int = 0
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
            viewModelScope.launch {
                try {
                    val devices = TcClient.getDeviceListForMigration() ?: return@launch
                    if(devices.isEmpty()) {
                        UtImmortalSimpleTask.runAsync("no device error") {
                            showConfirmMessageBox("Migration", "No devices found.")
                            true
                        }
                        return@launch
                    }
                    val device = UtImmortalSimpleTask.executeAsync("select device") {
                        var index =showDialog(taskName) {
                            UtSingleSelectionBox().apply {
                                items = devices.map { "${it.name} - ${it.clientId}" }.toTypedArray()
                                title = "Select device"
                            }
                        }.selectedIndex
                        if(index<0 || !showOkCancelMessageBox("Migration", "Are you sure to migrate data from ${devices[index].name}?")) {
                            null
                        } else devices[index]
                    }
                    if(device==null) {
                        return@launch
                    }
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
            .multiEnableBinding(arrayOf(controls.purgeButton,controls.backupButton), viewModel.isBusy, boolConvert = BoolConvert.Inverse)
            .visibilityBinding(controls.progressRing, viewModel.isBusy, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)
    }
}
package io.github.toyota32k.secureCamera

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.secureCamera.databinding.ActivityServerBinding
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.server.NetworkUtils
import io.github.toyota32k.secureCamera.server.TcServer
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ServerActivity : AppCompatActivity() {
    companion object {
        val logger = UtLog("SERVER")
    }
    class ServerViewModel(application: Application) : AndroidViewModel(application) {
        val port = Settings.SecureArchive.myPort
        val server = TcServer(port)
        val ipAddress = MutableStateFlow("unknown")
        val statusString = MutableStateFlow("Starting...")
        val backupCommand = LiteUnitCommand(::backup)
        val purgeCommand = LiteUnitCommand(::purge)
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
                val json = JSONObject()
                    .put("id", Settings.SecureArchive.clientId)
                    .put("name", Build.MODEL)
                    .put("type", "SecureCamera")
                    .put("token", TcServer.updateAuthToken())
                    .put("address", "${ipAddress.value}:${Settings.SecureArchive.myPort}")
                    .toString()
                val request = Request.Builder()
                    .url("http://${Settings.SecureArchive.address}/backup/request")
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
        override fun onCleared() {
            super.onCleared()
            server.close()
            logger.debug("stop server")
        }
    }

    private val viewModel by viewModels<ServerViewModel>()
    private lateinit var controls: ActivityServerBinding
    private val binder = Binder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controls = ActivityServerBinding.inflate(layoutInflater)
        setContentView(controls.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binder.owner(this)
//            .bindCommand(LiteUnitCommand(),controls.closeButton) { finish() }
            .textBinding(controls.serverStatus, viewModel.statusString.map { "Server Status: $it"})
            .textBinding(controls.serverAddress, viewModel.ipAddress)
            .bindCommand(viewModel.backupCommand, controls.backupButton)
            .bindCommand(viewModel.purgeCommand, controls.purgeButton)
            .multiEnableBinding(arrayOf(controls.purgeButton,controls.backupButton), viewModel.isBusy, boolConvert = BoolConvert.Inverse)
            .visibilityBinding(controls.progressRing, viewModel.isBusy, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)
    }
}
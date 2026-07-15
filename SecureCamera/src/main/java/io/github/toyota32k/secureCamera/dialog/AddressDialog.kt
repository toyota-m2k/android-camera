package io.github.toyota32k.secureCamera.dialog

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.combinatorialVisibilityBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.binder.recyclerViewBindingEx
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.data.PairingUri
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.broker.IUtActivityBrokerStoreProvider
import io.github.toyota32k.dialog.broker.UtPermissionBroker
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getActivity
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.withActivity
import io.github.toyota32k.secureCamera.MainActivity
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.SCApplication
import io.github.toyota32k.secureCamera.client.BooTubeDiscovery
import io.github.toyota32k.secureCamera.databinding.DialogAddressBinding
import io.github.toyota32k.secureCamera.databinding.ListItemDiscoveredServerBinding
import io.github.toyota32k.secureCamera.settings.SecureArchiveHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AddressDialog : UtDialogEx() {
    class AddressDialogViewModel : UtDialogViewModel() {
        var isPrimary:Boolean = true
        val address = MutableStateFlow("")
        val isHttps = MutableStateFlow(false)
        var pairingHost: SecureArchiveHost? = null
            set(v) {
                field = v
                if (v!=null) {
                    address.value = v.address
                    isHttps.value = v.isHttps
                }
            }

        fun initialize(host: SecureArchiveHost?, isPrimary: Boolean) {
            if (null == host) return // initial
            this.isPrimary = isPrimary
            if (host.isPairedHost) {
                pairingHost = host
            } else {
                address.value = host.address
                isHttps.value = host.isHttps
            }
        }

        val result: SecureArchiveHost?
            get() {
                return if (pairingHost != null && pairingHost?.address == address.value) {
                    pairingHost
                } else if (address.value.isNotBlank()) {
                    SecureArchiveHost(address.value, isHttps=isHttps.value)
                } else {
                    null
                }
            }

//        data class DiscoveredHostInfo(
//            val serviceName: String,
//            val fingerprint: String? = null,
//            val isHttps: Boolean = false,
//            val hostname: String? = null,
//        ) {
//            companion object {
//                fun fromHostAddressEntity(src: SecureArchiveHost): DiscoveredHostInfo? {
//                    val serviceName = src.serviceName ?: return null
//                    return DiscoveredHostInfo(serviceName,src.fingerprint,src.isHttps,src.hostname)
//                }
//            }
//        }

//        private var selectedHost: DiscoveredHostInfo? = null

        val discovering = MutableStateFlow<Boolean>(false)
        val commandDiscover = LiteUnitCommand {
            // （開始されていなければ）mDNSの検索を開始する
            UtImmortalTask.launchTask {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                    val permitted = withActivity<MainActivity, Boolean> { activity ->
                        activity.activityBrokers.permissionBroker.requestPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    }
                    if (!permitted) {
                        logger.warn("ACCESS_LOCAL_NETWORK permission denied.")
                        return@launchTask
                    }
                }
                if (!discovering.value) {
                    discovering.value = true
                    discoveryModel.start(viewModelScope)
                }
            }
        }

        class DiscoveryModel {
//            // mDNS で発見したサーバ由来のメタ情報。手入力に切替えたら null/false にリセットされる。
//            var serviceName: String? = null
//            var fingerprint: String? = null
//            var isHttps: Boolean = false
//            var hostname: String? = null

            // RecyclerView 用の発見リスト本体
            val discoveredServers = ObservableList<BooTubeDiscovery.DiscoveredServer>()

            // 「Searching…」の出し分け用 (ObservableList を Flow 化するのは骨が折れるので別管理)
            val hasDiscoveries = MutableStateFlow(false)

            // BooTubeDiscovery のラッパ。ダイアログ表示中だけ start/stop。
            private var discovery: BooTubeDiscovery? = null

            fun start(scope: CoroutineScope) {
                if (discovery != null) return
                val d = BooTubeDiscovery()
                discovery = d
                d.services.onEach { list ->
                    discoveredServers.replace(list)
                    hasDiscoveries.value = list.isNotEmpty()
                }.launchIn(scope)
                d.start()
            }

            fun stop() {
                discovery?.stop()
                discovery = null
            }
        }

        val discoveryModel = DiscoveryModel()

        override fun onCleared() {
            super.onCleared()
            discoveryModel.stop()
        }


        companion object {
//            fun createBy(task:IUtImmortalTask, initialAddress:String):AddressDialogViewModel {
//                return UtImmortalViewModelHelper.createBy(AddressDialogViewModel::class.java, task).apply {
//                    address.value = initialAddress
//                }
//            }
//            fun instanceFor(dialog: AddressDialog):AddressDialogViewModel {
//                return UtImmortalViewModelHelper.instanceFor(AddressDialogViewModel::class.java, dialog)
//            }
        }
    }

    val viewModel by lazy { getViewModel<AddressDialogViewModel>() }
    lateinit var controls: DialogAddressBinding

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
        title = requireActivity().getString(if(viewModel.isPrimary) R.string.secure_archive_address else R.string.secure_archive_2nd_address)
        enableFocusManagement()
            .setInitialFocus(R.id.address)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
//        viewModel = ViewModelProvider(requireActivity())[AddressDialogViewModel::class.java]
        controls = DialogAddressBinding.inflate(inflater.layoutInflater)
        return controls.root.also { _ ->
            binder
                .editTextBinding(controls.address, viewModel.address, BindingMode.TwoWay)
                .checkBinding(controls.sslCheckbox, viewModel.isHttps, mode= BindingMode.TwoWay)
                .combinatorialVisibilityBinding(viewModel.discovering) {
                    inverseGone(controls.discoverBtn)
                    straightGone(controls.discoverResultLabel)
                    straightGone(controls.discoverResult)
                }
                .visibilityBinding(
                    controls.emptyDiscoveryLabel,
                    viewModel.discoveryModel.hasDiscoveries.map { !it },
                    hiddenMode = VisibilityBinding.HiddenMode.HideByGone
                )
                .bindCommand(viewModel.commandDiscover, controls.discoverBtn)
                .bindCommand(LiteUnitCommand(::pairingWithQRCode), controls.scanQrCodeBtn)
                .recyclerViewBindingEx(controls.discoveredList) {
                    options(
                        list = viewModel.discoveryModel.discoveredServers,
                        inflater = ListItemDiscoveredServerBinding::inflate,
                        bindView = { itemControls, itemBinder, _, server ->
                            itemControls.discoveredNameText.text = server.serviceName
                            // hostname があれば「TOYOTA-PC.local (192.168.0.153:3501)」形式
                            itemControls.discoveredAddressText.text =
                                if (!server.hostname.isNullOrEmpty())
                                    "${server.hostname} (${server.host}:${server.port})"
                                else
                                    "${server.host}:${server.port}"
                            itemControls.httpsBadge.text = if (server.isHttps) "HTTPS" else "HTTP"
                            itemBinder.reset()
                            itemBinder.owner(owner)
                                .bindCommand(
                                    LiteCommand {
                                        viewModel.pairingHost = it.toHost()
                                    },
                                    itemControls.discoveredItemContainer,
                                    server,
                                )
                        },
                    )
                }
        }
    }

//    override fun onPositive() {
//        viewModel.save()
//        super.onPositive()
//    }

    fun pairingWithQRCode() {
        if (!QRCodeDialog.hasCamera(context)) return
        val broker = (requireActivity() as? IUtActivityBrokerStoreProvider)?.activityBrokers?.broker<UtPermissionBroker>() ?: return
        CoroutineScope(Dispatchers.Main).launch {
            if(broker.requestPermission(Manifest.permission.CAMERA)) {
                val tx = QRCodeDialog.show("Pairing", "Scan QR Code on your Boo App") {
                    it.startsWith("bootube:")
                } ?: return@launch
                logger.debug { tx }
                val pairing = PairingUri.parse(tx.toUri()) ?: return@launch
                logger.debug { pairing.toString() }
                viewModel.pairingHost = pairing.toHost()
            }
        }
    }

    companion object {
        data class Result(val status:Boolean, val host: SecureArchiveHost?)
        suspend fun show(initialHost: SecureArchiveHost?, isPrimary:Boolean): Result? {
            return UtImmortalTask.awaitTaskResult("editAddress") {
                val vm = createViewModel<AddressDialogViewModel> { initialize(initialHost, isPrimary) }
                if(showDialog(taskName) { AddressDialog() }.status.positive) {
                    Result(true,vm.result)
                } else null
            }
        }
    }
}
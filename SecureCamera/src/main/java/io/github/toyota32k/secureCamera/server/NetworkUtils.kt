package io.github.toyota32k.secureCamera.server

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.Inet4Address
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object NetworkUtils {
    suspend fun getIpAddress(context:Context) : String {
        return suspendCoroutine<String> { cont->
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties
                ) {
                    super.onLinkPropertiesChanged(network, linkProperties)
                    cont.resume(linkProperties.linkAddresses.filter { it.address is Inet4Address }[0].toString())
                }
            }
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val manager: ConnectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            manager.registerNetworkCallback(request, networkCallback)
        }
    }
}
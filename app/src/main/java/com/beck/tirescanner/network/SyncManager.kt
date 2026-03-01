package com.beck.tirescanner.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.beck.tirescanner.database.TireRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

class SyncManager(
    private val context: Context,
    private val tireRepository: TireRepository
) {
    private val SERVICE_TYPE = "_tirescanner._tcp."
    private val SERVICE_NAME = "TireScannerSync"
    val SYNC_PORT = 8765
    private val gson = Gson()

    private var nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var syncServer: SyncServer? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Start the local HTTP server and register it on the network
    fun startServer() {
        syncServer = SyncServer(SYNC_PORT, tireRepository)
        syncServer?.start()
        registerService()
    }

    fun stopServer() {
        syncServer?.stop()
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
        } catch (e: Exception) { }
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) { }
    }

    // Register this device as a sync service on the local network
    private fun registerService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = SYNC_PORT
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    // Discover other devices and sync with them
    suspend fun discoverAndSync(onResult: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val foundDevices = mutableListOf<NsdServiceInfo>()

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) {}
                override fun onDiscoveryStopped(type: String) {}
                override fun onStartDiscoveryFailed(type: String, code: Int) {}
                override fun onStopDiscoveryFailed(type: String, code: Int) {}

                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("tirescanner")) {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {}
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                // Skip ourselves
                                if (!isLocalAddress(info.host)) {
                                    foundDevices.add(info)
                                }
                            }
                        })
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {}
            }

            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            // Wait a few seconds for discovery
            Thread.sleep(3000)

            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) { }

            if (foundDevices.isEmpty()) {
                withContext(Dispatchers.Main) { onResult("No other device found on network") }
                return@withContext
            }

            // Sync with each found device
            var syncCount = 0
            for (device in foundDevices) {
                val success = syncWithDevice(device.host, device.port)
                if (success) syncCount++
            }

            withContext(Dispatchers.Main) {
                if (syncCount > 0) {
                    onResult("Synced with $syncCount device(s)")
                } else {
                    onResult("Found device but sync failed")
                }
            }
        }
    }

    // Push our inventory to the other device and pull theirs
    private fun syncWithDevice(host: InetAddress, port: Int): Boolean {
        return try {
            val baseUrl = "http://${host.hostAddress}:$port"
            android.util.Log.d("SyncManager", "Attempting sync with: $baseUrl")

            val getUrl = URL("$baseUrl/inventory")
            val getConn = getUrl.openConnection() as HttpURLConnection
            getConn.requestMethod = "GET"
            getConn.connectTimeout = 3000
            getConn.readTimeout = 3000

            val responseCode = getConn.responseCode
            android.util.Log.d("SyncManager", "Response code: $responseCode")

            val remoteJson = getConn.inputStream.bufferedReader().readText()
            android.util.Log.d("SyncManager", "Remote inventory: $remoteJson")
            getConn.disconnect()

            val remoteTires = gson.fromJson(remoteJson, Array<com.beck.tirescanner.database.TireEntry>::class.java).toList()
            tireRepository.upsertTires(remoteTires)

            val ourTires = tireRepository.getAllTires()
            val ourJson = gson.toJson(ourTires)

            val postUrl = URL("$baseUrl/sync")
            val postConn = postUrl.openConnection() as HttpURLConnection
            postConn.requestMethod = "POST"
            postConn.doOutput = true
            postConn.setRequestProperty("Content-Type", "application/json")
            postConn.connectTimeout = 3000
            postConn.readTimeout = 3000

            val writer = OutputStreamWriter(postConn.outputStream)
            writer.write(ourJson)
            writer.flush()
            writer.close()

            postConn.responseCode == 200
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Sync error: ${e.message}", e)
            false
        }
    }

    // Check if an address belongs to this device (so we don't sync with ourselves)
    private fun isLocalAddress(address: InetAddress): Boolean {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().any { iface ->
                iface.inetAddresses.toList().any { it == address }
            }
        } catch (e: Exception) {
            false
        }
    }

}
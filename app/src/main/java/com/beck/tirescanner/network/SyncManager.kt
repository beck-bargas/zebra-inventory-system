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
    private val tireRepository: TireRepository,
    private val syncToken: String
) {
    private val SERVICE_TYPE = "_tirescanner._tcp."
    private val SERVICE_NAME = "TireScannerSync"
    val SYNC_PORT = 8765
    private val gson = Gson()

    private var nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var syncServer: SyncServer? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startServer() {
        syncServer = SyncServer(SYNC_PORT, tireRepository, syncToken)
        syncServer?.start()
        registerService()
    }

    fun stopServer() {
        syncServer?.stop()
        try { registrationListener?.let { nsdManager.unregisterService(it) } } catch (e: Exception) { }
        try { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } } catch (e: Exception) { }
    }

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

    suspend fun discoverAndSync(onResult: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val foundDevices = mutableListOf<NsdServiceInfo>()

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) {
                    Log.d("SyncManager", "Discovery started")
                }
                override fun onDiscoveryStopped(type: String) {
                    Log.d("SyncManager", "Discovery stopped")
                }
                override fun onStartDiscoveryFailed(type: String, code: Int) {
                    Log.e("SyncManager", "Start discovery failed: $code")
                }
                override fun onStopDiscoveryFailed(type: String, code: Int) {}

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d("SyncManager", "Service found: ${service.serviceName} type: ${service.serviceType}")
                    if (service.serviceType.contains("tirescanner")) {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                                Log.e("SyncManager", "Resolve failed: $code")
                            }
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                Log.d("SyncManager", "Resolved: ${info.host}:${info.port}")
                                if (!isLocalAddress(info.host)) {
                                    foundDevices.add(info)
                                }
                            }
                        })
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.d("SyncManager", "Service lost: ${service.serviceName}")
                }
            }

            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            Thread.sleep(5000)

            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (e: Exception) { }
            Thread.sleep(1000) // wait for pending resolves to finish

            Log.d("SyncManager", "Found ${foundDevices.size} device(s)")

            if (foundDevices.isEmpty()) {
                withContext(Dispatchers.Main) { onResult("No other device found on network") }
                return@withContext
            }

            var syncCount = 0
            for (device in foundDevices) {
                val success = syncWithDevice(device.host, device.port)
                if (success) syncCount++
            }

            withContext(Dispatchers.Main) {
                if (syncCount > 0) onResult("Synced with $syncCount device(s)")
                else onResult("Found device but sync failed")
            }
        }
    }
    private fun syncWithDevice(host: InetAddress, port: Int): Boolean {
        return try {
            val baseUrl = "http://${host.hostAddress}:$port"
            Log.d("SyncManager", "Attempting sync with: $baseUrl")

            val getUrl = URL("$baseUrl/inventory")
            val getConn = getUrl.openConnection() as HttpURLConnection
            getConn.requestMethod = "GET"
            getConn.setRequestProperty("x-sync-token", syncToken)
            getConn.connectTimeout = 3000
            getConn.readTimeout = 3000

            val responseCode = getConn.responseCode
            Log.d("SyncManager", "GET response code: $responseCode")

            val remoteJson = getConn.inputStream.bufferedReader().readText()
            Log.d("SyncManager", "Remote inventory: $remoteJson")
            getConn.disconnect()

            val remoteTires = gson.fromJson(remoteJson, Array<com.beck.tirescanner.database.TireEntry>::class.java).toList()
            tireRepository.upsertTires(remoteTires)

            val ourTires = tireRepository.getAllTires()
            val ourJson = gson.toJson(ourTires)
            Log.d("SyncManager", "Sending our inventory: $ourJson")

            val postUrl = URL("$baseUrl/sync")
            val postConn = postUrl.openConnection() as HttpURLConnection
            postConn.requestMethod = "POST"
            postConn.doOutput = true
            postConn.setRequestProperty("Content-Type", "application/json")
            postConn.setRequestProperty("x-sync-token", syncToken)
            postConn.connectTimeout = 3000
            postConn.readTimeout = 3000

            val writer = OutputStreamWriter(postConn.outputStream)
            writer.write(ourJson)
            writer.flush()
            writer.close()

            val postCode = postConn.responseCode
            Log.d("SyncManager", "POST response code: $postCode")

            postCode == 200
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync error: ${e.message}", e)
            false
        }
    }
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
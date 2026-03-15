package com.beck.tirescanner.network

import android.content.Context
import android.util.Log
import com.beck.tirescanner.database.TireRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SyncManager(
    private val context: Context,
    private val tireRepository: TireRepository,
    private val syncToken: String
) {
    private val gson = Gson()
    suspend fun syncToWeb(onResult: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val tires = tireRepository.getAllTires()
                val json = gson.toJson(tires)

                val postUrl = URL("http://inventory.nixsauto.com/api/sync")
                val postConn = postUrl.openConnection() as HttpURLConnection
                postConn.requestMethod = "POST"
                postConn.doOutput = true
                postConn.setRequestProperty("Content-Type", "application/json")
                postConn.setRequestProperty("x-sync-token", syncToken)
                postConn.connectTimeout = 10000
                postConn.readTimeout = 10000
                val writer = OutputStreamWriter(postConn.outputStream)
                writer.write(json)
                writer.flush()
                writer.close()
                val code = postConn.responseCode
                postConn.disconnect()

                withContext(Dispatchers.Main) {
                    if (code == 200) onResult("Synced to website successfully")
                    else onResult("Web sync failed (HTTP $code)")
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Web sync error: ${e.message}", e)
                withContext(Dispatchers.Main) { onResult("Web sync error: ${e.message}") }
            }
        }
    }

    suspend fun pullFromWeb(onResult: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val getUrl = URL("http://inventory.nixsauto.com/api/inventory")
                val getConn = getUrl.openConnection() as HttpURLConnection
                getConn.requestMethod = "GET"
                getConn.setRequestProperty("x-sync-token", syncToken)
                getConn.connectTimeout = 10000
                getConn.readTimeout = 10000
                val response = getConn.inputStream.bufferedReader().readText()
                getConn.disconnect()

                val remoteTires = gson.fromJson(response, Array<com.beck.tirescanner.database.TireEntry>::class.java).toList()
                tireRepository.upsertTires(remoteTires)

                withContext(Dispatchers.Main) { onResult("Pulled ${remoteTires.size} tires from web") }
            } catch (e: Exception) {
                Log.e("SyncManager", "Pull error: ${e.message}", e)
                withContext(Dispatchers.Main) { onResult("Pull error: ${e.message}") }
            }
        }
    }
}
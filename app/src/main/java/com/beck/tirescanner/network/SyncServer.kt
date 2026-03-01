package com.beck.tirescanner.network

import com.beck.tirescanner.database.TireEntry
import com.beck.tirescanner.database.TireRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.iki.elonen.NanoHTTPD

class SyncServer(
    port: Int,
    private val tireRepository: TireRepository
) : NanoHTTPD(port) {

    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/inventory" -> {
                handleGetInventory()
            }
            session.method == Method.POST && session.uri == "/sync" -> {
                handleSync(session)
            }
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not found"
            )
        }
    }

    // Returns all local tire entries as JSON
    private fun handleGetInventory(): Response {
        val tires = tireRepository.getAllTires()
        val json = gson.toJson(tires)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    // Receives tire entries from other device and upserts them
    private fun handleSync(session: IHTTPSession): Response {
        return try {
            val body = mutableMapOf<String, String>()
            session.parseBody(body)
            val json = body["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No data"
            )

            val type = object : TypeToken<List<TireEntry>>() {}.type
            val remoteTires: List<TireEntry> = gson.fromJson(json, type)

            // Upsert each remote tire by syncId
            tireRepository.upsertTires(remoteTires)

            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: ${e.message}"
            )
        }
    }
}
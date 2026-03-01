package com.beck.tirescanner.network

import com.beck.tirescanner.database.TireEntry
import com.beck.tirescanner.database.TireRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.iki.elonen.NanoHTTPD

class SyncServer(
    port: Int,
    private val tireRepository: TireRepository,
    private val syncToken: String
) : NanoHTTPD(port) {

    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        val token = session.headers["x-sync-token"]
        if (token != syncToken) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
        }

        return when {
            session.method == Method.GET && session.uri == "/inventory" -> handleGetInventory()
            session.method == Method.POST && session.uri == "/sync" -> handleSync(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun handleGetInventory(): Response {
        val tires = tireRepository.getAllTires()
        val json = gson.toJson(tires)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleSync(session: IHTTPSession): Response {
        return try {
            val body = mutableMapOf<String, String>()
            session.parseBody(body)
            val json = body["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No data"
            )

            val type = object : TypeToken<List<TireEntry>>() {}.type
            val remoteTires: List<TireEntry> = gson.fromJson(json, type)

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
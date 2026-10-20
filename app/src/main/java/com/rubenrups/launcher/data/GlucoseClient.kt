package com.rubenrups.launcher.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One sensor reading. Always stored in mg/dL; the UI converts for display. */
data class GlucosePoint(val mgdl: Int, val timeMs: Long)

data class GlucoseReading(
    val mgdl: Int,
    /** LibreLinkUp trend: 1 falling fast … 3 steady … 5 rising fast; 0 = unknown. */
    val trend: Int,
    val timeMs: Long,
)

data class GlucoseSnapshot(val latest: GlucoseReading, val history: List<GlucosePoint>)

open class GlucoseException(message: String) : Exception(message)

/** Wrong email or password: retrying with the same login only risks locking the account. */
class GlucoseAuthException(message: String) : GlucoseException(message)

/**
 * Reads your glucose through LibreLinkUp, the "follower" side of FreeStyle Libre. This is an unofficial,
 * undocumented API: it can change without notice, so every failure is reported as a readable message and
 * nothing here is ever used to make a decision, only to display a number.
 *
 * Blocking: call from a background dispatcher. One instance keeps its login ticket between polls.
 */
class GlucoseClient {
    private var host = "api.libreview.io"
    private var token: String? = null
    private var accountId: String? = null
    private var patientId: String? = null
    private var credentialsKey: String? = null

    @Synchronized
    fun fetch(email: String, password: String): GlucoseSnapshot {
        val key = "$email\n$password"
        if (key != credentialsKey) {
            reset()
            credentialsKey = key
        }
        return try {
            read(email, password)
        } catch (e: UnauthorizedException) {
            // The ticket expired: log in again, once.
            token = null
            read(email, password)
        }
    }

    private fun reset() {
        host = "api.libreview.io"
        token = null
        accountId = null
        patientId = null
    }

    private fun read(email: String, password: String): GlucoseSnapshot {
        if (token == null) login(email, password)
        val connections = JSONObject(request("/llu/connections")).optJSONArray("data")
        val connection = connections?.optJSONObject(0)
            ?: throw GlucoseException("No sensor is shared with this LibreLinkUp account yet.")
        val patient = connection.getString("patientId").also { patientId = it }

        val graph = JSONObject(request("/llu/connections/$patient/graph")).getJSONObject("data")
        val latest = parseReading(graph.optJSONObject("connection")?.optJSONObject("glucoseMeasurement"))
            ?: parseReading(connection.optJSONObject("glucoseMeasurement"))
            ?: throw GlucoseException("No reading available yet.")

        val points = ArrayList<GlucosePoint>()
        graph.optJSONArray("graphData")?.let { array ->
            for (i in 0 until array.length()) {
                parseReading(array.getJSONObject(i))?.let { points.add(GlucosePoint(it.mgdl, it.timeMs)) }
            }
        }
        points.add(GlucosePoint(latest.mgdl, latest.timeMs))
        val history = points.distinctBy { it.timeMs }.sortedBy { it.timeMs }
        return GlucoseSnapshot(latest, history)
    }

    private fun login(email: String, password: String) {
        var response = post("https://$host/llu/auth/login", JSONObject().put("email", email).put("password", password))
        // Accounts live in a region: the first answer may say "ask api-<region> instead".
        val redirect = response.optJSONObject("data")
        if (redirect?.optBoolean("redirect") == true) {
            host = "api-${redirect.getString("region")}.libreview.io"
            response = post("https://$host/llu/auth/login", JSONObject().put("email", email).put("password", password))
        }
        when (response.optInt("status", -1)) {
            0 -> Unit
            2 -> throw GlucoseAuthException("LibreLinkUp rejected the email or password.")
            4 -> throw GlucoseException("Open the LibreLinkUp app once and accept its terms, then try again.")
            else -> throw GlucoseException("LibreLinkUp login failed (status ${response.optInt("status", -1)}).")
        }
        val data = response.getJSONObject("data")
        token = data.getJSONObject("authTicket").getString("token")
        accountId = sha256(data.getJSONObject("user").getString("id"))
    }

    private fun post(url: String, body: JSONObject): JSONObject {
        val connection = open(url)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            return JSONObject(readBody(connection))
        } catch (_: UnauthorizedException) {
            throw GlucoseAuthException("LibreLinkUp rejected the email or password.")
        } finally {
            connection.disconnect()
        }
    }

    private fun request(path: String): String {
        val connection = open("https://$host$path")
        connection.setRequestProperty("Authorization", "Bearer $token")
        accountId?.let { connection.setRequestProperty("Account-Id", it) }
        try {
            if (connection.responseCode == 401) throw UnauthorizedException()
            return readBody(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("product", "llu.android")
        connection.setRequestProperty("version", APP_VERSION)
        connection.setRequestProperty("Cache-Control", "no-cache")
        return connection
    }

    private fun readBody(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode == 401) throw UnauthorizedException()
        if (connection.responseCode !in 200..299 && text.isBlank()) {
            throw GlucoseException("LibreLinkUp answered with HTTP ${connection.responseCode}.")
        }
        return text
    }

    private fun parseReading(o: JSONObject?): GlucoseReading? {
        o ?: return null
        val mgdl = when {
            o.has("ValueInMgPerDl") -> o.optDouble("ValueInMgPerDl")
            o.has("Value") -> o.optDouble("Value")
            else -> return null
        }
        if (mgdl.isNaN() || mgdl <= 0.0) return null
        // FactoryTimestamp is UTC; "Timestamp" is the phone's local time with no zone attached.
        val stamp = o.optString("FactoryTimestamp").ifBlank { return null }
        val time = runCatching {
            SimpleDateFormat("M/d/yyyy h:mm:ss a", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(stamp)?.time
        }.getOrNull() ?: return null
        return GlucoseReading(Math.round(mgdl).toInt(), o.optInt("TrendArrow", 0), time)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private class UnauthorizedException : Exception()

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val APP_VERSION = "4.16.0"
    }
}

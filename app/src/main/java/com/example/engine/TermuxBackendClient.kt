package com.example.engine

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

data class TermuxBackendConnectionState(
    val backendUrlOrIp: String = "",
    val port: String = "",
    val resolvedBaseUrl: String = "",
    val healthEndpoint: String = "GET /api/health",
    val resolvedHealthUrl: String = "",
    val isSaved: Boolean = false,
    val isEditing: Boolean = true,
    val isChecking: Boolean = false,
    val isConnected: Boolean = false,
    val connectionStatus: String = "Disconnected",
    val backendStatus: String = "Not Connected",
    val llamaCppStatus: String = "Managed by Termux (Disconnected)",
    val ggufModelLoadedStatus: String = "Managed by Termux (Disconnected)",
    val remoteModelName: String = "",
    val statusMessage: String = "Configure your Termux backend URL/IP and Port to connect.",
    val errorMessage: String? = null,
    val rawHealthResponse: String = "",
    val lastCheckedAtMillis: Long = 0L
)

/**
 * Termux Backend HTTP Client for AI Mode.
 *
 * - Android does NOT select, import, or load GGUF files locally.
 * - GGUF models and llama.cpp runtime are managed entirely by the user's Termux backend.
 * - Does NOT hardcode any IP address; the user can enter, save, edit, test, and connect
 *   to any Termux backend address and port at any time.
 * - Executes real HTTP `GET /api/health` against the user-configured Termux backend and parses:
 *   1. Connection status (Connected / Disconnected)
 *   2. Backend status
 *   3. llama.cpp status
 *   4. GGUF model loaded status
 */
object TermuxBackendClient {

    private const val PREFS_NAME = "termux_backend_connection_prefs"
    private const val KEY_BACKEND_URL_OR_IP = "backend_url_or_ip"
    private const val KEY_BACKEND_PORT = "backend_port"
    private const val KEY_IS_SAVED = "is_connection_saved"

    /**
     * Loads any previously saved user connection from SharedPreferences.
     * Never hardcodes a default IP address if the user has not saved one.
     */
    @JvmStatic
    fun loadSavedConnectionState(context: Context): TermuxBackendConnectionState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrlOrIp = prefs.getString(KEY_BACKEND_URL_OR_IP, "")?.trim().orEmpty()
        val savedPort = prefs.getString(KEY_BACKEND_PORT, "")?.trim().orEmpty()
        val isSaved = prefs.getBoolean(KEY_IS_SAVED, false) && savedUrlOrIp.isNotEmpty()
        val baseUrl = buildBaseUrl(savedUrlOrIp, savedPort)
        val healthUrl = if (baseUrl.isNotEmpty()) "$baseUrl/api/health" else ""

        return TermuxBackendConnectionState(
            backendUrlOrIp = savedUrlOrIp,
            port = savedPort,
            resolvedBaseUrl = baseUrl,
            healthEndpoint = "GET /api/health",
            resolvedHealthUrl = healthUrl,
            isSaved = isSaved,
            isEditing = !isSaved,
            isChecking = false,
            isConnected = false,
            connectionStatus = "Disconnected",
            backendStatus = if (isSaved) "Saved ($baseUrl) — Tap Connect or Test" else "Not Connected",
            llamaCppStatus = "Managed by Termux (Tap Connect / Test)",
            ggufModelLoadedStatus = "Managed by Termux (Tap Connect / Test)",
            statusMessage = if (isSaved) {
                "Saved Termux backend: $baseUrl. Tap Connect or Test Connection to verify GET /api/health."
            } else {
                "Enter your Termux Backend URL/IP and Port to connect."
            }
        )
    }

    /**
     * Persists the user's Backend URL/IP and Port to SharedPreferences.
     */
    @JvmStatic
    fun saveConnectionConfig(
        context: Context,
        rawUrlOrIp: String,
        rawPort: String,
        currentState: TermuxBackendConnectionState = TermuxBackendConnectionState()
    ): TermuxBackendConnectionState {
        val (normalizedHost, normalizedPort) = normalizeUrlAndPortFields(rawUrlOrIp, rawPort)
        val baseUrl = buildBaseUrl(normalizedHost, normalizedPort)
        val healthUrl = if (baseUrl.isNotEmpty()) "$baseUrl/api/health" else ""

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BACKEND_URL_OR_IP, normalizedHost)
            .putString(KEY_BACKEND_PORT, normalizedPort)
            .putBoolean(KEY_IS_SAVED, baseUrl.isNotEmpty())
            .apply()

        return currentState.copy(
            backendUrlOrIp = normalizedHost,
            port = normalizedPort,
            resolvedBaseUrl = baseUrl,
            healthEndpoint = "GET /api/health",
            resolvedHealthUrl = healthUrl,
            isSaved = baseUrl.isNotEmpty(),
            isEditing = baseUrl.isEmpty(),
            errorMessage = if (baseUrl.isEmpty()) "Please enter a valid Backend URL or IP address." else null,
            statusMessage = if (baseUrl.isNotEmpty()) {
                "Saved Termux Backend Connection: $baseUrl"
            } else {
                "Please enter a valid Backend URL or IP address."
            }
        )
    }

    /**
     * Extracts URL/IP and Port cleanly even if the user pastes a combined URL such as
     * "http://127.0.0.1:8080" or "http://192.168.1.100:8080" into the URL/IP field.
     */
    @JvmStatic
    fun normalizeUrlAndPortFields(rawUrlOrIp: String, rawPort: String): Pair<String, String> {
        var cleanUrl = rawUrlOrIp.trim()
            .removeSuffix("/")
            .removeSuffix("/api/health")
            .removeSuffix("/health")
            .trim()
        var cleanPort = rawPort.trim().filter { it.isDigit() }

        if (cleanUrl.isEmpty()) {
            return "" to cleanPort
        }

        // Check if the URL/IP itself contains an explicit ":port" at the end
        val schemePrefix = when {
            cleanUrl.startsWith("https://", ignoreCase = true) -> "https://"
            cleanUrl.startsWith("http://", ignoreCase = true) -> "http://"
            else -> ""
        }
        val withoutScheme = if (schemePrefix.isNotEmpty()) {
            cleanUrl.substring(schemePrefix.length)
        } else {
            cleanUrl
        }
        val hostPortPart = withoutScheme.substringBefore('/')
        val colonIdx = hostPortPart.lastIndexOf(':')
        if (colonIdx > 0 && !hostPortPart.endsWith("]")) {
            val hostOnly = hostPortPart.substring(0, colonIdx)
            val embeddedPort = hostPortPart.substring(colonIdx + 1).filter { it.isDigit() }
            if (embeddedPort.isNotEmpty()) {
                if (cleanPort.isEmpty()) {
                    cleanPort = embeddedPort
                }
                cleanUrl = if (schemePrefix.isNotEmpty()) "$schemePrefix$hostOnly" else "http://$hostOnly"
                return cleanUrl to cleanPort
            }
        }

        val withScheme = if (schemePrefix.isNotEmpty()) cleanUrl else "http://$cleanUrl"
        return withScheme to cleanPort
    }

    /**
     * Builds the full base URL (e.g. "http://127.0.0.1:8080" or "http://192.168.1.100:8080")
     * from the user-supplied URL/IP and Port fields without hardcoding any IP.
     */
    @JvmStatic
    fun buildBaseUrl(rawUrlOrIp: String, rawPort: String): String {
        val (hostWithScheme, resolvedPort) = normalizeUrlAndPortFields(rawUrlOrIp, rawPort)
        if (hostWithScheme.isEmpty()) return ""
        return if (resolvedPort.isNotEmpty()) {
            "$hostWithScheme:$resolvedPort"
        } else {
            hostWithScheme
        }
    }

    /**
     * Executes `GET /api/health` against the user's Termux backend and parses:
     * - Connected / Disconnected
     * - Backend status
     * - llama.cpp status
     * - GGUF model loaded status
     */
    @JvmStatic
    fun performHealthCheckSync(
        rawUrlOrIp: String,
        rawPort: String,
        isSaved: Boolean = false,
        isEditing: Boolean = false,
        timeoutMs: Int = 5000
    ): TermuxBackendConnectionState {
        val (normalizedHost, normalizedPort) = normalizeUrlAndPortFields(rawUrlOrIp, rawPort)
        val baseUrl = buildBaseUrl(normalizedHost, normalizedPort)
        if (baseUrl.isEmpty()) {
            return TermuxBackendConnectionState(
                backendUrlOrIp = rawUrlOrIp.trim(),
                port = rawPort.trim(),
                resolvedBaseUrl = "",
                healthEndpoint = "GET /api/health",
                resolvedHealthUrl = "",
                isSaved = isSaved,
                isEditing = true,
                isChecking = false,
                isConnected = false,
                connectionStatus = "Disconnected",
                backendStatus = "Disconnected (No Address Entered)",
                llamaCppStatus = "Unavailable",
                ggufModelLoadedStatus = "Unavailable",
                statusMessage = "Please enter a Termux Backend URL/IP and Port.",
                errorMessage = "Backend URL/IP cannot be empty."
            )
        }

        val healthUrl = "$baseUrl/api/health"
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(healthUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                useCaches = false
                doInput = true
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("User-Agent", "StudioError-TermuxClient/1.0")
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val rawBody = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            }?.trim().orEmpty()

            parseHealthResponse(
                normalizedHost = normalizedHost,
                normalizedPort = normalizedPort,
                baseUrl = baseUrl,
                healthUrl = healthUrl,
                httpCode = responseCode,
                rawBody = rawBody,
                isSaved = isSaved,
                isEditing = isEditing
            )
        } catch (e: Exception) {
            val cleanReason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            TermuxBackendConnectionState(
                backendUrlOrIp = normalizedHost,
                port = normalizedPort,
                resolvedBaseUrl = baseUrl,
                healthEndpoint = "GET /api/health",
                resolvedHealthUrl = healthUrl,
                isSaved = isSaved,
                isEditing = isEditing,
                isChecking = false,
                isConnected = false,
                connectionStatus = "Disconnected",
                backendStatus = "Offline / Unreachable",
                llamaCppStatus = "Disconnected (Termux llama.cpp unreachable)",
                ggufModelLoadedStatus = "Not Loaded (Backend Disconnected)",
                statusMessage = "Disconnected from $baseUrl — GET /api/health failed.",
                errorMessage = "GET $healthUrl failed: $cleanReason",
                rawHealthResponse = "",
                lastCheckedAtMillis = System.currentTimeMillis()
            )
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Parses a JSON or plain-text response from `GET /api/health` into structured statuses.
     * Exposed so unit/Robolectric tests and live connections can verify exact parsing behavior.
     */
    @JvmStatic
    fun parseHealthResponse(
        normalizedHost: String,
        normalizedPort: String,
        baseUrl: String,
        healthUrl: String,
        httpCode: Int,
        rawBody: String,
        isSaved: Boolean = true,
        isEditing: Boolean = false
    ): TermuxBackendConnectionState {
        val isHttpOk = httpCode in 200..299
        if (!isHttpOk) {
            return TermuxBackendConnectionState(
                backendUrlOrIp = normalizedHost,
                port = normalizedPort,
                resolvedBaseUrl = baseUrl,
                healthEndpoint = "GET /api/health",
                resolvedHealthUrl = healthUrl,
                isSaved = isSaved,
                isEditing = isEditing,
                isChecking = false,
                isConnected = false,
                connectionStatus = "Disconnected",
                backendStatus = "HTTP Error $httpCode",
                llamaCppStatus = "Error / Unavailable (HTTP $httpCode)",
                ggufModelLoadedStatus = "Not Loaded (HTTP $httpCode)",
                statusMessage = "Backend returned HTTP $httpCode on GET /api/health.",
                errorMessage = if (rawBody.isNotBlank()) "HTTP $httpCode: $rawBody" else "HTTP $httpCode from $healthUrl",
                rawHealthResponse = rawBody,
                lastCheckedAtMillis = System.currentTimeMillis()
            )
        }

        var backendStatus = "Online (HTTP $httpCode)"
        var llamaCppStatus = "Running (Termux llama.cpp)"
        var ggufModelStatus = "Loaded (Managed by Termux)"
        var remoteModelName = ""

        if (rawBody.startsWith("{")) {
            try {
                val json = JSONObject(rawBody)

                // 1. Parse Backend Status
                val rawBackend = optFlexibleString(
                    json,
                    listOf("backend_status", "backend", "status", "server_status", "state", "health")
                )
                if (rawBackend.isNotEmpty()) {
                    backendStatus = formatStatusLabel(rawBackend, "Online")
                } else if (json.has("ok")) {
                    backendStatus = if (json.optBoolean("ok", true)) "Online (OK)" else "Degraded"
                } else if (json.has("healthy")) {
                    backendStatus = if (json.optBoolean("healthy", true)) "Healthy (Online)" else "Unhealthy"
                }

                // 2. Parse llama.cpp Status
                val llamaObj = json.optJSONObject("llama_cpp") ?: json.optJSONObject("llamacpp") ?: json.optJSONObject("llama")
                if (llamaObj != null) {
                    val subStatus = optFlexibleString(llamaObj, listOf("status", "state", "ready", "running"))
                    llamaCppStatus = if (subStatus.isNotEmpty()) {
                        formatStatusLabel(subStatus, "Running")
                    } else if (llamaObj.optBoolean("ready", true) || llamaObj.optBoolean("running", true)) {
                        "Running / Ready"
                    } else {
                        "Stopped"
                    }
                } else {
                    val rawLlama = optFlexibleString(
                        json,
                        listOf("llama_cpp_status", "llama_cpp", "llamacpp_status", "llamacpp", "llama_status", "llama", "engine", "runtime")
                    )
                    if (rawLlama.isNotEmpty()) {
                        llamaCppStatus = when (rawLlama.lowercase(Locale.US)) {
                            "true", "1", "ok", "ready", "running", "active", "online" -> "Running ($rawLlama)"
                            "false", "0", "stopped", "offline", "inactive" -> "Stopped ($rawLlama)"
                            else -> rawLlama
                        }
                    }
                }

                // 3. Parse GGUF Model Loaded Status & Model Name
                val modelObj = json.optJSONObject("gguf") ?: json.optJSONObject("model")
                val extractedModelName = optFlexibleString(
                    json,
                    listOf("model_name", "gguf_model", "model_file", "gguf_file", "loaded_model")
                ).ifEmpty {
                    if (modelObj != null) {
                        optFlexibleString(modelObj, listOf("name", "file", "model_name", "path"))
                    } else {
                        val directModel = json.optString("model", "").trim()
                        if (directModel.endsWith(".gguf", ignoreCase = true)) directModel else ""
                    }
                }
                remoteModelName = extractedModelName

                val explicitLoadedKey = listOf(
                    "gguf_model_loaded", "model_loaded", "gguf_loaded", "is_model_loaded", "loaded"
                ).firstOrNull { json.has(it) }

                if (explicitLoadedKey != null) {
                    val rawVal = json.opt(explicitLoadedKey)
                    val isLoaded = when (rawVal) {
                        is Boolean -> rawVal
                        is Number -> rawVal.toInt() != 0
                        is String -> rawVal.lowercase(Locale.US) in setOf("true", "1", "yes", "loaded", "ready", "ok")
                        else -> false
                    }
                    ggufModelStatus = if (isLoaded) {
                        if (remoteModelName.isNotEmpty()) "Loaded ($remoteModelName)" else "Loaded"
                    } else {
                        "Not Loaded"
                    }
                } else if (modelObj != null) {
                    val objLoaded = modelObj.optBoolean("loaded", true)
                    val objStatus = optFlexibleString(modelObj, listOf("status", "state"))
                    ggufModelStatus = when {
                        objStatus.isNotEmpty() && remoteModelName.isNotEmpty() -> "$objStatus ($remoteModelName)"
                        objStatus.isNotEmpty() -> objStatus
                        objLoaded && remoteModelName.isNotEmpty() -> "Loaded ($remoteModelName)"
                        objLoaded -> "Loaded"
                        else -> "Not Loaded"
                    }
                } else {
                    val rawModelStatus = optFlexibleString(
                        json,
                        listOf("gguf_status", "model_status", "gguf", "model")
                    )
                    if (rawModelStatus.isNotEmpty()) {
                        ggufModelStatus = when (rawModelStatus.lowercase(Locale.US)) {
                            "true", "1", "loaded", "ready", "ok" -> {
                                if (remoteModelName.isNotEmpty()) "Loaded ($remoteModelName)" else "Loaded"
                            }
                            "false", "0", "none", "unloaded", "not_loaded", "not loaded" -> "Not Loaded"
                            else -> rawModelStatus
                        }
                    } else if (remoteModelName.isNotEmpty()) {
                        ggufModelStatus = "Loaded ($remoteModelName)"
                    }
                }
            } catch (_: Exception) {
                // Non-JSON or partial JSON body on HTTP 200
            }
        } else if (rawBody.isNotBlank()) {
            backendStatus = "Online (${rawBody.take(40)})"
        }

        return TermuxBackendConnectionState(
            backendUrlOrIp = normalizedHost,
            port = normalizedPort,
            resolvedBaseUrl = baseUrl,
            healthEndpoint = "GET /api/health",
            resolvedHealthUrl = healthUrl,
            isSaved = isSaved,
            isEditing = isEditing,
            isChecking = false,
            isConnected = true,
            connectionStatus = "Connected",
            backendStatus = backendStatus,
            llamaCppStatus = llamaCppStatus,
            ggufModelLoadedStatus = ggufModelStatus,
            remoteModelName = remoteModelName,
            statusMessage = "Connected to Termux backend ($baseUrl) via GET /api/health",
            errorMessage = null,
            rawHealthResponse = rawBody,
            lastCheckedAtMillis = System.currentTimeMillis()
        )
    }

    /**
     * Sends a prompt to the connected Termux backend (tries `/api/generate`, `/api/chat`, or `/completion`)
     * if connected, returning the backend text response or null if the endpoint is not exposed.
     */
    @JvmStatic
    fun requestBackendCompletionOrNull(
        baseUrl: String,
        prompt: String,
        timeoutMs: Int = 8000
    ): String? {
        val cleanBase = baseUrl.trim().removeSuffix("/")
        if (cleanBase.isEmpty()) return null
        val candidatePaths = listOf("/api/generate", "/api/chat", "/completion", "/v1/chat/completions")
        for (path in candidatePaths) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$cleanBase$path")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                }
                val payload = JSONObject().apply {
                    put("prompt", prompt)
                    put("message", prompt)
                    put("stream", false)
                    put("n_predict", 256)
                }.toString()
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
                if (conn.responseCode in 200..299) {
                    val resp = conn.inputStream.use { input ->
                        BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
                    }.trim()
                    if (resp.isNotEmpty()) {
                        if (resp.startsWith("{")) {
                            val json = JSONObject(resp)
                            val extracted = optFlexibleString(
                                json,
                                listOf("response", "content", "text", "reply", "output")
                            )
                            if (extracted.isNotEmpty()) return extracted
                        }
                        return resp
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun optFlexibleString(json: JSONObject, keys: List<String>): String {
        for (k in keys) {
            if (json.has(k) && !json.isNull(k)) {
                val v = json.opt(k)
                if (v is Boolean || v is Number || v is String) {
                    val str = v.toString().trim()
                    if (str.isNotEmpty()) return str
                }
            }
        }
        return ""
    }

    private fun formatStatusLabel(raw: String, defaultPrefix: String): String {
        return when (raw.lowercase(Locale.US)) {
            "true", "1", "ok", "healthy", "online", "running", "ready", "active" -> {
                "$defaultPrefix (${raw.uppercase(Locale.US)})"
            }
            "false", "0", "offline", "stopped", "error" -> {
                "Offline ($raw)"
            }
            else -> raw
        }
    }
}

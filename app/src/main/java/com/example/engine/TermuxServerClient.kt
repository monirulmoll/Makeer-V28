package com.example.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TermuxServerConfig(
    val host: String = TermuxServerClient.DEFAULT_HOST,
    val port: String = TermuxServerClient.DEFAULT_PORT,
    val url: String = TermuxServerClient.DEFAULT_URL,
    val isConfigExpanded: Boolean = true,
    val connectionStatus: String = "Termux Server Ready (${TermuxServerClient.DEFAULT_URL})",
    val isTestingConnection: Boolean = false,
    val lastTestSuccess: Boolean? = null
)

data class TermuxServerResponse(
    val isSuccess: Boolean,
    val httpCode: Int = 0,
    val resolvedEndpoint: String = "",
    val rawResponseText: String = "",
    val parsedReplyText: String = "",
    val isBuildFromTermux: Boolean = false,
    val parsedAppName: String? = null,
    val parsedPackageName: String? = null,
    val parsedScratchFiles: Map<String, String> = emptyMap(),
    val parsedEditedFiles: Set<String> = emptySet(),
    val parsedBuildErrors: List<String> = emptyList(),
    val isConversationalOnly: Boolean? = null,
    val errorMessage: String? = null,
    val durationMs: Long = 0L
)

object TermuxServerClient {

    const val DEFAULT_HOST = "192.168.1.100"
    const val DEFAULT_PORT = "8080"
    const val DEFAULT_URL = "http://192.168.1.100:8080"

    private const val PREFS_NAME = "termux_server_config_prefs"
    private const val KEY_HOST = "termux_host"
    private const val KEY_PORT = "termux_port"
    private const val KEY_URL = "termux_url"

    @JvmStatic
    fun loadConfig(context: Context): TermuxServerConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedHost = prefs.getString(KEY_HOST, DEFAULT_HOST)?.trim().orEmpty().ifEmpty { DEFAULT_HOST }
        val savedPort = prefs.getString(KEY_PORT, DEFAULT_PORT)?.trim().orEmpty().ifEmpty { DEFAULT_PORT }
        val savedUrl = prefs.getString(KEY_URL, null)?.trim().orEmpty()
            .ifEmpty { buildUrlFromHostPort(savedHost, savedPort) }
        return TermuxServerConfig(
            host = savedHost,
            port = savedPort,
            url = savedUrl,
            isConfigExpanded = true,
            connectionStatus = "Configured: $savedUrl (Host: $savedHost, Port: $savedPort)"
        )
    }

    @JvmStatic
    fun saveConfig(
        context: Context,
        host: String,
        port: String,
        url: String,
        isExpanded: Boolean = true
    ): TermuxServerConfig {
        val cleanHost = host.trim().ifEmpty { DEFAULT_HOST }
        val cleanPort = port.trim().ifEmpty { DEFAULT_PORT }
        val cleanUrl = normalizeUrl(url.trim().ifEmpty { buildUrlFromHostPort(cleanHost, cleanPort) })
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOST, cleanHost)
            .putString(KEY_PORT, cleanPort)
            .putString(KEY_URL, cleanUrl)
            .apply()
        return TermuxServerConfig(
            host = cleanHost,
            port = cleanPort,
            url = cleanUrl,
            isConfigExpanded = isExpanded,
            connectionStatus = "Saved Termux Server: $cleanUrl (Host: $cleanHost, Port: $cleanPort)"
        )
    }

    @JvmStatic
    fun buildUrlFromHostPort(host: String, port: String, currentUrl: String = ""): String {
        val cleanHost = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
            .ifEmpty { DEFAULT_HOST }
        val cleanPort = port.trim().filter { it.isDigit() }
        val scheme = if (currentUrl.trim().startsWith("https://", ignoreCase = true)) "https" else "http"

        val pathSuffix = try {
            val trimmed = currentUrl.trim()
            if (trimmed.isNotEmpty()) {
                val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
                    trimmed
                } else {
                    "http://$trimmed"
                }
                val afterScheme = withScheme.substringAfter("://")
                val slashIdx = afterScheme.indexOf('/')
                if (slashIdx >= 0) afterScheme.substring(slashIdx) else ""
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }

        return if (cleanPort.isNotEmpty()) {
            "$scheme://$cleanHost:$cleanPort$pathSuffix"
        } else {
            "$scheme://$cleanHost$pathSuffix"
        }
    }

    @JvmStatic
    fun extractHostAndPortFromUrl(
        rawUrl: String,
        fallbackHost: String = DEFAULT_HOST,
        fallbackPort: String = DEFAULT_PORT
    ): Pair<String, String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return fallbackHost to fallbackPort
        return try {
            val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
                trimmed
            } else {
                "http://$trimmed"
            }
            val uri = URI(withScheme)
            val parsedHost = uri.host?.takeIf { it.isNotBlank() } ?: run {
                val authority = withScheme.substringAfter("://").substringBefore('/')
                authority.substringBefore(':').ifBlank { fallbackHost }
            }
            val parsedPort = if (uri.port > 0) {
                uri.port.toString()
            } else {
                val authority = withScheme.substringAfter("://").substringBefore('/')
                if (':' in authority) {
                    authority.substringAfter(':').filter { it.isDigit() }.ifBlank { fallbackPort }
                } else {
                    fallbackPort
                }
            }
            parsedHost to parsedPort
        } catch (_: Exception) {
            val authority = trimmed.removePrefix("http://").removePrefix("https://").substringBefore('/')
            val h = authority.substringBefore(':').ifBlank { fallbackHost }
            val p = if (':' in authority) authority.substringAfter(':').filter { it.isDigit() }.ifBlank { fallbackPort } else fallbackPort
            h to p
        }
    }

    @JvmStatic
    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return DEFAULT_URL
        return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    @JvmStatic
    fun pingServer(config: TermuxServerConfig): TermuxServerResponse {
        val startMs = System.currentTimeMillis()
        val targetUrl = normalizeUrl(config.url.ifBlank { buildUrlFromHostPort(config.host, config.port) })
        return try {
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("User-Agent", "StudioError-TermuxClient/1.0")
            }
            val code = conn.responseCode
            val body = readStreamSafe(conn)
            conn.disconnect()
            val elapsed = System.currentTimeMillis() - startMs
            TermuxServerResponse(
                isSuccess = code in 200..499,
                httpCode = code,
                resolvedEndpoint = targetUrl,
                rawResponseText = body,
                parsedReplyText = "Connected to Termux Server at $targetUrl (HTTP $code in ${elapsed}ms)",
                durationMs = elapsed
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startMs
            TermuxServerResponse(
                isSuccess = false,
                httpCode = 0,
                resolvedEndpoint = targetUrl,
                errorMessage = e.message ?: e.javaClass.simpleName,
                durationMs = elapsed
            )
        }
    }

    @JvmStatic
    fun sendPromptToServer(
        config: TermuxServerConfig,
        prompt: String,
        existingProjectName: String? = null
    ): TermuxServerResponse {
        val startMs = System.currentTimeMillis()
        val baseUrl = normalizeUrl(config.url.ifBlank { buildUrlFromHostPort(config.host, config.port) })

        val requestJson = JSONObject().apply {
            put("prompt", prompt)
            put("message", prompt)
            put("query", prompt)
            put("text", prompt)
            put("host", config.host)
            put("port", config.port)
            put("url", baseUrl)
            if (!existingProjectName.isNullOrBlank()) {
                put("existing_project", existingProjectName)
            }
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }
                    )
                }
            )
        }.toString()

        val candidateEndpoints = buildCandidateEndpoints(baseUrl)
        var lastError: String? = null
        var lastHttpCode = 0

        for (endpoint in candidateEndpoints) {
            try {
                val postResult = executePostJson(endpoint, requestJson)
                lastHttpCode = postResult.first
                val responseBody = postResult.second
                if (lastHttpCode in 200..299) {
                    val elapsed = System.currentTimeMillis() - startMs
                    return parseSuccessfulResponse(endpoint, lastHttpCode, responseBody, elapsed)
                } else if (lastHttpCode !in listOf(404, 405)) {
                    lastError = "HTTP $lastHttpCode from $endpoint: ${responseBody.take(180)}"
                }
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message ?: "Connection failed"}"
                break
            }
        }

        if (lastHttpCode in listOf(404, 405)) {
            try {
                val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
                val sep = if ('?' in baseUrl) "&" else "?"
                val getUrl = "${baseUrl}${sep}prompt=$encodedPrompt&message=$encodedPrompt"
                val conn = (URL(getUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 45000
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                }
                val code = conn.responseCode
                val body = readStreamSafe(conn)
                conn.disconnect()
                if (code in 200..299) {
                    val elapsed = System.currentTimeMillis() - startMs
                    return parseSuccessfulResponse(getUrl, code, body, elapsed)
                }
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message ?: "GET fallback failed"}"
            }
        }

        val elapsed = System.currentTimeMillis() - startMs
        return TermuxServerResponse(
            isSuccess = false,
            httpCode = lastHttpCode,
            resolvedEndpoint = baseUrl,
            errorMessage = lastError ?: "Could not connect to Termux server at $baseUrl",
            durationMs = elapsed
        )
    }

    private fun buildCandidateEndpoints(baseUrl: String): List<String> {
        val trimmed = baseUrl.trimEnd('/')
        val hasCustomPath = try {
            val path = URI(baseUrl).path
            !path.isNullOrEmpty() && path != "/"
        } catch (_: Exception) {
            false
        }
        return if (hasCustomPath) {
            listOf(baseUrl)
        } else {
            listOf(
                baseUrl,
                "$trimmed/generate",
                "$trimmed/chat",
                "$trimmed/build",
                "$trimmed/api/generate",
                "$trimmed/api/chat",
                "$trimmed/v1/chat/completions"
            )
        }
    }

    private fun executePostJson(endpointUrl: String, jsonPayload: String): Pair<Int, String> {
        val conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            connectTimeout = 6000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", "StudioError-TermuxClient/1.0")
        }
        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(jsonPayload)
            writer.flush()
        }
        val code = conn.responseCode
        val body = readStreamSafe(conn)
        conn.disconnect()
        return code to body
    }

    private fun readStreamSafe(conn: HttpURLConnection): String {
        val stream = try {
            if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        } catch (_: Exception) {
            conn.errorStream
        } ?: return ""
        return try {
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseSuccessfulResponse(
        endpoint: String,
        httpCode: Int,
        rawBody: String,
        durationMs: Long
    ): TermuxServerResponse {
        val trimmed = rawBody.trim()
        if (trimmed.startsWith("{")) {
            try {
                val json = JSONObject(trimmed)

                // Check if Termux explicitly reported an error or build error
                val explicitError = json.optString("error", "").trim()
                    .ifEmpty { json.optString("build_error", "").trim() }
                if (explicitError.isNotEmpty() && !json.optBoolean("success", true)) {
                    return TermuxServerResponse(
                        isSuccess = false,
                        httpCode = httpCode,
                        resolvedEndpoint = endpoint,
                        rawResponseText = trimmed,
                        errorMessage = explicitError,
                        durationMs = durationMs
                    )
                }

                val reply = listOf("reply", "response", "message", "output", "content", "text", "result", "answer")
                    .firstNotNullOfOrNull { key ->
                        if (json.has(key) && !json.isNull(key)) {
                            val v = json.optString(key, "").trim()
                            v.takeIf { it.isNotEmpty() && !it.startsWith("{") }
                        } else null
                    }
                    ?: extractOpenAiChoiceContent(json)
                    ?: trimmed

                val appName = json.optString("app_name", "").trim().takeIf { it.isNotEmpty() }
                    ?: json.optString("appName", "").trim().takeIf { it.isNotEmpty() }
                val pkgName = json.optString("package_name", "").trim().takeIf { it.isNotEmpty() }
                    ?: json.optString("packageName", "").trim().takeIf { it.isNotEmpty() }

                val scratchFiles = mutableMapOf<String, String>()
                val editedFiles = mutableSetOf<String>()
                val buildErrors = mutableListOf<String>()

                if (explicitError.isNotEmpty()) {
                    buildErrors.add(explicitError)
                }
                if (json.has("errors") && json.optJSONArray("errors") != null) {
                    val errArr = json.getJSONArray("errors")
                    for (i in 0 until errArr.length()) {
                        val errItem = errArr.optString(i, "").trim()
                        if (errItem.isNotEmpty()) buildErrors.add(errItem)
                    }
                }

                // Parse "files" object or array from Termux
                if (json.has("files")) {
                    val filesObj = json.optJSONObject("files")
                    if (filesObj != null) {
                        val keys = filesObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = filesObj.optString(k, "")
                            if (v.isNotBlank()) {
                                scratchFiles[k] = v
                            }
                        }
                    } else {
                        val filesArr = json.optJSONArray("files")
                        if (filesArr != null) {
                            for (i in 0 until filesArr.length()) {
                                val fObj = filesArr.optJSONObject(i) ?: continue
                                val path = fObj.optString("path", fObj.optString("name", "")).trim()
                                val content = fObj.optString("content", fObj.optString("code", ""))
                                val action = fObj.optString("action", fObj.optString("status", "")).lowercase()
                                if (path.isNotEmpty()) {
                                    scratchFiles[path] = content.ifEmpty { "// File generated by Termux: $path" }
                                    if ("edit" in action || "modify" in action || "update" in action) {
                                        editedFiles.add(path)
                                    }
                                }
                            }
                        }
                    }
                }

                if (json.has("created_files") && json.optJSONArray("created_files") != null) {
                    val arr = json.getJSONArray("created_files")
                    for (i in 0 until arr.length()) {
                        val p = arr.optString(i, "").trim()
                        if (p.isNotEmpty() && !scratchFiles.containsKey(p)) {
                            scratchFiles[p] = "// Created by Termux Server: $p"
                        }
                    }
                }

                if (json.has("edited_files") && json.optJSONArray("edited_files") != null) {
                    val arr = json.getJSONArray("edited_files")
                    for (i in 0 until arr.length()) {
                        val p = arr.optString(i, "").trim()
                        if (p.isNotEmpty()) {
                            editedFiles.add(p)
                            if (!scratchFiles.containsKey(p)) {
                                scratchFiles[p] = "// Edited by Termux Server: $p"
                            }
                        }
                    }
                }

                if (json.has("code")) {
                    val codeStr = json.optString("code", "").trim()
                    if (codeStr.isNotEmpty()) {
                        scratchFiles["src/main/java/com/termux/generated/MainActivity.kt"] = codeStr
                    }
                }

                // Also extract any markdown code blocks from the reply text
                val extractedFromMarkdown = extractCodeBlocksFromText(reply)
                scratchFiles.putAll(extractedFromMarkdown)

                val explicitChat = if (json.has("is_chat")) json.optBoolean("is_chat") else null
                val actionStr = json.optString("action", json.optString("mode", json.optString("type", ""))).lowercase()
                val explicitBuildFlag = json.optBoolean("build", false) ||
                    json.optBoolean("is_build", false) ||
                    json.optBoolean("should_build", false) ||
                    actionStr == "build" ||
                    actionStr == "compile" ||
                    actionStr == "generate_app" ||
                    endpoint.endsWith("/build")

                val hasBuildPayload = explicitBuildFlag ||
                    scratchFiles.isNotEmpty() ||
                    editedFiles.isNotEmpty() ||
                    appName != null ||
                    pkgName != null ||
                    json.has("components") ||
                    containsBuildDirectivesInText(reply)

                val isBuild = if (explicitChat == true || actionStr == "chat" || actionStr == "answer") {
                    false
                } else {
                    hasBuildPayload
                }

                return TermuxServerResponse(
                    isSuccess = true,
                    httpCode = httpCode,
                    resolvedEndpoint = endpoint,
                    rawResponseText = trimmed,
                    parsedReplyText = stripRawCodeBlocksForCleanChat(reply),
                    isBuildFromTermux = isBuild,
                    parsedAppName = appName,
                    parsedPackageName = pkgName,
                    parsedScratchFiles = scratchFiles,
                    parsedEditedFiles = editedFiles,
                    parsedBuildErrors = buildErrors,
                    isConversationalOnly = !isBuild,
                    durationMs = durationMs
                )
            } catch (_: Exception) {
            }
        }

        val extractedFiles = extractCodeBlocksFromText(trimmed)
        val isBuildText = extractedFiles.isNotEmpty() || containsBuildDirectivesInText(trimmed) || endpoint.endsWith("/build")

        return TermuxServerResponse(
            isSuccess = true,
            httpCode = httpCode,
            resolvedEndpoint = endpoint,
            rawResponseText = trimmed,
            parsedReplyText = stripRawCodeBlocksForCleanChat(trimmed).ifEmpty { "Termux Server responded (HTTP $httpCode)" },
            isBuildFromTermux = isBuildText,
            parsedScratchFiles = extractedFiles,
            isConversationalOnly = !isBuildText,
            durationMs = durationMs
        )
    }

    private fun extractCodeBlocksFromText(text: String): Map<String, String> {
        val files = mutableMapOf<String, String>()
        if (!text.contains("```")) return files
        val regex = Regex("```([a-zA-Z0-9_+-]*)\\s*\\n([\\s\\S]*?)```")
        var blockIndex = 1
        regex.findAll(text).forEach { match ->
            val lang = match.groupValues[1].lowercase().trim()
            val code = match.groupValues[2].trim()
            if (code.isNotEmpty()) {
                val firstLine = code.lineSequence().firstOrNull()?.trim().orEmpty()
                val hintedPath = if (firstLine.startsWith("//") && (firstLine.contains(".kt") || firstLine.contains(".java") || firstLine.contains(".xml"))) {
                    firstLine.removePrefix("//").removePrefix("File:").removePrefix("file:").removePrefix("Path:").trim()
                } else null

                val path = hintedPath ?: when (lang) {
                    "xml" -> if (code.contains("<manifest")) "AndroidManifest.xml" else "src/main/res/layout/activity_main.xml"
                    "java" -> "src/main/java/com/termux/app/TermuxGeneratedModule$blockIndex.java"
                    "gradle", "kts" -> "build.gradle.kts"
                    else -> if (blockIndex == 1) "src/main/java/com/termux/app/MainActivity.kt" else "src/main/java/com/termux/app/Module$blockIndex.kt"
                }
                files[path] = code
                blockIndex++
            }
        }
        return files
    }

    private fun containsBuildDirectivesInText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("create_file:") ||
            lower.contains("edit_file:") ||
            lower.contains("build_apk") ||
            lower.contains("building apk") ||
            (lower.contains("package com.") && lower.contains("class "))
    }

    private fun stripRawCodeBlocksForCleanChat(text: String): String {
        if (!text.contains("```")) return text.trim()
        val stripped = text.replace(Regex("```[a-zA-Z0-9_+-]*\\s*\\n[\\s\\S]*?```"), "").trim()
        return stripped.ifEmpty { "Code files received from Termux Server." }
    }

    private fun extractOpenAiChoiceContent(json: JSONObject): String? {
        val choices = json.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val first = choices.optJSONObject(0) ?: return null
        val msgObj = first.optJSONObject("message")
        if (msgObj != null) {
            val content = msgObj.optString("content", "").trim()
            if (content.isNotEmpty()) return content
        }
        val text = first.optString("text", "").trim()
        return text.takeIf { it.isNotEmpty() }
    }
}

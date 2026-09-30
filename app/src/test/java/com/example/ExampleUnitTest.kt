package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.GgufBlueprintEngine
import com.example.engine.TermuxBackendClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun verifyTermuxBackendUrlAndPortNormalizationWithoutHardcodedIp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Verify initial state has no hardcoded IP when not saved
        val prefs = context.getSharedPreferences("termux_backend_connection_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val initial = TermuxBackendClient.loadSavedConnectionState(context)
        assertEquals("", initial.backendUrlOrIp)
        assertEquals("", initial.port)
        assertFalse(initial.isConnected)
        assertEquals("Disconnected", initial.connectionStatus)

        // Example 1: http://127.0.0.1:8080
        val (host1, port1) = TermuxBackendClient.normalizeUrlAndPortFields("http://127.0.0.1:8080", "")
        assertEquals("http://127.0.0.1", host1)
        assertEquals("8080", port1)
        assertEquals("http://127.0.0.1:8080", TermuxBackendClient.buildBaseUrl(host1, port1))

        // Example 2: http://192.168.1.100:8080
        val (host2, port2) = TermuxBackendClient.normalizeUrlAndPortFields("192.168.1.100", "8080")
        assertEquals("http://192.168.1.100", host2)
        assertEquals("8080", port2)
        assertEquals("http://192.168.1.100:8080", TermuxBackendClient.buildBaseUrl(host2, port2))

        // Verify Save Connection and reload
        val saved = TermuxBackendClient.saveConnectionConfig(context, "http://192.168.1.100", "8080")
        assertTrue(saved.isSaved)
        assertFalse(saved.isEditing)
        assertEquals("http://192.168.1.100:8080", saved.resolvedBaseUrl)
        assertEquals("http://192.168.1.100:8080/api/health", saved.resolvedHealthUrl)

        val reloaded = TermuxBackendClient.loadSavedConnectionState(context)
        assertTrue(reloaded.isSaved)
        assertEquals("http://192.168.1.100", reloaded.backendUrlOrIp)
        assertEquals("8080", reloaded.port)
    }

    @Test
    fun verifyTermuxGetApiHealthLiveConnectionAndStatusParsing() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        var requestedPath = ""

        val serverThread = thread(start = true) {
            try {
                serverSocket.use { server ->
                    server.soTimeout = 5000
                    val client = server.accept()
                    client.use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))
                        val requestLine = reader.readLine().orEmpty()
                        requestedPath = requestLine.split(" ").getOrNull(1).orEmpty()
                        while (true) {
                            val line = reader.readLine()
                            if (line.isNullOrEmpty()) break
                        }
                        val jsonBody = """
                            {
                              "backend_status": "Online",
                              "llama_cpp_status": "Running",
                              "gguf_model_loaded": true,
                              "model_name": "qwen2.5-coder-1.5b-q4_k_m.gguf"
                            }
                        """.trimIndent()
                        val bodyBytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
                        val writer = OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8)
                        writer.write("HTTP/1.1 200 OK\r\n")
                        writer.write("Content-Type: application/json\r\n")
                        writer.write("Content-Length: ${bodyBytes.size}\r\n")
                        writer.write("Connection: close\r\n\r\n")
                        writer.write(jsonBody)
                        writer.flush()
                    }
                }
            } catch (_: Exception) {
            }
        }

        val connectedState = TermuxBackendClient.performHealthCheckSync(
            rawUrlOrIp = "http://127.0.0.1",
            rawPort = port.toString(),
            isSaved = true,
            isEditing = false,
            timeoutMs = 3000
        )
        serverThread.join(3000)

        assertEquals("/api/health", requestedPath)
        assertTrue(connectedState.isConnected)
        assertEquals("Connected", connectedState.connectionStatus)
        assertTrue(connectedState.backendStatus.contains("Online", ignoreCase = true))
        assertTrue(connectedState.llamaCppStatus.contains("Running", ignoreCase = true))
        assertTrue(connectedState.ggufModelLoadedStatus.contains("Loaded", ignoreCase = true))
        assertTrue(connectedState.ggufModelLoadedStatus.contains("qwen2.5-coder-1.5b-q4_k_m.gguf"))

        val syncedModelState = GgufBlueprintEngine.syncFromTermuxBackend(connectedState)
        assertTrue(syncedModelState.isValidGgufLoaded)
        assertEquals("qwen2.5-coder-1.5b-q4_k_m.gguf", syncedModelState.modelFileName)
    }
}

package com.example.smarthomekiosk

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import kotlin.concurrent.thread

class KioskHttpServer(
    private val context: Context,
    private val port: Int,
    private val password: String,
    private val listener: KioskCommandListener
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    interface KioskCommandListener {
        fun onScreenOn()
        fun onScreenOff()
        fun onSpeak(text: String)
        fun onSetVolume(volume: Int)
        fun getDeviceInfoJson(): String
        fun onReloadWebView()
        fun onUpdateSettings(dashboardUrl: String?, ignoreSslErrors: Boolean?)
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        thread(name = "KioskHttpServerThread") {
            try {
                serverSocket = ServerSocket(port)
                Log.i("KioskHttpServer", "Server started on port $port")
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    thread {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e("KioskHttpServer", "Error in server socket loop", e)
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverSocket = null
        Log.i("KioskHttpServer", "Server stopped")
    }

    private fun handleClient(socket: Socket) {
        var reader: BufferedReader? = null
        var output: OutputStream? = null
        try {
            reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            output = socket.getOutputStream()

            // 1. Read Request Line
            val reqLine = reader.readLine() ?: return
            val reqTokens = reqLine.split(" ")
            if (reqTokens.size < 2) return
            val method = reqTokens[0].uppercase(Locale.US)
            val fullPath = reqTokens[1]

            // 2. Read Headers
            val headers = HashMap<String, String>()
            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null) {
                if (line!!.trim().isEmpty()) break
                val colonIdx = line!!.indexOf(':')
                if (colonIdx != -1) {
                    val key = line!!.substring(0, colonIdx).trim().lowercase(Locale.US)
                    val value = line!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            // 3. Read Body
            val body = StringBuilder()
            if (contentLength > 0) {
                val buffer = CharArray(1024)
                var bytesRead = 0
                while (bytesRead < contentLength) {
                    val read = reader.read(buffer, 0, Math.min(buffer.size, contentLength - bytesRead))
                    if (read == -1) break
                    body.append(buffer, 0, read)
                    bytesRead += read
                }
            }

            // 4. Parse Path & Query Params
            val pathParts = fullPath.split("?")
            val path = pathParts[0]
            val queryParams = HashMap<String, String>()
            if (pathParts.size > 1) {
                val queryStr = pathParts[1]
                val pairs = queryStr.split("&")
                for (pair in pairs) {
                    val idx = pair.indexOf("=")
                    if (idx > 0) {
                        val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                        val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                        queryParams[key] = value
                    }
                }
            }

            // Serve Static Web Assets
            if (method == "GET") {
                when (path) {
                    "/" -> {
                        serveAsset(output, "web/index.html", "text/html; charset=UTF-8")
                        return
                    }
                    "/index.html" -> {
                        serveAsset(output, "web/index.html", "text/html; charset=UTF-8")
                        return
                    }
                    "/index.css" -> {
                        serveAsset(output, "web/index.css", "text/css; charset=UTF-8")
                        return
                    }
                    "/index.js" -> {
                        serveAsset(output, "web/index.js", "application/javascript; charset=UTF-8")
                        return
                    }
                    "/favicon.ico" -> {
                        val response = "HTTP/1.1 204 No Content\r\n" +
                                "Connection: close\r\n\r\n"
                        output.write(response.toByteArray(Charsets.UTF_8))
                        output.flush()
                        return
                    }
                }
            }

            // Handle Preflight OPTIONS request (CORS)
            if (method == "OPTIONS") {
                sendCorsResponse(output)
                return
            }

            // 5. Check Authentication
            var clientPassword = headers["x-kiosk-password"] ?: queryParams["password"]
            
            // Try parsing password from JSON body if not found in headers or query
            if (clientPassword.isNullOrEmpty() && body.isNotEmpty()) {
                try {
                    val json = JSONObject(body.toString())
                    if (json.has("password")) {
                        clientPassword = json.getString("password")
                    }
                } catch (e: Exception) {
                    // Ignore malformed JSON for now
                }
            }

            if (password.isNotEmpty() && clientPassword != password) {
                sendResponse(output, 401, "Unauthorized", "{\"error\":\"Unauthorized\"}")
                return
            }

            // 6. Handle Endpoints
            when {
                path == "/api/screen/on" && method == "POST" -> {
                    listener.onScreenOn()
                    sendResponse(output, 200, "OK", "{\"success\":true}")
                }
                path == "/api/screen/off" && method == "POST" -> {
                    listener.onScreenOff()
                    sendResponse(output, 200, "OK", "{\"success\":true}")
                }
                path == "/api/tts" && method == "POST" -> {
                    val text = try {
                        JSONObject(body.toString()).getString("text")
                    } catch (e: Exception) {
                        queryParams["text"] ?: ""
                    }
                    if (text.isNotEmpty()) {
                        listener.onSpeak(text)
                        sendResponse(output, 200, "OK", "{\"success\":true}")
                    } else {
                        sendResponse(output, 400, "Bad Request", "{\"error\":\"Missing 'text' parameter\"}")
                    }
                }
                path == "/api/volume" && method == "POST" -> {
                    val vol = try {
                        JSONObject(body.toString()).getInt("volume")
                    } catch (e: Exception) {
                        queryParams["volume"]?.toIntOrNull() ?: -1
                    }
                    if (vol in 0..100) {
                        listener.onSetVolume(vol)
                        sendResponse(output, 200, "OK", "{\"success\":true}")
                    } else {
                        sendResponse(output, 400, "Bad Request", "{\"error\":\"Invalid or missing 'volume' parameter (0-100)\"}")
                    }
                }
                path == "/api/device/info" && method == "GET" -> {
                    val info = listener.getDeviceInfoJson()
                    sendResponse(output, 200, "OK", info)
                }
                path == "/api/webview/reload" && method == "POST" -> {
                    listener.onReloadWebView()
                    sendResponse(output, 200, "OK", "{\"success\":true}")
                }
                path == "/api/settings" && method == "POST" -> {
                    val url = try {
                        val json = JSONObject(body.toString())
                        if (json.has("dashboardUrl")) json.getString("dashboardUrl") else null
                    } catch (e: Exception) {
                        queryParams["dashboardUrl"]
                    }
                    val ignoreSsl = try {
                        val json = JSONObject(body.toString())
                        if (json.has("ignoreSslErrors")) json.getBoolean("ignoreSslErrors") else null
                    } catch (e: Exception) {
                        queryParams["ignoreSslErrors"]?.toBooleanStrictOrNull()
                    }

                    if (url != null || ignoreSsl != null) {
                        listener.onUpdateSettings(url, ignoreSsl)
                        sendResponse(output, 200, "OK", "{\"success\":true}")
                    } else {
                        sendResponse(output, 400, "Bad Request", "{\"error\":\"No settings parameters provided\"}")
                    }
                }
                else -> {
                    sendResponse(output, 404, "Not Found", "{\"error\":\"Endpoint not found or method not allowed\"}")
                }
            }

        } catch (e: Exception) {
            Log.e("KioskHttpServer", "Error handling client", e)
        } finally {
            try {
                output?.close()
                reader?.close()
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, statusText: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, X-Kiosk-Password\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendCorsResponse(output: OutputStream) {
        val response = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, X-Kiosk-Password\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun serveAsset(output: OutputStream, assetPath: String, contentType: String) {
        try {
            context.assets.open(assetPath).use { inputStream ->
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                
                val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: $contentType\r\n" +
                        "Content-Length: $size\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                output.write(response.toByteArray(Charsets.UTF_8))
                output.write(buffer)
                output.flush()
            }
        } catch (e: Exception) {
            Log.e("KioskHttpServer", "Error serving asset $assetPath", e)
            sendResponse(output, 404, "Not Found", "{\"error\":\"Asset not found\"}")
        }
    }
}

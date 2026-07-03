package com.richardcbh.httpfileserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlin.text.StringBuilder
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.URLDecoder

class FileServerService : Service() {

    private val binder = LocalBinder()
    var isRunning = false
        private set
    var currentIPv6Address: String? = null
        private set
    var currentPort: Int = 8080
        private set

    private var httpServer: HttpFileServer? = null
    private var rootDocumentFile: DocumentFile? = null

    inner class LocalBinder : Binder() {
        fun getService(): FileServerService = this@FileServerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY

        val folderUriString = intent?.getStringExtra("folderUri")
        val port = intent?.getIntExtra("port", 8080) ?: 8080

        if (folderUriString == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val folderUri = Uri.parse(folderUriString)
        rootDocumentFile = DocumentFile.fromTreeUri(this, folderUri)

        if (rootDocumentFile == null || !rootDocumentFile!!.exists()) {
            stopSelf()
            return START_NOT_STICKY
        }

        currentPort = port
        currentIPv6Address = getDeviceIPv6Address()

        startHttpServer(port)
        startForegroundServiceNotification()

        isRunning = true
        Log.d("FileServerService", "Server started on port $port, IPv6: $currentIPv6Address")
        return START_STICKY
    }

    private fun startHttpServer(port: Int) {
        try {
            httpServer = HttpFileServer(this, "::", port, rootDocumentFile!!)
            httpServer?.start()
        } catch (e: Exception) {
            Log.e("FileServerService", "Failed to start HTTP server", e)
            stopSelf()
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "file_server_channel"
        val channelName = "HTTP File Server"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("IPv6 HTTP 文件共享运行中")
            .setContentText("端口: $currentPort | IPv6: ${currentIPv6Address ?: "N/A"}")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun getDeviceIPv6Address(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()

            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses.toList()
                for (addr in addresses) {
                    if (addr is Inet6Address &&
                        !addr.isLoopbackAddress &&
                        !addr.isLinkLocalAddress &&
                        !addr.isSiteLocalAddress) {
                        val hostAddress = addr.hostAddress
                        return hostAddress?.substringBefore('%')
                    }
                }
            }

            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses.toList()
                for (addr in addresses) {
                    if (addr is Inet6Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        return addr.hostAddress?.substringBefore('%')
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileServerService", "Error getting IPv6", e)
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
        isRunning = false
        Log.d("FileServerService", "Server stopped")
    }

    private class HttpFileServer(
        private val context: Context,
        hostname: String,
        port: Int,
        private val rootDoc: DocumentFile
    ) : NanoHTTPD(hostname, port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            Log.d("HttpFileServer", "Request: $uri")

            return try {
                if (uri == "/" || uri == "/index.html") {
                    serveDirectoryListing(rootDoc, "")
                } else {
                    val relativePath = uri.trimStart('/')
                    val targetDoc = findDocumentFile(rootDoc, relativePath)

                    if (targetDoc == null) {
                        newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found")
                    } else if (targetDoc.isDirectory) {
                        serveDirectoryListing(targetDoc, relativePath)
                    } else {
                        serveFile(targetDoc)
                    }
                }
            } catch (e: Exception) {
                Log.e("HttpFileServer", "Error serving request", e)
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Server Error: ${e.message}")
            }
        }

        private fun findDocumentFile(current: DocumentFile, path: String): DocumentFile? {
            if (path.isEmpty()) return current

            val parts = path.split("/").filter { it.isNotEmpty() }
            var doc = current

            for (part in parts) {
                val decodedPart = URLDecoder.decode(part, "UTF-8")
                val child = doc.findFile(decodedPart)
                if (child == null) return null
                doc = child
            }
            return doc
        }

        private fun serveDirectoryListing(doc: DocumentFile, currentPath: String): Response {
            val sb = StringBuilder()
            sb.append("""<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>IPv6 文件共享</title>
<style>body{font-family:Arial,sans-serif;margin:20px} a{text-decoration:none;color:#0066cc} li{margin:8px 0}</style>
</head><body>
<h1>文件共享 - ${doc.name ?: "Root"}</h1>
<ul>
""")

            if (currentPath.isNotEmpty()) {
                val parentPath = currentPath.substringBeforeLast('/', "")
                sb.append("<li><a href=\"/${parentPath}\">.. (返回上级)</a></li>")
            }

            val children = doc.listFiles()?.sortedBy { !it.isDirectory } ?: emptyList()
            for (child in children) {
                val childPath = if (currentPath.isEmpty()) child.name else "$currentPath/${child.name}"
                val encodedPath = java.net.URLEncoder.encode(childPath, "UTF-8")
                val icon = if (child.isDirectory) "📁" else "📄"
                sb.append("<li>$icon <a href=\"/$encodedPath\">${child.name}</a></li>")
            }

            sb.append("""</ul>
<p style="color:gray;font-size:12px">提示：点击文件名称即可下载。支持 IPv6 访问。</p>
</body></html>""")

            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", sb.toString())
        }

        private fun serveFile(doc: DocumentFile): Response {
            val mimeType = getMimeType(doc.name ?: "")
            val inputStream = context.contentResolver.openInputStream(doc.uri)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not accessible")

            val fileName = doc.name ?: "download"
            val response = newChunkedResponse(Response.Status.OK, mimeType, inputStream)
            response.addHeader("Content-Disposition", "attachment; filename=\"$fileName\"")
            return response
        }

        private fun getMimeType(fileName: String): String {
            val extension = fileName.substringAfterLast('.', "")
            return when (extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "pdf" -> "application/pdf"
                "txt" -> "text/plain"
                "mp4" -> "video/mp4"
                "mp3" -> "audio/mpeg"
                else -> "application/octet-stream"
            }
        }
    }
}

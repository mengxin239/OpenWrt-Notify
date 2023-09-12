package com.mengxin239.notifyapp

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.InetAddress

class NotificationClient : Activity() {
    private lateinit var client: OkHttpClient
    private lateinit var connectionStatus: TextView
    private val channelId = "OpenWrt Notify"
    private val serverAddr = "ws://192.168.0.1:8080/ws"
    private val notifyTitle = "OpenWrt Notify"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        connectionStatus = findViewById(R.id.connectionStatus)
        updateConnectionStatus("No WIFI Connection")
        client = OkHttpClient()
        waitForNetworkAndConnect()
    }

    private fun waitForNetworkAndConnect() {
        GlobalScope.launch {
            while (!isWifiAddressInSubnet(this@NotificationClient)) {
                delay(1000)
            }
            withContext(Dispatchers.Main) {
                updateConnectionStatus("WIFI connection detected")
                displayNotification(notifyTitle, "WIFI connection detected")
                connect()
            }
        }
    }

    private fun reconnectAfterDelay() {
        GlobalScope.launch {
            delay(5000)
            withContext(Dispatchers.Main) {
                connect()
            }
        }
    }

    private fun connect() {
        if (!isWifiAddressInSubnet(this)) {
            Log.d("WebSocket", "No Network")
            displayNotification(notifyTitle,"Waiting for WIFI connection")
            updateConnectionStatus("No WIFI Connection")
            waitForNetworkAndConnect()
        }
        val request = Request.Builder().url(serverAddr).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connection opened
                Log.d("WebSocket", "Connection opened.")
                updateConnectionStatus("Connected")
                displayNotification(notifyTitle,"Notify Application Online")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Received a notification
                Log.d("WebSocket", "Received notification: $text")
                displayNotification(notifyTitle,text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Received binary message
                Log.d("WebSocket", "Received binary message.")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // Connection closing
                Log.d("WebSocket", "Connection closing. Code: $code, Reason: $reason")
                updateConnectionStatus("Closing connection")
                displayNotification(notifyTitle,"Notify Application Offline. Trying Connect after 5 seconds.")
                reconnectAfterDelay()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Connection failure
                Log.d("WebSocket", "Connection failure.", t)
                updateConnectionStatus("Connection failed. Retrying...")
                displayNotification(notifyTitle,"Notify Application Offline. Trying Connect after 5 seconds.")
                reconnectAfterDelay()
            }
        }
        client.newWebSocket(request, listener)
    }

    private fun displayNotification(title: String, notification: String) {
        GlobalScope.launch(Dispatchers.Main) {
            val builder = NotificationCompat.Builder(this@NotificationClient, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, builder.build())
        }
    }

    private fun updateConnectionStatus(status: String) {
        runOnUiThread {
            connectionStatus.text = status
        }
    }

    private fun extractThirdOctet(address: String): Byte? {
        val ipPattern = """\b\d{1,3}\.\d{1,3}\.(\d{1,3})\.\d{1,3}\b""".toRegex()
        val matchResult = ipPattern.find(address)
        val thirdOctet = matchResult?.groups?.get(1)?.value?.toIntOrNull()
        return thirdOctet?.toByte()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notification Channel"
            val descriptionText = "Channel for NotificationClient"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isWifiAddressInSubnet(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork

        if (activeNetwork != null) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                val ipAddress = wifiInfo.ipAddress

                val ipString = String.format(
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )

                val inetAddress = InetAddress.getByName(ipString)
                val byteAddress = inetAddress.address
                return byteAddress[2] == extractThirdOctet(serverAddr)
            }
        }
        return false
    }
}

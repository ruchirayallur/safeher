package com.safeher.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove title bar and set content to WebView directly
        supportActionBar?.hide()

        webView = WebView(this)
        setContentView(webView)

        setupWebView()
        requestPermissions()

        // Load the local HTML file
        webView.loadUrl("file:///android_asset/index.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings: WebSettings = webView.settings

        // Enable Javascript and DOM Storage
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true

        // Allow file access to hit local endpoints if necessary
        webSettings.allowFileAccess = true
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true

        // Allow mixed content (HTTP connections from local files)
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Handle navigation within the WebView
        webView.webViewClient = WebViewClient()

        // Handle Geolocation permissions automatically for the WebView
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                // Grant permission automatically within WebView if app has the permission
                callback.invoke(origin, true, false)
            }
        }

        // Add the JS Bridge for SMS
        webView.addJavascriptInterface(AndroidSMSBridge(), "AndroidSMS")
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    inner class AndroidSMSBridge {
        @JavascriptInterface
        fun sendEmergencySMS(contactsJsonString: String, message: String) {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "SMS Permission not granted!", Toast.LENGTH_SHORT).show()
                }
                return
            }

            try {
                val contactsArray = JSONArray(contactsJsonString)
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                if (smsManager == null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "SMS Manager not available on this device", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                var successCount = 0
                for (i in 0 until contactsArray.length()) {
                    val phoneNumber = contactsArray.getString(i)
                    if (phoneNumber.isNotBlank()) {
                        val parts = smsManager.divideMessage(message)
                        if (parts.size > 1) {
                            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                        } else {
                            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                        }
                        successCount++
                    }
                }
                
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "SOS Sent to $successCount contacts silently!", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to send SMS silently: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}

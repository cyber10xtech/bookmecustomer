package com.bookmebusiness.bookmeapp

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val APP_URL = "https://www.business.bookmebusiness.com/business-android/index.php?v=up"
        const val NOTIFICATION_CHANNEL_ID = "bookme_business_channel"
        const val NOTIFICATION_CHANNEL_NAME = "BookMe Business Notifications"
    }

    private lateinit var webView: WebView
    private lateinit var checkButton: Button
    private lateinit var splashImage: ImageView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // ── File chooser launcher ────────────────────────────────────────────────
    private val fileUploadLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { WebChromeClient.FileChooserParams.parseResult(result.resultCode, it) }
            } else null
            fileUploadCallback?.onReceiveValue(uris)
            fileUploadCallback = null
        }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkButton = findViewById(R.id.buttonCheck)
        splashImage = findViewById(R.id.imageView)
        webView     = findViewById(R.id.WebView)

        createNotificationChannel()
        configureWebView()
        setupBackPress()

        if (checkForInternet()) {
            onInternetAvailable()
        } else {
            onNoInternet("BookMe Business is checking for a connection. Please wait…")
        }

        checkButton.setOnClickListener {
            if (checkForInternet()) {
                onInternetAvailable()
            } else {
                onNoInternet("No internet connection. Please check your network and try again.")
            }
        }

        // If this Activity was launched by tapping a push notification, handle
        // any deep-link URL that Firebase may have attached as an extra.
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    // ── Internet helpers ─────────────────────────────────────────────────────
    private fun checkForInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net  = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun onInternetAvailable() {
        Toast.makeText(this, "Connected — BookMe Business is loading…", Toast.LENGTH_LONG).show()
        hideSplashAfterDelay()
        webView.loadUrl(APP_URL)
    }

    private fun onNoInternet(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        checkButton.visibility = View.VISIBLE
        splashImage.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    private fun hideSplashAfterDelay() {
        webView.visibility = View.VISIBLE
        val delay = 3_000L // 3 seconds is plenty; was 7 seconds before
        checkButton.postDelayed({ checkButton.visibility = View.GONE }, delay)
        splashImage.postDelayed({ splashImage.visibility = View.GONE }, delay)
    }

    // ── WebView setup ────────────────────────────────────────────────────────
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            allowFileAccess      = true
            loadWithOverviewMode = true
            useWideViewPort      = true          // ensures pages render at full width
            cacheMode            = WebSettings.LOAD_DEFAULT
            // pluginState is fully deprecated; removed
            // builtInZoomControls / setSupportZoom left off intentionally (single-column app)
        }
        webView.scrollBarStyle        = WebView.SCROLLBARS_OUTSIDE_OVERLAY
        webView.isScrollbarFadingEnabled = false

        // File upload support
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Cancel any previous outstanding callback before assigning a new one
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback ?: return false
                fileUploadLauncher.launch(fileChooserParams?.createIntent())
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            // Keep all navigation inside the WebView
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // Allow mailto / tel / intent links to leave the app
                if (!url.startsWith("http")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) { }
                    return true
                }
                view?.loadUrl(url)
                return true
            }

            // The old shouldInterceptRequest override that re-downloaded images
            // via HttpURLConnection on the calling thread has been removed.
            // It caused NetworkOnMainThreadException and was completely unnecessary
            // because WebView handles image fetching natively.
        }
    }

    // ── Back-press (modern API – replaces deprecated onBackPressed override) ─
    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // ── Notification channel (required for Android 8+) ───────────────────────
    /**
     * Creates the default notification channel used by [BookmeFirebaseMessagingService].
     * Must be called before any notification is posted (safe to call repeatedly –
     * Android is idempotent for existing channels).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push notifications from BookMe Business"
                enableLights(true)
                enableVibration(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // ── Deep-link from notification tap ──────────────────────────────────────
    private fun handleNotificationIntent(intent: Intent?) {
        val deepUrl = intent?.getStringExtra("url") ?: return
        if (deepUrl.isNotBlank() && checkForInternet()) {
            webView.loadUrl(deepUrl)
        }
    }
}

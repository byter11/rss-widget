package com.byterdevs.rsswidget.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.byterdevs.rsswidget.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDragHandleView

class BottomSheetWebView(context: Context, activity: Activity, readerable: Boolean) : FrameLayout(context) {

    private val mBottomSheetDialog: BottomSheetDialog = BottomSheetDialog(context, R.style.ModalBottomSheetDialog)
    private var mCurrentWebViewScrollY = 0
    private var mDragHandleActive = false
    private var mDismissed = false
    private var readabilityDoneOnce = false

    init {
        inflateLayout(context)
        setupBottomSheetBehaviour()
        setupWebView(readerable)
        setupListeners(activity)
    }

    private fun inflateLayout(context: Context) {
        inflate(context, R.layout.bottom_sheet_webview, this)

        mBottomSheetDialog.setContentView(this)

        mBottomSheetDialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)
    }

    private fun setupBottomSheetBehaviour() {
        val dragHandle = findViewById<BottomSheetDragHandleView>(R.id.drag_handle)
        @SuppressLint("ClickableViewAccessibility")
        dragHandle.setOnTouchListener(object: OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        mDragHandleActive = true
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        mDragHandleActive = false
                        // Call performClick() to handle the click action
                        v?.performClick()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        mDragHandleActive = false
                        return false
                    }
                }
                return false
            }
        })

        (parent as? View)?.let { view ->
            BottomSheetBehavior.from(view).let { behaviour ->
                behaviour.addBottomSheetCallback(object :
                    BottomSheetBehavior.BottomSheetCallback() {
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    }

                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        if (newState == BottomSheetBehavior.STATE_DRAGGING && mCurrentWebViewScrollY > 0 && !mDragHandleActive) {
                            // this is where we check if webview can scroll up or not and based on that we let BottomSheet close on scroll down
                            behaviour.setState(BottomSheetBehavior.STATE_EXPANDED);
                        } else if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                            close()
                        }
                    }
                })
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(readerable: Boolean) {
        val progressBar = findViewById<LinearLayout>(R.id.reader_progress)
        val webView = findViewById<ObservableWebView>(R.id.webView)
        webView.loadUrl("about:blank")

        if (readerable) {
            webView.settings.javaScriptEnabled = false
            webView.settings.domStorageEnabled = true
        }

        readabilityDoneOnce = false

        webView.settings.isAlgorithmicDarkeningAllowed = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                if (url.scheme == "http" || url.scheme == "https") {
                    val context = view?.context ?: return false
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (e: ActivityNotFoundException) {
                        // no app available to open the link
                    }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                webView.settings.javaScriptEnabled = true
                if (readerable && !readabilityDoneOnce) {
                    val js = context.resources.openRawResource(R.raw.readability)
                        .bufferedReader().use { it.readText() }
                    view?.evaluateJavascript(js, null)
                }

                progressBar.visibility = GONE
                readabilityDoneOnce = true
            }
        }

        webView.onScrollChangedCallback = object : ObservableWebView.OnScrollChangeListener {
            override fun onScrollChanged(currentHorizontalScroll: Int, currentVerticalScroll: Int,
                                         oldHorizontalScroll: Int, oldcurrentVerticalScroll: Int) {
                mCurrentWebViewScrollY = currentVerticalScroll
            }
        }
    }

    private fun setupListeners(activity: Activity) {
        mBottomSheetDialog.setOnDismissListener {
            Log.d("BottomSheetWebView", "finish")
            close()
            activity.finish()
        }
    }

    fun showWithUrl(url: String) {
        findViewById<ObservableWebView>(R.id.webView).loadUrl(url)
        mBottomSheetDialog.show()
    }

    fun close() {
        if (mDismissed) {
            return
        }

        Log.d("BottomSheetWebView", "closed")
        val webView = findViewById<ObservableWebView>(R.id.webView)
        webView.stopLoading()
        webView.destroy()
        (parent as? ViewGroup)?.removeView(this)
        mBottomSheetDialog.dismiss()
        mDismissed = true
    }
}
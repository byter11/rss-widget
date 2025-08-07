package com.byterdevs.rsswidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import androidx.core.content.edit

class RssWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_rss_widget_configure)

        // Find the widget id from the intent.
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val urlInput = findViewById<TextInputEditText>(R.id.edit_rss_url)
        val addButton = findViewById<MaterialButton>(R.id.button_add)
        val switchTitle = findViewById<SwitchMaterial>(R.id.switch_title)
        val switchDescription = findViewById<SwitchMaterial>(R.id.switch_description)
        val titleInputLayout = findViewById<TextInputLayout>(R.id.title_input_layout)
        val titleEdit = findViewById<TextInputEditText>(R.id.edit_widget_title)
        val slider = findViewById<Slider>(R.id.slider_max_items)
        val labelMaxItems = findViewById<MaterialTextView>(R.id.label_max_items)

        val sampleButtonsContainer = findViewById<LinearLayout>(R.id.sample_buttons_container)
        val inflater = LayoutInflater.from(this)
        val samples = listOf(
            Pair("Reddit", "https://www.reddit.com/r/news/.rss"),
            Pair("Hacker News", "https://hnrss.org/frontpage?link=comments"),
            Pair("BBC", "http://feeds.bbci.co.uk/news/rss.xml"),
            Pair("NY Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"),
            Pair("Guardian", "https://www.theguardian.com/world/rss"),
        )

        samples.forEach { (label, url) ->
            val btn = inflater.inflate(R.layout.item_sample_rss_button, sampleButtonsContainer, false) as MaterialButton
            btn.text = label
            btn.setOnClickListener { urlInput.setText(url) }
            btn.setLines(2)
            btn.maxLines = 2
            btn.setStrokeColorResource(android.R.color.darker_gray)
            btn.strokeWidth = resources.getDimensionPixelSize(R.dimen.sample_button_stroke_width)
            sampleButtonsContainer.addView(btn)
        }

        var maxItems = 20
        slider.value = 20f
        slider.addOnChangeListener { _, value, _ ->
            maxItems = value.toInt()
            labelMaxItems.text = "Max items to display: $maxItems"
        }

        switchTitle.setOnCheckedChangeListener { _, isChecked ->
            titleInputLayout.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        addButton.setOnClickListener {
            val url = urlInput.text?.toString()?.trim() ?: ""
            val customTitle = if (switchTitle.isChecked) titleEdit.text?.toString()?.trim() else null
            if (url.isNotEmpty()) {
                val showDescription  = switchDescription.isChecked
                // Save the URL, title, and maxItems in SharedPreferences
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit {
                    putString(PREF_PREFIX_KEY + appWidgetId, url)
                        .putString(PREF_PREFIX_KEY + "title_" + appWidgetId, customTitle)
                        .putInt(PREF_PREFIX_KEY + "max_" + appWidgetId, maxItems)
                        .putBoolean(
                            PREF_PREFIX_KEY + "description_" + appWidgetId,
                            showDescription
                        )
                }

                // Update the widget
                val context = this@RssWidgetConfigureActivity
                val appWidgetManager = AppWidgetManager.getInstance(context)
                RssWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId, url, customTitle, maxItems, showDescription)

                // Return result
                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(RESULT_OK, resultValue)
                finish()
            } else {
                urlInput.error = "Please enter a valid RSS feed URL"
            }
        }
    }

    companion object {
        const val PREFS_NAME = "com.byterdevs.rsswidget.RssWidgetProvider"
        const val PREF_PREFIX_KEY = "rss_url_"
        fun loadRssUrlPref(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        }
        fun loadTitlePref(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_PREFIX_KEY + "title_" + appWidgetId, null)
        }
        fun loadMaxItemsPref(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(PREF_PREFIX_KEY + "max_" + appWidgetId, 20)
        }
        fun loadDescriptionPref(context: Context, appWidgetId: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_PREFIX_KEY + "description_" + appWidgetId, false)
        }
    }
}

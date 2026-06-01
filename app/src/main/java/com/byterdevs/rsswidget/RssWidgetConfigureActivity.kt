package com.byterdevs.rsswidget

import androidx.appcompat.app.AppCompatActivity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import androidx.core.content.edit
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch

import com.byterdevs.rsswidget.room.RssDatabase
import com.byterdevs.rsswidget.room.RssSourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import android.widget.ImageButton
import android.widget.TextView

const val PREFS_NAME = "com.byterdevs.rsswidget.RssWidgetProvider"
const val PREF_PREFIX_KEY = "rss_url_"

class RssWidgetConfigureActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val urlInput: TextInputEditText get() = findViewById(R.id.edit_rss_url)
    private val buttonAddFeed: MaterialButton get() = findViewById(R.id.button_add_feed)
    private val sourcesContainer: LinearLayout get() = findViewById(R.id.sources_container)
    private val themeToggleGroup: MaterialButtonToggleGroup get() = findViewById(R.id.theme_toggle_group)
    private val addButton: MaterialButton get() = findViewById(R.id.button_add)
    private val titleEdit: TextInputEditText get() = findViewById(R.id.edit_widget_title)

    private val slider: Slider get() = findViewById(R.id.slider_max_items)
    private val labelMaxItems: MaterialTextView get() = findViewById(R.id.label_max_items)

    private val switchDimRead: MaterialSwitch get() = findViewById(R.id.dim_read)
    private val switchShowHeaderBar: MaterialSwitch get() = findViewById(R.id.switch_show_header_bar)
    private val switchShowTitle: MaterialSwitch get() = findViewById(R.id.switch_show_title)
    private val titleInputLayout: View get() = findViewById(R.id.title_input_layout)

    private val switchDescription: MaterialSwitch get() = findViewById(R.id.switch_description)
    private val switchImages: MaterialSwitch get() = findViewById(R.id.switch_images)
    private val switchTrimDescription: MaterialSwitch get() = findViewById(R.id.switch_trim_description)
    private val sliderTrimDescription: Slider get() = findViewById(R.id.slider_trim_description)
    private val transparencySlider: Slider get() = findViewById(R.id.slider_transparency)
    private val labelTransparency: MaterialTextView get() = findViewById(R.id.label_transparency)
    private val sampleButtonsContainer: LinearLayout get() = findViewById(R.id.sample_buttons_container)
    private val switchSource: MaterialSwitch get() = findViewById(R.id.switch_source)
    private val toggleButtonGroup: MaterialButtonToggleGroup
        get() = findViewById(
            R.id.toggle_button_group
        )
    private val updateIntervalSpinner: Spinner get() = findViewById(R.id.spinner_update_interval)
    private val openLinkSpinner: Spinner get() = findViewById(R.id.spinner_open_link_with)
    private val switchRefreshButton: MaterialSwitch get() = findViewById(R.id.show_refresh)

    private val urlSamples = listOf(
        Pair("Reddit", "https://www.reddit.com/r/news/.rss"),
        Pair("Hacker News", "https://hnrss.org/frontpage?link=comments"),
        Pair("BBC", "https://feeds.bbci.co.uk/news/rss.xml"),
        Pair("NY Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"),
        Pair("Guardian", "https://www.theguardian.com/world/rss"),
    )
    private val intervalValues = listOf(0, 15, 30, 60, 180, 360, 720) // minutes, 0 = manual


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_rss_widget_configure)

        // Find the widget id from the intent.
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val inflater = LayoutInflater.from(this)

        urlSamples.forEach { (label, url) ->
            val btn = inflater.inflate(
                R.layout.item_sample_rss_button, sampleButtonsContainer, false
            ) as MaterialButton
            btn.text = label
            btn.setOnClickListener {
                addFeedSource(url)
            }
            btn.setLines(2)
            btn.maxLines = 2
            btn.setStrokeColorResource(android.R.color.darker_gray)
            btn.strokeWidth = resources.getDimensionPixelSize(R.dimen.sample_button_stroke_width)
            sampleButtonsContainer.addView(btn)
        }

        slider.addOnChangeListener { _, value, _ ->
            labelMaxItems.text = getString(R.string.max_items_to_display, slider.value.toInt())
        }

        transparencySlider.addOnChangeListener { _, value, _ ->
            labelTransparency.text = getString(R.string.widget_transparency, value.toInt())
        }

        switchShowHeaderBar.setOnCheckedChangeListener { _, isChecked ->
            switchShowTitle.visibility = if (isChecked) View.VISIBLE else View.GONE
            titleInputLayout.visibility = if (isChecked && switchShowTitle.isChecked) View.VISIBLE else View.GONE
        }

        switchShowTitle.setOnCheckedChangeListener { _, isChecked ->
            titleInputLayout.visibility = if (isChecked && switchShowHeaderBar.isChecked) View.VISIBLE else View.GONE
        }

        buttonAddFeed.setOnClickListener {
            val url = urlInput.text?.toString()?.trim() ?: ""
            if (url.isNotEmpty()) {
                addFeedSource(url)
                urlInput.setText("")
            }
        }

        switchDescription.setOnCheckedChangeListener { _, isChecked ->
            switchTrimDescription.visibility = if (isChecked) View.VISIBLE else View.GONE
            sliderTrimDescription.visibility =
                if (isChecked && switchTrimDescription.isChecked) View.VISIBLE else View.GONE
        }

        switchTrimDescription.setOnCheckedChangeListener { _, isChecked ->
            sliderTrimDescription.visibility = if (isChecked) View.VISIBLE else View.GONE

            switchTrimDescription.text = if (isChecked) getString(
                R.string.trim_description_length, sliderTrimDescription.value.toInt()
            )
            else getString(R.string.trim_description)
        }

        sliderTrimDescription.addOnChangeListener { _, value, _ ->
            switchTrimDescription.text = getString(R.string.trim_description_length, value.toInt())
        }

        val intervalOptions = listOf(
            getString(R.string.update_manual),
            getString(R.string.update_15min),
            getString(R.string.update_30min),
            getString(R.string.update_1hr),
            getString(R.string.update_3hr),
            getString(R.string.update_6hr),
            getString(R.string.update_12hr),
        )
        val intervalAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, intervalOptions)
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        updateIntervalSpinner.adapter = intervalAdapter
        updateIntervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                if (position == 0) { // manual
                    switchRefreshButton.isChecked = true
                    switchRefreshButton.isEnabled = false
                } else {
                    switchRefreshButton.isEnabled = true
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val linkOpeningOptions = listOf(
            getString(R.string.open_links_internal),
            getString(R.string.open_links_reader),
            getString(R.string.open_links_external),
        )
        val linkAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, linkOpeningOptions)
        linkAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        openLinkSpinner.adapter = linkAdapter

        addButton.setOnClickListener {
            val customTitle = titleEdit.text?.toString()?.trim()
            val themeMode = when (themeToggleGroup.checkedButtonId) {
                R.id.btn_theme_light -> ThemeMode.LIGHT
                R.id.btn_theme_dark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }

            val prefs = WidgetPrefs(
                url = null, // No longer using single URL
                customTitle = customTitle,
                maxItems = slider.value.toInt(),
                showDescription = switchDescription.isChecked,
                showImages = switchImages.isChecked,
                descriptionLength = if (switchTrimDescription.isChecked) sliderTrimDescription.value.toInt() else -1,
                transparency = transparencySlider.value,
                showSource = switchSource.isChecked,
                dateFormat = if (toggleButtonGroup.checkedButtonId == toggleButtonGroup.getChildAt(
                        0
                    ).id
                ) "relative" else "absolute",
                updateInterval = intervalValues[updateIntervalSpinner.selectedItemPosition],
                dimReadItems = switchDimRead.isChecked,
                showRefreshButton = switchRefreshButton.isChecked,
                readerType = ReaderType.entries[openLinkSpinner.selectedItemPosition],
                themeMode = themeMode,
                showHeaderBar = switchShowHeaderBar.isChecked,
                showTitle = switchShowTitle.isChecked
            )

            applicationContext.setWidgetPrefs(appWidgetId, prefs)


            // Force refresh
            val intent = Intent("com.byterdevs.rsswidget.ACTION_REFRESH")
            intent.component = ComponentName(this, RssWidgetProvider::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            sendBroadcast(intent)

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
            finish()
        }

        restoreConfig()
    }

    private fun addFeedSource(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = RssDatabase.getInstance(applicationContext)
            db.rssSourceDao().insert(RssSourceEntity(appWidgetId = appWidgetId, url = url))
            refreshSourcesList()
        }
    }

    private fun refreshSourcesList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = RssDatabase.getInstance(applicationContext)
            val sources = db.rssSourceDao().getSourcesForWidget(appWidgetId)
            withContext(Dispatchers.Main) {
                sourcesContainer.removeAllViews()
                val inflater = LayoutInflater.from(this@RssWidgetConfigureActivity)
                sources.forEach { source ->
                    val view = inflater.inflate(R.layout.item_rss_source, sourcesContainer, false)
                    val urlText = view.findViewById<TextView>(R.id.text_url)
                    val switchEnabled = view.findViewById<MaterialSwitch>(R.id.switch_enabled)
                    val btnDelete = view.findViewById<ImageButton>(R.id.btn_delete)

                    urlText.text = source.url
                    switchEnabled.isChecked = source.isEnabled
                    switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            db.rssSourceDao().update(source.copy(isEnabled = isChecked))
                        }
                    }
                    btnDelete.setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            db.rssSourceDao().delete(source)
                            refreshSourcesList()
                        }
                    }
                    sourcesContainer.addView(view)
                }
            }
        }
    }

    private fun restoreConfig() {
        refreshSourcesList()
        val prefs = applicationContext.getWidgetPrefs(appWidgetId)
        if (!prefs.url.isNullOrEmpty()) {
            urlInput.setText(prefs.url)
        }
        if (!prefs.customTitle.isNullOrEmpty()) {
            titleEdit.setText(prefs.customTitle)
        }
        slider.value = prefs.maxItems.toFloat()
        labelMaxItems.text = getString(R.string.max_items_to_display, prefs.maxItems)
        switchDescription.isChecked = prefs.showDescription
        switchImages.isChecked = prefs.showImages
        switchDimRead.isChecked = prefs.dimReadItems
        if (prefs.showDescription) {
            switchTrimDescription.visibility = View.VISIBLE
        }
        if (prefs.showDescription && prefs.descriptionLength > 0) {
            switchTrimDescription.isChecked = true
            sliderTrimDescription.visibility = View.VISIBLE
            sliderTrimDescription.value = prefs.descriptionLength.toFloat()
            switchTrimDescription.text =
                getString(R.string.trim_description_length, prefs.descriptionLength)
        }
        transparencySlider.value = prefs.transparency
        labelTransparency.text = getString(R.string.widget_transparency, transparencySlider.value.toInt())
        switchSource.isChecked = prefs.showSource
        val relativeBtnId = toggleButtonGroup.getChildAt(0).id
        val absoluteBtnId = toggleButtonGroup.getChildAt(1).id
        toggleButtonGroup.check(if (prefs.dateFormat == "absolute") absoluteBtnId else relativeBtnId)
        val intervalIdx = intervalValues.indexOf(prefs.updateInterval)
        updateIntervalSpinner.setSelection(intervalIdx)
        switchRefreshButton.isChecked = prefs.showRefreshButton
        openLinkSpinner.setSelection(prefs.readerType.ordinal)

        switchShowHeaderBar.isChecked = prefs.showHeaderBar
        switchShowTitle.isChecked = prefs.showTitle
        
        switchShowTitle.visibility = if (prefs.showHeaderBar) View.VISIBLE else View.GONE
        titleInputLayout.visibility = if (prefs.showHeaderBar && prefs.showTitle) View.VISIBLE else View.GONE

        val themeBtnId = when (prefs.themeMode) {
            ThemeMode.LIGHT -> R.id.btn_theme_light
            ThemeMode.DARK -> R.id.btn_theme_dark
            ThemeMode.SYSTEM -> R.id.btn_theme_system
        }
        themeToggleGroup.check(themeBtnId)
    }
}

enum class ReaderType {
    INTERNAL,
    READER,
    EXTERNAL
}

data class WidgetPrefs(
    val url: String?,
    val customTitle: String?,
    val maxItems: Int,
    val showDescription: Boolean,
    val showImages: Boolean,
    val descriptionLength: Int,
    val transparency: Float,
    val showSource: Boolean,
    val dateFormat: String,
    val updateInterval: Int,
    val dimReadItems: Boolean,
    val showRefreshButton: Boolean,
    val readerType: ReaderType,
    val themeMode: ThemeMode,
    val showHeaderBar: Boolean,
    val showTitle: Boolean,
    val titleColor: Int = 0,
    val descriptionColor: Int = 0,
    val compactMode: Boolean = false,
    val tapToRefresh: Boolean = false

)

fun Context.getWidgetPrefs(appWidgetId: Int): WidgetPrefs {
    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return WidgetPrefs(
        url = prefs.getString(widgetPrefKey(appWidgetId, "url"), null),
        customTitle = prefs.getString(widgetPrefKey(appWidgetId, "title"), null),
        maxItems = prefs.getInt(widgetPrefKey(appWidgetId, "max"), 20),
        showDescription = prefs.getBoolean(widgetPrefKey(appWidgetId, "description"), false),
        showImages = prefs.getBoolean(widgetPrefKey(appWidgetId, "images"), false),
        descriptionLength = prefs.getInt(widgetPrefKey(appWidgetId, "description_length"), -1),
        transparency = prefs.getFloat(widgetPrefKey(appWidgetId, "transparency"), 100f),
        showSource = prefs.getBoolean(widgetPrefKey(appWidgetId, "source"), false),
        dateFormat = prefs.getString(widgetPrefKey(appWidgetId, "date_format"), "relative")
            ?: "relative",
        updateInterval = prefs.getInt(widgetPrefKey(appWidgetId, "update_interval"), 30),
        dimReadItems = prefs.getBoolean(widgetPrefKey(appWidgetId, "dim_read"), false),
        showRefreshButton = prefs.getBoolean(widgetPrefKey(appWidgetId, "show_refresh"), true),
        readerType = ReaderType.entries[prefs.getInt(widgetPrefKey(appWidgetId, "reader_type"), 0)],
        themeMode = ThemeMode.entries[prefs.getInt(widgetPrefKey(appWidgetId, "theme_mode"), 0)],
        showHeaderBar = prefs.getBoolean(widgetPrefKey(appWidgetId, "show_header_bar"), true),
        showTitle = prefs.getBoolean(widgetPrefKey(appWidgetId, "show_title"), true)
    )
}

fun Context.setWidgetPrefs(appWidgetId: Int, prefs: WidgetPrefs) {
    val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    sp.edit {
        putString(widgetPrefKey(appWidgetId, "url"), prefs.url)
        putString(widgetPrefKey(appWidgetId, "title"), prefs.customTitle)
        putInt(widgetPrefKey(appWidgetId, "max"), prefs.maxItems)
        putInt(widgetPrefKey(appWidgetId, "description_length"), prefs.descriptionLength)
        putBoolean(widgetPrefKey(appWidgetId, "description"), prefs.showDescription)
        putBoolean(widgetPrefKey(appWidgetId, "images"), prefs.showImages)
        putFloat(widgetPrefKey(appWidgetId, "transparency"), prefs.transparency)
        putBoolean(widgetPrefKey(appWidgetId, "source"), prefs.showSource)
        putString(widgetPrefKey(appWidgetId, "date_format"), prefs.dateFormat)
        putInt(widgetPrefKey(appWidgetId, "update_interval"), prefs.updateInterval)
        putBoolean(widgetPrefKey(appWidgetId, "dim_read"), prefs.dimReadItems)
        putBoolean(widgetPrefKey(appWidgetId, "show_refresh"), prefs.showRefreshButton)
        putInt(widgetPrefKey(appWidgetId, "reader_type"), prefs.readerType.ordinal)
        putInt(widgetPrefKey(appWidgetId, "theme_mode"), prefs.themeMode.ordinal)
        putBoolean(widgetPrefKey(appWidgetId, "show_header_bar"), prefs.showHeaderBar)
        putBoolean(widgetPrefKey(appWidgetId, "show_title"), prefs.showTitle)
    }
}

fun widgetPrefKey(appWidgetId: Int, key: String): String = PREF_PREFIX_KEY + key + "_" + appWidgetId
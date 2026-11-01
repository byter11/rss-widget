package com.byterdevs.rsswidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import androidx.core.content.edit
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

const val PREFS_NAME = "com.byterdevs.rsswidget.RssWidgetProvider"
const val PREF_PREFIX_KEY = "rss_url_"

class RssWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val urlInput: TextInputEditText get() = findViewById(R.id.edit_rss_url)
    private val buttonAddFeed: MaterialButton get() = findViewById(R.id.button_add_feed)
    private val themeToggleGroup: MaterialButtonToggleGroup get() = findViewById(R.id.theme_toggle_group)
    private val addButton: MaterialButton get() = findViewById(R.id.button_add)
    private val titleEdit: TextInputEditText get() = findViewById(R.id.edit_widget_title)

    private val slider: Slider get() = findViewById(R.id.slider_max_items)
    private val labelMaxItems: MaterialTextView get() = findViewById(R.id.label_max_items)

    private val switchDimRead: MaterialSwitch get() = findViewById(R.id.dim_read)
    private val switchCompactMode: MaterialSwitch get() = findViewById(R.id.switch_compact_mode)
    private val switchShowHeaderBar: MaterialSwitch get() = findViewById(R.id.switch_show_header_bar)
    private val switchShowRefreshButton: MaterialSwitch get() = findViewById(R.id.switch_show_refresh_button)
    private val switchDescription: MaterialSwitch get() = findViewById(R.id.switch_description)
    private val switchImages: MaterialSwitch get() = findViewById(R.id.switch_images)
    private val switchImageOnRight: MaterialSwitch get() = findViewById(R.id.switch_image_on_right)
    private val switchTrimDescription: MaterialSwitch get() = findViewById(R.id.switch_trim_description)
    private val sliderTrimDescription: Slider get() = findViewById(R.id.slider_trim_description)
    private val transparencySlider: Slider get() = findViewById(R.id.slider_transparency)
    private val labelTransparency: MaterialTextView get() = findViewById(R.id.label_transparency)
    private val fontScaleSlider: Slider get() = findViewById(R.id.slider_font_scale)
    private val labelFontScale: MaterialTextView get() = findViewById(R.id.label_font_scale)
    private val fontFamilySpinner: Spinner get() = findViewById(R.id.spinner_font_family)
    private val viewTitleColorIndicator: View get() = findViewById(R.id.view_title_color_indicator)
    private val viewDescColorIndicator: View get() = findViewById(R.id.view_desc_color_indicator)
    private val sampleButtonsContainer: LinearLayout get() = findViewById(R.id.sample_buttons_container)
    private val sourcesContainer: LinearLayout get() = findViewById(R.id.sources_container)
    private val switchSource: MaterialSwitch get() = findViewById(R.id.switch_source)
    private val toggleButtonGroup: MaterialButtonToggleGroup
        get() = findViewById(
            R.id.toggle_button_group
        )
    private val updateIntervalSpinner: Spinner get() = findViewById(R.id.spinner_update_interval)
    private val activeHoursSpinner: Spinner get() = findViewById(R.id.spinner_active_hours)
    private val openLinkSpinner: Spinner get() = findViewById(R.id.spinner_open_link_with)

    private var selectedTitleColor: Int? = null
    private var selectedDescColor: Int? = null

    private val fontFamilies = listOf(
        Pair("Default (Sans)", "sans-serif"),
        Pair("Serif", "serif"),
        Pair("Monospace", "monospace")
    )

    private val activeHoursOptions = listOf(
        Pair(R.string.active_hours_all_day, -1 to -1),
        Pair(R.string.active_hours_daytime, 6 to 22),
        Pair(R.string.active_hours_standard, 7 to 23),
        Pair(R.string.active_hours_work, 8 to 20),
        Pair(R.string.active_hours_evening, 8 to 22)
    )

    private val urlSamples = listOf(
        Pair("Reddit", "https://www.reddit.com/r/news/.rss"),
        Pair("Hacker News", "https://hnrss.org/frontpage?link=comments"),
        Pair("BBC", "https://feeds.bbci.co.uk/news/rss.xml"),
        Pair("NY Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"),
        Pair("Guardian", "https://www.theguardian.com/world/rss"),
    )
    private val intervalValues = listOf(0, 15, 30, 60, 180, 360, 720) // minutes, 0 = manual

    private val urls = mutableSetOf<String>()
    private val feedColors = mutableMapOf<String, Int?>()
    private var editingUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_rss_widget_configure)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Find the widget id from the intent.
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        urlSamples.forEach { (label, url) ->
            val btn = layoutInflater.inflate(
                R.layout.item_sample_rss_button, sampleButtonsContainer, false
            ) as MaterialButton
            btn.text = label
            btn.setOnClickListener {
                editingUrl = null
                buttonAddFeed.text = getString(R.string.add_feed)
                urlInput.setText(url)
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

        fontScaleSlider.addOnChangeListener { _, value, _ ->
            labelFontScale.text = getString(R.string.font_scale, value)
        }

        buttonAddFeed.setOnClickListener {
            val rawUrl = urlInput.text?.toString().orEmpty()
            val url = UrlUtils.sanitizeUrl(rawUrl)
            if (url.isNotEmpty()) {
                val previousUrl = editingUrl
                if (previousUrl != null && previousUrl != url) {
                    urls.remove(previousUrl)
                    val color = feedColors.remove(previousUrl)
                    if (color != null) feedColors[url] = color
                }
                urls.add(url)
                editingUrl = null
                buttonAddFeed.text = getString(R.string.add_feed)
                urlInput.setText("")
                refreshSourcesList()
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

        switchShowHeaderBar.setOnCheckedChangeListener { _, isChecked ->
            titleEdit.visibility = if (isChecked) View.VISIBLE else View.GONE
            switchShowRefreshButton.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val fontNames = fontFamilies.map { it.first }
        val fontAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fontNames)
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fontFamilySpinner.adapter = fontAdapter

        viewTitleColorIndicator.setOnClickListener {
            showColorPickerDialog(getString(R.string.title_color), selectedTitleColor) { color ->
                selectedTitleColor = color
                updateColorIndicator(viewTitleColorIndicator, color)
            }
        }

        viewDescColorIndicator.setOnClickListener {
            showColorPickerDialog(getString(R.string.description_color), selectedDescColor) { color ->
                selectedDescColor = color
                updateColorIndicator(viewDescColorIndicator, color)
            }
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

        val activeHoursAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            activeHoursOptions.map { getString(it.first) }
        )
        activeHoursAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        activeHoursSpinner.adapter = activeHoursAdapter

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
            val currentUrl = UrlUtils.sanitizeUrl(urlInput.text?.toString().orEmpty())
            if (currentUrl.isNotEmpty()) {
                val previousUrl = editingUrl
                if (previousUrl != null && previousUrl != currentUrl) {
                    urls.remove(previousUrl)
                    val color = feedColors.remove(previousUrl)
                    if (color != null) feedColors[currentUrl] = color
                }
                urls.add(currentUrl)
                editingUrl = null
            }
            val title = titleEdit.text?.toString()?.trim().orEmpty()
            if (urls.isEmpty()) {
                urlInput.error = getString(R.string.rss_feed_url)
                return@setOnClickListener
            }

            val themeMode = when (themeToggleGroup.checkedButtonId) {
                R.id.btn_theme_light -> ThemeMode.LIGHT
                R.id.btn_theme_dark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }

            val selectedActiveHours = activeHoursOptions[activeHoursSpinner.selectedItemPosition].second
            val oldPrefs = applicationContext.getWidgetPrefs(appWidgetId)
            val selectedFontFamily = fontFamilies[fontFamilySpinner.selectedItemPosition].second
            val prefs = WidgetPrefs(
                urls = urls,
                title = title,
                maxItems = slider.value.toInt(),
                showDescription = switchDescription.isChecked,
                showImages = switchImages.isChecked,
                imageOnRight = switchImageOnRight.isChecked,
                descriptionLength = if (switchTrimDescription.isChecked) sliderTrimDescription.value.toInt() else -1,
                transparency = transparencySlider.value,
                fontScale = fontScaleSlider.value,
                showSource = switchSource.isChecked,
                dateFormat = when (toggleButtonGroup.checkedButtonId) {
                    R.id.btn_date_relative -> "relative"
                    R.id.btn_date_absolute -> "absolute"
                    else -> "off"
                },
                updateInterval = intervalValues[updateIntervalSpinner.selectedItemPosition],
                dimReadItems = switchDimRead.isChecked,
                readerType = ReaderType.entries[openLinkSpinner.selectedItemPosition],
                showHeaderBar = switchShowHeaderBar.isChecked,
                themeMode = themeMode,
                compactMode = switchCompactMode.isChecked,
                feedColors = feedColors.toMap(),
                lastUpdated = oldPrefs.lastUpdated,
                fontFamily = selectedFontFamily,
                titleColor = selectedTitleColor,
                descColor = selectedDescColor,
                showRefreshButton = switchShowRefreshButton.isChecked,
                readFilterMode = oldPrefs.readFilterMode,
                activeHoursStart = selectedActiveHours.first,
                activeHoursEnd = selectedActiveHours.second
            )

            applicationContext.setWidgetPrefs(appWidgetId, prefs)
            Log.d("RssWidgetConfigure", "addButton clicked: saved prefs for id=$appWidgetId, compact=${prefs.compactMode}")


            // Force refresh
            Log.d("RssWidgetConfigure", "Sending ACTION_REFRESH broadcast")
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

    private fun refreshSourcesList() {
        sourcesContainer.removeAllViews()
        urls.forEach { source ->
            val view = layoutInflater.inflate(R.layout.item_rss_source, sourcesContainer, false)
            val urlText = view.findViewById<TextView>(R.id.text_url)
            val btnDelete = view.findViewById<ImageButton>(R.id.btn_delete)
            val colorIndicator = view.findViewById<View>(R.id.view_color_indicator)

            urlText.text = source
            urlText.setOnClickListener {
                editingUrl = source
                buttonAddFeed.text = getString(R.string.update_feed)
                urlInput.setText(source)
                urlInput.requestFocus()
                urlInput.setSelection(source.length)
            }
            btnDelete.setOnClickListener {
                if (editingUrl == source) {
                    editingUrl = null
                    buttonAddFeed.text = getString(R.string.add_feed)
                    urlInput.setText("")
                }
                urls.remove(source)
                feedColors.remove(source)
                refreshSourcesList()
            }

            val currentColor = feedColors[source]
            updateColorIndicator(colorIndicator, currentColor)

            colorIndicator.setOnClickListener {
                showColorPickerDialog(source.removePrefix("https://").removePrefix("http://").take(40), currentColor) { selectedColor ->
                    feedColors[source] = selectedColor
                    updateColorIndicator(colorIndicator, selectedColor)
                }
            }

            sourcesContainer.addView(view)
        }
    }

    private fun updateColorIndicator(view: View, color: Int?) {
        if (color != null) {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.WHITE)
            }
            view.background = drawable
        } else {
            view.setBackgroundResource(R.drawable.ic_none)
        }
    }

    private val MATERIAL_COLORS = intArrayOf(
        0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(),
        0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(),
        0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
        0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(),
        0xFF795548.toInt(), 0xFF9E9E9E.toInt(), 0xFF607D8B.toInt(), 0xFF000000.toInt()
    )

    private fun showColorPickerDialog(title: String, currentColor: Int?, onColorSelected: (Int?) -> Unit) {
        val bottomSheet = BottomSheetDialog(this)
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        // Header
        container.addView(TextView(this).apply {
            text = title
            androidx.core.widget.TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
        })

        // "None" option
        val noneLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setPadding((8 * resources.displayMetrics.density).toInt(), (12 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt(), (12 * resources.displayMetrics.density).toInt())
            setOnClickListener {
                onColorSelected(null)
                bottomSheet.dismiss()
            }
        }

        noneLayout.addView(View(this).apply {
            val size = (28 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            if (currentColor == null) {
                val ring = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
                }
                val icon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_none)!!
                val ld = LayerDrawable(arrayOf(ring, icon))
                val inset = (4 * resources.displayMetrics.density).toInt()
                ld.setLayerInset(1, inset, inset, inset, inset)
                background = ld
            } else {
                setBackgroundResource(R.drawable.ic_none)
            }
        })

        noneLayout.addView(TextView(this).apply {
            text = getString(R.string.none)
            androidx.core.widget.TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (16 * resources.displayMetrics.density).toInt()
            }
            if (currentColor == null) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        })

        container.addView(noneLayout)

        // "Colors" heading
        container.addView(TextView(this).apply {
            text = "Colors"
            androidx.core.widget.TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, (12 * resources.displayMetrics.density).toInt())
        })

        // Grid
        val gridView = GridView(this).apply {
            numColumns = 5
            horizontalSpacing = (12 * resources.displayMetrics.density).toInt()
            verticalSpacing = (12 * resources.displayMetrics.density).toInt()
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        gridView.adapter = object : BaseAdapter() {
            override fun getCount(): Int = MATERIAL_COLORS.size
            override fun getItem(position: Int): Any = MATERIAL_COLORS[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val color = MATERIAL_COLORS[position]
                val view = convertView ?: View(this@RssWidgetConfigureActivity).apply {
                    val size = (44 * resources.displayMetrics.density).toInt()
                    layoutParams = AbsListView.LayoutParams(size, size)
                }
                
                val swatch = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }

                if (color == currentColor) {
                    val ring = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
                    }
                    val ld = LayerDrawable(arrayOf(ring, swatch))
                    val inset = (6 * resources.displayMetrics.density).toInt()
                    ld.setLayerInset(1, inset, inset, inset, inset)
                    view.background = ld
                } else {
                    view.background = swatch
                }

                view.setOnClickListener {
                    onColorSelected(color)
                    bottomSheet.dismiss()
                }
                return view
            }
        }
        container.addView(gridView)

        bottomSheet.setContentView(container)
        bottomSheet.show()
    }

    private fun restoreConfig() {
        val prefs = applicationContext.getWidgetPrefs(appWidgetId)
        urls.clear()
        urls.addAll(prefs.urls)
        feedColors.clear()
        feedColors.putAll(prefs.feedColors)
        refreshSourcesList()

        if (!prefs.title.isNullOrEmpty()) {
            titleEdit.setText(prefs.title)
        }
        slider.value = prefs.maxItems.toFloat()
        labelMaxItems.text = getString(R.string.max_items_to_display, prefs.maxItems)
        switchDescription.isChecked = prefs.showDescription
        switchImages.isChecked = prefs.showImages
        switchImageOnRight.isChecked = prefs.imageOnRight
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
        fontScaleSlider.value = prefs.fontScale
        labelFontScale.text = getString(R.string.font_scale, fontScaleSlider.value)
        switchSource.isChecked = prefs.showSource
        val dateBtnId = when (prefs.dateFormat) {
            "relative" -> R.id.btn_date_relative
            "absolute" -> R.id.btn_date_absolute
            else -> R.id.btn_date_off
        }
        toggleButtonGroup.check(dateBtnId)
        val intervalIdx = intervalValues.indexOf(prefs.updateInterval)
        updateIntervalSpinner.setSelection(intervalIdx)
        val activeHoursIdx = activeHoursOptions.indexOfFirst {
            it.second.first == prefs.activeHoursStart && it.second.second == prefs.activeHoursEnd
        }.coerceAtLeast(0)
        activeHoursSpinner.setSelection(activeHoursIdx)
        openLinkSpinner.setSelection(prefs.readerType.ordinal)
        switchShowHeaderBar.isChecked = prefs.showHeaderBar
        titleEdit.visibility = if (switchShowHeaderBar.isChecked) View.VISIBLE else View.GONE
        switchShowRefreshButton.visibility = if (switchShowHeaderBar.isChecked) View.VISIBLE else View.GONE
        switchShowRefreshButton.isChecked = prefs.showRefreshButton

        val fontIdx = fontFamilies.indexOfFirst { it.second == prefs.fontFamily }.coerceAtLeast(0)
        fontFamilySpinner.setSelection(fontIdx)

        selectedTitleColor = prefs.titleColor
        updateColorIndicator(viewTitleColorIndicator, selectedTitleColor)

        selectedDescColor = prefs.descColor
        updateColorIndicator(viewDescColorIndicator, selectedDescColor)

        val themeBtnId = when (prefs.themeMode) {
            ThemeMode.LIGHT -> R.id.btn_theme_light
            ThemeMode.DARK -> R.id.btn_theme_dark
            ThemeMode.SYSTEM -> R.id.btn_theme_system
        }
        themeToggleGroup.check(themeBtnId)
        switchCompactMode.isChecked = prefs.compactMode
    }
}

enum class ReaderType {
    INTERNAL,
    READER,
    EXTERNAL
}

enum class ReadFilterMode {
    SHOW,
    DIM,
    HIDE
}

data class WidgetPrefs(
    val urls: Set<String>,
    val title: String,
    val maxItems: Int,
    val showDescription: Boolean,
    val showImages: Boolean,
    val imageOnRight: Boolean = false,
    val descriptionLength: Int,
    val transparency: Float,
    val fontScale: Float = 1.0f,
    val showSource: Boolean,
    val dateFormat: String,
    val updateInterval: Int,
    val dimReadItems: Boolean,
    val readerType: ReaderType,
    val showHeaderBar: Boolean = true,
    val themeMode: ThemeMode,
    val compactMode: Boolean = false,
    val lastUpdated: Long = 0L,
    val feedColors: Map<String, Int?> = emptyMap(),
    val fontFamily: String = "sans-serif",
    val titleColor: Int? = null,
    val descColor: Int? = null,
    val showRefreshButton: Boolean = true,
    val readFilterMode: ReadFilterMode = ReadFilterMode.SHOW,
    val activeHoursStart: Int = -1,
    val activeHoursEnd: Int = -1,
)

fun Context.getWidgetPrefs(appWidgetId: Int): WidgetPrefs {
    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val urls = prefs.getStringSet(widgetPrefKey(appWidgetId, "url"), null).orEmpty()
    val feedColors = urls.associateWith { url ->
        val color = prefs.getInt(widgetPrefKey(appWidgetId, "color_$url"), Int.MIN_VALUE)
        if (color == Int.MIN_VALUE) null else color
    }
    val titleColorVal = prefs.getInt(widgetPrefKey(appWidgetId, "title_color"), Int.MIN_VALUE)
    val titleColor = if (titleColorVal == Int.MIN_VALUE) null else titleColorVal
    val descColorVal = prefs.getInt(widgetPrefKey(appWidgetId, "desc_color"), Int.MIN_VALUE)
    val descColor = if (descColorVal == Int.MIN_VALUE) null else descColorVal
    val savedFilterOrdinal = prefs.getInt(widgetPrefKey(appWidgetId, "read_filter_mode"), -1)
    val readFilterMode = if (savedFilterOrdinal in ReadFilterMode.entries.indices) {
        ReadFilterMode.entries[savedFilterOrdinal]
    } else {
        if (prefs.getBoolean(widgetPrefKey(appWidgetId, "dim_read"), false)) ReadFilterMode.DIM else ReadFilterMode.SHOW
    }

    return WidgetPrefs(
        urls = urls,
        title = prefs.getString(widgetPrefKey(appWidgetId, "title"), "HomeFeed").orEmpty(),
        maxItems = prefs.getInt(widgetPrefKey(appWidgetId, "max"), 20),
        showDescription = prefs.getBoolean(widgetPrefKey(appWidgetId, "description"), false),
        showImages = prefs.getBoolean(widgetPrefKey(appWidgetId, "images"), false),
        imageOnRight = prefs.getBoolean(widgetPrefKey(appWidgetId, "image_on_right"), false),
        descriptionLength = prefs.getInt(widgetPrefKey(appWidgetId, "description_length"), -1),
        transparency = prefs.getFloat(widgetPrefKey(appWidgetId, "transparency"), 100f),
        fontScale = prefs.getFloat(widgetPrefKey(appWidgetId, "font_scale"), 1.0f),
        showSource = prefs.getBoolean(widgetPrefKey(appWidgetId, "source"), urls.size > 1),
        dateFormat = prefs.getString(widgetPrefKey(appWidgetId, "date_format"), "relative")
            ?: "relative",
        updateInterval = prefs.getInt(widgetPrefKey(appWidgetId, "update_interval"), 30),
        dimReadItems = prefs.getBoolean(widgetPrefKey(appWidgetId, "dim_read"), false),
        readerType = ReaderType.entries[prefs.getInt(widgetPrefKey(appWidgetId, "reader_type"), 0)],
        showHeaderBar = prefs.getBoolean(widgetPrefKey(appWidgetId, "show_header_bar"), true),
        themeMode = ThemeMode.entries[prefs.getInt(widgetPrefKey(appWidgetId, "theme_mode"), 0)],
        compactMode = prefs.getBoolean(widgetPrefKey(appWidgetId, "compact_mode"), false),
        lastUpdated = prefs.getLong(widgetPrefKey(appWidgetId, "last_updated"), 0L),
        feedColors = feedColors,
        fontFamily = prefs.getString(widgetPrefKey(appWidgetId, "font_family"), "sans-serif") ?: "sans-serif",
        titleColor = titleColor,
        descColor = descColor,
        showRefreshButton = prefs.getBoolean(widgetPrefKey(appWidgetId, "show_refresh_button"), true),
        readFilterMode = readFilterMode,
        activeHoursStart = prefs.getInt(widgetPrefKey(appWidgetId, "active_hours_start"), -1),
        activeHoursEnd = prefs.getInt(widgetPrefKey(appWidgetId, "active_hours_end"), -1)
    )
}

fun Context.setWidgetPrefs(appWidgetId: Int, prefs: WidgetPrefs) {
    Log.d("RssWidgetPrefs", "setWidgetPrefs: id=$appWidgetId, compact=${prefs.compactMode}")
    val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    sp.edit(commit = true) {
        putStringSet(widgetPrefKey(appWidgetId, "url"), prefs.urls)
        putString(widgetPrefKey(appWidgetId, "title"), prefs.title)
        putInt(widgetPrefKey(appWidgetId, "max"), prefs.maxItems)
        putInt(widgetPrefKey(appWidgetId, "description_length"), prefs.descriptionLength)
        putBoolean(widgetPrefKey(appWidgetId, "description"), prefs.showDescription)
        putBoolean(widgetPrefKey(appWidgetId, "images"), prefs.showImages)
        putBoolean(widgetPrefKey(appWidgetId, "image_on_right"), prefs.imageOnRight)
        putFloat(widgetPrefKey(appWidgetId, "transparency"), prefs.transparency)
        putFloat(widgetPrefKey(appWidgetId, "font_scale"), prefs.fontScale)
        putBoolean(widgetPrefKey(appWidgetId, "source"), prefs.showSource)
        putString(widgetPrefKey(appWidgetId, "date_format"), prefs.dateFormat)
        putInt(widgetPrefKey(appWidgetId, "update_interval"), prefs.updateInterval)
        putBoolean(widgetPrefKey(appWidgetId, "dim_read"), prefs.dimReadItems)
        putInt(widgetPrefKey(appWidgetId, "reader_type"), prefs.readerType.ordinal)
        putBoolean(widgetPrefKey(appWidgetId, "show_header_bar"), prefs.showHeaderBar)
        putInt(widgetPrefKey(appWidgetId, "theme_mode"), prefs.themeMode.ordinal)
        putBoolean(widgetPrefKey(appWidgetId, "compact_mode"), prefs.compactMode)
        putLong(widgetPrefKey(appWidgetId, "last_updated"), prefs.lastUpdated)
        putString(widgetPrefKey(appWidgetId, "font_family"), prefs.fontFamily)
        if (prefs.titleColor != null) {
            putInt(widgetPrefKey(appWidgetId, "title_color"), prefs.titleColor)
        } else {
            remove(widgetPrefKey(appWidgetId, "title_color"))
        }
        if (prefs.descColor != null) {
            putInt(widgetPrefKey(appWidgetId, "desc_color"), prefs.descColor)
        } else {
            remove(widgetPrefKey(appWidgetId, "desc_color"))
        }
        putBoolean(widgetPrefKey(appWidgetId, "show_refresh_button"), prefs.showRefreshButton)
        putInt(widgetPrefKey(appWidgetId, "read_filter_mode"), prefs.readFilterMode.ordinal)
        putInt(widgetPrefKey(appWidgetId, "active_hours_start"), prefs.activeHoursStart)
        putInt(widgetPrefKey(appWidgetId, "active_hours_end"), prefs.activeHoursEnd)

        // Clear old colors? Or just overwrite.
        prefs.feedColors.forEach { (url, color) ->
            if (color != null) {
                putInt(widgetPrefKey(appWidgetId, "color_$url"), color)
            } else {
                remove(widgetPrefKey(appWidgetId, "color_$url"))
            }
        }
    }
}

fun Context.deleteWidgetPrefs(appWidgetId: Int) {
    Log.d("RssWidgetPrefs", "deleteWidgetPrefs: id=$appWidgetId")
    val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val prefs = getWidgetPrefs(appWidgetId)
    sp.edit(commit = true) {
        remove(widgetPrefKey(appWidgetId, "url"))
        remove(widgetPrefKey(appWidgetId, "title"))
        remove(widgetPrefKey(appWidgetId, "max"))
        remove(widgetPrefKey(appWidgetId, "description_length"))
        remove(widgetPrefKey(appWidgetId, "description"))
        remove(widgetPrefKey(appWidgetId, "images"))
        remove(widgetPrefKey(appWidgetId, "image_on_right"))
        remove(widgetPrefKey(appWidgetId, "transparency"))
        remove(widgetPrefKey(appWidgetId, "font_scale"))
        remove(widgetPrefKey(appWidgetId, "source"))
        remove(widgetPrefKey(appWidgetId, "date_format"))
        remove(widgetPrefKey(appWidgetId, "update_interval"))
        remove(widgetPrefKey(appWidgetId, "dim_read"))
        remove(widgetPrefKey(appWidgetId, "reader_type"))
        remove(widgetPrefKey(appWidgetId, "show_header_bar"))
        remove(widgetPrefKey(appWidgetId, "theme_mode"))
        remove(widgetPrefKey(appWidgetId, "compact_mode"))
        remove(widgetPrefKey(appWidgetId, "last_updated"))
        remove(widgetPrefKey(appWidgetId, "font_family"))
        remove(widgetPrefKey(appWidgetId, "title_color"))
        remove(widgetPrefKey(appWidgetId, "desc_color"))
        remove(widgetPrefKey(appWidgetId, "show_refresh_button"))
        remove(widgetPrefKey(appWidgetId, "read_filter_mode"))
        remove(widgetPrefKey(appWidgetId, "active_hours_start"))
        remove(widgetPrefKey(appWidgetId, "active_hours_end"))

        prefs.urls.forEach { url ->
            remove(widgetPrefKey(appWidgetId, "color_$url"))
        }
    }
}

private fun widgetPrefKey(appWidgetId: Int, key: String): String {
    return PREF_PREFIX_KEY + appWidgetId + "_" + key
}

package com.tamay.iptv

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.PlayerView
import com.tamay.iptv.data.IptvChannel
import com.tamay.iptv.data.ContentType
import com.tamay.iptv.data.M3uParser
import com.tamay.iptv.player.StreamPlayer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object { private const val MAX_PLAYLIST_BYTES = 128L * 1024L * 1024L }

    private val io = Executors.newSingleThreadExecutor()
    private val channels = mutableListOf<IptvChannel>()
    private val visibleChannels = mutableListOf<IptvChannel>()
    private val favorites = mutableSetOf<String>()
    private val preferences by lazy { getSharedPreferences("tamay_iptv", MODE_PRIVATE) }

    private lateinit var root: LinearLayout
    private lateinit var topPanel: LinearLayout
    private lateinit var playerContainer: FrameLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var playerView: PlayerView
    private lateinit var urlInput: EditText
    private lateinit var searchInput: EditText
    private lateinit var status: TextView
    private lateinit var channelList: ListView
    private lateinit var categorySpinner: Spinner
    private lateinit var countrySpinner: Spinner
    private lateinit var favoritesButton: Button
    private lateinit var subtitleButton: Button
    private lateinit var streamPlayer: StreamPlayer
    private lateinit var channelAdapter: ChannelAdapter

    private var selectedCategory = "Tüm kategoriler"
    private var selectedCountry = "Tüm ülkeler"
    private var selectedContent = "LIVE"
    private var showingFavoritesOnly = false
    private var fullScreen = false
    private var currentChannelIndex = -1

    private val openM3uFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        loadLocalFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 17, 22)
        window.navigationBarColor = Color.rgb(12, 17, 22)
        favorites.addAll(preferences.getStringSet("favorite_streams", emptySet()).orEmpty())
        buildInterface()
        streamPlayer = StreamPlayer(this, playerView) { message -> runOnUiThread { status.text = "Yayın hatası: $message" } }
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 17, 22))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        topPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "TAMAY IPTV  •  LIVE TV"
            textSize = 20f; setTextColor(Color.rgb(45, 212, 191)); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, dp(4))
        }
        topPanel.addView(title, LinearLayout.LayoutParams(-1, dp(38)))

        urlInput = EditText(this).apply {
            hint = "M3U / M3U8 bağlantısı"; setSingleLine(true); textSize = 14f
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); setPadding(dp(10), 0, dp(10), 0)
        }
        topPanel.addView(urlInput, LinearLayout.LayoutParams(-1, dp(44)))
        val sourceButtons = horizontalRow()
        sourceButtons.addView(actionButton("URL YÜKLE") { loadPlaylistUrl(urlInput.text.toString().trim()) }, weighted())
        sourceButtons.addView(actionButton("DOSYA AÇ") {
            openM3uFile.launch(arrayOf("audio/x-mpegurl", "application/x-mpegURL", "text/plain", "application/octet-stream"))
        }, weighted())
        topPanel.addView(sourceButtons)
        status = TextView(this).apply {
            text = "M3U bağlantısı girin veya dosya seçin."; textSize = 12f; setTextColor(Color.LTGRAY)
            setPadding(dp(4), dp(4), dp(4), dp(4)); maxLines = 2
        }
        topPanel.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(topPanel, LinearLayout.LayoutParams(-1, -2))

        playerContainer = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = false
            setBackgroundColor(Color.BLACK)
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerContainer.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        playerView.setOnClickListener { if (fullScreen) setFullScreen(false) }
        playerContainer.setOnClickListener { if (fullScreen) setFullScreen(false) }
        root.addView(playerContainer, LinearLayout.LayoutParams(-1, 0, 5.2f))

        bottomPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val playbackControls = horizontalRow()
        playbackControls.addView(actionButton("◀ ÖNCEKİ") { playRelative(-1) }, weighted())
        subtitleButton = actionButton("CC ALTYAZI") { toggleSubtitles() }
        playbackControls.addView(subtitleButton, weighted())
        playbackControls.addView(actionButton("SONRAKİ ▶") { playRelative(1) }, weighted())
        bottomPanel.addView(playbackControls)

        val utilityControls = horizontalRow()
        utilityControls.addView(actionButton("KANAL REHBERİ") { showChannelGuide() }, weighted())
        utilityControls.addView(actionButton("TAM EKRAN") { setFullScreen(true) }, weighted())
        bottomPanel.addView(utilityControls)

        val contentTabs = horizontalRow()
        fun tab(label: String, key: String) = actionButton(label) {
            selectedContent = key
            selectedCategory = "Tüm kategoriler"
            selectedCountry = "Tüm ülkeler"
            if (::categorySpinner.isInitialized) categorySpinner.setSelection(0)
            if (::countrySpinner.isInitialized) countrySpinner.setSelection(0)
            status.text = when (key) {
                "LIVE" -> "Canlı yayınlar"
                "MOVIE" -> "Filmler"
                "SERIES" -> "Diziler"
                "COUNTRY" -> "Ülkelere göre içerikler"
                else -> "Tüm içerikler"
            }
            applyFilters()
        }
        contentTabs.addView(tab("CANLI TV", "LIVE"), weighted())
        contentTabs.addView(tab("FİLMLER", "MOVIE"), weighted())
        contentTabs.addView(tab("DİZİLER", "SERIES"), weighted())
        contentTabs.addView(tab("ÜLKELER", "COUNTRY"), weighted())
        bottomPanel.addView(contentTabs)

        searchInput = EditText(this).apply {
            hint = "Kanal ara…"; setSingleLine(true); textSize = 14f
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); setPadding(dp(10), 0, dp(10), 0)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilters()
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        bottomPanel.addView(searchInput, LinearLayout.LayoutParams(-1, dp(42)))
        val filters = horizontalRow()
        categorySpinner = Spinner(this)
        favoritesButton = actionButton("☆ FAVORİLER") {
            showingFavoritesOnly = !showingFavoritesOnly
            favoritesButton.text = if (showingFavoritesOnly) "★ TÜM KANALLAR" else "☆ FAVORİLER"
            applyFilters()
        }
        filters.addView(categorySpinner, weighted())
        favoritesButton.text = "☆ FAVORİLER"
        filters.addView(favoritesButton, weighted())
        bottomPanel.addView(filters)

        countrySpinner = Spinner(this)
        bottomPanel.addView(countrySpinner, LinearLayout.LayoutParams(-1, dp(42)))

        channelList = ListView(this).apply { dividerHeight = dp(1); setBackgroundColor(Color.rgb(22, 29, 35)) }
        channelAdapter = ChannelAdapter()
        channelList.adapter = channelAdapter
        channelList.setOnItemClickListener { _, _, position, _ -> visibleChannels.getOrNull(position)?.let(::playChannel) }
        bottomPanel.addView(channelList, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomPanel, LinearLayout.LayoutParams(-1, 0, 4.8f))
        setContentView(root)
    }

    private fun horizontalRow() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun weighted() = LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 11f; isAllCaps = false; setPadding(dp(3), 0, dp(3), 0); setOnClickListener { action() }
    }

    private fun setFullScreen(enabled: Boolean) {
        fullScreen = enabled
        topPanel.visibility = if (enabled) View.GONE else View.VISIBLE
        bottomPanel.visibility = if (enabled) View.GONE else View.VISIBLE
        val params = playerContainer.layoutParams as LinearLayout.LayoutParams
        params.height = if (enabled) -1 else 0
        params.weight = if (enabled) 0f else 5.2f
        playerContainer.layoutParams = params
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        root.requestLayout()
    }

    private fun toggleSubtitles() {
        val enabled = streamPlayer.toggleSubtitles()
        subtitleButton.text = if (enabled) "CC AÇIK" else "CC KAPALI"
        status.text = if (enabled) "Altyazı izi etkinleştirildi (yayında altyazı varsa görünür)." else "Altyazı kapatıldı."
    }

    private fun playRelative(delta: Int) {
        if (channels.isEmpty()) return
        val base = currentChannelIndex.takeIf { it >= 0 } ?: 0
        val next = (base + delta).coerceIn(0, channels.lastIndex)
        playChannel(channels[next])
    }

    private fun showChannelGuide() {
        if (channels.isEmpty()) { status.text = "Önce bir M3U listesi yükleyin."; return }
        val labels = channels.mapIndexed { index, channel -> "${index + 1}. ${channel.name}  •  ${channel.groupTitle}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("KANAL REHBERİ • ${channels.size} KANAL")
            .setItems(labels) { _, which -> playChannel(channels[which]) }
            .setNegativeButton("KAPAT", null).show()
    }

    private fun playChannel(channel: IptvChannel) {
        currentChannelIndex = channels.indexOfFirst { it.streamUrl == channel.streamUrl }
        status.text = "Yayın başlatılıyor: ${channel.name}"
        try { streamPlayer.play(channel.streamUrl) }
        catch (e: Exception) { status.text = "Yayın başlatılamadı: ${e.localizedMessage ?: "Geçersiz yayın adresi"}" }
    }

    private fun loadPlaylistUrl(value: String) {
        if (value.isBlank() || !(value.startsWith("https://") || value.startsWith("http://"))) {
            status.text = "Geçerli bir http:// veya https:// M3U adresi girin."; return
        }
        status.text = "Oynatma listesi indiriliyor…"
        io.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(value).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000; readTimeout = 20000; instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "TAMAY-IPTV/0.4 Android")
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("Sunucu HTTP $code yanıtı verdi")
                if (connection.contentLengthLong > MAX_PLAYLIST_BYTES) throw IOException("Liste 128 MB sınırını aşıyor")
                val text = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
                    while (true) { val count = input.read(buffer); if (count < 0) break; total += count
                        if (total > MAX_PLAYLIST_BYTES) throw IOException("Liste 128 MB sınırını aştı")
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
                if (text.contains("#EXT-X-", true) && !text.contains("#EXTINF", true)) runOnUiThread {
                    status.text = "HLS yayını açılıyor…"; streamPlayer.play(value)
                } else showParsedPlaylist(text)
            } catch (e: Exception) { runOnUiThread { status.text = "Liste yüklenemedi: ${e.localizedMessage ?: "Bağlantı hatası"}" } }
            finally { connection?.disconnect() }
        }
    }

    private fun loadLocalFile(uri: Uri) {
        status.text = "Dosya okunuyor…"
        io.execute {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Dosya açılamadı")
                showParsedPlaylist(text)
            } catch (e: Exception) { runOnUiThread { status.text = "Dosya okunamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}" } }
        }
    }

    private fun showParsedPlaylist(text: String) {
        val parsed = M3uParser.parse(text)
        runOnUiThread {
            channels.clear(); channels.addAll(parsed)
            val categories = listOf("Tüm kategoriler") + parsed.map { it.groupTitle }.distinct().sorted()
            categorySpinner.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, categories).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedCategory = categories.getOrElse(position) { "Tüm kategoriler" }; applyFilters()
                }
            }
            val countries = listOf("Tüm ülkeler") + parsed.map { it.country }.distinct().sorted()
            countrySpinner.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, countries).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            countrySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedCountry = countries.getOrElse(position) { "Tüm ülkeler" }; applyFilters()
                }
            }
            selectedCategory = "Tüm kategoriler"
            selectedCountry = "Tüm ülkeler"
            selectedContent = "LIVE"
            applyFilters()
            val live = parsed.count { it.contentType == ContentType.LIVE }
            val movies = parsed.count { it.contentType == ContentType.MOVIE }
            val series = parsed.count { it.contentType == ContentType.SERIES }
            status.text = if (parsed.isEmpty()) "Kanal bulunamadı. M3U formatını kontrol edin." else
                "${parsed.size} içerik yüklendi • Canlı: $live • Film: $movies • Dizi: $series"
        }
    }

    private fun applyFilters() {
        if (!::channelAdapter.isInitialized) return
        val query = if (::searchInput.isInitialized) searchInput.text.toString().trim() else ""
        val filtered = channels.filter { c ->
            val contentMatches = when (selectedContent) {
                "LIVE" -> c.contentType == ContentType.LIVE || c.contentType == ContentType.UNKNOWN
                "MOVIE" -> c.contentType == ContentType.MOVIE
                "SERIES" -> c.contentType == ContentType.SERIES
                "COUNTRY" -> true
                else -> true
            }
            contentMatches &&
                (selectedCategory == "Tüm kategoriler" || c.groupTitle == selectedCategory) &&
                (selectedCountry == "Tüm ülkeler" || c.country == selectedCountry) &&
                (query.isBlank() || c.name.contains(query, true) || c.groupTitle.contains(query, true) || c.country.contains(query, true)) &&
                (!showingFavoritesOnly || favorites.contains(c.streamUrl))
        }
        visibleChannels.clear(); visibleChannels.addAll(filtered); channelAdapter.notifyDataSetChanged()
    }

    private fun toggleFavorite(channel: IptvChannel) {
        if (!favorites.add(channel.streamUrl)) favorites.remove(channel.streamUrl)
        preferences.edit().putStringSet("favorite_streams", favorites.toSet()).apply(); applyFilters()
    }

    private inner class ChannelAdapter : BaseAdapter() {
        override fun getCount() = visibleChannels.size
        override fun getItem(position: Int) = visibleChannels[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val channel = getItem(position)
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(5), dp(8), dp(5)); minimumHeight = dp(54)
                setBackgroundColor(Color.rgb(22, 29, 35)); isClickable = true; isFocusable = true
            }
            row.setOnClickListener { playChannel(channel) }; row.removeAllViews()
            val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this@MainActivity).apply { text = channel.name; textSize = 14f; setTextColor(Color.WHITE); maxLines = 1 })
            labels.addView(TextView(this@MainActivity).apply { text = channel.groupTitle; textSize = 10f; setTextColor(Color.LTGRAY); maxLines = 1 })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this@MainActivity).apply {
                text = if (favorites.contains(channel.streamUrl)) "★" else "☆"; isFocusable = false
                setOnClickListener { toggleFavorite(channel) }
            }, LinearLayout.LayoutParams(dp(52), dp(44)))
            return row
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() { if (::streamPlayer.isInitialized) streamPlayer.release(); io.shutdownNow(); super.onDestroy() }
}

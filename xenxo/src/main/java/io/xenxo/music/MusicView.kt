package io.xenxo.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import io.xenxo.ui.XTheme
import java.io.File
import java.util.Locale

/**
 * Vista principal de "Música xenxo".
 * Estética openGym (fondo negro puro AMOLED, tarjetas #1C1C1E, acentos rosa/magenta iOS #FF2D55).
 * Permite reproducir música offline en AirPods vinculados por Bluetooth directamente al reloj,
 * gestionar la biblioteca rotativa de 20 canciones y regular el volumen con la corona giratoria.
 */
class MusicView(context: Context) : FrameLayout(context), MusicPlayer.Listener {

    private enum class Mode { PLAYER, LIST }

    private val player = MusicPlayer.get(context)
    private val store = MusicStore.get(context)
    private val density = resources.displayMetrics.density
    private val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var currentMode = Mode.PLAYER

    // Contenedores principales
    private val playerContainer: LinearLayout
    private val listContainer: LinearLayout
    private val listScrollView: ScrollView
    private val listItemsLayout: LinearLayout

    // Elementos del Reproductor
    private val headerStoragePill: TextView
    private val coverArtCard: FrameLayout
    private val trackTitleText: TextView
    private val trackArtistText: TextView
    private val progressBar: ProgressBar
    private val timeCurrentText: TextView
    private val timeTotalText: TextView
    private val btnPrev: TextView
    private val btnPlayPause: TextView
    private val btnNext: TextView
    private val audioOutputPill: TextView

    // Overlay HUD de volumen (se muestra al girar la corona)
    private val volumeHud: TextView
    private val hudHandler = Handler(Looper.getMainLooper())
    private val hideHudRunnable = Runnable { volumeHud.visibility = View.GONE }

    // Scroll con ruleta en la lista
    private var rotaryAccumulator = 0f
    private val rotaryThreshold = 35f * density

    init {
        setBackgroundColor(XTheme.BG_BLACK)

        // ==========================================
        // 1. MODO REPRODUCTOR (NOW PLAYING)
        // ==========================================
        playerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            layoutParams = LayoutParams(-1, -1)
        }

        // Barra superior: Título y píldora de biblioteca / canciones
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (4 * density).toInt()
            }
        }

        val appTitle = TextView(context).apply {
            text = "MÚSICA XENXO"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(XTheme.TEXT_SECONDARY)
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        headerStoragePill = TextView(context).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(XTheme.PINK)
            gravity = Gravity.CENTER
            minWidth = (54 * density).toInt()
            isClickable = true
            isFocusable = true
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            background = XTheme.roundRect(XTheme.CARD_BG, 12f, XTheme.CARD_STROKE, 1f, density)
            setOnClickListener {
                vibrateTick()
                showLibrary()
            }
        }

        topBar.addView(appTitle)
        topBar.addView(headerStoragePill)
        playerContainer.addView(topBar)

        // Carátula / Badge squircle openGym (50x50 dp)
        coverArtCard = FrameLayout(context).apply {
            val size = (50 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = (4 * density).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(XTheme.CARD_BG)
                setStroke((1.5f * density).toInt(), XTheme.PINK)
            }
            isClickable = true
            isFocusable = true

            val icon = ImageView(context).apply {
                setImageResource(context.resources.getIdentifier("ic_music", "drawable", context.packageName))
                val iconSize = (32 * density).toInt()
                layoutParams = LayoutParams(iconSize, iconSize, Gravity.CENTER)
                isClickable = false
                isFocusable = false
            }
            addView(icon)

            setOnClickListener {
                vibrateTick()
                showLibrary()
            }
        }
        playerContainer.addView(coverArtCard)

        // Título de la canción
        trackTitleText = TextView(context).apply {
            text = "Sin canciones"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(XTheme.TEXT_PRIMARY)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (1 * density).toInt()
            }
        }
        playerContainer.addView(trackTitleText)

        // Artista / Info
        trackArtistText = TextView(context).apply {
            text = "Toca para abrir la biblioteca"
            textSize = 11f
            setTextColor(XTheme.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (6 * density).toInt()
            }
        }
        playerContainer.addView(trackArtistText)

        // Barra de progreso openGym
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 1000
            progress = 0
            val h = (3.5f * density).toInt()
            layoutParams = LinearLayout.LayoutParams((170 * density).toInt(), h).apply {
                bottomMargin = (2 * density).toInt()
            }
            progressDrawable = GradientDrawable().apply {
                cornerRadius = 2 * density
                setColor(XTheme.CARD_BG)
            }
        }
        playerContainer.addView(progressBar)

        // Tiempos (actual y restante)
        val timeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams((170 * density).toInt(), -2).apply {
                bottomMargin = (6 * density).toInt()
            }
        }
        timeCurrentText = TextView(context).apply {
            text = "0:00"
            textSize = 10f
            setTextColor(XTheme.TEXT_SECONDARY)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        timeTotalText = TextView(context).apply {
            text = "--:--"
            textSize = 10f
            setTextColor(XTheme.TEXT_SECONDARY)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        timeRow.addView(timeCurrentText)
        timeRow.addView(timeTotalText)
        playerContainer.addView(timeRow)

        // Fila de controles de transporte (⏮  ▶  ⏭)
        val controlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (4 * density).toInt()
            }
        }

        btnPrev = TextView(context).apply {
            text = "⏮"
            textSize = 15f
            gravity = Gravity.CENTER
            val s = (36 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                rightMargin = (14 * density).toInt()
            }
            background = XTheme.circleButtonDrawable(density, XTheme.SURFACE_BTN, XTheme.SURFACE_BTN_PRESS)
            setTextColor(XTheme.TEXT_PRIMARY)
            setOnClickListener {
                vibrateTick()
                if (player.currentTrack == null) {
                    val tracks = store.getAllTracks()
                    if (tracks.isNotEmpty()) player.playPlaylist(tracks, 0)
                } else {
                    player.previous()
                }
            }
        }

        btnPlayPause = TextView(context).apply {
            text = "▶"
            textSize = 20f
            gravity = Gravity.CENTER
            val s = (48 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s)
            background = XTheme.circleButtonDrawable(density, XTheme.PINK, XTheme.PINK_DARK)
            setTextColor(Color.WHITE)
            isClickable = true
            setOnClickListener {
                vibrateTick()
                val count = store.getTrackCount()
                if (count == 0) {
                    // Si no hay canciones, abrir biblioteca
                    switchMode(Mode.LIST)
                } else if (player.currentTrack == null) {
                    val tracks = store.getAllTracks()
                    if (tracks.isNotEmpty()) {
                        player.playPlaylist(tracks, 0)
                    }
                } else {
                    player.togglePlayPause()
                }
            }
        }

        btnNext = TextView(context).apply {
            text = "⏭"
            textSize = 15f
            gravity = Gravity.CENTER
            val s = (36 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                leftMargin = (14 * density).toInt()
            }
            background = XTheme.circleButtonDrawable(density, XTheme.SURFACE_BTN, XTheme.SURFACE_BTN_PRESS)
            setTextColor(XTheme.TEXT_PRIMARY)
            isClickable = true
            setOnClickListener {
                vibrateTick()
                if (player.currentTrack == null) {
                    val tracks = store.getAllTracks()
                    if (tracks.isNotEmpty()) player.playPlaylist(tracks, 0)
                } else {
                    player.next()
                }
            }
        }

        controlsRow.addView(btnPrev)
        controlsRow.addView(btnPlayPause)
        controlsRow.addView(btnNext)
        playerContainer.addView(controlsRow)

        // Píldora de salida de audio / AirPods
        audioOutputPill = TextView(context).apply {
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (3 * density).toInt(), (10 * density).toInt(), (3 * density).toInt())
            background = XTheme.roundRect(XTheme.CARD_BG, 12f, XTheme.CARD_STROKE, 1f, density)
            setTextColor(XTheme.TEXT_SECONDARY)
            setOnClickListener {
                vibrateTick()
                // Abrir pantalla de ajustes Bluetooth del reloj para conectar AirPods
                try {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                } catch (_: Exception) {}
            }
        }
        playerContainer.addView(audioOutputPill)

        addView(playerContainer)

        // ==========================================
        // 2. MODO BIBLIOTECA (LISTA DE 20 CANCIONES)
        // ==========================================
        listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(XTheme.BG_BLACK)
            setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            layoutParams = LayoutParams(-1, -1)
            visibility = View.GONE
        }

        // Cabecera de la biblioteca
        val listTopBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (8 * density).toInt()
            }
        }

        val btnBackToPlayer = TextView(context).apply {
            text = "◀ Volver"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(XTheme.PINK)
            setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
            background = XTheme.roundRect(XTheme.CARD_BG, 12f, XTheme.CARD_STROKE, 1f, density)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                vibrateTick()
                switchMode(Mode.PLAYER)
            }
        }

        val listTitle = TextView(context).apply {
            text = "BIBLIOTECA"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(XTheme.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        listTopBar.addView(btnBackToPlayer)
        listTopBar.addView(listTitle)
        listContainer.addView(listTopBar)

        // Banner informativo de auto-renovación
        val autoRenewalBanner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            background = XTheme.roundRect(XTheme.CARD_BG, 10f, null, 0f, density)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (8 * density).toInt()
            }

            val t1 = TextView(context).apply {
                text = "🔄 Auto-renovación activa (Máx 20)"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(XTheme.TEXT_PRIMARY)
            }
            val t2 = TextView(context).apply {
                text = "Al guardar una nueva canción se borra la más antigua para mantener libre la memoria del reloj."
                textSize = 10f
                setTextColor(XTheme.TEXT_SECONDARY)
            }
            addView(t1)
            addView(t2)
        }
        listContainer.addView(autoRenewalBanner)

        // ScrollView con las canciones
        listScrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            isVerticalScrollBarEnabled = false
        }
        listItemsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -2)
        }
        listScrollView.addView(listItemsLayout)
        listContainer.addView(listScrollView)

        addView(listContainer)

        // ==========================================
        // 3. OVERLAY HUD DE VOLUMEN (CORONA)
        // ==========================================
        volumeHud = TextView(context).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(0xDD1C1C1E.toInt())
                setStroke((1 * density).toInt(), XTheme.CARD_STROKE)
            }
            val lp = LayoutParams(-2, -2, Gravity.CENTER)
            layoutParams = lp
            visibility = View.GONE
        }
        addView(volumeHud)

        player.addListener(this)
        ioExecutor.execute {
            if (store.getTrackCount() == 0) {
                importExistingAudioFiles()
            }
            post { refresh() }
        }
    }

    fun showLibrary() = switchMode(Mode.LIST)
    fun showPlayer() = switchMode(Mode.PLAYER)
    fun isShowingLibrary(): Boolean = currentMode == Mode.LIST

    private fun switchMode(mode: Mode) {
        currentMode = mode
        if (mode == Mode.PLAYER) {
            listContainer.visibility = View.GONE
            playerContainer.visibility = View.VISIBLE
            updatePlayerView()
        } else {
            playerContainer.visibility = View.GONE
            listContainer.visibility = View.VISIBLE
            renderTrackList()
        }
    }

    fun refresh() {
        headerStoragePill.text = "${store.getTrackCount()}/20"
        updateAudioOutputPill()
        updatePlayerView()
        if (currentMode == Mode.LIST) {
            renderTrackList()
        }
    }

    private fun updateAudioOutputPill() {
        val isBt = player.isAirPodsOrBluetoothConnected()
        if (isBt) {
            audioOutputPill.text = "🎧 AirPods / BT conectado"
            audioOutputPill.setTextColor(XTheme.PINK)
        } else {
            audioOutputPill.text = "🔊 Altavoz del reloj (Toca p/ BT)"
            audioOutputPill.setTextColor(XTheme.TEXT_SECONDARY)
        }
    }

    private fun updatePlayerView() {
        val track = player.currentTrack
        if (track != null) {
            trackTitleText.text = track.title
            trackArtistText.text = "${track.artist} · ${track.formattedDuration}"
        } else {
            val count = store.getTrackCount()
            if (count > 0) {
                trackTitleText.text = "Música lista"
                trackArtistText.text = "$count canciones en el reloj · Toca ▶"
            } else {
                trackTitleText.text = "Sin canciones"
                trackArtistText.text = "Toca para abrir la biblioteca"
            }
        }

        // Actualizar icono Play/Pausa
        btnPlayPause.text = if (player.state == PlayState.PLAYING) "⏸" else "▶"
        headerStoragePill.text = "${store.getTrackCount()}/20"
        updateAudioOutputPill()
    }

    private fun renderTrackList() {
        listItemsLayout.removeAllViews()
        val tracks = store.getAllTracks()

        if (tracks.isEmpty()) {
            val emptyCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding((14 * density).toInt(), (20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt())
                background = XTheme.roundRect(XTheme.CARD_BG, 14f, XTheme.CARD_STROKE, 1f, density)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = (12 * density).toInt()
                }

                val t1 = TextView(context).apply {
                    text = "Aún no hay canciones"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(XTheme.TEXT_PRIMARY)
                    gravity = Gravity.CENTER
                }
                val t2 = TextView(context).apply {
                    text = "Copia tus canciones a la carpeta Music del reloj o usa la sincronización."
                    textSize = 11f
                    setTextColor(XTheme.TEXT_SECONDARY)
                    gravity = Gravity.CENTER
                    setPadding(0, (4 * density).toInt(), 0, (12 * density).toInt())
                }
                val btnSync = TextView(context).apply {
                    text = "🔄 Sincronizar con Spotube (Móvil)"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
                    background = XTheme.pillButtonDrawable(density, true, XTheme.PINK, XTheme.PINK_DARK)
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                        bottomMargin = (8 * density).toInt()
                    }
                    setOnClickListener {
                        vibrateTick()
                        MusicSync.requestSync(context)
                    }
                }
                val btnDemo = TextView(context).apply {
                    text = "＋ Buscar canciones locales"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(XTheme.TEXT_SECONDARY)
                    gravity = Gravity.CENTER
                    setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
                    setOnClickListener {
                        vibrateTick()
                        importExistingAudioFiles()
                    }
                }
                addView(t1)
                addView(t2)
                addView(btnSync)
                addView(btnDemo)
            }
            listItemsLayout.addView(emptyCard)
            return
        }

        // Botón superior para sincronizar con Spotube del móvil vía Bluetooth
        val syncBar = TextView(context).apply {
            text = "🔄 Sincronizar canciones de Spotube"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (7 * density).toInt(), (10 * density).toInt(), (7 * density).toInt())
            background = XTheme.roundRect(XTheme.PINK_DARK, 10f, XTheme.PINK, 1f, density)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (6 * density).toInt()
            }
            setOnClickListener {
                vibrateTick()
                MusicSync.requestSync(context)
            }
        }
        listItemsLayout.addView(syncBar)

        // Botón para importar canciones sueltas de /sdcard/Music si las hay
        val importBar = TextView(context).apply {
            text = "＋ Buscar en almacenamiento local"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(XTheme.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (8 * density).toInt()
            }
            setOnClickListener {
                vibrateTick()
                importExistingAudioFiles()
            }
        }
        listItemsLayout.addView(importBar)

        for ((idx, track) in tracks.withIndex()) {
            val isCurrent = player.currentTrack?.id == track.id
            val isPlaying = isCurrent && player.state == PlayState.PLAYING

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = (6 * density).toInt()
                }

                if (isCurrent) {
                    background = XTheme.roundRect(XTheme.PINK_SUBTLE, 12f, XTheme.PINK, 1.2f, density)
                } else {
                    background = XTheme.roundRect(XTheme.CARD_BG, 12f, XTheme.CARD_STROKE, 1f, density)
                }

                // Contenedor principal clickeable para reproducir
                val cardBody = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    setOnClickListener {
                        vibrateTick()
                        player.playPlaylist(tracks, idx)
                        switchMode(Mode.PLAYER)
                    }
                }

                // Indicador de número o Play
                val indicator = TextView(context).apply {
                    text = if (isPlaying) "▶" else "${idx + 1}"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isCurrent) XTheme.PINK else XTheme.TEXT_SECONDARY)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), -2)
                }
                cardBody.addView(indicator)

                // Info (Título, Artista, Duración)
                val infoLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        leftMargin = (4 * density).toInt()
                        rightMargin = (4 * density).toInt()
                    }

                    val titleView = TextView(context).apply {
                        text = track.title
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(if (isCurrent) Color.WHITE else XTheme.TEXT_PRIMARY)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    val subtitleView = TextView(context).apply {
                        text = "${track.artist} · ${track.formattedDuration} ${if (track.formattedSize.isNotEmpty()) "· ${track.formattedSize}" else ""}"
                        textSize = 10f
                        setTextColor(XTheme.TEXT_SECONDARY)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    addView(titleView)
                    addView(subtitleView)
                }
                cardBody.addView(infoLayout)
                addView(cardBody)

                // Botón borrar canción (✕) con área táctil generosa y visualización clara
                val btnDelete = TextView(context).apply {
                    text = "✕"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(XTheme.TEXT_SECONDARY)
                    val s = (36 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(s, s)
                    background = XTheme.roundRect(XTheme.SURFACE_BTN, 10f, null, 0f, density)
                    setOnClickListener {
                        vibrateTick()
                        ioExecutor.execute {
                            store.deleteTrack(track.id)
                            post {
                                if (player.currentTrack?.id == track.id) {
                                    player.stop()
                                }
                                refresh()
                            }
                        }
                    }
                }
                addView(btnDelete)
            }
            listItemsLayout.addView(card)
        }
    }

    /**
     * Importa archivos de audio (.mp3, .m4a) que se encuentren en /sdcard/Music o /sdcard/Download
     * aplicando el límite FIFO de 20 canciones.
     */
    fun importExistingAudioFiles() {
        ioExecutor.execute {
            val searchDirs = listOf(
                File("/sdcard/Music"),
                File("/sdcard/Download"),
                File("/sdcard")
            )
            var imported = 0
            for (dir in searchDirs) {
                if (!dir.exists() || !dir.isDirectory) continue
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (f.isFile && (f.extension.equals("mp3", true) || f.extension.equals("m4a", true))) {
                        val id = "imported_${f.nameWithoutExtension.hashCode()}"
                        val title = f.nameWithoutExtension.replace(Regex("[-_]"), " ")
                        val track = store.saveTrack(id, title, "Local", 0, f)
                        if (track != null) imported++
                    }
                }
            }
            if (imported > 0) {
                post { refresh() }
            }
        }
    }

    // ==========================================
    // CONTROL CON CORONA (ROULETTE / ROTARY ENCODER)
    // ==========================================

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL) {
            val axis = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (axis == 0f) return false

            if (currentMode == Mode.PLAYER) {
                // En modo Reproductor: La corona regula el volumen del sistema suavemente
                val step = if (axis > 0f) 1 else -1
                val volume = player.adjustVolume(step)
                showVolumeHud(volume)
                return true
            } else {
                // En modo Lista: La corona hace scroll vertical suave
                val scrollByPx = (-axis * 40f * density).toInt()
                listScrollView.smoothScrollBy(0, scrollByPx)
                return true
            }
        }
        return super.onGenericMotionEvent(ev)
    }

    private fun showVolumeHud(volumePercent: Int) {
        val icon = if (volumePercent == 0) "🔇" else if (volumePercent < 50) "🔉" else "🔊"
        val out = if (player.isAirPodsOrBluetoothConnected()) "🎧 AirPods" else "Altavoz"
        volumeHud.text = "$icon $volumePercent%\n$out"
        volumeHud.visibility = View.VISIBLE
        hudHandler.removeCallbacks(hideHudRunnable)
        hudHandler.postDelayed(hideHudRunnable, 1400L)
    }

    private fun vibrateTick() {
        performHapticFeedback(
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    fun handleBack(): Boolean {
        if (currentMode == Mode.LIST) {
            switchMode(Mode.PLAYER)
            return true
        }
        return false
    }

    // ==========================================
    // IMPLEMENTACIÓN DE MusicPlayer.Listener
    // ==========================================

    override fun onStateChanged(state: PlayState, track: Track?) {
        post {
            updatePlayerView()
            if (currentMode == Mode.LIST) {
                renderTrackList()
            }
        }
    }

    override fun onProgress(currentMs: Int, totalMs: Int) {
        post {
            val progressRatio = (currentMs.toFloat() / totalMs.toFloat().coerceAtLeast(1f))
            progressBar.progress = (progressRatio * 1000).toInt()

            val curSec = currentMs / 1000
            val totSec = totalMs / 1000
            timeCurrentText.text = String.format(Locale.ROOT, "%d:%02d", curSec / 60, curSec % 60)
            timeTotalText.text = String.format(Locale.ROOT, "%d:%02d", totSec / 60, totSec % 60)
        }
    }

    private val musicUpdateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            post { refresh() }
        }
    }

    fun onPause() {
        hudHandler.removeCallbacks(hideHudRunnable)
        player.removeListener(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        player.addListener(this)
        try {
            context.registerReceiver(musicUpdateReceiver, android.content.IntentFilter("io.xenxo.MUSIC_UPDATED"))
        } catch (_: Exception) {}
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onPause()
        try {
            context.unregisterReceiver(musicUpdateReceiver)
        } catch (_: Exception) {}
    }
}

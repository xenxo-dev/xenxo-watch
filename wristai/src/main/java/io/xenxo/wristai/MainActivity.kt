package io.xenxo.wristai

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantalla principal de Wrist AI (xenxoVoice):
 *  - Estética ChatGPT Voice Live con orbe orgánico animado en pantalla AMOLED negra pura.
 *  - Reactividad de audio y voz en tiempo real.
 *  - 4 botones circulares de control: Transcripción (📄), Micrófono (🎤), Opciones (···) y Salir (✕).
 *  - Métricas de salud en 0 ms (:xenxo), Groq Whisper STT (~200 ms) + Llama 3.3 / Gemini.
 *  - Corona rotatoria para desplazar texto cuando la lámina está abierta.
 */
class MainActivity : Activity() {

    private lateinit var cfg: Config
    private lateinit var voiceOrb: VoiceOrbView
    private lateinit var status: TextView
    private lateinit var providerBtn: Button

    // Lámina de transcripción y detalles
    private lateinit var transcriptSheet: ScrollView
    private lateinit var sheetTitle: TextView
    private lateinit var answer: TextView
    private lateinit var btnCloseSheet: Button

    // Dashboard de 4 cuadrantes
    private lateinit var dashboard: LinearLayout
    private lateinit var btnDashWeight: Button
    private lateinit var btnDashGym: Button
    private lateinit var btnDashPadel: Button
    private lateinit var btnDashBattery: Button
    private lateinit var btnDashSos: Button

    // 4 botones inferiores
    private lateinit var btnSheetToggle: Button
    private lateinit var mic: Button
    private lateinit var btnOptions: Button
    private lateinit var btnClose: Button

    private val main = Handler(Looper.getMainLooper())
    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var localVoiceFallback: android.speech.tts.Voice? = null
    private var lastSpokenText: String = ""
    private val utteranceWaitMap = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()
    private val pcmPlayer = PcmAudioPlayer { amp -> main.post { voiceOrb.setAmplitude(amp) } }
    private var busy = false
    private var vibrator: Vibrator? = null

    // Detección inteligente de voz y silencio (VAD)
    private var recordStartTime = 0L
    private var speechFrames = 0
    private var hasSpoken = false
    private var silenceDurationMs = 0L
    private var sampleCount = 0

    /** Muestreo de amplitud del micrófono a 40 ms para alimentar el orbe y detectar silencio automático */
    private val amplitudeTracker = object : Runnable {
        override fun run() {
            val r = recorder
            if (r != null) {
                val maxAmp = try { r.maxAmplitude } catch (e: Exception) { 0 }
                val norm = (maxAmp / 28000f).coerceIn(0f, 1f)
                voiceOrb.setAmplitude(norm)

                sampleCount++
                // Descartar las primeras 7 muestras (280 ms) para ignorar el 'clic' del botón físico o vibración háptica
                if (sampleCount > 7) {
                    if (maxAmp >= SPEECH_THRESHOLD) {
                        speechFrames++
                        if (speechFrames >= 3) {
                            hasSpoken = true
                        }
                        silenceDurationMs = 0L
                    } else if (maxAmp < SILENCE_THRESHOLD) {
                        if (hasSpoken) {
                            silenceDurationMs += 40L
                            // Silencio continuo de 1.4 segundos tras haber hablado -> autoenvío
                            if (silenceDurationMs >= 1400L) {
                                android.util.Log.i("WristAI_VAD", "Silencio detectado tras voz ($silenceDurationMs ms). Auto-enviando consulta...")
                                vibrate(30)
                                stopAndSend()
                                return
                            }
                        } else {
                            val elapsed = System.currentTimeMillis() - recordStartTime
                            if (elapsed >= 7500L) {
                                android.util.Log.i("WristAI_VAD", "Tiempo de espera agotado sin voz ($elapsed ms).")
                                cancelRecording()
                                setStatus("No te he oído")
                                return
                            }
                        }
                    } else {
                        // Zona intermedia leve (1500 <= maxAmp < 1800)
                        if (hasSpoken) {
                            silenceDurationMs += 25L
                            if (silenceDurationMs >= 1600L) {
                                android.util.Log.i("WristAI_VAD", "Silencio acumulado en umbral bajo ($silenceDurationMs ms). Auto-enviando...")
                                vibrate(30)
                                stopAndSend()
                                return
                            }
                        }
                    }
                }

                main.postDelayed(this, 40L)
            } else {
                voiceOrb.setAmplitude(0f)
            }
        }
    }

    /** Caché en memoria del snapshot de salud para inyectar en el prompt en 0 ms */
    private var cachedHealthJson = ""
    private var cachedHealthBundle: Bundle? = null

    private val history = ArrayList<Turn>()
    private val autoStop = Runnable { if (recorder != null) stopAndSend() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("wristai", MODE_PRIVATE)
        if (Config.importIfPresent(this, prefs)) toast("Configuración importada ✓")
        cfg = Config(prefs)

        voiceOrb = findViewById(R.id.voice_orb)
        status = findViewById(R.id.status)
        providerBtn = findViewById(R.id.provider)

        transcriptSheet = findViewById(R.id.transcript_sheet)
        sheetTitle = findViewById(R.id.sheet_title)
        answer = findViewById(R.id.answer)
        btnCloseSheet = findViewById(R.id.btn_close_sheet)

        dashboard = findViewById(R.id.dashboard)
        btnDashWeight = findViewById(R.id.btn_dash_weight)
        btnDashGym = findViewById(R.id.btn_dash_gym)
        btnDashPadel = findViewById(R.id.btn_dash_padel)
        btnDashBattery = findViewById(R.id.btn_dash_battery)
        btnDashSos = findViewById(R.id.btn_dash_sos)

        btnSheetToggle = findViewById(R.id.btn_sheet_toggle)
        mic = findViewById(R.id.mic)
        btnOptions = findViewById(R.id.btn_options)
        btnClose = findViewById(R.id.btn_close)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        // 1. Selector de proveedor superior
        providerBtn.setOnClickListener { cycleProvider() }
        providerBtn.setOnLongClickListener {
            newConversation()
            true
        }

        // 2. Toque directo en el orbe animado
        voiceOrb.setOnClickListener {
            if (busy) return@setOnClickListener
            if (recorder != null) stopAndSend() else startRecording()
        }

        // 3. Botón 1: Alternar lámina de texto / transcripción
        btnSheetToggle.setOnClickListener {
            toggleTranscriptSheet()
        }
        btnCloseSheet.setOnClickListener {
            transcriptSheet.visibility = View.GONE
        }

        // 4. Botón 2: Micrófono (Soporte doble: Push-to-Talk y toque alternante)
        mic.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (busy) return@setOnTouchListener false
                    recordStartTime = System.currentTimeMillis()
                    vibrate(30)
                    if (recorder == null) startRecording()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (busy) return@setOnTouchListener false
                    val elapsed = System.currentTimeMillis() - recordStartTime
                    vibrate(30)
                    if (recorder != null) {
                        if (elapsed >= 500) {
                            stopAndSend()
                        } else {
                            // Toque corto: mantener grabando hasta segundo toque
                            setStatus("Escuchando… toca ■ para enviar")
                        }
                    }
                    true
                }
                else -> false
            }
        }
        mic.setOnClickListener {
            if (recorder != null) stopAndSend() else startRecording()
        }

        // 5. Botón 3: Opciones (···) -> cambiar proveedor o silenciar TTS
        btnOptions.setOnClickListener { cycleProvider() }
        btnOptions.setOnLongClickListener {
            val currentTts = cfg.ttsEnabled
            cfg.ttsEnabled = !currentTts
            toast(if (!currentTts) "Voz activada 🔊" else "Voz silenciada 🔇")
            true
        }

        // 6. Botón 4: Salir (✕)
        btnClose.setOnClickListener {
            finish()
        }

        setupDashboardClicks()

        initTts()
        if (cfg.piperTts) {
            bgExecutor.execute {
                val ok = PiperTts.init(applicationContext)
                android.util.Log.i("WristAI_TTS", "Piper TTS inicializado: $ok (sampleRate=${PiperTts.getSampleRate()})")
            }
        }
        refreshProvider()
        showIdle()

        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()

        val testText = intent?.getStringExtra("test_speak")
        if (!testText.isNullOrBlank()) {
            main.postDelayed({ speak(testText) }, 1200L)
        } else {
            val fromStem = intent?.getBooleanExtra("from_stem_button", false) == true
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                triggerAutoListenIfEnabled(350L, force = fromStem)
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        window.decorView.requestFocus()
        val testText = intent?.getStringExtra("test_speak")
        if (!testText.isNullOrBlank()) {
            speak(testText)
            return
        }
        if (transcriptSheet.visibility == View.VISIBLE) {
            transcriptSheet.visibility = View.GONE
        }
        if (!busy) {
            if (recorder != null) {
                // Segundo toque al botón físico: enviar consulta
                stopAndSend()
            } else {
                // Toque al botón físico: empezar a escuchar de inmediato
                triggerAutoListenIfEnabled(100L, force = true)
            }
        }
    }

    private fun triggerAutoListenIfEnabled(delayMs: Long = 350L, force: Boolean = false) {
        if (!force && !cfg.autoListenOnOpen) return
        main.postDelayed({
            if (!isFinishing && !isDestroyed && recorder == null && !busy) {
                vibrate(35)
                startRecording()
            }
        }, delayMs)
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext, { st ->
            if (st == TextToSpeech.SUCCESS) {
                var r = tts?.setLanguage(Locale("es", "ES"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    r = tts?.setLanguage(Locale("es"))
                }
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    r = tts?.setLanguage(Locale.getDefault())
                }
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED

                try {
                    val voices = tts?.voices
                    val spanishVoices = voices?.filter { it.locale.language.equals("es", ignoreCase = true) } ?: emptyList()

                    for (v in spanishVoices) {
                        android.util.Log.i("WristAI_TTS", "Voz disponible: ${v.name}, locale=${v.locale}, calidad=${v.quality}, red=${v.isNetworkConnectionRequired}")
                    }

                    // 1. Voz Neural de Google (es-es-x-ana-network) para máxima fluidez y entonación
                    val neuralVoice = spanishVoices.firstOrNull { it.name.contains("es-es-x-ana-network", ignoreCase = true) }
                        ?: spanishVoices.firstOrNull { it.locale.country.equals("ES", ignoreCase = true) && it.isNetworkConnectionRequired }

                    // 2. Voz Local offline (es-es-x-ana-local) como fallback garantizado
                    val localVoice = spanishVoices.firstOrNull { it.name.contains("es-es-x-ana-local", ignoreCase = true) }
                        ?: spanishVoices.firstOrNull { it.locale.country.equals("ES", ignoreCase = true) && !it.isNetworkConnectionRequired }
                        ?: spanishVoices.firstOrNull { !it.isNetworkConnectionRequired }

                    localVoiceFallback = localVoice

                    // Seleccionar Neural si está disponible
                    val chosenVoice = neuralVoice ?: localVoice
                    if (chosenVoice != null) {
                        android.util.Log.i("WristAI_TTS", "Voz primaria seleccionada: ${chosenVoice.name} (calidad=${chosenVoice.quality}, red=${chosenVoice.isNetworkConnectionRequired})")
                        tts?.voice = chosenVoice
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WristAI_TTS", "Error seleccionando voz: ${e.message}")
                }

                // USAGE_ASSISTANT aplica DSP específico de claridad vocal en Wear OS
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        main.post {
                            if (!isFinishing && !isDestroyed) {
                                voiceOrb.state = VoiceOrbView.State.SPEAKING
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null) {
                            utteranceWaitMap.remove(utteranceId)?.invoke()
                        }
                        if (utteranceId != null && (utteranceId.startsWith("utt_last_") || utteranceId == "fallback_local")) {
                            main.post {
                                if (!isFinishing && !isDestroyed) {
                                    voiceOrb.state = VoiceOrbView.State.IDLE
                                }
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (utteranceId != null) {
                            utteranceWaitMap.remove(utteranceId)?.invoke()
                        }
                        android.util.Log.w("WristAI_TTS", "Fallo de síntesis con utteranceId=$utteranceId")
                        main.post {
                            if (!isFinishing && !isDestroyed) {
                                val fallback = localVoiceFallback
                                if (fallback != null && tts?.voice != fallback && lastSpokenText.isNotBlank()) {
                                    android.util.Log.i("WristAI_TTS", "Reintentando de inmediato con voz local offline: ${fallback.name}")
                                    tts?.voice = fallback
                                    val params = Bundle().apply {
                                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
                                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                                    }
                                    tts?.speak(lastSpokenText, TextToSpeech.QUEUE_FLUSH, params, "fallback_local")
                                } else {
                                    voiceOrb.state = VoiceOrbView.State.IDLE
                                }
                            }
                        }
                    }
                })
            }
        }, "com.google.android.tts")
    }

    private fun toggleTranscriptSheet() {
        if (transcriptSheet.visibility == View.VISIBLE) {
            transcriptSheet.visibility = View.GONE
        } else {
            transcriptSheet.visibility = View.VISIBLE
            transcriptSheet.scrollTo(0, 0)
        }
    }

    private fun cycleProvider() {
        if (busy || recorder != null) return
        cfg.provider = cfg.provider.next()
        newConversation()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.requestFocus()
        refreshHealthDataAsync()
    }

    override fun onPause() {
        super.onPause()
        if (recorder != null) cancelRecording()
        pcmPlayer.stop()
        tts?.stop()
        utteranceWaitMap.clear()
        voiceOrb.state = VoiceOrbView.State.IDLE
    }

    override fun onDestroy() {
        main.removeCallbacks(amplitudeTracker)
        main.removeCallbacks(autoStop)
        main.removeCallbacks(restoreStatusRunnable)
        pcmPlayer.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    // ---------- Sincronización local de salud (0 ms) ----------

    private fun refreshHealthDataAsync() {
        bgExecutor.execute {
            val uri = Uri.parse("content://io.xenxo.health.provider")
            val b = runCatching {
                contentResolver.call(uri, "getHealthContext", null, null)
            }.getOrNull()

            if (b != null) {
                cachedHealthBundle = b
                cachedHealthJson = b.getString("json", "")
                val wt = b.getFloat("currentWeight", 0f)
                val sets = b.getInt("lastWorkoutSets", 0)
                val padelRes = b.getString("lastPadelResult", "")
                val batt = b.getInt("batteryLevel", 0)

                main.post {
                    if (isFinishing || isDestroyed) return@post
                    if (wt > 0f) btnDashWeight.text = "⚖️ ${"%.1f".format(wt)} kg"
                    if (sets > 0) btnDashGym.text = "🏋️ $sets series"
                    if (padelRes.isNotBlank()) btnDashPadel.text = "🎾 ${padelRes.take(10)}"
                    if (batt > 0) btnDashBattery.text = "🔋 $batt%"
                }
            }
        }
    }

    private fun setupDashboardClicks() {
        btnDashWeight.setOnClickListener {
            val b = cachedHealthBundle
            val wt = b?.getFloat("currentWeight", 0f) ?: 0f
            val avg = b?.getFloat("avgWeight7d", 0f) ?: 0f
            val trend = b?.getFloat("weightTrend", 0f) ?: 0f
            val msg = "Peso actual: ${"%.1f".format(wt)} kg\nMedia 7 días: ${"%.1f".format(avg)} kg (${if (trend >= 0) "+" else ""}${"%.1f".format(trend)} kg)"
            showInstantAnswer("⚖️ Peso registrado", msg)
        }
        btnDashGym.setOnClickListener {
            val b = cachedHealthBundle
            val date = b?.getString("lastWorkoutDate", "Hoy") ?: "Hoy"
            val sets = b?.getInt("lastWorkoutSets", 0) ?: 0
            val top = b?.getString("lastWorkoutTop", "Entreno") ?: "Entreno"
            val msg = "Última sesión ($date):\n$sets series completadas.\nDestacado: $top"
            showInstantAnswer("🏋️ Gimnasio", msg)
        }
        btnDashPadel.setOnClickListener {
            val b = cachedHealthBundle
            val date = b?.getString("lastPadelDate", "Reciente") ?: "Reciente"
            val res = b?.getString("lastPadelResult", "Sin partidos") ?: "Sin partidos"
            val msg = "Último partido ($date):\n$res"
            showInstantAnswer("🎾 Pádel", msg)
        }
        btnDashBattery.setOnClickListener {
            val b = cachedHealthBundle
            val lvl = b?.getInt("batteryLevel", 0) ?: 0
            val drain = b?.getFloat("drainRate", 0f) ?: 0f
            val msg = "Nivel de batería: $lvl%\nGasto estimado: ${"%.1f".format(drain)}%/hora"
            showInstantAnswer("🔋 Batería", msg)
        }
        btnDashSos.setOnClickListener {
            showInstantAnswer(
                "🚨 Emergencia",
                "Si sufres dolor en el pecho, dificultad para respirar o mareo súbito, llama de inmediato al 112 desde tu teléfono.",
                isEmergency = true
            )
        }
    }

    private fun showInstantAnswer(title: String, text: String, isEmergency: Boolean = false) {
        setStatus(title)
        answer.setTextColor(if (isEmergency) 0xFFEF5350.toInt() else 0xFFFFFFFF.toInt())
        answer.text = text
        transcriptSheet.visibility = View.VISIBLE
        transcriptSheet.scrollTo(0, 0)
        speak(text)
    }

    // ---------- Grabación ----------

    private fun startRecording() {
        if (busy) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        if (!cfg.hasAnyKey()) {
            setStatus("Sin claves de API")
            answer.text = "Pasa config.json por ADB con tu groq_key o gemini_key."
            transcriptSheet.visibility = View.VISIBLE
            return
        }
        tts?.stop()
        pcmPlayer.stop()
        utteranceWaitMap.clear()

        val file = File(cacheDir, "pregunta.m4a")
        file.delete()

        val r = MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            setStatus("Error de micrófono")
            answer.text = e.message ?: ""
            return
        }
        recorder = r
        audioFile = file
        recordStartTime = System.currentTimeMillis()
        hasSpoken = false
        speechFrames = 0
        silenceDurationMs = 0L
        sampleCount = 0
        mic.setBackgroundResource(R.drawable.bg_voice_circle_active)
        mic.text = "■"
        voiceOrb.state = VoiceOrbView.State.LISTENING
        main.post(amplitudeTracker)

        keepScreenOn(true)
        setStatus("Escuchando…")
        main.postDelayed(autoStop, MAX_RECORD_MS)
    }

    private fun stopRecording(): Boolean {
        main.removeCallbacks(autoStop)
        main.removeCallbacks(amplitudeTracker)
        voiceOrb.setAmplitude(0f)
        val r = recorder ?: return false
        recorder = null
        mic.setBackgroundResource(R.drawable.bg_voice_circle_btn)
        mic.text = "🎤"
        return try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false
        } finally {
            r.release()
        }
    }

    private fun cancelRecording() {
        stopRecording()
        keepScreenOn(false)
        voiceOrb.state = VoiceOrbView.State.IDLE
        showIdle()
    }

    private fun stopAndSend() {
        val ok = stopRecording()
        val file = audioFile
        if (!ok || file == null || file.length() < 1_000) {
            keepScreenOn(false)
            voiceOrb.state = VoiceOrbView.State.IDLE
            setStatus("Demasiado corto, prueba otra vez")
            return
        }
        send(file)
    }

    // ---------- Envío de la consulta ----------

    private fun send(file: File) {
        busy = true
        mic.isEnabled = false
        providerBtn.isEnabled = false
        voiceOrb.state = VoiceOrbView.State.THINKING
        val provider = cfg.provider
        setStatus("Pensando con ${provider.label}…")

        bgExecutor.execute {
            val result = runCatching {
                AiClient(cfg).ask(
                    provider = provider,
                    audio = file,
                    history = history.toList(),
                    healthContextJson = cachedHealthJson,
                    ctx = this
                )
            }
            file.delete()
            main.post {
                if (isFinishing || isDestroyed) return@post
                busy = false
                mic.isEnabled = true
                providerBtn.isEnabled = true
                keepScreenOn(false)
                result.onSuccess { a ->
                    val clean = cleanForWatch(a.text)
                    if (!a.isAction) {
                        history.add(Turn(a.question, clean))
                        while (history.size > MAX_TURNS) history.removeAt(0)
                    }
                    setStatus(a.question.ifBlank { a.providerBadge })
                    sheetTitle.text = "${a.providerBadge} • ${a.question}"
                    answer.setTextColor(if (a.isEmergency) 0xFFEF5350.toInt() else 0xFFFFFFFF.toInt())
                    answer.text = clean
                    speak(clean, a.audioBytes)

                    // Si se registró peso, refrescar datos locales
                    if (a.isAction) refreshHealthDataAsync()
                }.onFailure { e ->
                    voiceOrb.state = VoiceOrbView.State.IDLE
                    setStatus("Error de conexión")
                    answer.setTextColor(0xFFEF5350.toInt())
                    answer.text = e.message ?: e.javaClass.simpleName
                    transcriptSheet.visibility = View.VISIBLE
                }
            }
        }
    }

    // ---------- UI ----------

    private fun newConversation() {
        history.clear()
        pcmPlayer.stop()
        tts?.stop()
        voiceOrb.state = VoiceOrbView.State.IDLE
        refreshProvider()
        showIdle()
        toast("Modo ${cfg.provider.label}")
    }

    private fun refreshProvider() {
        providerBtn.text = cfg.provider.label
    }

    private fun showIdle() {
        setStatus("Toca 🎤 o el orbe para hablar")
        answer.setTextColor(0xFFFFFFFF.toInt())
        answer.text = "Mantén 🎤 para hablar o pulsa el orbe.\n\nPuedes dictar tu peso, pedir rutinas o consultar tu salud."
    }

    private fun setStatus(s: String) {
        status.text = s
    }

    private fun speak(text: String, audioBytes: ByteArray? = null) {
        if (!cfg.ttsEnabled) {
            voiceOrb.state = VoiceOrbView.State.IDLE
            return
        }

        unmuteAudio()

        // 1. Si disponemos de audio directo PCM sintetizado por Gemini, reproducir en alta fidelidad
        if (audioBytes != null && audioBytes.isNotEmpty()) {
            pcmPlayer.play(
                pcm = audioBytes,
                sampleRate = 24000,
                onStart = {
                    main.post {
                        if (!isFinishing && !isDestroyed) {
                            voiceOrb.state = VoiceOrbView.State.SPEAKING
                        }
                    }
                },
                onDone = {
                    main.post {
                        if (!isFinishing && !isDestroyed) {
                            voiceOrb.state = VoiceOrbView.State.IDLE
                        }
                    }
                }
            )
            return
        }

        // 2. Piper TTS local (solo si está activado explícitamente en la configuración)
        if (cfg.piperTts && PiperTts.isAvailable()) {
            voiceOrb.state = VoiceOrbView.State.THINKING
            bgExecutor.execute {
                val t0 = System.currentTimeMillis()
                val pcm = PiperTts.synthesizeWithPauses(text)
                val elapsed = System.currentTimeMillis() - t0
                android.util.Log.i("WristAI_TTS", "Piper TTS síntesis completada en ${elapsed}ms (${pcm?.size ?: 0} bytes)")
                main.post {
                    if (isFinishing || isDestroyed) return@post
                    if (pcm != null && pcm.isNotEmpty()) {
                        pcmPlayer.play(
                            pcm = pcm,
                            sampleRate = PiperTts.getSampleRate(),
                            onStart = {
                                main.post {
                                    if (!isFinishing && !isDestroyed) {
                                        voiceOrb.state = VoiceOrbView.State.SPEAKING
                                    }
                                }
                            },
                            onDone = {
                                main.post {
                                    if (!isFinishing && !isDestroyed) {
                                        voiceOrb.state = VoiceOrbView.State.IDLE
                                    }
                                }
                            }
                        )
                    } else if (ttsReady && tts != null) {
                        speakWithAndroidTts(text)
                    } else {
                        voiceOrb.state = VoiceOrbView.State.IDLE
                    }
                }
            }
            return
        }

        // 2. Respuesta instantánea con Google TTS local (0 ms de red, latencia mínima)
        if (!cfg.cloudTts && ttsReady && tts != null) {
            speakWithAndroidTts(text)
            return
        }

        // 3. Fallback Gemini Cloud TTS si se prefiere voz Cloud
        if (cfg.geminiKey.isNotBlank()) {
            voiceOrb.state = VoiceOrbView.State.THINKING
            bgExecutor.execute {
                val pcm = AiClient(cfg).geminiTts(text)
                main.post {
                    if (isFinishing || isDestroyed) return@post
                    if (pcm != null && pcm.isNotEmpty()) {
                        pcmPlayer.play(
                            pcm,
                            onStart = {
                                if (!isFinishing && !isDestroyed) {
                                    voiceOrb.state = VoiceOrbView.State.SPEAKING
                                }
                            },
                            onDone = {
                                if (!isFinishing && !isDestroyed) {
                                    voiceOrb.state = VoiceOrbView.State.IDLE
                                }
                            }
                        )
                    } else if (ttsReady && tts != null) {
                        speakWithAndroidTts(text)
                    } else {
                        voiceOrb.state = VoiceOrbView.State.IDLE
                    }
                }
            }
            return
        }

        // 4. Fallback motor Android TTS
        if (ttsReady && tts != null) {
            speakWithAndroidTts(text)
        } else {
            voiceOrb.state = VoiceOrbView.State.IDLE
        }
    }

    private fun speakWithAndroidTts(text: String) {
        val speechText = AiClient.prepareForSpeech(text)
        lastSpokenText = speechText

        // Dividir en frases/cláusulas según signos de puntuación (. ! ? , : ;)
        val phrases = speechText.split(Regex("(?<=[.,!?:;])\\s+")).filter { it.isNotBlank() }
        if (phrases.isEmpty()) {
            voiceOrb.state = VoiceOrbView.State.IDLE
            return
        }

        // Si solo hay una frase, reproducción directa e instantánea
        if (phrases.size == 1) {
            voiceOrb.state = VoiceOrbView.State.SPEAKING
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.setSpeechRate(1.02f)
            tts?.setPitch(0.97f)
            tts?.speak(phrases[0], TextToSpeech.QUEUE_FLUSH, params, "utt_last_${System.currentTimeMillis()}")
            return
        }

        // Múltiples frases: reproducción secuencial con pausa física real e ineludible en hilo de fondo
        bgExecutor.execute {
            main.post {
                if (!isFinishing && !isDestroyed) {
                    voiceOrb.state = VoiceOrbView.State.SPEAKING
                }
            }

            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.setSpeechRate(1.02f)
            tts?.setPitch(0.97f)

            for (i in phrases.indices) {
                if (recorder != null || isFinishing || isDestroyed) break

                val phrase = phrases[i]
                val isLast = (i == phrases.size - 1)
                val uttId = if (isLast) "utt_last_${System.currentTimeMillis()}_$i" else "utt_seq_${System.currentTimeMillis()}_$i"

                val syncLock = Object()
                var completed = false

                utteranceWaitMap[uttId] = {
                    synchronized(syncLock) {
                        completed = true
                        syncLock.notifyAll()
                    }
                }

                tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, uttId)

                // Esperar a que la frase termine de sonar en el altavoz
                synchronized(syncLock) {
                    val waitStart = System.currentTimeMillis()
                    while (!completed && (System.currentTimeMillis() - waitStart < 8000L)) {
                        syncLock.wait(200L)
                        if (recorder != null || isFinishing || isDestroyed) break
                    }
                }
                utteranceWaitMap.remove(uttId)

                if (recorder != null || isFinishing || isDestroyed) break

                // Micro-pausa física real entre frases (320ms punto, 180ms coma)
                if (!isLast) {
                    val pauseMs = if (phrase.endsWith(".") || phrase.endsWith("!") || phrase.endsWith("?")) 320L else 180L
                    try {
                        Thread.sleep(pauseMs)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    private fun unmuteAudio() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        try {
            if (am.isStreamMute(android.media.AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
            }
            val curr = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            if (curr < max / 2) {
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (max * 0.75f).toInt(), 0)
            }
        } catch (_: Exception) {}
    }

    private fun vibrate(ms: Long) {
        runCatching {
            vibrator?.vibrate(ms)
        }
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun cleanForWatch(s: String): String = s
        .replace(Regex("\\*\\*|__|`+"), "")
        .replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
        .replace(Regex("(?m)^\\s*[-*•]\\s+"), "· ")
        .trim()

    private var rotaryAccumulator = 0f
    private var lastStatusBeforeVolume: String? = null
    private val restoreStatusRunnable = Runnable {
        lastStatusBeforeVolume?.let { setStatus(it) }
        lastStatusBeforeVolume = null
    }

    /**
     * Ajuste suave de volumen de medios con respuesta visual y háptica al girar la corona.
     */
    private fun changeVolume(steps: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (am.isStreamMute(AudioManager.STREAM_MUSIC) && steps > 0) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val target = (current + steps).coerceIn(0, max)
            if (target != current) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                vibrate(18) // pulso háptico con cada clic de la corona
            }

            val pct = ((target.toFloat() / max.toFloat()) * 100f).roundToInt()
            val icon = when {
                target == 0 -> "🔇"
                pct < 50 -> "🔉"
                else -> "🔊"
            }
            if (lastStatusBeforeVolume == null) {
                lastStatusBeforeVolume = status.text.toString()
            }
            setStatus("$icon Volumen $pct%")
            main.removeCallbacks(restoreStatusRunnable)
            main.postDelayed(restoreStatusRunnable, 1800)
        } catch (_: Exception) {}
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val axis = -ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (axis != 0f) {
                rotaryAccumulator += axis
                if (rotaryAccumulator >= 0.65f) {
                    changeVolume(1)
                    rotaryAccumulator = 0f
                    return true
                } else if (rotaryAccumulator <= -0.65f) {
                    changeVolume(-1)
                    rotaryAccumulator = 0f
                    return true
                }
            }
        }
        return super.onGenericMotionEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_STEM_2, KeyEvent.KEYCODE_STEM_3,
            KeyEvent.KEYCODE_STEM_PRIMARY, KeyEvent.KEYCODE_FUNCTION, KeyEvent.KEYCODE_BUTTON_1 -> {
                if (busy) return true
                vibrate(35)
                if (recorder != null) stopAndSend() else startRecording()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                changeVolume(1)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                changeVolume(-1)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == REQ_MIC) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                triggerAutoListenIfEnabled(200L)
            } else {
                setStatus("Sin permiso de micrófono")
            }
        }
    }

    companion object {
        private const val REQ_MIC = 1
        private const val MAX_RECORD_MS = 20_000L
        private const val MAX_TURNS = 6
        private const val SPEECH_THRESHOLD = 1800
        private const val SILENCE_THRESHOLD = 1500
    }
}

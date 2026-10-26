package io.xenxo.wristai.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.xenxo.wristai.AiClient
import io.xenxo.wristai.Config
import io.xenxo.wristai.R
import java.io.File
import kotlin.math.roundToInt

class ReplyActivity : Activity() {

    private lateinit var scroll: ScrollView
    private lateinit var senderTv: TextView
    private lateinit var messageTv: TextView
    private lateinit var voiceBtn: Button

    private lateinit var cfg: Config
    private var targetNotif: ActionableNotification? = null
    private val main = Handler(Looper.getMainLooper())
    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var vibrator: Vibrator? = null

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var touchDownTime = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reply)

        val prefs = getSharedPreferences("wristai", MODE_PRIVATE)
        cfg = Config(prefs)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        scroll = findViewById(R.id.reply_scroll)
        senderTv = findViewById(R.id.reply_sender)
        messageTv = findViewById(R.id.reply_message)
        voiceBtn = findViewById(R.id.btn_reply_voice)

        val key = intent.getStringExtra("notif_key")
        targetNotif = if (key != null) ActiveNotificationStore.get(key) else ActiveNotificationStore.getLatest()

        val notif = targetNotif
        if (notif == null) {
            senderTv.text = "Sin notificaciones"
            messageTv.text = "No hay mensajes entrantes para responder en este momento."
            voiceBtn.isEnabled = false
            return
        }

        val appLabel = when {
            notif.packageName.contains("whatsapp") -> "💬 WhatsApp"
            notif.packageName.contains("telegram") -> "✈️ Telegram"
            notif.packageName.contains("messaging") -> "✉️ SMS"
            else -> "Notificación"
        }
        senderTv.text = "$appLabel • ${notif.sender}"
        messageTv.text = notif.message

        setupQuickReplies(notif)
        setupVoiceDictation(notif)
    }

    private fun setupQuickReplies(notif: ActionableNotification) {
        findViewById<Button>(R.id.btn_reply_ok).setOnClickListener { sendAndFinish(notif, "Vale, de acuerdo 👍") }
        findViewById<Button>(R.id.btn_reply_onmyway).setOnClickListener { sendAndFinish(notif, "Voy de camino 🚶") }
        findViewById<Button>(R.id.btn_reply_calllater).setOnClickListener { sendAndFinish(notif, "Te llamo en 5 minutos ⏰") }
        findViewById<Button>(R.id.btn_reply_gym).setOnClickListener { sendAndFinish(notif, "Entrenando en el gym, te hablo luego 🏋️") }
        findViewById<Button>(R.id.btn_reply_cant).setOnClickListener { sendAndFinish(notif, "Ahora no puedo hablar ❌") }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupVoiceDictation(notif: ActionableNotification) {
        voiceBtn.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownTime = System.currentTimeMillis()
                    vibrate(30)
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchDownTime
                    vibrate(30)
                    if (recorder != null) {
                        if (duration >= 500) {
                            stopAndDictate(notif)
                        } else {
                            voiceBtn.text = "■ Toca para enviar"
                        }
                    }
                    true
                }
                else -> false
            }
        }
        voiceBtn.setOnClickListener {
            if (recorder != null) stopAndDictate(notif) else startRecording()
        }
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        val file = File(cacheDir, "reply_audio.m4a")
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
            Toast.makeText(this, "Error de micro", Toast.LENGTH_SHORT).show()
            return
        }
        recorder = r
        audioFile = file
        voiceBtn.text = "■ Grabando… suelta"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stopRecording(): Boolean {
        val r = recorder ?: return false
        recorder = null
        voiceBtn.text = "🎤 Dictar respuesta"
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        return try {
            r.stop()
            true
        } catch (e: Exception) {
            false
        } finally {
            r.release()
        }
    }

    private fun stopAndDictate(notif: ActionableNotification) {
        val ok = stopRecording()
        val file = audioFile
        if (!ok || file == null || file.length() < 1000) {
            Toast.makeText(this, "Audio demasiado corto", Toast.LENGTH_SHORT).show()
            return
        }

        voiceBtn.isEnabled = false
        voiceBtn.text = "Transcribiendo…"

        bgExecutor.execute {
            val client = AiClient(cfg)
            // Groq Whisper transcribirá en ~200 ms
            val transcribed = runCatching {
                // Usar Groq o Gemini para transcribir
                val m = AiClient::class.java.getDeclaredMethod("transcribe", File::class.java)
                m.isAccessible = true
                m.invoke(client, file) as String
            }.getOrNull()

            file.delete()
            main.post {
                if (isFinishing || isDestroyed) return@post
                voiceBtn.isEnabled = true
                voiceBtn.text = "🎤 Dictar respuesta"
                if (!transcribed.isNullOrBlank()) {
                    sendAndFinish(notif, transcribed)
                } else {
                    Toast.makeText(this@ReplyActivity, "No se entendió el audio", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendAndFinish(notif: ActionableNotification, replyText: String) {
        val ok = NotificationReplyHelper.sendReply(this, notif.replyAction, replyText)
        if (ok) {
            vibrate(50)
            Toast.makeText(this, "Enviado: \"$replyText\" ✓", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Error al enviar respuesta", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibrate(ms: Long) {
        runCatching { vibrator?.vibrate(ms) }
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = -ev.getAxisValue(MotionEvent.AXIS_SCROLL) *
                ViewConfiguration.get(this).scaledVerticalScrollFactor
            scroll.scrollBy(0, delta.roundToInt())
            return true
        }
        return super.onGenericMotionEvent(ev)
    }
}

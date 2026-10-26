package io.xenxo.notes

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aplicación de Notas 100% Offline para Xiaomi Mi Watch (368×448 AMOLED).
 *
 * Funcionalidades:
 *  - Almacenamiento local SQLite (xenxo_notes.db).
 *  - Grabación y reproducción de notas de voz con el micrófono del reloj.
 *  - Plantillas rápidas: Compra, Gym, Idea, PIN.
 *  - Listas de tareas con casillas interactivas [ ] / [x].
 *  - Fijar notas importantes al principio (pin).
 *  - Desplazamiento ultra fluido con corona rotatoria y respuesta háptica.
 */
class NotesActivity : Activity() {

    private lateinit var db: NotesDb
    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var notesContainer: LinearLayout
    private lateinit var tvSubtitle: TextView
    private var vibrator: Vibrator? = null

    private var recorder: MediaRecorder? = null
    private var activeRecordingFile: File? = null
    private var isRecording = false

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingId: Long? = null

    private val timeFmt = SimpleDateFormat("d MMM, HH:mm", Locale("es", "ES"))
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = NotesDb(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val d = resources.displayMetrics.density

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Cabecera superior compacta
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF141416.toInt())
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Notas xenxo"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(title)

        val btnNew = TextView(this).apply {
            text = "+ Nueva"
            textSize = 12f
            setTextColor(Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * d
                setColor(0xFFFFD60A.toInt()) // Amarillo elegante
            }
            setOnClickListener {
                vibrateTick()
                showCreateDialog()
            }
        }
        topRow.addView(btnNew)
        header.addView(topRow)

        tvSubtitle = TextView(this).apply {
            text = "Cargando notas..."
            textSize = 11f
            setTextColor(0xFF8E8E93.toInt())
            setPadding(0, (2 * d).toInt(), 0, 0)
        }
        header.addView(tvSubtitle)
        root.addView(header)

        // 2. Barra de plantillas rápidas
        root.addView(createTemplatesBar(d))

        // 3. Lista de notas scrollable con corona
        scroll = object : ScrollView(this) {
            override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
                if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                    val delta = -ev.getAxisValue(MotionEvent.AXIS_SCROLL) * (60 * d)
                    smoothScrollBy(0, delta.toInt())
                    vibrateTick()
                    return true
                }
                return super.onGenericMotionEvent(ev)
            }
        }.apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        notesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (40 * d).toInt())
        }
        scroll.addView(notesContainer)
        root.addView(scroll)

        setContentView(root)
        refreshNotesList()
    }

    private fun createTemplatesBar(d: Float): HorizontalScrollView {
        val hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xFF0C0C0E.toInt())
            setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun addTemplatePill(iconTitle: String, cat: String, defaultTitle: String, defaultBody: String) {
            val pill = TextView(this).apply {
                text = iconTitle
                textSize = 11f
                setTextColor(0xFFE5E5EA.toInt())
                setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10 * d
                    setColor(0xFF242426.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (6 * d).toInt()
                }
                setOnClickListener {
                    vibrateTick()
                    db.insertNote(defaultTitle, defaultBody, cat)
                    refreshNotesList()
                    Toast.makeText(this@NotesActivity, "Nota creada: $defaultTitle", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(pill)
        }

        addTemplatePill("🛒 Compra", "compra", "🛒 Compra", "[ ] ")
        addTemplatePill("🏋️ Gym", "gym", "🏋️ Rutina Gym", "• Ejercicio: ")
        addTemplatePill("💡 Idea", "idea", "💡 Idea", "")
        addTemplatePill("🔑 PIN / Código", "pin", "🔑 Código", "")
        addTemplatePill("🎤 Audio", "voz", "🎤 Nota de Voz", "")

        hScroll.addView(row)
        return hScroll
    }

    private fun refreshNotesList() {
        notesContainer.removeAllViews()
        val d = resources.displayMetrics.density
        val notes = db.getAllNotes()

        tvSubtitle.text = "${notes.size} nota(s) offline"

        if (notes.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No tienes notas todavía.\nToca '+ Nueva' o una plantilla rápida."
                textSize = 13f
                setTextColor(0xFF8E8E93.toInt())
                gravity = Gravity.CENTER
                setPadding((20 * d).toInt(), (40 * d).toInt(), (20 * d).toInt(), (40 * d).toInt())
            }
            notesContainer.addView(emptyTv)
            return
        }

        for (note in notes) {
            notesContainer.addView(createNoteCard(note, d))
        }
    }

    private fun createNoteCard(note: NoteItem, d: Float): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14 * d
                setColor(0xFF1C1C1E.toInt())
            }
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * d).toInt()
            }
        }

        // Fila de arriba de la tarjeta: Categoría + Pin + Fecha
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val catColor = when (note.category) {
            "compra" -> 0xFF30D158.toInt()
            "gym" -> 0xFFFF9F0A.toInt()
            "idea" -> 0xFFBF5AF2.toInt()
            "pin" -> 0xFFFF453A.toInt()
            "voz" -> 0xFF00E5FF.toInt()
            else -> 0xFF0A84FF.toInt()
        }

        val catTag = TextView(this).apply {
            text = note.category.uppercase(Locale.ROOT)
            textSize = 9f
            setTextColor(catColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((6 * d).toInt(), (1 * d).toInt(), (6 * d).toInt(), (1 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 4 * d
                setColor(0x33000000 or (catColor and 0x00FFFFFF))
            }
        }
        topRow.addView(catTag)

        if (note.isPinned) {
            val pinIcon = TextView(this).apply {
                text = " 📌"
                textSize = 11f
            }
            topRow.addView(pinIcon)
        }

        val space = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        topRow.addView(space)

        val dateTv = TextView(this).apply {
            text = timeFmt.format(Date(note.updatedAt))
            textSize = 10f
            setTextColor(0xFF8E8E93.toInt())
        }
        topRow.addView(dateTv)
        card.addView(topRow)

        // Título de la nota
        val titleTv = TextView(this).apply {
            text = note.title
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, (4 * d).toInt(), 0, (2 * d).toInt())
        }
        card.addView(titleTv)

        // Contenido / Tareas interactivas
        if (note.content.isNotBlank()) {
            val preview = TextView(this).apply {
                text = note.content
                textSize = 12f
                setTextColor(0xFFD1D1D6.toInt())
                maxLines = 4
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, (2 * d).toInt(), 0, (4 * d).toInt())
            }
            card.addView(preview)
        }

        // Si tiene audio adjunto: Botón de Reproducir Audio
        if (note.audioPath != null && File(note.audioPath).exists()) {
            val isPlaying = (currentlyPlayingId == note.id)
            val btnAudio = TextView(this).apply {
                text = if (isPlaying) "⏹ Detener audio" else "▶ Escuchar audio (${formatFileSize(File(note.audioPath).length())})"
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((8 * d).toInt(), (5 * d).toInt(), (8 * d).toInt(), (5 * d).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * d
                    setColor(if (isPlaying) 0xFFFF453A.toInt() else 0xFF00E5FF.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (4 * d).toInt()
                }
                setOnClickListener {
                    vibrateTick()
                    if (isPlaying) {
                        stopAudioPlayback()
                    } else {
                        playAudio(note)
                    }
                }
            }
            card.addView(btnAudio)
        }

        // Al pulsar la tarjeta: abrir opciones / editor
        card.setOnClickListener {
            vibrateTick()
            showNoteOptionsDialog(note)
        }

        return card
    }

    private fun showNoteOptionsDialog(note: NoteItem) {
        val options = arrayOf(
            "✏️ Editar contenido",
            if (note.isPinned) "Desfijar de arriba" else "📌 Fijar arriba",
            "🎤 Grabar nota de voz",
            "🗑️ Eliminar nota"
        )

        AlertDialog.Builder(this)
            .setTitle(note.title)
            .setItems(options) { _, which ->
                vibrateTick()
                when (which) {
                    0 -> showEditNoteDialog(note)
                    1 -> {
                        db.togglePin(note.id, note.isPinned)
                        refreshNotesList()
                    }
                    2 -> startVoiceRecordingForNote(note)
                    3 -> confirmDeleteNote(note)
                }
            }
            .setNegativeButton("Volver", null)
            .show()
    }

    private fun showCreateDialog() {
        val d = resources.displayMetrics.density
        val inputTitle = EditText(this).apply {
            hint = "Título..."
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8E8E93.toInt())
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * d
                setColor(0xFF2C2C2E.toInt())
            }
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
        }

        val inputBody = EditText(this).apply {
            hint = "Escribe el contenido aquí..."
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8E8E93.toInt())
            textSize = 13f
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * d
                setColor(0xFF2C2C2E.toInt())
            }
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            addView(inputTitle)
            addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, (8 * d).toInt()) })
            addView(inputBody)
        }

        AlertDialog.Builder(this)
            .setTitle("Nueva Nota")
            .setView(col)
            .setPositiveButton("Guardar") { _, _ ->
                val t = inputTitle.text.toString().trim()
                val b = inputBody.text.toString().trim()
                if (t.isNotBlank() || b.isNotBlank()) {
                    db.insertNote(t.ifBlank { "Nota rápida" }, b, "general")
                    refreshNotesList()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditNoteDialog(note: NoteItem) {
        val d = resources.displayMetrics.density
        val inputTitle = EditText(this).apply {
            setText(note.title)
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * d
                setColor(0xFF2C2C2E.toInt())
            }
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
        }

        val inputBody = EditText(this).apply {
            setText(note.content)
            setTextColor(Color.WHITE)
            textSize = 13f
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * d
                setColor(0xFF2C2C2E.toInt())
            }
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            addView(inputTitle)
            addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, (8 * d).toInt()) })
            addView(inputBody)
        }

        AlertDialog.Builder(this)
            .setTitle("Editar Nota")
            .setView(col)
            .setPositiveButton("Guardar") { _, _ ->
                val t = inputTitle.text.toString().trim()
                val b = inputBody.text.toString().trim()
                db.updateNote(note.id, t, b, note.category, note.isPinned, note.audioPath)
                refreshNotesList()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startVoiceRecordingForNote(note: NoteItem) {
        if (isRecording) {
            stopVoiceRecording(note)
            return
        }

        val dir = File(filesDir, "audio_notes").apply { mkdirs() }
        val file = File(dir, "note_${note.id}_${System.currentTimeMillis()}.m4a")

        try {
            val r = MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()

            recorder = r
            activeRecordingFile = file
            isRecording = true

            Toast.makeText(this, "🔴 Grabando voz... Toca para detener", Toast.LENGTH_SHORT).show()

            AlertDialog.Builder(this)
                .setTitle("Grabando Nota de Voz")
                .setMessage("Habla al micrófono del reloj...")
                .setPositiveButton("■ Detener y Guardar") { _, _ ->
                    stopVoiceRecording(note)
                }
                .setCancelable(false)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error de micro: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoiceRecording(note: NoteItem) {
        if (!isRecording) return
        isRecording = false
        val r = recorder
        recorder = null
        try {
            r?.stop()
        } catch (_: Exception) {}
        r?.release()

        val file = activeRecordingFile
        if (file != null && file.exists() && file.length() > 500) {
            db.updateNote(note.id, note.title, note.content, "voz", note.isPinned, file.absolutePath)
            Toast.makeText(this, "Audio guardado ✓", Toast.LENGTH_SHORT).show()
        }
        refreshNotesList()
    }

    private fun playAudio(note: NoteItem) {
        val path = note.audioPath ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "Archivo de audio no encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        stopAudioPlayback()

        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                stopAudioPlayback()
            }
            mp.prepare()
            mp.start()
            mediaPlayer = mp
            currentlyPlayingId = note.id
            refreshNotesList()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al reproducir audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudioPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentlyPlayingId = null
        refreshNotesList()
    }

    private fun confirmDeleteNote(note: NoteItem) {
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar nota?")
            .setMessage("\"${note.title}\" se borrará permanentemente.")
            .setPositiveButton("Eliminar") { _, _ ->
                db.deleteNote(note.id)
                refreshNotesList()
                Toast.makeText(this, "Nota eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatFileSize(bytes: Long): String {
        return if (bytes < 1024) "$bytes B" else "${bytes / 1024} KB"
    }

    private fun vibrateTick() {
        runCatching { vibrator?.vibrate(25) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioPlayback()
        if (isRecording) {
            try { recorder?.stop() } catch (_: Exception) {}
            recorder?.release()
        }
    }
}

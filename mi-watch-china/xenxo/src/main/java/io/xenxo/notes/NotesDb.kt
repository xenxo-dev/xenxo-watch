package io.xenxo.notes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

data class NoteItem(
    val id: Long,
    val title: String,
    val content: String,
    val category: String, // "general", "compra", "idea", "gym", "pin", "voz"
    val audioPath: String? = null,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

class NotesDb(private val ctx: Context) : SQLiteOpenHelper(ctx, "xenxo_notes.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'general',
                audio_path TEXT,
                is_pinned INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_notes_pinned ON notes(is_pinned DESC, updated_at DESC)")

        // Insertar notas de bienvenida útiles si es la primera vez
        insertDefaultNotes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun insertDefaultNotes(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val cv1 = ContentValues().apply {
            put("title", "🛒 Lista de Compra")
            put("content", "[ ] Huevos\n[ ] Avena\n[ ] Pechuga de pollo\n[ ] Plátanos\n[ ] Café")
            put("category", "compra")
            put("is_pinned", 1)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insert("notes", null, cv1)

        val cv2 = ContentValues().apply {
            put("title", "🏋️ Rutina Empuje Gym")
            put("content", "• Press banca 4x8 (80 kg)\n• Press militar 3x10 (45 kg)\n• Fondos 3x12\n• Elevaciones laterales 4x15")
            put("category", "gym")
            put("is_pinned", 0)
            put("created_at", now - 3600_000)
            put("updated_at", now - 3600_000)
        }
        db.insert("notes", null, cv2)
    }

    fun getAllNotes(): List<NoteItem> {
        val list = mutableListOf<NoteItem>()
        readableDatabase.rawQuery(
            "SELECT id, title, content, category, audio_path, is_pinned, created_at, updated_at FROM notes ORDER BY is_pinned DESC, updated_at DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    NoteItem(
                        id = c.getLong(0),
                        title = c.getString(1),
                        content = c.getString(2),
                        category = c.getString(3),
                        audioPath = c.getString(4),
                        isPinned = c.getInt(5) == 1,
                        createdAt = c.getLong(6),
                        updatedAt = c.getLong(7)
                    )
                )
            }
        }
        return list
    }

    fun getNote(id: Long): NoteItem? {
        readableDatabase.rawQuery(
            "SELECT id, title, content, category, audio_path, is_pinned, created_at, updated_at FROM notes WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                return NoteItem(
                    id = c.getLong(0),
                    title = c.getString(1),
                    content = c.getString(2),
                    category = c.getString(3),
                    audioPath = c.getString(4),
                    isPinned = c.getInt(5) == 1,
                    createdAt = c.getLong(6),
                    updatedAt = c.getLong(7)
                )
            }
        }
        return null
    }

    fun insertNote(title: String, content: String, category: String = "general", audioPath: String? = null, isPinned: Boolean = false): Long {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("title", title.ifBlank { "Sin título" })
            put("content", content)
            put("category", category)
            put("audio_path", audioPath)
            put("is_pinned", if (isPinned) 1 else 0)
            put("created_at", now)
            put("updated_at", now)
        }
        return writableDatabase.insert("notes", null, cv)
    }

    fun updateNote(id: Long, title: String, content: String, category: String, isPinned: Boolean, audioPath: String? = null): Boolean {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("title", title.ifBlank { "Sin título" })
            put("content", content)
            put("category", category)
            if (audioPath != null) put("audio_path", audioPath)
            put("is_pinned", if (isPinned) 1 else 0)
            put("updated_at", now)
        }
        return writableDatabase.update("notes", cv, "id = ?", arrayOf(id.toString())) > 0
    }

    fun togglePin(id: Long, currentPin: Boolean): Boolean {
        val cv = ContentValues().apply {
            put("is_pinned", if (currentPin) 0 else 1)
            put("updated_at", System.currentTimeMillis())
        }
        return writableDatabase.update("notes", cv, "id = ?", arrayOf(id.toString())) > 0
    }

    fun deleteNote(id: Long): Boolean {
        val note = getNote(id)
        if (note?.audioPath != null) {
            runCatching { File(note.audioPath).delete() }
        }
        return writableDatabase.delete("notes", "id = ?", arrayOf(id.toString())) > 0
    }
}

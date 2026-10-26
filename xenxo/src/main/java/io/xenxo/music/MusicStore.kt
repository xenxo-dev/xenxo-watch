package io.xenxo.music

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

/**
 * Gestor de almacenamiento local para "Música xenxo".
 * Implementa una cola rotativa FIFO con un límite estricto de [MAX_TRACKS] canciones (20 canciones).
 * Al añadir la canción 21, se elimina automáticamente la más antigua del disco y de la base de datos
 * para mantener la memoria del reloj ligera y eficiente.
 */
class MusicStore(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "xenxo-music"
        const val DB_NAME = "xenxo_music.db"
        const val DB_VERSION = 1
        const val MAX_TRACKS = 20

        private const val TABLE_TRACKS = "tracks"
        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_ARTIST = "artist"
        private const val COL_DURATION = "duration_sec"
        private const val COL_PATH = "file_path"
        private const val COL_ADDED_AT = "added_at"
        private const val COL_LAST_PLAYED = "last_played_at"
        private const val COL_SIZE = "file_size_bytes"

        @Volatile
        private var instance: MusicStore? = null

        fun get(context: Context): MusicStore {
            return instance ?: synchronized(this) {
                instance ?: MusicStore(context.applicationContext).also { instance = it }
            }
        }
    }

    /** Directorio exclusivo para almacenar las canciones en el reloj. */
    val musicDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(context.filesDir, "music")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TRACKS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_DURATION INTEGER NOT NULL DEFAULT 0,
                $COL_PATH TEXT NOT NULL,
                $COL_ADDED_AT INTEGER NOT NULL,
                $COL_LAST_PLAYED INTEGER NOT NULL DEFAULT 0,
                $COL_SIZE INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migraciones futuras
    }

    /**
     * Guarda una canción desde un archivo fuente local, aplicando la política de rotación
     * automática de 20 canciones. Si ya hay 20 canciones, borra la más antigua.
     */
    @Synchronized
    fun saveTrack(
        id: String,
        title: String,
        artist: String,
        durationSec: Int,
        sourceFile: File
    ): Track? {
        if (!sourceFile.exists() || sourceFile.length() <= 0) {
            Log.e(TAG, "El archivo fuente no existe o está vacío: ${sourceFile.absolutePath}")
            return null
        }

        // 1. Política de rotación FIFO: hacer espacio si se alcanza el límite
        evictOldestIfNeeded()

        // 2. Copiar archivo al directorio de música de xenxo
        val ext = sourceFile.extension.ifEmpty { "mp3" }
        val safeName = "track_${System.currentTimeMillis()}_${id.hashCode().coerceAtLeast(0)}.$ext"
        val destFile = File(musicDir, safeName)

        try {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al copiar archivo de audio", e)
            if (destFile.exists()) destFile.delete()
            return null
        }

        val addedAt = System.currentTimeMillis()
        val fileSize = destFile.length()

        // 3. Registrar en SQLite
        val cv = ContentValues().apply {
            put(COL_ID, id)
            put(COL_TITLE, title.trim())
            put(COL_ARTIST, artist.trim())
            put(COL_DURATION, durationSec)
            put(COL_PATH, destFile.absolutePath)
            put(COL_ADDED_AT, addedAt)
            put(COL_LAST_PLAYED, 0L)
            put(COL_SIZE, fileSize)
        }

        writableDatabase.insertWithOnConflict(
            TABLE_TRACKS,
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        Log.i(TAG, "Canción guardada: '$title' por '$artist' ($fileSize bytes) -> ${destFile.name}")
        return Track(id, title, artist, durationSec, destFile.absolutePath, addedAt, 0L, fileSize)
    }

    /**
     * Guarda una canción a partir de un flujo de bytes (InputStream).
     */
    @Synchronized
    fun saveTrackFromStream(
        id: String,
        title: String,
        artist: String,
        durationSec: Int,
        stream: InputStream,
        extension: String = "mp3"
    ): Track? {
        evictOldestIfNeeded()

        val safeName = "track_${System.currentTimeMillis()}_${id.hashCode().coerceAtLeast(0)}.$extension"
        val destFile = File(musicDir, safeName)

        try {
            FileOutputStream(destFile).use { output ->
                stream.copyTo(output)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando stream de audio", e)
            if (destFile.exists()) destFile.delete()
            return null
        }

        val addedAt = System.currentTimeMillis()
        val fileSize = destFile.length()

        val cv = ContentValues().apply {
            put(COL_ID, id)
            put(COL_TITLE, title.trim())
            put(COL_ARTIST, artist.trim())
            put(COL_DURATION, durationSec)
            put(COL_PATH, destFile.absolutePath)
            put(COL_ADDED_AT, addedAt)
            put(COL_LAST_PLAYED, 0L)
            put(COL_SIZE, fileSize)
        }

        writableDatabase.insertWithOnConflict(
            TABLE_TRACKS,
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        return Track(id, title, artist, durationSec, destFile.absolutePath, addedAt, 0L, fileSize)
    }

    /**
     * Comprueba si se ha alcanzado el límite de 20 canciones.
     * Si count >= MAX_TRACKS, borra del disco y de la base de datos la canción más antigua.
     */
    private fun evictOldestIfNeeded() {
        val count = getTrackCount()
        if (count < MAX_TRACKS) return

        val tracksToEvict = (count - MAX_TRACKS + 1).coerceAtLeast(1)
        val db = writableDatabase

        // Buscamos las canciones más antiguas por fecha de adición
        val cursor = db.query(
            TABLE_TRACKS,
            arrayOf(COL_ID, COL_PATH, COL_TITLE),
            null,
            null,
            null,
            null,
            "$COL_ADDED_AT ASC",
            tracksToEvict.toString()
        )

        val toDelete = mutableListOf<Pair<String, String>>()
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val pathIdx = c.getColumnIndexOrThrow(COL_PATH)
            val titleIdx = c.getColumnIndexOrThrow(COL_TITLE)
            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val path = c.getString(pathIdx)
                val title = c.getString(titleIdx)
                Log.i(TAG, "Auto-renovación FIFO: Eliminando canción antigua '$title' ($path)")
                toDelete.add(Pair(id, path))
            }
        }

        for ((id, path) in toDelete) {
            try {
                val f = File(path)
                if (f.exists()) {
                    val deleted = f.delete()
                    Log.d(TAG, "Archivo físico borrado ($path): $deleted")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo borrar archivo físico: $path", e)
            }
            db.delete(TABLE_TRACKS, "$COL_ID = ?", arrayOf(id))
        }
    }

    /**
     * Obtiene la lista de canciones ordenadas por fecha de inclusión (más recientes primero).
     */
    @Synchronized
    fun getAllTracks(): List<Track> {
        val tracks = mutableListOf<Track>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TRACKS,
            arrayOf(COL_ID, COL_TITLE, COL_ARTIST, COL_DURATION, COL_PATH, COL_ADDED_AT, COL_LAST_PLAYED, COL_SIZE),
            null,
            null,
            null,
            null,
            "$COL_ADDED_AT DESC"
        )

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val titleIdx = c.getColumnIndexOrThrow(COL_TITLE)
            val artistIdx = c.getColumnIndexOrThrow(COL_ARTIST)
            val durIdx = c.getColumnIndexOrThrow(COL_DURATION)
            val pathIdx = c.getColumnIndexOrThrow(COL_PATH)
            val addIdx = c.getColumnIndexOrThrow(COL_ADDED_AT)
            val playIdx = c.getColumnIndexOrThrow(COL_LAST_PLAYED)
            val sizeIdx = c.getColumnIndexOrThrow(COL_SIZE)

            while (c.moveToNext()) {
                val path = c.getString(pathIdx)
                // Verificación de integridad: comprobar que el archivo existe en disco
                val f = File(path)
                if (f.exists()) {
                    tracks.add(
                        Track(
                            id = c.getString(idIdx),
                            title = c.getString(titleIdx),
                            artist = c.getString(artistIdx),
                            durationSec = c.getInt(durIdx),
                            filePath = path,
                            addedAt = c.getLong(addIdx),
                            lastPlayedAt = c.getLong(playIdx),
                            fileSizeBytes = c.getLong(sizeIdx)
                        )
                    )
                }
            }
        }
        return tracks
    }

    @Synchronized
    fun getTrack(id: String): Track? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TRACKS,
            null,
            "$COL_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        return cursor.use { c ->
            if (c.moveToFirst()) {
                Track(
                    id = c.getString(c.getColumnIndexOrThrow(COL_ID)),
                    title = c.getString(c.getColumnIndexOrThrow(COL_TITLE)),
                    artist = c.getString(c.getColumnIndexOrThrow(COL_ARTIST)),
                    durationSec = c.getInt(c.getColumnIndexOrThrow(COL_DURATION)),
                    filePath = c.getString(c.getColumnIndexOrThrow(COL_PATH)),
                    addedAt = c.getLong(c.getColumnIndexOrThrow(COL_ADDED_AT)),
                    lastPlayedAt = c.getLong(c.getColumnIndexOrThrow(COL_LAST_PLAYED)),
                    fileSizeBytes = c.getLong(c.getColumnIndexOrThrow(COL_SIZE))
                )
            } else null
        }
    }

    @Synchronized
    fun deleteTrack(id: String): Boolean {
        val track = getTrack(id) ?: return false
        try {
            val f = File(track.filePath)
            if (f.exists()) f.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Error borrando archivo de track $id", e)
        }
        val rows = writableDatabase.delete(TABLE_TRACKS, "$COL_ID = ?", arrayOf(id))
        return rows > 0
    }

    @Synchronized
    fun updateLastPlayed(id: String) {
        val cv = ContentValues().apply {
            put(COL_LAST_PLAYED, System.currentTimeMillis())
        }
        writableDatabase.update(TABLE_TRACKS, cv, "$COL_ID = ?", arrayOf(id))
    }

    @Synchronized
    fun getTrackCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_TRACKS", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    @Synchronized
    fun getTotalSizeBytes(): Long {
        val cursor = readableDatabase.rawQuery("SELECT SUM($COL_SIZE) FROM $TABLE_TRACKS", null)
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    /**
     * Importa un archivo de audio recién recibido (ej. por Bluetooth desde Spotube),
     * extrayendo metadatos ID3 reales (título, artista, duración) y aplicando la rotación FIFO de 20 canciones.
     */
    @Synchronized
    fun importSingleFile(file: File, fallbackName: String? = null): Track? {
        if (!file.exists() || file.length() <= 0) return null

        var title = (fallbackName ?: file.nameWithoutExtension).replace(Regex("[-_]"), " ")
        var artist = "Spotube"
        var durationSec = 0

        try {
            val mmr = android.media.MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val t = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            val a = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val d = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!t.isNullOrBlank()) title = t.trim()
            if (!a.isNullOrBlank()) artist = a.trim()
            if (!d.isNullOrBlank()) durationSec = (d.toLongOrNull() ?: 0L).toInt() / 1000
            mmr.release()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron extraer tags ID3 de ${file.name}", e)
        }

        val id = "spotube_${title.lowercase().hashCode().coerceAtLeast(0)}_${file.length()}"
        return saveTrack(id, title, artist, durationSec, file)
    }

    /**
     * Resumen de capacidad actual formateado (ej. "3/20 canciones · 14.5 MB").
     */
    fun getStorageSummary(): String {
        val count = getTrackCount()
        val bytes = getTotalSizeBytes()
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.ROOT, "%d/%d canciones · %.1f MB", count, MAX_TRACKS, mb)
    }

    @Synchronized
    fun clearAll() {
        val tracks = getAllTracks()
        for (t in tracks) {
            try {
                File(t.filePath).delete()
            } catch (_: Exception) {}
        }
        writableDatabase.delete(TABLE_TRACKS, null, null)
    }
}

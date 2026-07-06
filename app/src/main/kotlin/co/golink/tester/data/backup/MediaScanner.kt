package co.golink.tester.data.backup

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Colecções suportadas pelo backup automático. */
enum class BackupCollection(val defaultFolder: String) {
    IMAGES("Imagens"),
    VIDEOS("Vídeos"),
    AUDIOS("Áudios"),
    DOCUMENTS("Documentos"),
    DOWNLOADS("Downloads"),
}

data class BackupItem(
    val mediaStoreId: Long,
    val uri: Uri,
    val collection: BackupCollection,
    val displayName: String,
    val sizeBytes: Long = 0L,
    // Pasta de origem (BUCKET_DISPLAY_NAME: "Camera", "Screenshots", …) —
    // enviada ao servidor para organizar o backup em subpastas.
    val bucket: String? = null,
) {
    val isVideo: Boolean get() = collection == BackupCollection.VIDEOS
}

/** Uma pasta do dispositivo dentro de uma colecção, com o nº de ficheiros. */
data class BackupFolder(val name: String, val count: Int)

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scanNew(
        collection: BackupCollection,
        sinceId: Long,
        limit: Int,
        folders: Set<String>? = null,
    ): List<BackupItem> = query(collection, sinceId, limit, folders)

    fun countNew(collection: BackupCollection, sinceId: Long, folders: Set<String>? = null): Int {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val (selection, args) = selectionFor(collection, sinceId, folders)
        return runCatching {
            context.contentResolver.query(contentUri(collection), projection, selection, args, null)
                ?.use { it.count }
                ?: 0
        }.getOrDefault(0)
    }

    /** Soma o tamanho (bytes) de tudo o que falta enviar — para o "X MB de Y MB". */
    fun sumNewBytes(collection: BackupCollection, sinceId: Long, folders: Set<String>? = null): Long {
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        val (selection, args) = selectionFor(collection, sinceId, folders)
        return runCatching {
            context.contentResolver.query(contentUri(collection), projection, selection, args, null)?.use { c ->
                val idx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                var sum = 0L
                while (c.moveToNext()) {
                    if (idx >= 0 && !c.isNull(idx)) sum += c.getLong(idx).coerceAtLeast(0L)
                }
                sum
            } ?: 0L
        }.getOrDefault(0L)
    }

    /**
     * Lista as pastas (BUCKET_DISPLAY_NAME) existentes numa colecção, com o nº
     * de ficheiros em cada, ordenadas por contagem decrescente. Usado para o
     * utilizador escolher que pastas entram no backup. Pastas sem nome caem no
     * nome por omissão da colecção.
     */
    @Suppress("DEPRECATION")
    fun listFolders(collection: BackupCollection): List<BackupFolder> = runCatching {
        val hasBucketColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            (collection != BackupCollection.DOCUMENTS && collection != BackupCollection.DOWNLOADS)
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            if (hasBucketColumn) add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.DATA)
        }.toTypedArray()
        // sinceId=0 → toda a colecção (não só o que falta enviar).
        val (selection, args) = selectionFor(collection, sinceId = 0L, folders = null)
        val counts = LinkedHashMap<String, Int>()
        context.contentResolver.query(contentUri(collection), projection, selection, args, null)?.use { c ->
            val bucketIdx = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            while (c.moveToNext()) {
                val name = bucketNameFrom(c, bucketIdx, dataIdx) ?: collection.defaultFolder
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        counts.entries
            .map { BackupFolder(it.key, it.value) }
            .sortedWith(compareByDescending<BackupFolder> { it.count }.thenBy { it.name.lowercase() })
    }.getOrDefault(emptyList())

    private fun contentUri(collection: BackupCollection): Uri = when (collection) {
        BackupCollection.IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        BackupCollection.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        BackupCollection.AUDIOS -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        // Documentos/Downloads não têm colecção própria — vêm de Files,
        // filtrados por pasta. No Android 13+ só os ficheiros multimédia são
        // visíveis a apps de terceiros via MediaStore (limitação da plataforma).
        BackupCollection.DOCUMENTS, BackupCollection.DOWNLOADS ->
            MediaStore.Files.getContentUri("external")
    }

    @Suppress("DEPRECATION")
    private fun selectionFor(
        collection: BackupCollection,
        sinceId: Long,
        folders: Set<String>?,
    ): Pair<String, Array<String>> {
        val base = "${MediaStore.MediaColumns._ID} > ?"
        val args = mutableListOf(sinceId.toString())
        val extra = when (collection) {
            BackupCollection.DOCUMENTS, BackupCollection.DOWNLOADS -> {
                val pathColumn: String
                val pattern: String
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    pathColumn = MediaStore.MediaColumns.RELATIVE_PATH
                    pattern = if (collection == BackupCollection.DOCUMENTS) "Documents/%" else "Download/%"
                } else {
                    pathColumn = MediaStore.MediaColumns.DATA
                    pattern = if (collection == BackupCollection.DOCUMENTS) "%/Documents/%" else "%/Download/%"
                }
                args += pattern
                // MIME_TYPE IS NOT NULL exclui directórios.
                " AND $pathColumn LIKE ? AND ${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL"
            }
            else -> ""
        }
        // Filtro por pastas seleccionadas (opt-in). Só aplicável onde a coluna
        // BUCKET_DISPLAY_NAME existe: colecções de média em qualquer versão e
        // Files a partir do Android 10. Fora disso o filtro é ignorado.
        val bucketFilter = if (!folders.isNullOrEmpty() && bucketColumnAvailable(collection)) {
            val placeholders = folders.joinToString(",") { "?" }
            args += folders
            " AND ${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} IN ($placeholders)"
        } else ""
        return (base + extra + bucketFilter) to args.toTypedArray()
    }

    private fun bucketColumnAvailable(collection: BackupCollection): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            (collection != BackupCollection.DOCUMENTS && collection != BackupCollection.DOWNLOADS)

    // Extrai o nome da pasta do cursor: coluna BUCKET_DISPLAY_NAME quando existe,
    // caso contrário o penúltimo segmento do caminho (DATA), como no query().
    private fun bucketNameFrom(c: android.database.Cursor, bucketIdx: Int, dataIdx: Int): String? = when {
        bucketIdx >= 0 && !c.isNull(bucketIdx) -> c.getString(bucketIdx)
        dataIdx >= 0 && !c.isNull(dataIdx) ->
            c.getString(dataIdx)?.substringBeforeLast('/')?.substringAfterLast('/')
        else -> null
    }?.takeIf { it.isNotBlank() }

    @Suppress("DEPRECATION")
    private fun query(collection: BackupCollection, sinceId: Long, limit: Int, folders: Set<String>?): List<BackupItem> {
        val out = mutableListOf<BackupItem>()
        // BUCKET_DISPLAY_NAME só existe na colecção Files a partir do Android 10;
        // nas colecções de média existe desde sempre. Fallback: extrai do DATA.
        val hasBucketColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            collection != BackupCollection.DOCUMENTS && collection != BackupCollection.DOWNLOADS
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            if (hasBucketColumn) add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.DATA)
        }.toTypedArray()
        val (selection, selectionArgs) = selectionFor(collection, sinceId, folders)
        val sortOrder = "${MediaStore.MediaColumns._ID} ASC"
        val contentUri = contentUri(collection)

        // Android 11+ strips "LIMIT" from the sortOrder string and throws
        // SQLiteException("Invalid token LIMIT"). On those releases we must use
        // the Bundle-based query API and pass QUERY_ARG_LIMIT separately. Below
        // R we keep the legacy form because MediaStore on those versions
        // doesn't honour QUERY_ARG_LIMIT.
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }
            context.contentResolver.query(contentUri, projection, queryArgs, null)
        } else {
            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                "$sortOrder LIMIT $limit",
            )
        }

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val bucketIdx = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val name = c.getString(nameIdx) ?: "media-$id"
                val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else 0L
                val bucket = bucketNameFrom(c, bucketIdx, dataIdx)
                out += BackupItem(
                    mediaStoreId = id,
                    uri = ContentUris.withAppendedId(contentUri, id),
                    collection = collection,
                    displayName = name,
                    sizeBytes = size,
                    bucket = bucket,
                )
            }
        }
        return out
    }
}

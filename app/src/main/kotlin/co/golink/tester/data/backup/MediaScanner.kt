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

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scanNew(collection: BackupCollection, sinceId: Long, limit: Int): List<BackupItem> =
        query(collection, sinceId, limit)

    fun countNew(collection: BackupCollection, sinceId: Long): Int {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val (selection, args) = selectionFor(collection, sinceId)
        return runCatching {
            context.contentResolver.query(contentUri(collection), projection, selection, args, null)
                ?.use { it.count }
                ?: 0
        }.getOrDefault(0)
    }

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
    private fun selectionFor(collection: BackupCollection, sinceId: Long): Pair<String, Array<String>> {
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
        return (base + extra) to args.toTypedArray()
    }

    @Suppress("DEPRECATION")
    private fun query(collection: BackupCollection, sinceId: Long, limit: Int): List<BackupItem> {
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
        val (selection, selectionArgs) = selectionFor(collection, sinceId)
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
                val bucket = when {
                    bucketIdx >= 0 && !c.isNull(bucketIdx) -> c.getString(bucketIdx)
                    dataIdx >= 0 && !c.isNull(dataIdx) ->
                        c.getString(dataIdx)?.substringBeforeLast('/')?.substringAfterLast('/')
                    else -> null
                }?.takeIf { it.isNotBlank() }
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

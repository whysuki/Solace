package com.solace.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

class MediaRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun loadFolders(): List<MediaFolder> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
        )
        val selection =
            "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val groups = HashMap<Long, FolderBuilder>()
        resolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection,
            selection,
            args,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketIdCol)
                val isImage = cursor.getInt(mediaTypeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                val id = cursor.getLong(idCol)
                val builder = groups.getOrPut(bucketId) { FolderBuilder() }

                if (isImage) {
                    builder.imageCount++
                    if (builder.thumbnail?.type != MediaType.IMAGE) {
                        builder.thumbnail = thumbnailItem(id, isImage, cursor.getLong(dateCol))
                    }
                } else {
                    builder.videoCount++
                    if (builder.thumbnail == null) {
                        builder.thumbnail = thumbnailItem(id, isImage, cursor.getLong(dateCol))
                    }
                }

                builder.name = cursor.getString(bucketNameCol) ?: ""
                if (pathCol >= 0) {
                    builder.path = cursor.getString(pathCol) ?: ""
                }
            }
        }

        return groups.map { (bucketId, b) ->
            MediaFolder(
                bucketId = bucketId,
                name = b.name.ifBlank { b.path.ifBlank { "未分类" } },
                path = b.path,
                imageCount = b.imageCount,
                videoCount = b.videoCount,
                thumbnail = b.thumbnail,
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun loadFolderItems(
        bucketId: Long,
        offset: Int,
        limit: Int,
        sort: FolderSort,
        filter: MediaFilter,
    ): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
        )
        val mediaTypeSelection = when (filter) {
            MediaFilter.ALL ->
                "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
            MediaFilter.IMAGE, MediaFilter.VIDEO ->
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        }
        val mediaTypeArgs = when (filter) {
            MediaFilter.ALL -> arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            )
            MediaFilter.IMAGE -> arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            MediaFilter.VIDEO -> arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        }
        val selection = "${MediaStore.Files.FileColumns.BUCKET_ID} = ? AND $mediaTypeSelection"
        val args = arrayOf(bucketId.toString()) + mediaTypeArgs
        val (sortColumns, sortDirection) = sortSpec(sort)
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, sortColumns)
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, sortDirection)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }

        val items = mutableListOf<MediaItem>()
        resolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection,
            queryArgs,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val durationCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateTakenCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN)
            val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val isImage = cursor.getInt(mediaTypeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                val id = cursor.getLong(idCol)
                items += MediaItem(
                    id = id,
                    name = cursor.getString(nameCol) ?: "",
                    uri = mediaUri(id, isImage),
                    type = if (isImage) MediaType.IMAGE else MediaType.VIDEO,
                    mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else "",
                    durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0,
                    dateAdded = cursor.getLong(dateCol),
                    dateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0,
                    size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0,
                    path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else "",
                )
            }
        }
        return items
    }

    private fun sortSpec(sort: FolderSort): Pair<Array<String>, Int> = when (sort) {
        FolderSort.TIME -> arrayOf(
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns._ID,
        ) to ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
        FolderSort.NAME -> arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns._ID,
        ) to ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
        FolderSort.SIZE -> arrayOf(
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns._ID,
        ) to ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
    }

    private fun thumbnailItem(id: Long, isImage: Boolean, dateAdded: Long): MediaItem =
        MediaItem(
            id = id,
            name = "",
            uri = mediaUri(id, isImage),
            type = if (isImage) MediaType.IMAGE else MediaType.VIDEO,
            mimeType = "",
            dateAdded = dateAdded,
        )

    private fun mediaUri(id: Long, isImage: Boolean): Uri =
        if (isImage) {
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        } else {
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        }

    private class FolderBuilder {
        var imageCount = 0
        var videoCount = 0
        var name = ""
        var path = ""
        var thumbnail: MediaItem? = null
    }
}

package com.solace.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

class MediaRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** 媒体行共用投影；缩略图/时长等列在下查询按需取（getColumnIndex 兼容缺失列）。 */
    private val itemProjection = arrayOf(
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
        }
            // 作品集素材在 app 私有目录（getExternalFilesDir），MediaStore 中无作品集行，无需过滤。
            .sortedBy { it.name.lowercase() }
    }

    fun loadFolderItems(
        bucketId: Long,
        offset: Int,
        limit: Int,
        sort: FolderSort,
        filter: MediaFilter,
    ): List<MediaItem> {
        val (mediaTypeSelection, mediaTypeArgs) = mediaTypeClause(filter)
        val selection = "${MediaStore.Files.FileColumns.BUCKET_ID} = ? AND $mediaTypeSelection"
        val args = (listOf(bucketId.toString()) + mediaTypeArgs).toTypedArray()
        val (sortColumns, sortDirection) = sortSpec(sort)
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, sortColumns)
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, sortDirection)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        return queryItems(queryArgs)
    }

    /**
     * 最近媒体（作品创建素材选择器）：按时间倒序分页，覆盖图片+视频。
     * 作品集素材在 app 私有目录（不在 MediaStore 中），天然不会出现在这里。
     */
    fun loadRecentItems(offset: Int, limit: Int, filter: MediaFilter): List<MediaItem> {
        val (mediaTypeSelection, mediaTypeArgs) = mediaTypeClause(filter)
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        conditions += mediaTypeSelection
        args += mediaTypeArgs

        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, conditions.joinToString(" AND "))
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns._ID,
                ),
            )
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        return queryItems(queryArgs)
    }

    private fun queryItems(queryArgs: Bundle): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        resolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            itemProjection,
            queryArgs,
            null,
        )?.use { cursor -> items += cursor.parseItems() }
        return items
    }

    private fun Cursor.parseItems(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val idCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val mediaTypeCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val nameCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val mimeCol = getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
        val durationCol = getColumnIndex(MediaStore.Files.FileColumns.DURATION)
        val dateCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val dateTakenCol = getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN)
        val sizeCol = getColumnIndex(MediaStore.Files.FileColumns.SIZE)
        val pathCol = getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)

        while (moveToNext()) {
            val isImage = getInt(mediaTypeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            items += MediaItem(
                id = getLong(idCol),
                name = getString(nameCol) ?: "",
                uri = mediaUri(getLong(idCol), isImage),
                type = if (isImage) MediaType.IMAGE else MediaType.VIDEO,
                mimeType = if (mimeCol >= 0) getString(mimeCol) ?: "" else "",
                durationMs = if (durationCol >= 0) getLong(durationCol) else 0,
                dateAdded = getLong(dateCol),
                dateTaken = if (dateTakenCol >= 0) getLong(dateTakenCol) else 0,
                size = if (sizeCol >= 0) getLong(sizeCol) else 0,
                path = if (pathCol >= 0) getString(pathCol) ?: "" else "",
            )
        }
        return items
    }

    private fun mediaTypeClause(filter: MediaFilter): Pair<String, List<String>> = when (filter) {
        MediaFilter.ALL -> "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? " +
            "OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)" to listOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        MediaFilter.IMAGE -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?" to listOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        )
        MediaFilter.VIDEO -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?" to listOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
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

package com.solace.app.data

import android.net.Uri

enum class MediaType { IMAGE, VIDEO }

data class MediaItem(
    val id: Long,
    val name: String,
    val uri: Uri,
    val type: MediaType,
    val mimeType: String,
    val durationMs: Long = 0,
    val dateAdded: Long = 0,
    val dateTaken: Long = 0,
    val size: Long = 0,
    val path: String = "",
) {
    val key: String get() = "${type.name}_$id"
}

data class MediaFolder(
    val bucketId: Long,
    val name: String,
    val path: String,
    val imageCount: Int,
    val videoCount: Int,
    val thumbnail: MediaItem?,
)

enum class FolderSort { TIME, NAME, SIZE }

enum class MediaFilter { ALL, IMAGE, VIDEO }

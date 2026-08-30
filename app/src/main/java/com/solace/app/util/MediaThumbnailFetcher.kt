package com.solace.app.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 拦截 MediaStore 的 content:// Uri，走系统缩略图缓存（loadThumbnail），
 * 避免 Coil 默认按原图解码。非 MediaStore Uri 返回 null 交给 Coil 默认管道。
 */
class MediaThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val size: coil.size.Size,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val signal = CancellationSignal()
        try {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(signal) } ?: return null
            return DrawableResult(
                drawable = bitmap.toDrawable(context.resources),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        } catch (e: CancellationException) {
            signal.cancel()
            throw e
        } catch (_: Exception) {
            return null
        }
    }

    private fun loadBitmap(signal: CancellationSignal): android.graphics.Bitmap? {
        val width = (size.width as? Dimension.Pixels)?.px ?: DEFAULT_SIZE
        val height = (size.height as? Dimension.Pixels)?.px ?: DEFAULT_SIZE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(width, height), signal)
            }.getOrNull()
        } else {
            runCatching { loadLegacy() }.getOrNull()
        }
    }

    private fun loadLegacy(): android.graphics.Bitmap? {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        val resolver = context.contentResolver
        return if (uri.path?.contains("/images/") == true) {
            MediaStore.Images.Thumbnails.getThumbnail(
                resolver,
                id,
                MediaStore.Images.Thumbnails.MINI_KIND,
                null,
            )
        } else {
            MediaStore.Video.Thumbnails.getThumbnail(
                resolver,
                id,
                MediaStore.Video.Thumbnails.MINI_KIND,
                null,
            )
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == ContentResolver.SCHEME_CONTENT &&
                data.authority == MediaStore.AUTHORITY
            ) {
                MediaThumbnailFetcher(options.context, data, options.size)
            } else {
                null
            }
    }

    private companion object {
        const val DEFAULT_SIZE = 512
    }
}

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
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != ContentResolver.SCHEME_CONTENT ||
                data.authority != MediaStore.AUTHORITY
            ) {
                return null
            }
            // 仅对缩略图级别的请求走系统缩略图缓存；全屏查看的目标尺寸很大，
            // 落到 Coil 默认管道（按目标尺寸降采样解码，不整图解码，避免 OOM）。
            return if (options.size.shouldUseThumbnail()) {
                MediaThumbnailFetcher(options.context, data, options.size)
            } else {
                null
            }
        }
    }

    private companion object {
        const val DEFAULT_SIZE = 512
    }
}

// 缩略图尺寸上限（像素）：高于此视为全屏查看，交给 Coil 默认解码。
private const val MAX_THUMBNAIL = 1024

// 目标尺寸是否属于缩略图级别：两端可解析为像素且都不超过阈值才走缩略图缓存。
// Original（尺寸未知）交给默认解码，避免把全屏原图误当缩略图。
private fun coil.size.Size.shouldUseThumbnail(): Boolean {
    if (this == coil.size.Size.ORIGINAL) return false
    val w = (width as? Dimension.Pixels)?.px
    val h = (height as? Dimension.Pixels)?.px
    if (w == null || h == null) return false
    return w <= MAX_THUMBNAIL && h <= MAX_THUMBNAIL
}

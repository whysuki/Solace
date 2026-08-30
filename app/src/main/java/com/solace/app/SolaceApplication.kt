package com.solace.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.solace.app.util.MediaThumbnailFetcher

class SolaceApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(MediaThumbnailFetcher.Factory()) }
            // 系统缩略图已有磁盘缓存，Coil 磁盘缓存会双份占空间，关闭
            .diskCache(null)
            .build()
}

package com.solace.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.solace.app.util.MediaThumbnailFetcher

class SolaceApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(MediaThumbnailFetcher.Factory())
                // 请求目标尺寸超过缩略图门控（>1024px，如作品预览页）时交回默认管道解码；
                // 视频帧解码需要 coil-video（图片/视频全屏播放走 ExoPlayer，此处仅预览视频用）。
                add(VideoFrameDecoder.Factory())
            }
            // 系统缩略图已有磁盘缓存，Coil 磁盘缓存会双份占空间，关闭
            .diskCache(null)
            .build()
}

package top.gochiusa.glplayer.entity

import android.net.Uri

class MediaItem {

    internal val url: String
    internal val uri: Uri?

    private constructor(uri: Uri) {
        this.uri = uri
        url = ""
    }

    private constructor(url: String) {
        this.url = url
        uri = null
    }

    fun localSource(): Boolean = uri != null || !url.startsWith("http")

    companion object {

        /**
         * 通过Url创建MediaItem，支持http/https或者file://
         */
        fun fromUrl(url: String): MediaItem = MediaItem(url)

        /**
         * 通过[Uri]创建MediaItem
         * [Uri]可以指向本地文件、APP内的资源文件、网络上的文件(http/https)
         *
         * 使用[Uri]将关闭自动的网络连通检查
         */
        fun fromUri(uri: Uri): MediaItem = MediaItem(uri)
    }

}
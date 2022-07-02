package top.gochiusa.glplayer.data

import android.content.Context

interface MediaSourceFactory {

    fun createMediaSource(
        context: Context
    ): MediaSource
}
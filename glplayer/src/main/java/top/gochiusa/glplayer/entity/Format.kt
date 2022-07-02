package top.gochiusa.glplayer.entity

import android.media.MediaFormat

class Format(
    val mediaFormat: MediaFormat,
    internal val trackIndex: Int,
) {
    val sampleMimeType: String?

    val duration: Long

    // Video only
    val width: Int

    val height: Int

    val frameRate: Float

    val rotation: Int

    // Audio only.
    val channelCount: Int
    val sampleRate: Int

    init {
        mediaFormat.let {
            sampleMimeType = it.getString(MediaFormat.KEY_MIME)
            duration = it.getLongOrDefault(MediaFormat.KEY_DURATION, -1L)

            width = it.getIntOrDefault(MediaFormat.KEY_WIDTH, NO_VALUE)
            height = it.getIntOrDefault(MediaFormat.KEY_HEIGHT, NO_VALUE)
            frameRate = it.getFloatOrDefault(MediaFormat.KEY_FRAME_RATE, -1F)
            rotation = it.getIntOrDefault(MediaFormat.KEY_ROTATION, 0)

            channelCount = it.getIntOrDefault(MediaFormat.KEY_CHANNEL_COUNT, NO_VALUE)
            sampleRate = it.getIntOrDefault(MediaFormat.KEY_SAMPLE_RATE, NO_VALUE)
        }
    }

    fun isVideo(): Boolean = sampleMimeType?.let { sampleMimeType.startsWith("video/") } == true

    fun isAudio(): Boolean = sampleMimeType?.let { sampleMimeType.startsWith("audio/") } == true

    private fun MediaFormat.getLongOrDefault(name: String, def: Long): Long {
        return runCatching {
            getLong(name)
        }.getOrDefault(def)
    }

    private fun MediaFormat.getIntOrDefault(name: String, def: Int): Int {
        return runCatching {
            getInteger(name)
        }.getOrDefault(def)
    }

    private fun MediaFormat.getFloatOrDefault(name: String, def: Float): Float {
        return runCatching {
            getFloat(name)
        }.getOrDefault(def)
    }

    override fun toString(): String {
        return "[sampleType: $sampleMimeType, duration :$duration, width: $width, height: $height" +
                ", frameRate: $frameRate, channelCount: $channelCount, sampleRate: $sampleRate]"
    }

    companion object {
        const val NO_VALUE = -1
    }

}
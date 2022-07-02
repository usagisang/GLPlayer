package top.gochiusa.glplayer.data

import android.content.Context
import android.media.MediaExtractor
import android.net.ConnectivityManager
import top.gochiusa.glplayer.base.Receiver
import top.gochiusa.glplayer.base.Sample
import top.gochiusa.glplayer.entity.Format
import top.gochiusa.glplayer.entity.MediaItem
import top.gochiusa.glplayer.entity.SampleData
import top.gochiusa.glplayer.util.NetworkUnreachableException
import top.gochiusa.glplayer.util.PlayerLog
import top.gochiusa.glplayer.util.getActiveNetworkCompat
import java.io.IOException
import java.nio.ByteBuffer

class DefaultMediaSource(
    private var context: Context?
) : MediaSource {

    private var mediaExtractor: MediaExtractor? = null

    private val _formats: MutableList<Format> = mutableListOf()
    override val format: List<Format> = _formats

    private val receiverMap: MutableMap<Int, Receiver> = mutableMapOf()

    private var hasNext = true
    private val sample: Sample by lazy { DefaultSample() }

    private var _durationUs: Long = -1L
    override val durationUs: Long
        get() = _durationUs

    override val cacheDurationUs: Long
        get() = mediaExtractor?.cachedDuration ?: -1L

    private lateinit var mediaItem: MediaItem

    private var connectivityManager: ConnectivityManager? =
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager


    override fun bindTrack(format: Format, receiver: Receiver) {
        mediaExtractor?.selectTrack(format.trackIndex)
        receiverMap[format.trackIndex] = receiver
    }

    override fun unbindTrack(format: Format, receiver: Receiver) {
        if (receiverMap.containsKey(format.trackIndex)) {
            mediaExtractor?.unselectTrack(format.trackIndex)
            receiverMap.remove(format.trackIndex)
        }
    }

    override fun sendData(): Boolean {
        val extractor = mediaExtractor ?: return false
        if (extractor.sampleTrackIndex < 0) {
            return if (hasNext) {
                hasNext = extractor.advance()
                true
            } else {
                receiverMap.forEach {
                    it.value.receiveData(sample)
                }
                false
            }
        }
        val receiver = receiverMap[extractor.sampleTrackIndex]
        if (receiver == null) {
            hasNext = extractor.advance()
            return true
        }
        val consumed = receiver.receiveData(sample)
        if (consumed) {
            hasNext = extractor.advance()
        }
        return consumed
    }


    override fun setDataSource(
        mediaItem: MediaItem,
        requestHeaders: Map<String, String>?
    ) {
        releaseOldMediaExtractor()
        this.mediaItem = mediaItem
        val mediaExtractor = MediaExtractor()

        if (!mediaItem.localSource()) {
            connectivityManager?.run {
                if (getActiveNetworkCompat() == null) {
                    throw NetworkUnreachableException("network is unreachable, try setDataSource later")
                }
            }
        }

        // may block
        mediaItem.uri?.let {
            val context = context ?: throw IOException("cannot open the uri")
            mediaExtractor.setDataSource(context, it, requestHeaders)
        } ?: let {
            mediaExtractor.setDataSource(mediaItem.url)
        }
        _formats.clear()
        parseFormats(mediaExtractor)
        this.mediaExtractor = mediaExtractor
    }

    override fun release() {
        mediaExtractor?.release()
        context = null
        connectivityManager = null
    }

    override fun seekTo(positionUs: Long, seekMode: SeekMode): Long {
        val extractor = mediaExtractor ?: return _durationUs
        extractor.seekTo(positionUs, seekMode.mode)
        hasNext = true
        var cacheUs = extractor.cachedDuration
        var count = 0
        while (cacheUs < 500000 && count < 10) {
            try {
                Thread.sleep(10)
            } catch (e: Exception) {
                PlayerLog.v(message = e)
            }
            count ++
            cacheUs = extractor.cachedDuration
        }
        return extractor.sampleTime
    }

    override fun hasCacheReachedEndOfStream(): Boolean {
        return mediaExtractor?.hasCacheReachedEndOfStream() ?: true
    }

    private fun parseFormats(mediaExtractor: MediaExtractor) {
        for (i in 0 until mediaExtractor.trackCount) {
            val format = Format(mediaExtractor.getTrackFormat(i), i)
            _formats.add(format)
            if (_durationUs < 0) {
                _durationUs = format.duration
            }
        }
    }

    private fun releaseOldMediaExtractor() {
        runCatching {
            mediaExtractor?.release()
            mediaExtractor = null
            // 清除与旧的MediaExtractor相关的数据
            _durationUs = -1L
            hasNext = true
            receiverMap.clear()
        }
    }

    inner class DefaultSample: Sample {

        override fun readData(format: Format, byteBuffer: ByteBuffer): SampleData {
            if (!hasNext) {
                return SampleData(-1, _durationUs, 0, true)
            }
            val extractor = mediaExtractor!!

            val sampleTime = extractor.sampleTime
            val sampleFlags = extractor.sampleFlags

            val readSize = extractor.readSampleData(byteBuffer, 0)

            return SampleData(readSize, sampleTime, sampleFlags, false)
        }
    }
}

class DefaultMediaSourceFactory: MediaSourceFactory {
    override fun createMediaSource(context: Context): MediaSource {
        return DefaultMediaSource(context)
    }
}
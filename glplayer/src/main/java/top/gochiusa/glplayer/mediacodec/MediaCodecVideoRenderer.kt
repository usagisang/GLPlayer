package top.gochiusa.glplayer.mediacodec

import android.media.MediaCodec
import android.os.Build
import android.view.Surface
import top.gochiusa.glplayer.base.MediaClock
import top.gochiusa.glplayer.base.Sender
import top.gochiusa.glplayer.entity.Format
import top.gochiusa.glplayer.entity.MediaCodecConfiguration
import top.gochiusa.glplayer.listener.VideoFrameListener
import top.gochiusa.glplayer.listener.VideoMetadataListener
import top.gochiusa.glplayer.listener.VideoSurfaceListener
import top.gochiusa.glplayer.util.Constants
import top.gochiusa.glplayer.util.PlayerLog
import java.nio.ByteBuffer

class MediaCodecVideoRenderer(
    private val leadingLimitUs: Long = 1000000L,
    renderTimeLimitMs: Long = LIMIT_NOT_SET,
    private val syncLimitUs: Long = DEFAULT_VIDEO_SYNC_LIMIT,
    private var videoMetadataListener: VideoMetadataListener? = null,
    private var videoFrameListener: VideoFrameListener? = null
): MediaCodecRenderer(Constants.TRACK_TYPE_VIDEO, renderTimeLimitMs), VideoSurfaceListener {

    private val videoClock: MediaClock by lazy { VideoClock() }

    private var surface: Surface? = null
    private var videoFormat: Format? = null

    override fun onSenderChanged(
        format: List<Format>,
        oldSender: Sender?,
        newSender: Sender?,
        startPositionUs: Long
    ) {
        super.onSenderChanged(format, oldSender, newSender, startPositionUs)

        videoFormat?.let {
            oldSender?.unbindTrack(it, this)
            releaseCodec()
            videoFormat = null
        }
        for (f in sampleFormats) {
            if (f.isVideo()) {
                videoFormat = f
                newSender?.bindTrack(f, this)
                videoMetadataListener?.onVideoMetadataChanged(f)
                return
            }
        }
        if (sampleFormats.isNotEmpty()) {
            // 将播放位置重置为无效位置
            lastPositionUs = -1L
            PlayerLog.w(
                message = "The media file does not contain media types supported by " +
                        "MediaCodecVideoRenderer"
            )
        }
    }

    override fun processOutputBuffer(
        positionUs: Long,
        elapsedRealtimeUs: Long,
        codec: MediaCodec,
        buffer: ByteBuffer?,
        bufferIndex: Int,
        bufferInfo: MediaCodec.BufferInfo
    ): Boolean {
        val bufferSize: Int = bufferInfo.size
        val presentationTimeUs: Long = bufferInfo.presentationTimeUs
        //PlayerLog.d(message = "video position $positionUs bufferTime ${bufferInfo.presentationTimeUs}")
        if (surface == null || bufferSize <= 0) {
            buffer?.clear()
            codec.releaseOutputBuffer(bufferIndex, false)
            return true
        }
        val syncLimit = if (syncLimitUs > 0) syncLimitUs else 1

        val syncRange = (presentationTimeUs - syncLimit)..(presentationTimeUs
                + syncLimit)
        // 当前播放位置处于可同步的范围内(不超出此帧的PTS前后syncLimitMs内)
        return if (positionUs in syncRange) {
            try {
                codec.releaseOutputBuffer(bufferIndex, true)
                videoFrameListener?.onFrameRelease()
            } catch (error: MediaCodec.CodecException) {
                // 如果报出MediaCodec.CodecException，有可能是释放了一个不完整的帧
                PlayerLog.e(message = error)
            }
            true
        } else if (presentationTimeUs in positionUs..(positionUs + leadingLimitUs)) {
            // 此帧超前，但不超过leadingLimitMs指定的最大限度
            false
        } else {
            videoFrameListener?.onFrameLose()
            codec.releaseOutputBuffer(bufferIndex, false)
            true
        }
    }

    override fun getMediaCodecConfiguration(): MediaCodecConfiguration? {
        val format = videoFormat
        val s = surface
        if (s == null || format == null) {
            return null
        }
        return format.sampleMimeType?.let {
            MediaCodecConfiguration(
                type = it,
                format = format.mediaFormat,
                surface = s,
                crypto = null,
                flags = 0
            )
        }
    }

    override fun getFormat(): Format? = videoFormat

    override fun getMediaClock(): MediaClock {
        return videoClock
    }

    override fun onPause() {}

    override fun onVideoSurfaceCreated(surface: Surface) {
        setOutput(surface)
        videoFormat?.let {
            sender?.bindTrack(it, this)
        }
    }

    override fun onVideoSurfaceDestroyed(surface: Surface?) {
        setOutput(null)
        // 销毁Surface之后，解绑相应轨道
        videoFormat?.let {
            sender?.unbindTrack(it, this)
        }
    }

    private fun setOutput(output: Surface?) {
        if (output == surface) {
            return
        }
        val mediaCodec = codec
        // 如果mediaCodec已经创建并且存在旧的输出平面、并且新的平面不为空、并且API版本大于等于23
        if (mediaCodec != null && surface != null && output != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaCodec.setOutputSurface(output)
        } else {
            releaseCodec()
        }
        surface = output
    }

    inner class VideoClock: MediaClock {
        override fun getPositionUs(durationUs: Long): Long = lastPositionUs

        override fun getDurationUs(): Long {
            return videoFormat?.duration ?: -1L
        }
    }
}
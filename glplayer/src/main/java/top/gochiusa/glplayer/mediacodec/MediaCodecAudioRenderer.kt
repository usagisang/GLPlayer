package top.gochiusa.glplayer.mediacodec

import android.media.*
import top.gochiusa.glplayer.base.MediaClock
import top.gochiusa.glplayer.base.Sender
import top.gochiusa.glplayer.entity.Format
import top.gochiusa.glplayer.entity.MediaCodecConfiguration
import top.gochiusa.glplayer.util.Constants
import top.gochiusa.glplayer.util.PlayerLog
import java.lang.reflect.Method
import java.nio.ByteBuffer

class MediaCodecAudioRenderer(
    renderTimeLimitMs: Long = 0L,
): MediaCodecRenderer(Constants.TRACK_TYPE_AUDIO, renderTimeLimitMs) {

    private val audioClock: AudioClock by lazy { AudioClock() }

    private var audioTrack: AudioTrack? = null

    private var audioFormat: Format? = null

    private var bufferSize: Int = 0
    private var outputPcmFrameSize: Int = 0

    override fun onSenderChanged(
        format: List<Format>,
        oldSender: Sender?,
        newSender: Sender?,
        startPositionUs: Long
    ) {
        super.onSenderChanged(format, oldSender, newSender, startPositionUs)
        audioClock.reset()
        val cacheFormat = audioFormat
        var newFormat: Format?
        if (cacheFormat != null) {
            oldSender?.unbindTrack(cacheFormat, this)
            releaseCodec()
            newFormat = format.firstOrNull {
                it.isAudio() && it.sampleMimeType == cacheFormat.sampleMimeType
                    && it.channelCount== cacheFormat.channelCount
                    && it.sampleRate == cacheFormat.sampleRate
            }
            // 不管newFormat是否为null，更新audioFormat
            audioFormat = newFormat
            if (newFormat == null) {
                releaseAudioTrack()
            } else {
                // 如果该轨道的音频数据与之前的音频数据关于AudioTrack的参数一致，不需要release
                // 启用相关数据的读取
                newSender?.bindTrack(newFormat, this)
                return
            }
        }

        newFormat = format.firstOrNull { it.isAudio() }
        if (newFormat == null) {
            // 将播放位置重置为无效位置
            lastPositionUs = -1L
            // 找不到音频轨道
            PlayerLog.w(
                message = "The media file does not contain media types supported by " +
                        "MediaCodecAudioRenderer"
            )
        } else {
            audioFormat = newFormat
            // 启用相关数据的读取
            newSender?.bindTrack(newFormat, this)
            createAudioTrack(newFormat)
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
        val bufferSize = bufferInfo.size
        val audioOutput = audioTrack
        if (audioOutput == null || bufferSize <= 0 || buffer == null) {
            buffer?.clear()
            codec.releaseOutputBuffer(bufferIndex, false)
            return true
        }

        try {
            val writeSize = audioOutput.write(buffer.duplicate(), bufferSize,
                AudioTrack.WRITE_NON_BLOCKING)
            audioOutput.play()

            return if (writeSize < bufferSize) {
                bufferInfo.offset += writeSize
                bufferInfo.size -= writeSize
                false
            } else {
                codec.releaseOutputBuffer(bufferIndex, false)
                true
            }
        } catch (e: Exception) {
            PlayerLog.e(message = e)
            codec.releaseOutputBuffer(bufferIndex, false)
            return true
        }
    }

    override fun getMediaCodecConfiguration(): MediaCodecConfiguration? {
        return audioFormat?.sampleMimeType?.let {
            MediaCodecConfiguration(
                type = it,
                format = audioFormat?.mediaFormat,
                surface = null,
                crypto = null,
                flags = 0
            )
        }
    }

    override fun getFormat(): Format? = audioFormat

    override fun getMediaClock(): MediaClock {
        return audioClock
    }

    override fun onPause() {
        runCatching {
            audioTrack?.run {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    pause()
                }
            }
        }
    }

    override fun onDisabled(oldSender: Sender?) {
        super.onDisabled(oldSender)
        runCatching {
            audioClock.reset()
            audioTrack?.flush()
        }
    }

    override fun onSeekTo(startPositionUs: Long) {
        super.onSeekTo(startPositionUs)
        runCatching {
            audioClock.onSeekTo(startPositionUs)
            audioTrack?.run {
                flush()
                // 为了彻底清除playbackHeadPosition的计数
                stop()
            }
        }
    }

    private fun releaseAudioTrack() {
        audioTrack?.release()
        audioTrack = null
    }

    private fun createAudioTrack(format: Format) {
        if (audioTrack == null) {
            val channelConfiguration = if (format.channelCount == 1)
                AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            outputPcmFrameSize = format.channelCount * 2

            val minSize = AudioTrack.getMinBufferSize(
                format.sampleRate,
                channelConfiguration,
                encoding
            )
            val attribute: AudioAttributes = AudioAttributes.Builder()
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(format.sampleRate)
                .setChannelMask(channelConfiguration)
                .setEncoding(encoding)
                .build()

            bufferSize = minSize * 2

            audioTrack = AudioTrack(
                attribute, audioFormat, bufferSize,
                AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
            ).apply {
                this.playbackHeadPosition
            }
        }
    }

    inner class AudioClock : MediaClock {

        private var lastRawPlaybackHeadPosition: Long = 0L
        private var rawPlaybackHeadWrapCount: Long = 0L
        private var positionOffsetUs: Long = 0L

        private val getLatencyMethod: Method = AudioTrack::class.java.getMethod("getLatency")

        override fun getPositionUs(durationUs: Long): Long {
            // TODO 考虑支持更精细的计算方式
            /*val position = audioTrack?.getPlaybackPosition()

            return if(lastPositionUs >= durationUs) {
                MediaClock.END_OF_RENDER
            } else if (position != null && lastPositionUs > 0) {
                (position + positionOffsetUs)
            } else {
                lastPositionUs
            }*/
            val audioTrack = audioTrack ?: return -1L
            return if (lastPositionUs >= durationUs) {
                 MediaClock.END_OF_RENDER
            } else if (lastPositionUs > 0) {
                (lastPositionUs - audioTrack.getLatency()).coerceAtLeast(0L)
            } else {
                lastPositionUs
            }
        }

        override fun getDurationUs(): Long {
            return audioFormat?.duration ?: -1L
        }

        internal fun reset() {
            positionOffsetUs = 0L
            lastRawPlaybackHeadPosition = 0L
            rawPlaybackHeadWrapCount = 0L
        }

        internal fun onSeekTo(startPositionUs: Long) {
            positionOffsetUs = startPositionUs
            lastRawPlaybackHeadPosition = 0L
        }

        private fun framesToDurationUs(frameCount: Long, sampleRate: Int): Long {
            return frameCount * 1000000L / sampleRate
        }

        /**
         * 获取AudioTrack输出的帧计数
         */
        private fun AudioTrack.getPlaybackFramePosition(): Long {
            val rawPlaybackHeadPosition = 0xFFFFFFFFL and playbackHeadPosition.toLong()

            if (lastRawPlaybackHeadPosition > rawPlaybackHeadPosition) {
                // The value must have wrapped around.
                rawPlaybackHeadWrapCount++
            }
            lastRawPlaybackHeadPosition = rawPlaybackHeadPosition
            return rawPlaybackHeadPosition + (rawPlaybackHeadWrapCount shl 32)
        }

        /**
         * 根据[getPlaybackFramePosition]来估算[AudioTrack]的播放位置
         */
        private fun AudioTrack.getPlaybackPosition(): Long {
            return (framesToDurationUs(getPlaybackFramePosition(),
                sampleRate) - getLatencyWithoutBuffer()).coerceAtLeast(0L)
        }

        private fun AudioTrack.getLatencyWithoutBuffer(): Long {
            val bufferSizeUs = framesToDurationUs(
                bufferSize.toLong() / outputPcmFrameSize, sampleRate)
            return getLatency() - bufferSizeUs
        }

        /**
         * 根据隐藏的方法来获取AudioTrack的时间延迟信息
         */
        private fun AudioTrack.getLatency(): Long = getLatencyMethod.invoke(this) as Int * 1000L
    }
}
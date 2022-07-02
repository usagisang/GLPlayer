package top.gochiusa.glplayer.mediacodec

import android.media.MediaCodec
import android.os.SystemClock
import top.gochiusa.glplayer.base.BaseRenderer
import top.gochiusa.glplayer.base.MediaClock
import top.gochiusa.glplayer.base.Sample
import top.gochiusa.glplayer.base.Sender
import top.gochiusa.glplayer.entity.Format
import top.gochiusa.glplayer.entity.MediaCodecConfiguration
import top.gochiusa.glplayer.util.PlayerLog
import java.nio.ByteBuffer
import java.util.*

/**
 * 基于[MediaCodec]而实现的渲染适配器
 */
abstract class MediaCodecRenderer(
    trackType: Int,
    var renderTimeLimitMs: Long = LIMIT_NOT_SET
): BaseRenderer(trackType) {

    protected var lastPositionUs: Long = -1L

    protected var codec: MediaCodec? = null
        private set

    private val pendingBufferQueue: Queue<PendingBuffer> = PriorityQueue()


    final override fun render(positionUs: Long, elapsedRealtimeMs: Long) {
        super.render(positionUs, elapsedRealtimeMs)
        try {
            mayInitCodec()
            val renderStartTimeMs = SystemClock.elapsedRealtime()
            while (drainOutputBuffer(positionUs, elapsedRealtimeMs) && shouldContinueRendering(
                    renderStartTimeMs)) {}
        } catch (e: Exception) {
            if (e is IllegalStateException) {
                releaseCodec()
            } else {
                throw e
            }
        }
    }

    override fun onEnable() {}

    override fun onDisabled(oldSender: Sender?) {
        getFormat()?.let {
            oldSender?.unbindTrack(it, this)
        }
        flushCodec()
    }

    override fun release() {
        releaseCodec()
    }

    override fun onSeekTo(startPositionUs: Long) {
        flushCodec()
        lastPositionUs = startPositionUs
    }

    override fun onSenderChanged(
        format: List<Format>,
        oldSender: Sender?,
        newSender: Sender?,
        startPositionUs: Long
    ) {
        lastPositionUs = startPositionUs
        flushCodec()
    }

    override fun receiveData(sample: Sample): Boolean {
        return runCatching {
            feedInputBuffer(sample)
        }.onFailure {
            if (it is IllegalStateException) {
                releaseCodec()
            } else {
                throw it
            }
        }.getOrDefault(false)
    }

    /**
     * 交由子类实现的处理输出帧的具体逻辑
     * @param bufferIndex 缓冲区索引，父类保证其有效
     * @param bufferInfo 缓冲区的相关信息
     * @param buffer 如果配置了承接输出数据的surface，[buffer]为null
     * @return 如果调用了[MediaCodec.releaseOutputBuffer]，请返回true，否则请返回false
     */
    abstract fun processOutputBuffer(
        positionUs: Long,
        elapsedRealtimeUs: Long,
        codec: MediaCodec,
        buffer: ByteBuffer?,
        bufferIndex: Int,
        bufferInfo: MediaCodec.BufferInfo
    ): Boolean


    /**
     * 获取初始化设置MediaCodec的参数，可以为空，但这种情况下渲染器不会进行任何工作
     */
    abstract fun getMediaCodecConfiguration(): MediaCodecConfiguration?

    abstract fun getFormat(): Format?

    internal fun mayInitCodec() {
        if (codec == null) {
            initCodec()
        }
    }

    private fun initCodec() {
        val configuration = getMediaCodecConfiguration() ?: return
        runCatching {
            val mediaCodec = MediaCodec.createDecoderByType(configuration.type)
            with(configuration) {
                mediaCodec.configure(format, surface, crypto, flags)
            }
            mediaCodec.start()
            codec = mediaCodec
        }.onFailure {
            PlayerLog.e(message = "cannot init MediaCodec instance of type: ${configuration.type}" +
                    " info: ${it.message}")
            throw it
        }
    }

    internal fun flushCodec() {
        val codec = codec
        pendingBufferQueue.clear()
        try {
            codec?.flush()
        } catch (e: IllegalStateException) {
            releaseCodec()
        }
    }

    internal fun releaseCodec() {
        codec?.release()
        pendingBufferQueue.clear()
        codec = null
    }


    private fun feedInputBuffer(sample: Sample): Boolean {
        mayInitCodec()
        val mediaCodec = codec ?: return false

        val index = mediaCodec.dequeueInputBuffer(0)
        if (index < 0) {
            return false
        }

        val sampleData = sample.readData(getFormat()!!, mediaCodec.getInputBuffer(index)!!)

        return if (sampleData.endOfSample && sampleData.size < 0) {
            mediaCodec.queueInputBuffer(
                index, 0, 0, sampleData.sampleTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            true
        } else {
            mediaCodec.queueInputBuffer(
                index, 0, sampleData.size, sampleData.sampleTimeUs, 0
            )
            true
        }

    }

    /**
     * 判断渲染处理时间是否已经超时
     */
    private fun shouldContinueRendering(renderStartTimeMs: Long): Boolean {
        return renderTimeLimitMs == LIMIT_NOT_SET ||
                (SystemClock.elapsedRealtime() - renderStartTimeMs < renderTimeLimitMs)
    }

    private fun drainOutputBuffer(positionUs: Long, elapsedRealtimeUs: Long): Boolean {
        val mediaCodec = codec ?: return false

        var info = MediaCodec.BufferInfo()
        var index = mediaCodec.dequeueOutputBuffer(info, 0L)

        if (index >= 0) {
            pendingBufferQueue.offer(PendingBuffer(index, info))
        }
        val pendingBuffer = pendingBufferQueue.peek()
            ?: return index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED

        info = pendingBuffer.info
        index = pendingBuffer.index

        val outputBuffer = mediaCodec.getOutputBuffer(index)
        outputBuffer?.let {
            it.position(info.offset)
            it.limit(info.offset + info.size)
        }

        val processOutput = processOutputBuffer(
            positionUs,
            elapsedRealtimeUs,
            mediaCodec,
            outputBuffer,
            index,
            info
        )
        if (processOutput) {
            lastPositionUs = info.presentationTimeUs
            // 丢弃相应的信息
            pendingBufferQueue.poll()

            val hasNext = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0
            if (hasNext) {
                return true
            } else {
                lastPositionUs = MediaClock.END_OF_RENDER
            }
        }
        return false
    }

    companion object {
        const val LIMIT_NOT_SET = -1L
        const val DEFAULT_VIDEO_SYNC_LIMIT = 50000L
    }

    data class PendingBuffer(
        val index: Int,
        val info: MediaCodec.BufferInfo
    ): Comparable<PendingBuffer> {
        override fun compareTo(other: PendingBuffer): Int {
           return if (info.presentationTimeUs < other.info.presentationTimeUs) {
                -1
            } else if (info.presentationTimeUs == other.info.presentationTimeUs) {
                0
            } else {
                1
            }
        }
    }
}
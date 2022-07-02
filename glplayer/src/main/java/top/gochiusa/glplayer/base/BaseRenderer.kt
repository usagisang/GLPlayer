package top.gochiusa.glplayer.base

import top.gochiusa.glplayer.entity.Format
import top.gochiusa.glplayer.util.Assert

abstract class BaseRenderer(
    private val trackType: Int
): Renderer {

    final override var state: Int = Renderer.STATE_DISABLE
        private set

    protected var sampleFormats: List<Format> = emptyList()
    protected var sender: Sender? = null


    final override fun getTrackType(): Int = trackType

    final override fun enable(
        format: List<Format>,
        sender: Sender,
        positionUs: Long,
    ) {
        Assert.checkState(state == Renderer.STATE_DISABLE)
        state = Renderer.STATE_ENABLE

        onEnable()
        replaceSender(format, sender, positionUs)
    }

    final override fun replaceSender(format: List<Format>, sender: Sender?,
                                     startPositionUs: Long) {
        sampleFormats = format
        val oldSender = this.sender
        this.sender = sender

        onSenderChanged(format, oldSender, sender, startPositionUs)
    }

    final override fun disable() {
        state = Renderer.STATE_DISABLE
        val oldSender = sender
        sender = null
        sampleFormats = emptyList()
        onDisabled(oldSender)
    }

    override fun render(positionUs: Long, elapsedRealtimeMs: Long) {
        Assert.checkState(state == Renderer.STATE_ENABLE)
    }

    abstract fun onEnable()

    abstract fun onSenderChanged(format: List<Format>, oldSender: Sender?,
                                 newSender: Sender?, startPositionUs: Long)

    abstract fun onDisabled(oldSender: Sender?)
}
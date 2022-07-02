package top.gochiusa.glplayer.listener

interface VideoFrameListener {

    fun onFrameRelease()

    fun onFrameLose()
}
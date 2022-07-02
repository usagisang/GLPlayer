package top.gochiusa.glplayer.base

import top.gochiusa.glplayer.entity.Format
import top.gochiusa.glplayer.entity.SampleData
import java.nio.ByteBuffer

interface Sample {

    fun readData(format: Format, byteBuffer: ByteBuffer): SampleData
}

interface Sender {

    fun bindTrack(format: Format, receiver: Receiver)

    fun sendData(): Boolean

    fun unbindTrack(format: Format, receiver: Receiver)
}

interface Receiver {

    fun receiveData(sample: Sample): Boolean
}
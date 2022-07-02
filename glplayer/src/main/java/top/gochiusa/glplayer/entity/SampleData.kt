package top.gochiusa.glplayer.entity

data class SampleData(
    val size: Int,
    val sampleTimeUs: Long,
    val sampleFlags: Int,
    val endOfSample: Boolean
)
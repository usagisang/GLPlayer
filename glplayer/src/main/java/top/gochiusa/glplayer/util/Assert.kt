package top.gochiusa.glplayer.util

import android.os.Looper

internal object Assert {

    fun checkState(expression: Boolean, errorMsg: String? = null) {
        if (!expression) {
            throw IllegalStateException(errorMsg)
        }
    }

    fun verifyMainThread(message: String? = null) {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw IllegalStateException(message ?: "Player is accessed on the wrong thread")
        }
    }
}
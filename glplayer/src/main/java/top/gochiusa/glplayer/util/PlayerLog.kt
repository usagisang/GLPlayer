package top.gochiusa.glplayer.util

import android.util.Log

internal object PlayerLog {
    const val TAG = "GLPlayer"
    var show = true

    fun v(tag: String = TAG, message: Throwable): PlayerLog {
        if (show) {
            Log.v(tag, Log.getStackTraceString(message))
        }
        return this
    }

    fun v(tag: String = TAG, message: Any?): PlayerLog {
        if (show) {
            Log.v(tag, message.toString())
        }
        return this
    }

    fun d(tag: String = TAG, message: Throwable): PlayerLog {
        if (show) {
            Log.d(tag, Log.getStackTraceString(message))
        }
        return this
    }

    fun d(tag: String = TAG, message: Any?): PlayerLog {
        if (show) {
            Log.d(tag, message.toString())
        }
        return this
    }

    fun i(tag: String = TAG, message: Throwable): PlayerLog {
        if (show) {
            Log.i(tag, Log.getStackTraceString(message))
        }
        return this
    }

    fun i(tag: String = TAG, message: Any?): PlayerLog {
        if (show) {
            Log.i(tag, message.toString())
        }
        return this
    }

    fun w(tag: String = TAG, message: Throwable):PlayerLog {
        if (show) {
            Log.w(tag, Log.getStackTraceString(message))
        }
        return this
    }

    fun w(tag: String = TAG, message: Any?):PlayerLog {
        if (show) {
            Log.w(tag, message.toString())
        }
        return this
    }

    fun e(tag: String = TAG, message: Throwable): PlayerLog {
        if (show) {
            Log.e(tag, Log.getStackTraceString(message))
        }
        return this
    }

    fun e(tag: String = TAG, message: Any?): PlayerLog {
        if (show) {
            Log.e(tag, message.toString())
        }
        return this
    }

    fun showLog(b: Boolean): PlayerLog {
        show = b
        return this
    }
}
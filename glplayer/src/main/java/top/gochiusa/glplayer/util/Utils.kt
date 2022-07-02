package top.gochiusa.glplayer.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import androidx.annotation.RawRes
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * 从资源文件中加载字符串
 */
internal fun Context.readStringFromRaw(@RawRes resId: Int): String {
    return runCatching {
        val builder = StringBuilder()
        val reader = BufferedReader(InputStreamReader(resources.openRawResource(resId)))
        var nextLine: String? = reader.readLine()
        while (nextLine != null) {
            builder.append(nextLine).append("\n")
            nextLine = reader.readLine()
        }
        reader.close()
        builder.toString()
    }.onFailure {
        when(it) {
            is IOException -> {
                throw RuntimeException("Could not open resource: $resId", it)
            }
            is Resources.NotFoundException -> {
                throw RuntimeException("Resource not found: $resId", it)
            }
            else -> {}
        }
    }.getOrThrow()
}

/**
 * 判断是否为debug版本
 */
internal fun Context.isDebugVersion(): Boolean =
    runCatching {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }.getOrDefault(true)




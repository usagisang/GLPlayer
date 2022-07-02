package top.gochiusa.glplayer.util

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build


/**
 * 获取活跃网络信息的版本适配方法
 * @return 当前正在活跃的网络，可以为null
 */
internal fun ConnectivityManager.getActiveNetworkCompat(): Network? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        activeNetwork
    } else {
        // allNetworks在API 31被废弃，但是进入这个else语句的API低于23，因此可以使用
       allNetworks.find {
            // 寻找一个正在活跃的网络
            getNetworkCapabilities(it)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ?: false
        }
    }
}

class NetworkUnreachableException(message: String? = null) : IllegalStateException(message)

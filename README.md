# GLPlayer

![LICENSE](https://img.shields.io/badge/license-MIT-blue)

GLPlayer是一个在Android平台上工作的音频/视频播放器。"GL"两个字母取自"OpenGL"，表明这是利用OpenGL的相应API进行视频渲染的媒体播放器。它的主要功能实现依赖于Android平台内的基础Media组件：解封装媒体文件基于`MediaExtractor`，音频帧/视频帧基于`MediaCodec`进行硬解码，视频渲染基于OpenGL ES，音频渲染基于`AudioTrack`。

GLPlayer的API设计相当程度上参考了[ExoPlayer](https://github.com/google/ExoPlayer)，对于有音视频播放需求的开发者，强烈建议使用[ExoPlayer](https://github.com/google/ExoPlayer)而不是GLPlayer。**GLPlayer目前不能保证实际应用环境中的稳定性，也缺少ExoPlayer所提供的许多高级功能**。

## 如何添加该库

### Groovy

在`settings.gradle`添加：

```groovy
dependencyResolutionManagement {
    ...
    repositories {
        maven { url 'https://jitpack.io' }
        ...
    }
}
```

然后，在对应模块内的的`build.gradle`添加：

```groovy
dependencies {
    implementation 'com.github.usagisang:GLPlayer:0.1.0'
}
```

### Kotlin Script

在`settings.gradle.kts`添加：

```kotlin
dependencyResolutionManagement {
    ...
    repositories {
        ...
        maven(url = "https://jitpack.io")
    }
}
```

然后，在对应模块内的的`build.gradle.kts`添加：

```kotlin
dependencies {
    implementation("com.github.usagisang:GLPlayer:0.1.0")
}
```

## 快速使用指南

快速使用指南（Kotlin）：

```kotlin
// 构造Player实例
val player: Player = GLPlayerBuilder(context).setPlayAfterLoading(true).build()
// PlayerView是GLPlayer提供的视频渲染顶层View
val playerView = findViewById<PlayerView>(R.id.player_view)
// 绑定Lifecycle
playerView.bindLifecycle(lifecycle)
// 将Player绑定到PlayerView以进行视频渲染
playerView.setPlayer(player)
// 设置需要加载的媒体，这里采用网络url
player.setMediaItem(MediaItem.fromUrl(videoUrl))
// 开始准备媒体资源
player.prepare()
```

快速使用指南（Java）：

```java
private Player player;
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
    // ....
    String mediaUrl = "...";
    // 构造Player实例
    player = new GLPlayerBuilder(this).setPlayAfterLoading(true).build();
    // PlayerView是GLPlayer提供的视频渲染顶层View
    PlayerView playerView = findViewById(R.id.player_view);
    // 绑定Lifecycle
    playerView.bindLifecycle(getLifecycle());
    // 将Player绑定到PlayerView以进行视频渲染
    playerView.setPlayer(player);
    // 设置需要加载的媒体
    player.setMediaItem(MediaItem.Companion.fromUrl(mediaUrl));
    // 开始准备媒体资源
    player.prepare();
}
```

## 为什么建议为PlayerView绑定Lifecycle

GLPlayer使用OpenGL ES进行视频渲染，当PlayerView离开屏幕（例如，回到桌面或者锁屏），OpenGL所依赖的EGL Display等资源会被自动释放，这时如果仍试图更新纹理就会抛出异常，会导致后续`PlayerView`无法再次渲染视频。`PlayerView`自动处理了锁屏事件，但不绑定`Lifecycle`则无法监听与生命周期相关的事件。因此，如果希望播放器进入后台时也继续播放音频，并能够在应用回到前台后恢复视频渲染，建议为PlayerView绑定`Lifecycle`。

如果确认播放器不需要在后台播放视频的音频轨道，那么，只要在相应事件（`onPause`等）发生后调用`player.pause()`来停止播放，也可以避免EGL Display被释放的异常，这种情况下不需要为`PlayerView`绑定`Lifecycle`。

如果只打算播放纯音频，那么不需要使用PlayerView，安全忽略绑定Lifecycle的要求即可。

## LICENSE

```
MIT License

Copyright (c) 2022 usagisang

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
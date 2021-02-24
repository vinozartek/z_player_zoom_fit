package com.flutter_zplayer

import io.flutter.plugin.common.PluginRegistry
import com.flutter_zplayer.video.PlayerViewFactory

class FlutterZplayerPlugin {
  companion object {
    @JvmStatic
    fun registerWith(registrar: PluginRegistry.Registrar) {
      PlayerViewFactory.registerWith(registrar)
    }
  }
}

package app.sonder.sonder

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // SonderChannelPlugin is not a pub plugin, so GeneratedPluginRegistrant
        // does not know about it. Register it explicitly so the Dart
        // `sonder/access` MethodChannel/EventChannel have a native handler
        // while the engine lives. The accessibility service communicates via
        // the plugin's static event sink when the engine is not attached.
        flutterEngine.plugins.add(SonderChannelPlugin())
    }
}

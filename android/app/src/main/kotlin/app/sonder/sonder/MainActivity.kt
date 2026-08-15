package app.sonder.sonder

import android.content.Intent
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

        // If the activity was launched with a gate intent (cold start),
        // forward the extras to Flutter so the gate screen opens.
        forwardGateExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // When Sonder is already running and the overlay launches it via the
        // GATE intent (singleTop), forward the extras to Flutter.
        forwardGateExtras(intent)
    }

    private fun forwardGateExtras(intent: Intent?) {
        val packageName = intent?.getStringExtra("packageName") ?: return
        val surface = intent.getStringExtra("surface") ?: "wholeApp"
        SonderChannelPlugin.sendTargetIntercepted(
            packageName = packageName,
            surface = surface,
            atEpochMs = System.currentTimeMillis(),
        )
    }
}

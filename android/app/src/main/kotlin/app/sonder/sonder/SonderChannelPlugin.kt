package app.sonder.sonder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

private const val TAG = "SonderChannel"
private const val METHOD_CHANNEL = "sonder/access"
private const val EVENT_CHANNEL = "sonder/access/events"

/**
 * Flutter bridge for `sonder/access`.
 *
 * Exact contract per plan:
 *   Flutter -> native methods:
 *     syncSnapshots { snapshots: [...] }
 *     listLaunchableApps {}
 *     openAccessibilitySettings {}
 *     getEnforcementCapabilities {}
 *   Native -> Flutter events:
 *     targetIntercepted { packageName, surface, atEpochMs }
 *     targetForeground  { packageName, surface, atEpochMs }
 *     targetBackground  { packageName, surface, atEpochMs }
 *     serviceStateChanged { accessibilityEnabled, overlayAvailable }
 *
 * All method argument keys and event type strings are taken verbatim from
 * employ/plan.md so Flutter domain tests can exercise the bridge without
 * modification.
 */
class SonderChannelPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler {

    private var methodChannel: MethodChannel? = null
    private var eventChannel: EventChannel? = null
    private var context: Context? = null
    private var activity: Activity? = null
    private var sessionStore: SessionStore? = null
    private var appCatalog: AppTargetCatalog? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        sessionStore = SessionStore(binding.applicationContext)
        appCatalog = AppTargetCatalog(binding.applicationContext)
        methodChannel = MethodChannel(binding.binaryMessenger, METHOD_CHANNEL).also {
            it.setMethodCallHandler(this)
        }
        eventChannel = EventChannel(binding.binaryMessenger, EVENT_CHANNEL).also {
            it.setStreamHandler(EventStreamHandler())
        }
        instance = this
        Log.i(TAG, "SonderChannelPlugin attached (channel=$METHOD_CHANNEL)")
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel?.setMethodCallHandler(null)
        eventChannel?.setStreamHandler(null)
        methodChannel = null
        eventChannel = null
        sessionStore = null
        appCatalog = null
        context = null
        instance = null
    }

    // ActivityAware — needed for openAccessibilitySettings and for deep-link extras.
    override fun onAttachedToActivity(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivity() { activity = null }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "syncSnapshots" -> handleSyncSnapshots(call, result)
            "listLaunchableApps" -> handleListLaunchableApps(result)
            "openAccessibilitySettings" -> handleOpenAccessibilitySettings(result)
            "getEnforcementCapabilities" -> handleGetCapabilities(result)
            else -> result.notImplemented()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleSyncSnapshots(call: MethodCall, result: MethodChannel.Result) {
        val args = call.arguments as? Map<*, *> ?: run {
            result.error("ARG_ERROR", "syncSnapshots requires {snapshots: [...]}", null); return
        }
        val rawList = args["snapshots"] as? List<*> ?: run {
            result.error("ARG_ERROR", "syncSnapshots.snapshots must be a list", null); return
        }

        // Validate atomically before any write: malformed entries fail the whole batch.
        val typedList: List<Map<*, *>> = try {
            rawList.map { entry ->
                entry as? Map<*, *> ?: throw IllegalArgumentException("snapshot entry must be a map")
            }
        } catch (e: Exception) {
            result.error("VALIDATION_ERROR", "snapshot entry not a map: ${e.message}", null)
            return
        }

        val validated = SessionStore.validateSnapshotsList(typedList)
        if (validated == null) {
            // Per plan: malformed/unknown snapshot must fail closed for that target and log.
            // We fail the entire sync so no temporary grant is introduced.
            Log.w(TAG, "syncSnapshots rejected — one or more snapshots malformed/unknown (fail-closed)")
            result.error("VALIDATION_ERROR", "one or more snapshots malformed; no state was written", null)
            return
        }

        // Persist atomically on IO scope; reply synchronously after enqueue.
        val store = sessionStore ?: run { result.error("UNAVAILABLE", "SessionStore not attached", null); return }
        // Persist via coroutine; reply on main handler.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                store.syncSnapshotsTyped(validated)
                // Post result on main.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    result.success(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncSnapshots write failed", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    result.error("STORE_ERROR", e.message, null)
                }
            }
        }
    }

    private fun handleListLaunchableApps(result: MethodChannel.Result) {
        val cat = appCatalog ?: run {
            // Contract: Flutter's Job 04 MethodChannelAccessPlatform expects {apps: [...]}.
            // The plan allows either a list or wrapped map; we return the wrapped map for compat.
            result.success(mapOf("apps" to emptyList<Map<String, String>>()))
            return
        }
        val apps = cat.listLaunchableApps()
        val out = apps.map { mapOf("packageName" to it.packageName, "displayName" to it.displayName) }
        // Primary shape: wrapped map {apps: [...]} per Job 04's implementation.
        // Also accepted by plan as a bare list — callers handle both.
        result.success(mapOf("apps" to out))
    }

    private fun handleOpenAccessibilitySettings(result: MethodChannel.Result) {
        val ctx = context ?: activity ?: run { result.error("UNAVAILABLE", "No context", null); return }
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
            result.success(null)
        } catch (e: Exception) {
            Log.e(TAG, "openAccessibilitySettings failed", e)
            result.error("ACTIVITY_ERROR", e.message, null)
        }
    }

    private fun handleGetCapabilities(result: MethodChannel.Result) {
        val ctx = context ?: run {
            result.success(mapOf("accessibilityEnabled" to false, "overlayAvailable" to false)); return
        }
        // Check if our accessibility service is enabled.
        val accessibilityEnabled = isAccessibilityServiceEnabled(ctx)
        val overlayAvailable = GateOverlayController.isOverlayAvailable(ctx) && accessibilityEnabled
        result.success(mapOf("accessibilityEnabled" to accessibilityEnabled, "overlayAvailable" to overlayAvailable))
    }

    private fun isAccessibilityServiceEnabled(ctx: Context): Boolean {
        val expected = "${ctx.packageName}/${SonderAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(":").any { it == expected }
    }

    // -----------------------------------------------------------------------
    // Static event sink — allows SonderAccessibilityService to push events
    // without holding a Plugin reference (service and engine may be in different
    // lifecycles).
    // -----------------------------------------------------------------------
    companion object {
        private var instance: SonderChannelPlugin? = null
        private var eventSink: EventChannel.EventSink? = null
        private var pendingEvents: MutableList<Map<String, Any?>> = mutableListOf()

        fun notifyServiceStateChanged(accessibilityEnabled: Boolean, overlayAvailable: Boolean) {
            sendEvent(mapOf(
                "type" to "serviceStateChanged",
                "accessibilityEnabled" to accessibilityEnabled,
                "overlayAvailable" to overlayAvailable,
            ))
        }

        fun sendTargetIntercepted(packageName: String, surface: String, atEpochMs: Long) {
            sendEvent(mapOf(
                "type" to "targetIntercepted",
                "packageName" to packageName,
                "surface" to surface,
                "atEpochMs" to atEpochMs,
            ))
        }

        fun sendTargetForeground(packageName: String, surface: String, atEpochMs: Long) {
            sendEvent(mapOf(
                "type" to "targetForeground",
                "packageName" to packageName,
                "surface" to surface,
                "atEpochMs" to atEpochMs,
            ))
        }

        fun sendTargetBackground(packageName: String, surface: String, atEpochMs: Long) {
            sendEvent(mapOf(
                "type" to "targetBackground",
                "packageName" to packageName,
                "surface" to surface,
                "atEpochMs" to atEpochMs,
            ))
        }

        private fun sendEvent(event: Map<String, Any?>) {
            val sink = eventSink
            if (sink != null) {
                try { sink.success(event) } catch (e: Exception) { Log.w(TAG, "eventSink.success failed", e) }
            } else {
                // Buffer until Flutter subscribes (EventChannel lifecycle).
                pendingEvents.add(event)
                if (pendingEvents.size > 50) pendingEvents.removeAt(0)
                Log.d(TAG, "Buffered event (no sink): $event")
            }
        }

        internal fun setEventSink(sink: EventChannel.EventSink?) {
            eventSink = sink
            if (sink != null && pendingEvents.isNotEmpty()) {
                for (e in pendingEvents.toList()) {
                    try { sink.success(e) } catch (_: Exception) {}
                }
                pendingEvents.clear()
            }
        }
    }

    private class EventStreamHandler : EventChannel.StreamHandler {
        override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
            Log.i(TAG, "EventChannel onListen")
            setEventSink(events)
            // Emit initial service state immediately so Flutter can show correct education UI.
            val inst = instance
            if (inst != null) {
                val ctx = inst.context
                if (ctx != null) {
                    val enabled = try {
                        val expected = "${ctx.packageName}/${SonderAccessibilityService::class.java.canonicalName}"
                        val raw = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                        raw?.split(":")?.any { it == expected } == true
                    } catch (_: Exception) { false }
                    events.success(mapOf(
                        "type" to "serviceStateChanged",
                        "accessibilityEnabled" to enabled,
                        "overlayAvailable" to (enabled && GateOverlayController.isOverlayAvailable(ctx)),
                    ))
                }
            }
        }
        override fun onCancel(arguments: Any?) {
            Log.i(TAG, "EventChannel onCancel")
            setEventSink(null)
        }
    }
}

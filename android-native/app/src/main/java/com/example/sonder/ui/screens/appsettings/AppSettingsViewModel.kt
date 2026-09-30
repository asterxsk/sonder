package com.example.sonder.ui.screens.appsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.data.repo.InstalledAppsRepository
import com.example.sonder.domain.AccessPolicy
import com.example.sonder.domain.BlockScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How the settings screen finished, if it has: both outcomes close it onto the list. */
enum class AppSettingsFinish { SAVED, REMOVED }

/**
 * One published state for the per-app settings screen. The package is bound by the
 * screen once (the destination carries it as an argument), then the rule streams from
 * the repository so a change made elsewhere is reflected live.
 *
 * [maxMillis] is what the controls are showing, which is the draft whenever there is one —
 * not what is stored. That is the whole point of the SAVE step: the knob reads as the value
 * the user has chosen, and [dirty] is the only thing that says whether the app is actually
 * enforcing it yet.
 */
data class AppSettingsUiState(
    val packageName: String = "",
    /** null until the launcher enumeration resolves it; the header falls back to the id. */
    val label: String? = null,
    /** The bank ceiling: the most access this app can ever hold at once. */
    val maxMillis: Long = AccessPolicy.DEFAULT_MAX_MILLIS,
    /**
     * The scope as shown, which is the drafted one while there is a draft. It is not a
     * per-app number and does not fold into the draft value: it is a different kind of
     * target, so it is drafted and saved beside it rather than inside it.
     */
    val blockScope: BlockScope = BlockScope.WHOLE_APP,
    /**
     * Epoch millis the removal lock runs to, or 0 when removal is allowed.
     *
     * Set for twelve hours from the moment this app's bank ran out, whether it was spent or
     * lost: an app the user has just exhausted is exactly the one they would think to stop
     * tracking, and the lock is what makes the exhaustion mean something. It is a lock on
     * *removal* only — the app is gated as it always was, and a won hand clears it.
     */
    val removalLockedUntilMillis: Long = 0L,
    /**
     * True while the screen holds changes SAVE has not written. Nothing is written until the
     * second press of the hold button, so this is the app's own answer to "did my edit land?".
     */
    val dirty: Boolean = false,
    /**
     * True once a SAVE has completed and the screen has not been edited since. Never true
     * at the same time as [dirty].
     */
    val saved: Boolean = false,
) {
    /** True while this app's limit cannot be removed. */
    val removalLocked: Boolean
        get() = removalLockedUntilMillis > System.currentTimeMillis()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val appsRepository: InstalledAppsRepository,
    private val enforcement: EnforcementRepository,
) : ViewModel() {

    private val packageName = MutableStateFlow<String?>(null)
    private val label = MutableStateFlow<String?>(null)

    /** The in-flight label load, so a re-bind supersedes it instead of racing it. */
    private var labelJob: Job? = null

    /** Edits SAVE has not written. Null means "no edit"; a control reads `draft ?: stored`. */
    private val draftMax = MutableStateFlow<Long?>(null)
    private val draftScope = MutableStateFlow<BlockScope?>(null)

    /** True from the moment a SAVE lands until the next edit, so the badge can be shown. */
    private val saved = MutableStateFlow(false)

    /** The removal lock as last read. Refreshed on bind and after a refused removal. */
    private val lockedUntil = MutableStateFlow(0L)

    /**
     * Emits when the screen is done: the limit was saved, or the limit was removed. Both are
     * a completed act with nothing left to do on the page, so both close it onto the list.
     * One-shot, because a replay would navigate a screen the user has since returned to.
     */
    private val _finished = MutableSharedFlow<AppSettingsFinish>(extraBufferCapacity = 4)
    val finished: SharedFlow<AppSettingsFinish> = _finished

    private data class Stored(
        val packageName: String,
        val label: String?,
        val maxMillis: Long,
    )

    private val stored: Flow<Stored> = combine(
        packageName,
        label,
        packageName.flatMapLatest { name ->
            if (name == null) {
                flowOf(AccessPolicy.DEFAULT_MAX_MILLIS)
            } else {
                enforcement.observeRules(name).map { it.maxMillis }
            }
        },
    ) { name, resolvedLabel, max ->
        Stored(
            packageName = name.orEmpty(),
            label = resolvedLabel,
            maxMillis = max,
        )
    }

    private val scopeFlow: Flow<BlockScope> = packageName.flatMapLatest { name ->
        if (name == null) flowOf(BlockScope.WHOLE_APP) else enforcement.observeBlockScope(name)
    }

    /** The two halves of the draft as one value, so the combine below stays a typed one. */
    private data class Draft(val maxMillis: Long?, val scope: BlockScope?)

    private val draft: Flow<Draft> = combine(draftMax, draftScope) { max, scope ->
        Draft(max, scope)
    }

    val ui: StateFlow<AppSettingsUiState> =
        combine(
            stored,
            scopeFlow,
            draft,
            saved,
            lockedUntil,
        ) { storedNow, scopeStored, draftNow, savedNow, lockedNow ->
            // The draft wins where it exists, per field. A screen that has drafted only the
            // scope still shows the stored ceiling.
            val max = draftNow.maxMillis ?: storedNow.maxMillis
            val scope = draftNow.scope ?: scopeStored
            val dirty = max != storedNow.maxMillis || scope != scopeStored
            AppSettingsUiState(
                packageName = storedNow.packageName,
                label = storedNow.label,
                maxMillis = max,
                blockScope = scope,
                removalLockedUntilMillis = lockedNow,
                dirty = dirty,
                // Never both: a SAVE the user has since edited past is no longer news, and
                // showing SAVED over a pending edit is the exact lie the button prevents.
                saved = savedNow && !dirty,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettingsUiState(),
        )

    /** Bind the destination's package. Idempotent, so recomposition cannot re-trigger it. */
    fun bind(packageName: String) {
        if (this.packageName.value == packageName) return
        this.packageName.value = packageName
        label.value = null
        // A new package's screen must not inherit the last one's pending edits.
        draftMax.value = null
        draftScope.value = null
        saved.value = false
        lockedUntil.value = 0L
        labelJob?.cancel()
        labelJob = viewModelScope.launch {
            // The label is display-only; PackageManager is binder traffic and can throw,
            // so a failure falls back to the package id rather than blanking the header.
            // labelFor reads the name directly — no icon rasterization for the whole device.
            val resolved = try {
                appsRepository.labelFor(packageName)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
            label.value = resolved ?: packageName
        }
        viewModelScope.launch { refreshLock(packageName) }
    }

    /** Re-read the removal lock from the warm cache. */
    private fun refreshLock(pkg: String) {
        lockedUntil.value = enforcement.cachedRemovalLockedUntil(pkg)
    }

    /** Draft the bank ceiling. */
    fun setMax(millis: Long) {
        if (packageName.value == null) return
        saved.value = false
        draftMax.value = millis
    }

    /**
     * The scope is drafted like the ceiling and written by the same SAVE. It carries no
     * read-modify-write of its own — it is one value, not a field inside a set.
     */
    fun setBlockScope(scope: BlockScope) {
        if (packageName.value == null) return
        saved.value = false
        draftScope.value = scope
    }

    /**
     * Write the draft through to the repository.
     *
     * A failed write must not read as SAVED, so the flag is the outcome of the write and not
     * of the tap: failure leaves `dirty` true and the button armed again. The draft itself is
     * left in place on success — it is dropped the moment Room re-emits, because it equals the
     * stored value by then and `dirty` goes false on its own. Clearing it here instead would
     * flash the old value for however long the write takes to round trip.
     */
    fun save() {
        val pkg = packageName.value ?: return
        val pendingMax = draftMax.value
        val pendingScope = draftScope.value
        if (pendingMax == null && pendingScope == null) return
        viewModelScope.launch {
            val ok = runCatching {
                pendingMax?.let { enforcement.setMax(pkg, it) }
                pendingScope?.let { enforcement.setBlockScope(pkg, it) }
            }.isSuccess
            saved.value = ok
            if (ok) _finished.tryEmit(AppSettingsFinish.SAVED)
        }
    }

    /**
     * Remove the app's limit: the target row and its bank both go.
     *
     * Refused while the removal lock is running, which the repository decides rather than this
     * screen — the lock is about the app's own state, and a screen that had been open across
     * the twelve hours must not be able to write its stale copy of the lock over the answer.
     * A refusal re-reads the lock so the button explains itself with the wait that is actually
     * left rather than the one it opened with.
     */
    fun removeLimit() {
        val pkg = packageName.value ?: return
        viewModelScope.launch {
            val removed = runCatching { enforcement.removeTarget(pkg) }.getOrDefault(false)
            if (removed) {
                _finished.tryEmit(AppSettingsFinish.REMOVED)
            } else {
                refreshLock(pkg)
            }
        }
    }
}

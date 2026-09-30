package com.example.sonder.ui.screens.appsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.data.repo.InstalledAppsRepository
import com.example.sonder.domain.AccessRules
import com.example.sonder.domain.BlockScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One published state for the per-app settings screen. The package is bound by the
 * screen once (the destination carries it as an argument), then rules and today's
 * grant stream from the repository so a hand played elsewhere is reflected live.
 *
 * [rules] and [overrides] describe what the controls are showing, which is the draft
 * whenever there is one — not what is stored. That is the whole point of the SAVE step:
 * every knob on screen reads as the value the user has chosen, and [dirty] is the only
 * thing that says whether the app is actually enforcing those choices yet.
 */
data class AppSettingsUiState(
    val packageName: String = "",
    /** null until the launcher enumeration resolves it; the header falls back to the id. */
    val label: String? = null,
    val rules: AccessRules = AccessRules(),
    /**
     * The raw overrides behind [rules]. The screen needs these — not the effective
     * values — to know which knobs are chosen and which inherit, since an effective value
     * equal to the default is ambiguous.
     */
    val overrides: EnforcementRepository.TargetOverrides = EnforcementRepository.TargetOverrides(),
    val grantedTodayMillis: Long = 0L,
    /**
     * The scope as shown, which is the drafted one while there is a draft. Unlike the
     * knob values this is not a number and does not fold into [overrides]: it is not a
     * per-app override but a different kind of target, so it is drafted and saved beside
     * them rather than inside them.
     */
    val blockScope: BlockScope = BlockScope.WHOLE_APP,
    /**
     * True while the screen holds knob or scope changes SAVE has not written. Nothing is
     * written until it is false, so this is the app's own answer to "did my edit land?".
     */
    val dirty: Boolean = false,
    /**
     * True once a SAVE has completed and the screen has not been edited since. Never true
     * at the same time as [dirty] — see [AppSettingsViewModel.ui].
     */
    val saved: Boolean = false,
)

/**
 * What SAVE would write, as one value, so "is there anything to save" is a comparison
 * rather than three nullable flows that must agree. [overrides] and [scope] are null when
 * that half has no edit, matching the draft convention: absence is "follow what is
 * stored", not "clear it".
 */
data class SettingsDraft(
    val overrides: EnforcementRepository.TargetOverrides? = null,
    val scope: BlockScope? = null,
) {
    val isEmpty: Boolean get() = overrides == null && scope == null
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

    /**
     * Edits that SAVE has not written. Null on both halves means "no edits", and every
     * control reads `draft ?: stored`, so a screen with no draft is exactly today's screen.
     */
    private val draft = MutableStateFlow(SettingsDraft())

    /** True from the moment a SAVE lands until the next edit, so the badge can be shown. */
    private val saved = MutableStateFlow(false)

    /**
     * The last stored overrides, mirrored so a knob tap can compose onto them without a
     * database round trip between the tap and the value it composes onto — the same
     * read-modify-write the old `update` guarded with a mutex, against a copy that only
     * this thread writes.
     *
     * The package travels with the value because a mirror that is merely *empty* is not
     * the same as one that is *not loaded*: before the first emission for the bound
     * package the cached value is the blank set, and composing a tap onto that would write
     * null over every knob the user has not touched. Absence of the binding is the check
     * that tells those apart, and [edit] falls back to a real read while it is absent.
     */
    private var mirroredOverrides: Pair<String, EnforcementRepository.TargetOverrides>? = null

    private val overridesFlow: Flow<Pair<String?, EnforcementRepository.TargetOverrides>> =
        packageName.flatMapLatest { name ->
            if (name == null) {
                flowOf(null to EnforcementRepository.TargetOverrides())
            } else {
                enforcement.observeOverrides(name)
                    .map { name to (it ?: EnforcementRepository.TargetOverrides()) }
            }
        }

    private val scopeFlow: Flow<BlockScope> = packageName.flatMapLatest { name ->
        if (name == null) flowOf(BlockScope.WHOLE_APP) else enforcement.observeBlockScope(name)
    }

    /**
     * What is stored, resolved for display: label, the stored overrides, today's grant and
     * the stored scope. The overrides are also what the effective rules are built from, so
     * the display path and the write path cannot disagree about which fields are set.
     */
    private data class Stored(
        val packageName: String,
        val label: String?,
        val overrides: EnforcementRepository.TargetOverrides,
        val grantedTodayMillis: Long,
        val scope: BlockScope,
    )

    private val stored: Flow<Stored> = combine(
        packageName,
        label,
        overridesFlow.map { it.second },
        packageName.flatMapLatest { name ->
            if (name == null) flowOf(0L) else enforcement.observeDailyGrantedMillis(name)
        },
        scopeFlow,
    ) { name, resolvedLabel, overrides, granted, scope ->
        Stored(
            packageName = name.orEmpty(),
            label = resolvedLabel,
            overrides = overrides,
            grantedTodayMillis = granted,
            scope = scope,
        )
    }

    init {
        // The mirror is fed by the same cold flow [stored] reads. Two collectors of a Room
        // flow are one query each, and this one runs for the ViewModel's whole life, so the
        // mirror is warm before the user can reach a knob.
        viewModelScope.launch {
            overridesFlow.collect { (name, overrides) ->
                if (name != null) mirroredOverrides = name to overrides
            }
        }
    }

    val ui: StateFlow<AppSettingsUiState> =
        combine(stored, draft, saved) { stored, draft, savedNow ->
            // The draft wins where it exists, per field. A screen that has drafted only the
            // scope still shows the stored knobs.
            val overrides = draft.overrides ?: stored.overrides
            val scope = draft.scope ?: stored.scope
            val dirty = overrides != stored.overrides || scope != stored.scope
            AppSettingsUiState(
                packageName = stored.packageName,
                label = stored.label,
                rules = enforcement.rulesOf(overrides),
                overrides = overrides,
                grantedTodayMillis = stored.grantedTodayMillis,
                blockScope = scope,
                dirty = dirty,
                // Never both: a SAVE that the user has since edited past is no longer news,
                // and showing SAVED over a pending edit is the exact lie the button prevents.
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
        draft.value = SettingsDraft()
        saved.value = false
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
    }

    fun setWinGrant(millis: Long) = edit { it.copy(winGrantMillis = millis) }
    fun setLossDebt(millis: Long) = edit { it.copy(lossDebtMillis = millis) }
    fun setDebtCeiling(millis: Long) = edit { it.copy(maxDebtMillis = millis) }
    fun setAbsenceRevoke(millis: Long) = edit { it.copy(absenceRevokeMillis = millis) }
    fun setDailyCap(millis: Long?) = edit { it.copy(dailyCapMillis = millis) }

    /**
     * The scope is drafted like the knobs and written by the same SAVE. It carries no
     * read-modify-write of its own — it is one value, not a field inside a set — so it does
     * not need the mirror and can never be composed onto a stale base.
     */
    fun setBlockScope(scope: BlockScope) {
        if (packageName.value == null) return
        saved.value = false
        draft.value = draft.value.copy(scope = scope)
    }

    /**
     * Write the draft through to the repository. One call per half that has an edit, then
     * the whole draft stays in place: it is dropped the moment Room re-emits, because the
     * draft equals the stored value by then and `dirty` goes false on its own. Clearing it
     * here instead would flash the old value for however long the write takes to round trip.
     */
    fun save() {
        val pkg = packageName.value ?: return
        val pending = draft.value
        if (pending.isEmpty) return
        viewModelScope.launch {
            // A failed write must not read as SAVED, so the flag is the outcome of the write
            // and not of the tap. Failure leaves `dirty` true and the button live.
            saved.value = runCatching {
                pending.overrides?.let { enforcement.updateOverrides(pkg, it) }
                pending.scope?.let { enforcement.setBlockScope(pkg, it) }
            }.isSuccess
        }
    }

    /**
     * The one place a knob writes. It composes onto the draft if there is one and onto the
     * mirror otherwise, so repeated taps accumulate instead of each one starting from
     * whatever was stored when the screen opened.
     *
     * The mirror is only trusted when it is bound to this package. Before its first
     * emission it holds the blank set, and `copy` onto that would write null over every
     * knob the user has not touched; in that window the edit reads the row instead. The
     * window is one Room round trip wide and only reachable by tapping a knob in the first
     * frames of the screen, so the read costs nothing anyone can see.
     */
    private fun edit(
        transform: (EnforcementRepository.TargetOverrides) -> EnforcementRepository.TargetOverrides,
    ) {
        val pkg = packageName.value ?: return
        // Any edit supersedes the last SAVE's confirmation.
        saved.value = false
        val mirror = mirroredOverrides
        if (mirror != null && mirror.first == pkg) {
            draft.value = draft.value.copy(
                overrides = transform(draft.value.overrides ?: mirror.second),
            )
            return
        }
        viewModelScope.launch {
            val loaded = enforcement.overridesFor(pkg)
            mirroredOverrides = pkg to loaded
            draft.value = draft.value.copy(
                overrides = transform(draft.value.overrides ?: loaded),
            )
        }
    }
}

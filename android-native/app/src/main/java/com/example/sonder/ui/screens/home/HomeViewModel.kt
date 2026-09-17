package com.example.sonder.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.db.GrantDao
import com.example.sonder.data.db.LockoutDao
import com.example.sonder.data.db.TargetDao
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.LockoutSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    targetDao: TargetDao,
    grantDao: GrantDao,
    lockoutDao: LockoutDao,
) : ViewModel() {

    /** One tick per second. Cold, so it only runs while [state] has a subscriber. */
    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    /**
     * Home's panel and rows. `null` is the Loading presentation: Room has not emitted
     * yet, so there is nothing truthful to draw. The clock joins the combine so an
     * active countdown keeps ticking instead of freezing until the next Room emission.
     */
    val state: StateFlow<HomeState?> =
        combine(
            targetDao.observeAll(),
            grantDao.observeAll(),
            lockoutDao.observeAll(),
            clock,
        ) { targets, grants, lockouts, nowMillis ->
            mapHomeState(
                targets = targets,
                grants = grants.map { GrantSnapshot(it.packageName, it.endAtMillis, it.lastSeenMillis) },
                lockouts = lockouts.map { LockoutSnapshot(it.packageName, it.untilMillis, 0L) },
                nowMillis = nowMillis,
            )
        }.stateIn(
            viewModelScope,
            // No stop timeout: the ticker runs only while Home has a subscriber, and
            // HomeScreen collects lifecycle-aware, so it stops when Home is off-screen.
            SharingStarted.WhileSubscribed(),
            null,
        )
}

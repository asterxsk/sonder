package com.example.sonder.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.db.TargetDao
import com.example.sonder.data.db.TimeBankDao
import com.example.sonder.domain.model.TimeBankSnapshot
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
    timeBankDao: TimeBankDao,
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
     * yet, so there is nothing truthful to draw. The clock joins the combine so a countdown
     * keeps ticking instead of freezing until the next Room emission.
     *
     * The clock is also the only thing that can notice a day rolling over: a bank belongs to
     * the day it was earned on, and the mapper reads a stale one as nothing.
     */
    val state: StateFlow<HomeState?> =
        combine(
            targetDao.observeAll(),
            timeBankDao.observeAll(),
            clock,
        ) { targets, banks, nowMillis ->
            mapHomeState(
                targets = targets,
                banks = banks.map {
                    TimeBankSnapshot(
                        packageName = it.packageName,
                        remainingMillis = it.remainingMillis,
                        epochDay = it.epochDay,
                        lastSeenMillis = it.lastSeenMillis,
                        emptySinceMillis = it.emptySinceMillis,
                    )
                },
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

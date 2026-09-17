package com.example.sonder.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.db.HandDao
import com.example.sonder.data.db.HandEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The whole Stats screen in one state: ledger counts, history, and whether Room has answered. */
data class StatsUiState(
    val wins: Int = 0,
    val losses: Int = 0,
    val hands: List<HandEntity> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    handDao: HandDao,
) : ViewModel() {

    /**
     * One combined flow so the screen renders from a single state. [StatsUiState.loaded]
     * separates "Room has not answered yet" from "no hands have been played", which the
     * empty state depends on: an unanswered ledger must not read as an empty one.
     */
    val state: StateFlow<StatsUiState> =
        combine(
            handDao.observeRecent(50),
            handDao.observeWinCount(),
            handDao.observeLossCount(),
        ) { hands, wins, losses -> StatsUiState(wins, losses, hands, loaded = true) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

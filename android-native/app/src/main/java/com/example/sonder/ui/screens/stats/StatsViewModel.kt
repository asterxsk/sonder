package com.example.sonder.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.db.HandDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StatsViewModel @Inject constructor(
    handDao: HandDao,
) : ViewModel() {

    val recentHands: StateFlow<List<com.example.sonder.data.db.HandEntity>> =
        handDao.observeRecent(50).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wins: StateFlow<Int> =
        handDao.observeWinCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val losses: StateFlow<Int> =
        handDao.observeLossCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

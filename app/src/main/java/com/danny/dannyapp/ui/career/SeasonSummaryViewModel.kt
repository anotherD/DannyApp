package com.danny.dannyapp.ui.career

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.appContainer
import com.danny.dannyapp.domain.model.SeasonSummaryData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SeasonSummaryUiState {
    object Loading : SeasonSummaryUiState
    data class Ready(val summary: SeasonSummaryData) : SeasonSummaryUiState
    object Advancing : SeasonSummaryUiState
    data class Completed(val newSeasonId: Long) : SeasonSummaryUiState
    data class Error(val message: String) : SeasonSummaryUiState
}

class SeasonSummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val appContainer = application.appContainer
    private val getSeasonSummaryUseCase = appContainer.getSeasonSummaryUseCase
    private val advanceSeasonUseCase = appContainer.advanceSeasonUseCase

    private val _uiState = MutableStateFlow<SeasonSummaryUiState>(SeasonSummaryUiState.Loading)
    val uiState: StateFlow<SeasonSummaryUiState> = _uiState.asStateFlow()

    fun loadSummary(careerId: Long) {
        if (careerId <= 0L) {
            _uiState.value = SeasonSummaryUiState.Error("Ungültige Karriere-ID")
            return
        }
        viewModelScope.launch {
            _uiState.value = SeasonSummaryUiState.Loading
            try {
                val summary = getSeasonSummaryUseCase(careerId)
                _uiState.value = SeasonSummaryUiState.Ready(summary)
            } catch (e: Exception) {
                appContainer.reportError("Saison-Zusammenfassung laden", e)
                _uiState.value = SeasonSummaryUiState.Error(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    fun startNextSeason(careerId: Long, selectedLevel: Int) {
        viewModelScope.launch {
            _uiState.value = SeasonSummaryUiState.Advancing
            try {
                val newSeasonId = advanceSeasonUseCase(careerId, selectedLevel)
                _uiState.value = SeasonSummaryUiState.Completed(newSeasonId)
            } catch (e: Exception) {
                appContainer.reportError("Saisonwechsel", e)
                try {
                    val summary = getSeasonSummaryUseCase(careerId)
                    _uiState.value = SeasonSummaryUiState.Ready(summary)
                } catch (ex: Exception) {
                    appContainer.reportError("Saison-Zusammenfassung neu laden", ex)
                    _uiState.value = SeasonSummaryUiState.Error(e.message ?: "Unbekannter Fehler")
                }
            }
        }
    }
}

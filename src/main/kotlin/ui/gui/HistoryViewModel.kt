package ui.gui

import app.service.HistoryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
ViewModel экрана истории. Загружает список завершённых партий.
*/
class HistoryViewModel(
    private val history: HistoryService
) {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /*
    Перезагружает список партий из хранилища.
    */
    fun refresh() {
        _state.value = try {
            val games = history.getAllFinishedGames().map {
                GameSummaryView(
                    gameId = it.gameId,
                    players = it.playerNames.joinToString(", "),
                    winner = it.winnerName ?: "—",
                    turns = it.turnsPlayed
                )
            }
            HistoryUiState(games = games)
        } catch (e: Exception) {
            HistoryUiState(errorMessage = "Ошибка загрузки: ${e.message}")
        }
    }
}
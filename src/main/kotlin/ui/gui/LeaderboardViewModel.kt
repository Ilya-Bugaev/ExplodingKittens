package ui.gui

import app.service.StatisticsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
ViewModel экрана лидеров. Загружает отсортированный список
статистики игроков.
*/
class LeaderboardViewModel(
    private val statistics: StatisticsService
) {
    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = try {
            val players = statistics.getLeaderboard().map {
                PlayerStatsView(
                    name = it.name,
                    gamesPlayed = it.gamesPlayed,
                    wins = it.wins,
                    winRate = it.winRate
                )
            }
            LeaderboardUiState(players = players)
        } catch (e: Exception) {
            LeaderboardUiState(errorMessage = "Ошибка загрузки: ${e.message}")
        }
    }
}
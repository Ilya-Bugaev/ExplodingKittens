package ui.gui

/*
Состояние экрана истории партий.
*/
data class HistoryUiState(
    val games: List<GameSummaryView> = emptyList(),
    val errorMessage: String? = null
)

data class GameSummaryView(
    val gameId: Int,
    val players: String,
    val winner: String,
    val turns: Int
)
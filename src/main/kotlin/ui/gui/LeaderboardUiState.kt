package ui.gui

/*
Состояние экрана лидеров.
*/
data class LeaderboardUiState(
    val players: List<PlayerStatsView> = emptyList(),
    val errorMessage: String? = null
)

data class PlayerStatsView(
    val name: String,
    val gamesPlayed: Int,
    val wins: Int,
    val winRate: Double
)
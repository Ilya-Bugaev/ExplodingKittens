package app.service

import app.dto.GameSummary
import app.dto.PlayerStats
import app.repository.IStatisticsRepository

/*
Агрегирует статистику игроков из завершённых партий.
При updateStats(gameSummary) обновляет gamesPlayed у всех участников
и wins у победителя, затем пересчитывает winRate.
*/
class StatisticsService(
    private val repo: IStatisticsRepository,
    private val playerRegistry: PlayerRegistryService
) {
    fun updateStats(gameSummary: GameSummary) {
        gameSummary.playerNames.forEach { name ->
            val playerId = playerRegistry.findPlayerId(name) ?: return@forEach
            val current = repo.getPlayerStats(playerId)
                ?: PlayerStats(playerId = playerId, name = name)

            val won = name == gameSummary.winnerName
            val updated = current.copy(
                wins = current.wins + if (won) 1 else 0,
                gamesPlayed = current.gamesPlayed + 1
            ).withRecalculatedWinRate()

            repo.updateStats(updated)
        }
    }

    fun getPlayerStats(playerId: Int): PlayerStats? = repo.getPlayerStats(playerId)

    fun getAllPlayerStats(): List<PlayerStats> = repo.getAllPlayerStats()

    fun getLeaderboard(): List<PlayerStats> =
        repo.getAllPlayerStats().sortedWith(
            compareByDescending<PlayerStats> { it.wins }
                .thenByDescending { it.winRate }
                .thenBy { it.name }
        )

    private fun PlayerStats.withRecalculatedWinRate(): PlayerStats = copy(
        winRate = if (gamesPlayed > 0) wins.toDouble() / gamesPlayed else 0.0
    )
}
package app.repository

import app.dto.PlayerStats

interface IStatisticsRepository {
    fun getPlayerStats(playerId: Int): PlayerStats?
    fun getAllPlayerStats(): List<PlayerStats>
    fun updateStats(stats: PlayerStats): Unit
}
package app.dto

import kotlinx.serialization.Serializable

/*
Статистика одного игрока. wins и gamesPlayed аккумулируются
StatisticsService при завершении партий. rating - заглушка
под будущую формулу Эло.
*/
@Serializable
data class PlayerStats(
    val playerId: Int,
    val name: String,
    val wins: Int = 0,
    val gamesPlayed: Int = 0,
    val winRate: Double = 0.0,
    val rating: Double = 0.0
)
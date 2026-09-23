package app.storage

import app.dto.PlayerStats
import app.repository.IStatisticsRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class StatisticsData(
    val players: List<PlayerStats> = emptyList()
)

/*
Хранение статистики игроков в JSON. При updateStats запись
добавляется или заменяется по playerId.
*/
class JsonStatisticsRepository(
    private val file: File
) : IStatisticsRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private var data: StatisticsData = load()

    override fun getPlayerStats(playerId: Int): PlayerStats? =
        data.players.firstOrNull { it.playerId == playerId }

    override fun getAllPlayerStats(): List<PlayerStats> = data.players

    override fun updateStats(stats: PlayerStats) {
        data = data.copy(
            players = data.players.filter { it.playerId != stats.playerId } + stats
        )
        save()
    }

    private fun load(): StatisticsData {
        if (!file.exists()) return StatisticsData()
        return try {
            json.decodeFromString(StatisticsData.serializer(), file.readText())
        } catch (e: Exception) {
            StatisticsData()
        }
    }

    private fun save() {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(StatisticsData.serializer(), data))
    }
}
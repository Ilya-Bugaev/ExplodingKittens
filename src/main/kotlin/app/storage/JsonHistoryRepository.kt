package app.storage

import app.dto.GameSummary
import app.repository.IHistoryRepository
import domain.Game
import kotlinx.serialization.json.Json
import java.io.File

/*
Хранение истории партий в JSON. Файл содержит все завершённые партии.
При saveGame запись добавляется (или заменяется по gameId).
*/
class JsonHistoryRepository(
    private val file: File
) : IHistoryRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private var data: HistoryData = load()

    override fun saveGame(game: Game) {
        val record = game.toRecord()
        data = data.copy(games = data.games.filter { it.gameId != record.gameId } + record)
        save()
    }

    override fun getRecord(gameId: Int): GameRecord? =
        data.games.firstOrNull { it.gameId == gameId }

    override fun getAllRecords(): List<GameRecord> = data.games

    override fun getAllFinishedGames(): List<GameSummary> =
        data.games.map { it.toSummary() }

    private fun load(): HistoryData {
        if (!file.exists()) return HistoryData()
        return try {
            json.decodeFromString(HistoryData.serializer(), file.readText())
        } catch (e: Exception) {
            HistoryData()
        }
    }

    private fun save() {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(HistoryData.serializer(), data))
    }
}
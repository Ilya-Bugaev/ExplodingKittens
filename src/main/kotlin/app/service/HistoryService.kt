package app.service

import app.dto.GameSummary
import app.repository.IHistoryRepository
import app.storage.GameRecord
import domain.Game

class HistoryService(
    private val repo: IHistoryRepository
) {
    fun saveGame(game: Game) = repo.saveGame(game)

    fun getRecord(gameId: Int): GameRecord? = repo.getRecord(gameId)

    fun getAllRecords(): List<GameRecord> = repo.getAllRecords()

    fun getAllFinishedGames(): List<GameSummary> = repo.getAllFinishedGames()
}
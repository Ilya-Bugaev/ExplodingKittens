package app.repository

import app.dto.GameSummary
import app.storage.GameRecord
import domain.Game

interface IHistoryRepository {
    fun saveGame(game: Game): Unit
    fun getRecord(gameId: Int): GameRecord?
    fun getAllRecords(): List<GameRecord>
    fun getAllFinishedGames(): List<GameSummary>
}
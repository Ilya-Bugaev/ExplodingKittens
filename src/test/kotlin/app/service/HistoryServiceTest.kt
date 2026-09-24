package app.service

import app.storage.JsonHistoryRepository
import domain.Game
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.random.Random

class HistoryServiceTest {

    @TempDir
    lateinit var tempDir: File

    private fun service(): HistoryService =
        HistoryService(JsonHistoryRepository(File(tempDir, "history.json")))

    private fun finishedGame(id: Int): Game {
        val game = Game(id = id)
        game.startGame(listOf("Аня", "Боря"), Random(42))
        game.players[1].eliminate()
        game.finish()
        return game
    }

    // saveGame сохраняет партию, getRecord её находит.
    @Test
    fun `saveGame persists and getRecord finds`() {
        val service = service()
        val game = finishedGame(1)
        service.saveGame(game)

        assertNotNull(service.getRecord(1))
    }

    // getRecord возвращает null для неизвестной партии.
    @Test
    fun `getRecord returns null for unknown`() {
        val service = service()
        assertNull(service.getRecord(999))
    }

    // getAllFinishedGames возвращает сводку.
    @Test
    fun `getAllFinishedGames returns summaries`() {
        val service = service()
        service.saveGame(finishedGame(1))
        service.saveGame(finishedGame(2))

        assertEquals(2, service.getAllFinishedGames().size)
    }
}
package app.storage.sql

import app.dto.GameSummary
import domain.Game
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SqlHistoryRepositoryTest {

    private lateinit var db: Database
    private lateinit var repo: SqlHistoryRepository

    @BeforeEach
    fun setUp() {
        db = Database.inMemory()
        repo = SqlHistoryRepository(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    private fun finishedGame(id: Int): Game {
        val game = Game(id = id)
        game.startGame(listOf("Аня", "Боря"), Random(42))
        game.players[1].eliminate()
        game.finish()
        return game
    }

    // Пустая БД — пустая история.
    @Test
    fun `empty database yields empty history`() {
        assertTrue(repo.getAllRecords().isEmpty())
        assertTrue(repo.getAllFinishedGames().isEmpty())
    }

    // saveGame сохраняет партию, getRecord её находит.
    @Test
    fun `saveGame persists and getRecord finds it`() {
        val game = finishedGame(1)
        repo.saveGame(game)

        val record = repo.getRecord(1)

        assertNotNull(record)
        assertEquals(listOf("Аня", "Боря"), record!!.playerNames)
        assertEquals("Аня", record.winnerName)
    }

    // getRecord возвращает null для неизвестной партии.
    @Test
    fun `getRecord returns null for unknown game`() {
        assertNull(repo.getRecord(999))
    }

    // Повторное сохранение перезаписывает партию, не дублирует.
    @Test
    fun `saveGame twice replaces existing record`() {
        val game = finishedGame(1)
        repo.saveGame(game)
        repo.saveGame(game)

        assertEquals(1, repo.getAllRecords().size)
    }

    // getAllFinishedGames возвращает сводки с корректным победителем.
    @Test
    fun `getAllFinishedGames returns summaries`() {
        repo.saveGame(finishedGame(1))

        val summaries: List<GameSummary> = repo.getAllFinishedGames()

        assertEquals(1, summaries.size)
        assertEquals("Аня", summaries[0].winnerName)
    }

    // Две партии сохраняются независимо.
    @Test
    fun `two games are stored separately`() {
        repo.saveGame(finishedGame(1))
        repo.saveGame(finishedGame(2))

        assertEquals(2, repo.getAllRecords().size)
        assertEquals(2, repo.getAllFinishedGames().size)
    }

    // Ходы партии сохраняются и читаются с правильным количеством.
    @Test
    fun `moves are persisted with the game`() {
        val game = finishedGame(1)
        repo.saveGame(game)

        val record = repo.getRecord(1)!!
        // START + (если было) другие ходы. Минимум 1 (START).
        assertTrue(record.moves.isNotEmpty())
        assertEquals("START", record.moves.first().type)
    }

    // Данные сохраняются между экземплярами репозитория на одной БД.
    @Test
    fun `data persists between repository instances`() {
        repo.saveGame(finishedGame(1))
        val repo2 = SqlHistoryRepository(db)
        assertEquals(1, repo2.getAllRecords().size)
    }
}
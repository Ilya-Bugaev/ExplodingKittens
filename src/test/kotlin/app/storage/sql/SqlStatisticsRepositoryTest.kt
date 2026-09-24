package app.storage.sql

import app.dto.PlayerStats
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SqlStatisticsRepositoryTest {

    private lateinit var db: Database
    private lateinit var repo: SqlStatisticsRepository

    @BeforeEach
    fun setUp() {
        db = Database.inMemory()
        repo = SqlStatisticsRepository(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    // Пустая БД — пустая статистика.
    @Test
    fun `empty database yields empty stats`() {
        assertTrue(repo.getAllPlayerStats().isEmpty())
    }

    // updateStats сохраняет, getPlayerStats находит.
    @Test
    fun `updateStats saves and getPlayerStats finds`() {
        val stats = PlayerStats(playerId = 1, name = "Аня", wins = 3, gamesPlayed = 5)
        repo.updateStats(stats)

        assertEquals(stats, repo.getPlayerStats(1))
    }

    // getPlayerStats возвращает null для неизвестного.
    @Test
    fun `getPlayerStats returns null for unknown`() {
        assertNull(repo.getPlayerStats(999))
    }

    // Повторный updateStats перезаписывает по player_id.
    @Test
    fun `updateStats replaces existing stats`() {
        repo.updateStats(PlayerStats(playerId = 1, name = "Аня", wins = 1, gamesPlayed = 1))
        repo.updateStats(PlayerStats(playerId = 1, name = "Аня", wins = 2, gamesPlayed = 2))

        assertEquals(1, repo.getAllPlayerStats().size)
        assertEquals(2, repo.getPlayerStats(1)!!.wins)
    }

    // getAllPlayerStats возвращает всех в порядке id.
    @Test
    fun `getAllPlayerStats returns stats ordered by id`() {
        repo.updateStats(PlayerStats(playerId = 2, name = "Боря"))
        repo.updateStats(PlayerStats(playerId = 1, name = "Аня"))
        repo.updateStats(PlayerStats(playerId = 3, name = "Ваня"))

        assertEquals(listOf("Аня", "Боря", "Ваня"), repo.getAllPlayerStats().map { it.name })
    }

    // winRate и rating сохраняются как Double.
    @Test
    fun `winRate and rating are preserved`() {
        val stats = PlayerStats(playerId = 1, name = "Аня", wins = 5, gamesPlayed = 10, winRate = 0.5, rating = 1250.75)
        repo.updateStats(stats)

        val loaded = repo.getPlayerStats(1)!!
        assertEquals(0.5, loaded.winRate, 0.0001)
        assertEquals(1250.75, loaded.rating, 0.0001)
    }

    // Данные сохраняются между экземплярами репозитория.
    @Test
    fun `data persists between repository instances`() {
        repo.updateStats(PlayerStats(playerId = 1, name = "Аня", wins = 3))

        val repo2 = SqlStatisticsRepository(db)

        assertEquals(3, repo2.getPlayerStats(1)!!.wins)
    }
}
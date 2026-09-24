package system

import app.dto.GameSummary
import app.service.GameplayService
import app.service.HistoryService
import app.service.PlayerRegistryService
import app.service.StatisticsService
import app.storage.sql.Database
import app.storage.sql.SqlHistoryRepository
import app.storage.sql.SqlPlayerRepository
import app.storage.sql.SqlStatisticsRepository
import app.validator.EKMoveValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.random.Random

class SqlSystemScenarioTest {

    @TempDir
    lateinit var tempDir: File

    private fun dbFile() = File(tempDir, "tracker.db")

    /*
    Одна «сессия приложения»: открывает БД, создаёт сервисы.
    */
    private class Session(val dbFile: File) : AutoCloseable {
        val db = Database.open(dbFile)
        val registry = PlayerRegistryService(SqlPlayerRepository(db))
        val history = HistoryService(SqlHistoryRepository(db))
        val stats = StatisticsService(SqlStatisticsRepository(db), registry)
        val gameplay = GameplayService(EKMoveValidator(), registry, history, stats)

        override fun close() = db.close()
    }

    // Партия сохраняется в файловую БД и читается после перезапуска.
    @Test
    fun `game persists across restart`() {
        Session(dbFile()).use { s ->
            val id = s.gameplay.startGame(listOf("Аня", "Боря"), Random(42))
            s.gameplay.endGame(id)
        }

        Session(dbFile()).use { s2 ->
            val games = s2.history.getAllFinishedGames()
            assertEquals(1, games.size)
        }
    }

    // Статистика накапливается между сессиями.
    @Test
    fun `stats accumulate across restarts`() {
        Session(dbFile()).use { s ->
            val id = s.gameplay.startGame(listOf("Аня", "Боря"), Random(1))
            s.gameplay.endGame(id)
        }
        Session(dbFile()).use { s ->
            val id = s.gameplay.startGame(listOf("Аня", "Боря"), Random(2))
            s.gameplay.endGame(id)
        }
        Session(dbFile()).use { s3 ->
            s3.stats.getAllPlayerStats().forEach {
                assertEquals(2, it.gamesPlayed)
            }
        }
    }

    // Реестр игроков сохраняется между сессиями.
    @Test
    fun `player registry persists across restarts`() {
        Session(dbFile()).use { s ->
            s.gameplay.startGame(listOf("Аня", "Боря"), Random(42))
        }

        Session(dbFile()).use { s2 ->
            assertTrue(s2.registry.getKnownPlayerNames().containsAll(listOf("Аня", "Боря")))
        }
    }

    // Нумерация партий не перезаписывает старые при перезапуске.
    @Test
    fun `game ids continue after restart`() {
        val id1 = Session(dbFile()).use { s ->
            val id = s.gameplay.startGame(listOf("Аня", "Боря"), Random(42))
            s.gameplay.endGame(id)
            id
        }
        val id2 = Session(dbFile()).use { s ->
            val id = s.gameplay.startGame(listOf("Аня", "Боря"), Random(43))
            s.gameplay.endGame(id)
            id
        }

        assertTrue(id2 > id1, "second game id should be greater than first: $id1 → $id2")
        Session(dbFile()).use { s3 ->
            assertEquals(2, s3.history.getAllRecords().size)
        }
    }

    // Победитель сохраняется и читается после перезапуска.
    @Test
    fun `winner persists across restart`() {
        Session(dbFile()).use { s ->
            val id = s.gameplay.startGame(listOf("Аня", "Боря"), Random(42))
            val game = s.gameplay.getCurrentGame(id)!!
            game.players[1].eliminate()
            s.gameplay.endGame(id)
        }

        Session(dbFile()).use { s2 ->
            val summary: GameSummary = s2.history.getAllFinishedGames().first()
            assertEquals("Аня", summary.winnerName)
        }
    }

    // Много партий в одной сессии, потом новая - все на месте.
    @Test
    fun `many games then new session sees all`() {
        Session(dbFile()).use { s ->
            repeat(5) { i ->
                val id = s.gameplay.startGame(listOf("Аня", "Боря"), Random(i))
                s.gameplay.endGame(id)
            }
        }

        Session(dbFile()).use { s2 ->
            assertEquals(5, s2.history.getAllRecords().size)
        }
    }
}
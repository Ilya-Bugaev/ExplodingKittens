package regression

import app.dto.PlayerStats
import app.repository.IHistoryRepository
import app.repository.IPlayerRepository
import app.repository.IStatisticsRepository
import app.storage.JsonHistoryRepository
import app.storage.JsonPlayerRepository
import app.storage.JsonStatisticsRepository
import app.storage.sql.Database
import app.storage.sql.SqlHistoryRepository
import app.storage.sql.SqlPlayerRepository
import app.storage.sql.SqlStatisticsRepository
import domain.Game
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.random.Random

class RepositoryParityTest {

    @TempDir
    lateinit var tempDir: File

    private fun jsonPlayerRepo(): IPlayerRepository =
        JsonPlayerRepository(File(tempDir, "p.json"))

    private fun sqlPlayerRepo(): Pair<IPlayerRepository, Database> {
        val db = Database.inMemory()
        return SqlPlayerRepository(db) to db
    }

    @Test
    fun `players - both repos register sequentially`() {
        val json = jsonPlayerRepo()
        val (sql, db) = sqlPlayerRepo()
        db.use {
            assertEquals(json.register("Аня"), sql.register("Аня"))
            assertEquals(json.register("Боря"), sql.register("Боря"))
            assertEquals(json.register("Аня"), sql.register("Аня"))  // повтор
        }
    }

    @Test
    fun `players - both repos preserve insertion order`() {
        val json = jsonPlayerRepo()
        val (sql, db) = sqlPlayerRepo()
        db.use {
            listOf("Аня", "Боря", "Ваня").forEach { json.register(it); sql.register(it) }
            assertEquals(json.findAll(), sql.findAll())
        }
    }

    @Test
    fun `players - both repos return null for unknown`() {
        val json = jsonPlayerRepo()
        val (sql, db) = sqlPlayerRepo()
        db.use {
            assertNull(json.findIdByName("Незнакомец"))
            assertNull(sql.findIdByName("Незнакомец"))
        }
    }

    private fun jsonStatsRepo(): IStatisticsRepository =
        JsonStatisticsRepository(File(tempDir, "s.json"))

    private fun sqlStatsRepo(): Pair<IStatisticsRepository, Database> {
        val db = Database.inMemory()
        return SqlStatisticsRepository(db) to db
    }

    @Test
    fun `stats - both repos upsert by playerId`() {
        val json = jsonStatsRepo()
        val (sql, db) = sqlStatsRepo()
        db.use {
            val s1 = PlayerStats(playerId = 1, name = "Аня", wins = 1, gamesPlayed = 1)
            json.updateStats(s1); sql.updateStats(s1)

            val s2 = PlayerStats(playerId = 1, name = "Аня", wins = 2, gamesPlayed = 2)
            json.updateStats(s2); sql.updateStats(s2)

            assertEquals(json.getPlayerStats(1), sql.getPlayerStats(1))
            assertEquals(2, sql.getPlayerStats(1)!!.wins)
        }
    }

    @Test
    fun `stats - both repos preserve winRate and rating`() {
        val json = jsonStatsRepo()
        val (sql, db) = sqlStatsRepo()
        db.use {
            val s = PlayerStats(playerId = 1, name = "Аня", wins = 5, gamesPlayed = 10, winRate = 0.5, rating = 1250.75)
            json.updateStats(s); sql.updateStats(s)
            assertEquals(json.getPlayerStats(1), sql.getPlayerStats(1))
        }
    }

    private fun jsonHistoryRepo(): IHistoryRepository =
        JsonHistoryRepository(File(tempDir, "h.json"))

    private fun sqlHistoryRepo(): Pair<IHistoryRepository, Database> {
        val db = Database.inMemory()
        return SqlHistoryRepository(db) to db
    }

    private fun finishedGame(id: Int): Game {
        val game = Game(id = id)
        game.startGame(listOf("Аня", "Боря"), Random(42))
        game.players[1].eliminate()
        game.finish()
        return game
    }

    @Test
    fun `history - both repos save and find games`() {
        val json = jsonHistoryRepo()
        val (sql, db) = sqlHistoryRepo()
        db.use {
            val game = finishedGame(1)
            json.saveGame(game); sql.saveGame(game)

            assertEquals(json.getRecord(1)?.winnerName, sql.getRecord(1)?.winnerName)
            assertEquals(json.getRecord(1)?.playerNames, sql.getRecord(1)?.playerNames)
        }
    }

    @Test
    fun `history - both repos replace on second save`() {
        val json = jsonHistoryRepo()
        val (sql, db) = sqlHistoryRepo()
        db.use {
            val game = finishedGame(1)
            json.saveGame(game); json.saveGame(game)
            sql.saveGame(game); sql.saveGame(game)

            assertEquals(1, json.getAllRecords().size)
            assertEquals(1, sql.getAllRecords().size)
        }
    }

    @Test
    fun `history - both repos return null for unknown game`() {
        val json = jsonHistoryRepo()
        val (sql, db) = sqlHistoryRepo()
        db.use {
            assertNull(json.getRecord(999))
            assertNull(sql.getRecord(999))
        }
    }

    @Test
    fun `history - both repos store moves count consistently`() {
        val json = jsonHistoryRepo()
        val (sql, db) = sqlHistoryRepo()
        db.use {
            val game = finishedGame(1)
            json.saveGame(game); sql.saveGame(game)

            val jsonMoves = json.getRecord(1)!!.moves
            val sqlMoves = sql.getRecord(1)!!.moves
            assertEquals(jsonMoves.size, sqlMoves.size)
        }
    }
}
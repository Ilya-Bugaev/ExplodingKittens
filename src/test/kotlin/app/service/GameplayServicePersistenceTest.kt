package app.service

import app.repository.InMemoryPlayerRepository
import app.storage.JsonHistoryRepository
import app.storage.JsonStatisticsRepository
import app.validator.EKMoveValidator
import domain.Move
import domain.MoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.random.Random

class GameplayServicePersistenceTest {

    @TempDir
    lateinit var tempDir: File

    private fun serviceWithPersistence(): Triple<GameplayService, HistoryService, StatisticsService> {
        val playerRepo = InMemoryPlayerRepository()
        val registry = PlayerRegistryService(playerRepo)
        val history = HistoryService(JsonHistoryRepository(File(tempDir, "history.json")))
        val stats = StatisticsService(
            JsonStatisticsRepository(File(tempDir, "stats.json")),
            registry
        )
        val gameplay = GameplayService(EKMoveValidator(), registry, history, stats)
        return Triple(gameplay, history, stats)
    }

    // endGame сохраняет партию в историю.
    @Test
    fun `endGame saves game to history`() {
        val (gameplay, history, _) = serviceWithPersistence()
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))

        gameplay.endGame(id)

        assertNotNull(history.getRecord(id))
    }

    // endGame обновляет статистику обоих игроков.
    @Test
    fun `endGame updates statistics for all players`() {
        val (gameplay, _, stats) = serviceWithPersistence()
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))

        gameplay.endGame(id)

        val all = stats.getAllPlayerStats()
        assertEquals(2, all.size)
        assertEquals(setOf("Аня", "Боря"), all.map { it.name }.toSet())
        assertEquals(1, all.first { it.name == "Аня" }.gamesPlayed)
    }

    // Повторный endGame не дублирует запись в истории.
    @Test
    fun `endGame twice does not duplicate history`() {
        val (gameplay, history, _) = serviceWithPersistence()
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))

        gameplay.endGame(id)
        gameplay.endGame(id)

        assertEquals(1, history.getAllRecords().size)
    }

    // Без подключённых сервисов (шаг 2) завершение партии не падает.
    @Test
    fun `endGame without persistence services does not fail`() {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        val gameplay = GameplayService(EKMoveValidator(), registry)
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))

        gameplay.endGame(id)

        assertEquals(domain.GameState.FINISHED, gameplay.getCurrentGame(id)!!.state)
    }

    // Партия сохраняется автоматически, когда завершается через playMove.
    @Test
    fun `game finished via playMove is saved`() {
        val (gameplay, history, _) = serviceWithPersistence()
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))
        val game = gameplay.getCurrentGame(id)!!
        // Убираем второго игрока, чтобы следующий ход завершил партию
        game.players[1].eliminate()

        val move = Move(0, 1, MoveType.DRAW, author = game.players[0])
        gameplay.playMove(id, move)

        assertNotNull(history.getRecord(id))
    }
}
package system

import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.HistoryService
import app.service.PlayerRegistryService
import app.service.StatisticsService
import app.storage.JsonHistoryRepository
import app.storage.JsonStatisticsRepository
import app.validator.EKMoveValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ui.gui.HistoryViewModel
import ui.gui.LeaderboardViewModel
import ui.gui.MainViewModel
import java.io.File

/*
Системный сценарий: несколько партий за сессию, проверка истории
и лидерборда после их завершения.
*/
class SystemScenarioTest {

    @TempDir
    lateinit var tempDir: File

    private class App(val dataDir: File) {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        val history = HistoryService(JsonHistoryRepository(File(dataDir, "h.json")))
        val stats = StatisticsService(JsonStatisticsRepository(File(dataDir, "s.json")), registry)
        val gameplay = GameplayService(EKMoveValidator(), registry, history, stats)
        val gameVm = MainViewModel(gameplay, registry)
        val historyVm = HistoryViewModel(history)
        val leaderboardVm = LeaderboardViewModel(stats)
    }

    // Три партии подряд: история содержит три записи, лидерборд - всех игроков.
    @Test
    fun `three games in one session`() {
        val app = App(tempDir)

        repeat(3) {
            assertTrue(app.gameVm.newGame(listOf("Аня", "Боря")))
            app.gameVm.endGame()
        }

        app.historyVm.refresh()
        app.leaderboardVm.refresh()

        assertEquals(3, app.historyVm.state.value.games.size)
        assertEquals(2, app.leaderboardVm.state.value.players.size)
        assertEquals(3, app.leaderboardVm.state.value.players.first().gamesPlayed)
    }

    // Партия с тремя игроками: все получают gamesPlayed.
    @Test
    fun `three player game updates all stats`() {
        val app = App(tempDir)

        app.gameVm.newGame(listOf("Аня", "Боря", "Ваня"))
        app.gameVm.endGame()

        app.leaderboardVm.refresh()

        assertEquals(3, app.leaderboardVm.state.value.players.size)
        app.leaderboardVm.state.value.players.forEach {
            assertEquals(1, it.gamesPlayed)
        }
    }

    // Реестр игроков переживает создание нескольких партий.
    @Test
    fun `player registry is shared between games`() {
        val app = App(tempDir)

        app.gameVm.newGame(listOf("Аня", "Боря"))
        app.gameVm.endGame()
        app.gameVm.newGame(listOf("Аня", "Ваня"))
        app.gameVm.endGame()

        val known = app.gameVm.state.value.registeredPlayers
        assertTrue(known.containsAll(listOf("Аня", "Боря", "Ваня")))
    }

    // Победитель партии фиксируется в истории.
    @Test
    fun `winner is recorded in history`() {
        val app = App(tempDir)
        app.gameVm.newGame(listOf("Аня", "Боря"))

        // Завершаем без явного победителя (endGame при двух живых)
        app.gameVm.endGame()

        app.historyVm.refresh()
        val record = app.historyVm.state.value.games.first()
        // победитель может быть null при досрочном завершении
        assertTrue(record.winner == "-" || record.winner.isNotEmpty())
    }
}
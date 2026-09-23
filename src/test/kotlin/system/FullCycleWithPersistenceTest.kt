package system

import app.service.GameplayService
import app.service.HistoryService
import app.service.PlayerRegistryService
import app.service.StatisticsService
import app.storage.JsonHistoryRepository
import app.storage.JsonPlayerRepository
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

class FullCycleWithPersistenceTest {

    @TempDir
    lateinit var tempDir: File

    private fun playerRepoFile() = File(tempDir, "players.json")
    private fun historyFile() = File(tempDir, "history.json")
    private fun statsFile() = File(tempDir, "stats.json")

    /*
    Создаёт связку сервисов как в Main.kt. Вызывается дважды:
    до и после «перезапуска приложения».
    */
    private class Session(val dataDir: File) {
        val registry = PlayerRegistryService(JsonPlayerRepository(File(dataDir, "players.json")))
        val history = HistoryService(JsonHistoryRepository(File(dataDir, "history.json")))
        val stats = StatisticsService(
            JsonStatisticsRepository(File(dataDir, "stats.json")),
            registry
        )
        val gameplay = GameplayService(EKMoveValidator(), registry, history, stats)
        val gameVm = MainViewModel(gameplay, registry)
        val historyVm = HistoryViewModel(history)
        val leaderboardVm = LeaderboardViewModel(stats)
    }

    // Партия, перезапуск, история и лидерборд содержат данные.
    @Test
    fun `game persists across restart`() {
        // Первая сессия: играем партию
        val s1 = Session(tempDir)
        s1.gameVm.newGame(listOf("Аня", "Боря"))
        s1.gameVm.endGame()

        // «Перезапуск»: новые сервисы на тех же файлах
        val s2 = Session(tempDir)
        s2.historyVm.refresh()
        s2.leaderboardVm.refresh()

        assertEquals(1, s2.historyVm.state.value.games.size)
        assertEquals(2, s2.leaderboardVm.state.value.players.size)
    }

    // Реестр игроков переживает перезапуск.
    @Test
    fun `player registry persists across restart`() {
        val s1 = Session(tempDir)
        s1.gameVm.newGame(listOf("Аня", "Боря"))
        s1.gameVm.endGame()

        val s2 = Session(tempDir)

        assertTrue(s2.gameVm.state.value.registeredPlayers.containsAll(listOf("Аня", "Боря")))
    }

    // Вторая партия в новой сессии добавляется к существующей статистике.
    @Test
    fun `stats accumulate across sessions`() {
        val s1 = Session(tempDir)
        s1.gameVm.newGame(listOf("Аня", "Боря"))
        s1.gameVm.endGame()

        val s2 = Session(tempDir)
        s2.gameVm.newGame(listOf("Аня", "Боря"))
        s2.gameVm.endGame()
        s2.leaderboardVm.refresh()

        s2.leaderboardVm.state.value.players.forEach {
            assertEquals(2, it.gamesPlayed)
        }
    }
}
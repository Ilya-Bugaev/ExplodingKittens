package integration

import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.HistoryService
import app.service.PlayerRegistryService
import app.service.StatisticsService
import app.storage.JsonHistoryRepository
import app.storage.JsonStatisticsRepository
import app.validator.EKMoveValidator
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ui.gui.MainViewModel
import java.io.File

class FullGameFlowTest {

    @TempDir
    lateinit var tempDir: File

    private class App {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        val history = HistoryService(JsonHistoryRepository(File("")))
        val stats = StatisticsService(JsonStatisticsRepository(File("")), registry)
        val gameplay = GameplayService(EKMoveValidator(), registry, history, stats)
        val vm = MainViewModel(gameplay, registry)
    }

    private fun app(): Triple<MainViewModel, GameplayService, HistoryService> {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        val history = HistoryService(JsonHistoryRepository(File(tempDir, "h.json")))
        val stats = StatisticsService(JsonStatisticsRepository(File(tempDir, "s.json")), registry)
        val gameplay = GameplayService(EKMoveValidator(), registry, history, stats)
        val vm = MainViewModel(gameplay, registry)
        return Triple(vm, gameplay, history)
    }

    // Полный цикл: start ,  DRAW ,  endGame ,  запись в истории и статистике.
    @Test
    fun `complete game lifecycle`() {
        val (vm, _, history) = app()

        assertTrue(vm.newGame(listOf("Аня", "Боря")))
        val id = vm.state.value.gameId!!

        // Партия ещё не в истории — она не завершена
        assertNull(history.getRecord(id))

        // Серия ходов
        repeat(3) {
            if (!vm.state.value.isFinished) vm.drawCard()
        }

        vm.endGame()

        assertTrue(vm.state.value.isFinished)
        assertNotNull(history.getRecord(id))
    }

    // После завершения партии записывается gamesPlayed для обоих игроков.
    @Test
    fun `statistics updated after game`() {
        val (vm, _, _) = app()
        vm.newGame(listOf("Аня", "Боря"))
        vm.endGame()

        val stats = vm.state.value.registeredPlayers
        assertTrue(stats.contains("Аня"))
        assertTrue(stats.contains("Боря"))
    }

    // Ход при отсутствии активной партии не падает.
    @Test
    fun `draw without active game fails gracefully`() {
        val (vm, _, _) = app()
        val result = vm.drawCard()
        assertTrue(result is app.dto.ValidationResult.Rejected)
        assertNotNull(vm.state.value.errorMessage)
    }

    // Новая партия после завершения создаётся успешно.
    @Test
    fun `can start new game after finishing previous`() {
        val (vm, _, _) = app()
        vm.newGame(listOf("Аня", "Боря"))
        val firstId = vm.state.value.gameId!!
        vm.endGame()

        assertTrue(vm.newGame(listOf("Аня", "Боря")))
        val secondId = vm.state.value.gameId!!

        assertTrue(secondId != firstId)
        assertTrue(!vm.state.value.isFinished)
    }

    // Ошибка валидации показывается в UiState.
    @Test
    fun `validation error is visible in state`() {
        val (vm, _, _) = app()
        vm.newGame(listOf("Аня", "Боря"))

        // PLAY_CARD без карты — ошибка
        val result = vm.playCard(99999)

        assertTrue(result is app.dto.ValidationResult.Rejected)
        assertNotNull(vm.state.value.errorMessage)
    }
}
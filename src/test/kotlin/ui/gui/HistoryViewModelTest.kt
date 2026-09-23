package ui.gui

import app.service.HistoryService
import app.storage.JsonHistoryRepository
import domain.Game
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.random.Random

class HistoryViewModelTest {

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

    // Пустая история — пустой список.
    @Test
    fun `empty history yields empty state`() {
        val vm = HistoryViewModel(service())
        assertTrue(vm.state.value.games.isEmpty())
    }

    // После сохранения партии она появляется в списке.
    @Test
    fun `history contains saved games`() {
        val svc = service()
        svc.saveGame(finishedGame(1))
        svc.saveGame(finishedGame(2))

        val vm = HistoryViewModel(svc)

        assertEquals(2, vm.state.value.games.size)
        assertEquals(setOf(1, 2), vm.state.value.games.map { it.gameId }.toSet())
    }

    // Победитель корректно отображается в summary.
    @Test
    fun `summary shows winner`() {
        val svc = service()
        svc.saveGame(finishedGame(1))

        val vm = HistoryViewModel(svc)
        val summary = vm.state.value.games.first()

        assertEquals("Аня", summary.winner)
        assertEquals("Аня, Боря", summary.players)
    }

    // refresh подхватывает новые записи.
    @Test
    fun `refresh picks up newly saved games`() {
        val svc = service()
        val vm = HistoryViewModel(svc)
        assertTrue(vm.state.value.games.isEmpty())

        svc.saveGame(finishedGame(1))
        vm.refresh()

        assertEquals(1, vm.state.value.games.size)
    }
}
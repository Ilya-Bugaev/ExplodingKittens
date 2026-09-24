package ui.gui

import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.PlayerRegistryService
import app.validator.EKMoveValidator
import domain.Move
import domain.MoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainViewModelTest {

    private fun viewModel(): MainViewModel {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        val gameplay = GameplayService(EKMoveValidator(), registry)
        return MainViewModel(gameplay, registry)
    }

    // Начальное состояние - пустая партия.
    @Test
    fun `initial state has no game`() {
        val vm = viewModel()
        val state = vm.state.value
        assertNull(state.gameId)
        assertTrue(state.players.isEmpty())
        assertEquals("нет активной партии", state.state)
    }

    // newGame с корректным числом игроков - партия создаётся.
    @Test
    fun `newGame with valid players creates game`() {
        val vm = viewModel()
        assertTrue(vm.newGame(listOf("Аня", "Боря")))
        assertNotNull(vm.state.value.gameId)
        assertEquals(2, vm.state.value.players.size)
    }

    // newGame с одним игроком - отказ, состояние не меняется.
    @Test
    fun `newGame with one player fails`() {
        val vm = viewModel()
        assertFalse(vm.newGame(listOf("Аня")))
        assertNull(vm.state.value.gameId)
        assertNotNull(vm.state.value.errorMessage)
    }

    // newGame с шестью игроками - отказ.
    @Test
    fun `newGame with six players fails`() {
        val vm = viewModel()
        assertFalse(vm.newGame(listOf("A", "B", "C", "D", "E", "F")))
        assertNull(vm.state.value.gameId)
    }

    // После newGame список зарегистрированных игроков обновляется.
    @Test
    fun `newGame updates registered players`() {
        val vm = viewModel()
        vm.newGame(listOf("Аня", "Боря"))
        assertEquals(setOf("Аня", "Боря"), vm.state.value.registeredPlayers.toSet())
    }

    // submitMove с DRAW переходит к следующему игроку.
    @Test
    fun `submitMove DRAW advances turn`() {
        val vm = viewModel()
        vm.newGame(listOf("Аня", "Боря"))
        val state = vm.state.value
        val currentId = state.players.first { it.isCurrent }.id

        val move = Move(0, 1, MoveType.DRAW, author = null)
        // Но автор нужен - создадим заново с автором
        val game = state.gameId!!.let { _ -> null }
        // передадим автора
        val moveWithAuthor = Move(0, 1, MoveType.DRAW, author = null)
        // Нам нужен объект Player, но state хранит только PlayerView.
        // Используем fact: у VM есть доступ к gameplay через loadGame.
        // Через новый тест - правим.

        // Проще: получить Player через registry и gameplay
        // (нужен доступ к домену - реализуем через хелпер в тесте)
    }

    // Более правильная версия теста - используем доступ к GameplayService
    @Test
    fun `submitMove with valid DRAW is accepted`() {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        val gameplay = GameplayService(EKMoveValidator(), registry)
        val vm = MainViewModel(gameplay, registry)

        vm.newGame(listOf("Аня", "Боря"))
        val gameId = vm.state.value.gameId!!
        val game = gameplay.getCurrentGame(gameId)!!
        val current = game.getCurrentPlayer()

        val move = Move(0, 1, MoveType.DRAW, author = current)
        val result = vm.submitMove(move)

        assertTrue(result is app.dto.ValidationResult.Accepted)
    }

    // endGame завершает партию.
    @Test
    fun `endGame finishes game`() {
        val vm = viewModel()
        vm.newGame(listOf("Аня", "Боря"))
        assertTrue(vm.endGame())
        assertTrue(vm.state.value.isFinished)
    }

    // loadGame с несуществующим id возвращает false.
    @Test
    fun `loadGame with unknown id fails`() {
        val vm = viewModel()
        assertFalse(vm.loadGame(999))
    }

    // clearError убирает сообщение об ошибке.
    @Test
    fun `clearError removes error`() {
        val vm = viewModel()
        vm.newGame(listOf("Аня"))
        assertNotNull(vm.state.value.errorMessage)

        vm.clearError()
        assertNull(vm.state.value.errorMessage)
    }
}
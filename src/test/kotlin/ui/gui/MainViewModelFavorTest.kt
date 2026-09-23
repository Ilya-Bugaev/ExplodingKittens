package ui.gui

import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.PlayerRegistryService
import app.validator.EKMoveValidator
import domain.Card
import domain.CardType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainViewModelFavorTest {

    private fun setup(): Triple<MainViewModel, GameplayService, Int> {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        val gameplay = GameplayService(EKMoveValidator(), registry)
        val vm = MainViewModel(gameplay, registry)
        vm.newGame(listOf("Аня", "Боря"))
        val id = vm.state.value.gameId!!
        return Triple(vm, gameplay, id)
    }

    // FAVOR с целью проходит, партия переходит в AwaitingResponse.
    @Test
    fun `playFavor returns awaiting response`() {
        val (vm, gameplay, id) = setup()
        val game = gameplay.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val target = game.players.first { it.id != current.id }
        val favor = Card(9999, CardType.FAVOR)
        current.addCard(favor)
        // обновим состояние, чтобы карта появилась в UiState
        vm.loadGame(id)

        val result = vm.playFavor(favor.id, target.id)

        assertTrue(result is app.dto.ValidationResult.AwaitingResponse)
    }

    // FAVOR с некорректной целью отклоняется.
    @Test
    fun `playFavor with bad target is rejected`() {
        val (vm, gameplay, id) = setup()
        val game = gameplay.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val favor = Card(9999, CardType.FAVOR)
        current.addCard(favor)
        vm.loadGame(id)

        val result = vm.playFavor(favor.id, 99999)

        assertTrue(result is app.dto.ValidationResult.Rejected)
    }
}
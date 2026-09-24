package ui.gui

import app.dto.ValidationResult
import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.PlayerRegistryService
import app.validator.EKMoveValidator
import domain.Card
import domain.CardType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainViewModelComboTest {

    private fun setup(): Triple<MainViewModel, GameplayService, Int> {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        val gameplay = GameplayService(EKMoveValidator(), registry)
        val vm = MainViewModel(gameplay, registry)
        vm.newGame(listOf("Аня", "Боря"))
        val id = vm.state.value.gameId!!
        return Triple(vm, gameplay, id)
    }

    // Пара одинаковых карт принимается.
    @Test
    fun `pair of same type is accepted`() {
        val (vm, gameplay, id) = setup()
        val game = gameplay.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val target = game.players[1]
        val c1 = Card(9001, CardType.CAT_TACO)
        val c2 = Card(9002, CardType.CAT_TACO)
        current.addCard(c1)
        current.addCard(c2)
        vm.loadGame(id)

        val result = vm.playTwoOfAKind(listOf(c1.id, c2.id), target.id)

        assertTrue(result is ValidationResult.Accepted)
    }

    // Пара карт разного типа отклоняется.
    @Test
    fun `pair with different types is rejected`() {
        val (vm, gameplay, id) = setup()
        val game = gameplay.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val target = game.players[1]
        val c1 = Card(9001, CardType.CAT_TACO)
        val c2 = Card(9002, CardType.CAT_BEARD)
        current.addCard(c1)
        current.addCard(c2)
        vm.loadGame(id)

        val result = vm.playTwoOfAKind(listOf(c1.id, c2.id), target.id)

        assertTrue(result is ValidationResult.Rejected)
    }

    // Тройка с запросом типа принимается.
    @Test
    fun `triple with requested type is accepted`() {
        val (vm, gameplay, id) = setup()
        val game = gameplay.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val target = game.players[1]
        val cards = listOf(
            Card(9001, CardType.CAT_TACO),
            Card(9002, CardType.CAT_TACO),
            Card(9003, CardType.CAT_TACO)
        )
        cards.forEach { current.addCard(it) }
        vm.loadGame(id)

        val result = vm.playThreeOfAKind(cards.map { it.id }, target.id, CardType.SKIP)

        assertTrue(result is ValidationResult.Accepted)
    }

    // Пятёрка разных с выбором карты из сброса.
    @Test
    fun `five different with chosen discard card is accepted`() {
        val (vm, gameplay, id) = setup()
        val game = gameplay.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val cards = listOf(
            Card(9001, CardType.CAT_TACO),
            Card(9002, CardType.CAT_BEARD),
            Card(9003, CardType.CAT_HAIRY_POTATO),
            Card(9004, CardType.CAT_CATERMELON),
            Card(9005, CardType.CAT_RAINBOW_RALPHING)
        )
        cards.forEach { current.addCard(it) }
        val discardCard = Card(8000, CardType.SKIP)
        game.discardPile.add(discardCard)
        vm.loadGame(id)

        val result = vm.playFiveDifferent(cards.map { it.id }, discardCard.id)

        assertTrue(result is ValidationResult.Accepted)
        assertTrue(current.hand.contains(discardCard))
    }

    // Пятёрка с повторами отклоняется.
    @Test
    fun `five different with duplicates is rejected`() {
        val (vm, gameplay, id) = setup()
        val game = gameplay.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val cards = listOf(
            Card(9001, CardType.CAT_TACO),
            Card(9002, CardType.CAT_TACO),
            Card(9003, CardType.CAT_HAIRY_POTATO),
            Card(9004, CardType.CAT_CATERMELON),
            Card(9005, CardType.CAT_RAINBOW_RALPHING)
        )
        cards.forEach { current.addCard(it) }
        game.discardPile.add(Card(8000, CardType.SKIP))
        vm.loadGame(id)

        val result = vm.playFiveDifferent(cards.map { it.id })

        assertTrue(result is ValidationResult.Rejected)
    }
}
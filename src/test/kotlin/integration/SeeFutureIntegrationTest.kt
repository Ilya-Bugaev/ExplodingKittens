package integration

import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.PlayerRegistryService
import app.validator.EKMoveValidator
import domain.Card
import domain.CardType
import domain.Move
import domain.MoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SeeFutureIntegrationTest {

    private fun newService(): GameplayService {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        return GameplayService(EKMoveValidator(), registry)
    }

    // SEE_FUTURE заполняет lastPeekedCards тремя верхними картами.
    @Test
    fun `see future peeks top three cards`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val expected = game.deck.peekTop(3)

        val card = Card(9999, CardType.SEE_FUTURE)
        current.addCard(card)
        val move = Move(0, 1, MoveType.PLAY_CARD, author = current, cardsPlayed = listOf(card))
        service.playMove(id, move)
        service.resolveNopeWindow(id)

        assertEquals(expected.map { it.id }, game.lastPeekedCards?.map { it.id })
    }

    // Колода не меняется после SEE_FUTURE.
    @Test
    fun `see future does not modify deck`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val sizeBefore = game.deck.size

        val current = game.getCurrentPlayer()
        val card = Card(9999, CardType.SEE_FUTURE)
        current.addCard(card)
        service.playMove(id, Move(0, 1, MoveType.PLAY_CARD, author = current, cardsPlayed = listOf(card)))
        service.resolveNopeWindow(id)

        assertEquals(sizeBefore, game.deck.size)
    }
}
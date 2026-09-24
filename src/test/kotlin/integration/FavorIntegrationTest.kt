package integration

import app.dto.ValidationResult
import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.PlayerRegistryService
import app.validator.EKMoveValidator
import domain.Card
import domain.CardType
import domain.Move
import domain.MoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class FavorIntegrationTest {

    private fun newService(): GameplayService {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        return GameplayService(EKMoveValidator(), registry)
    }

    // Полный цикл: FAVOR, ответ картой, карта у автора.
    @Test
    fun `favor transfers chosen card to author`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.players[0]
        val target = game.players[1]

        // Аня играет FAVOR
        val favor = Card(9999, CardType.FAVOR)
        author.addCard(favor)
        val favorMove = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author, cardsPlayed = listOf(favor), target = target
        )
        val r1 = service.playMove(id, favorMove)
        assertTrue(r1 is ValidationResult.AwaitingResponse)

        // Боря отдаёт карту
        val cardToGive = target.hand.first()
        val responseMove = Move(
            0, 2, MoveType.RESOLVE_PENDING,
            author = target, cardsPlayed = listOf(cardToGive)
        )
        val r2 = service.playMove(id, responseMove)

        assertTrue(r2 is ValidationResult.Accepted)
        assertTrue(author.hand.contains(cardToGive))
        assertTrue(!target.hand.contains(cardToGive))
    }

    // FAVOR-карта уходит в сброс сразу, ход остаётся у автора.
    @Test
    fun `favor card goes to discard and turn stays with author`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.players[0]
        val target = game.players[1]
        val discardBefore = game.discardPile.size()

        val favor = Card(9999, CardType.FAVOR)
        author.addCard(favor)
        val favorMove = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author, cardsPlayed = listOf(favor), target = target
        )
        service.playMove(id, favorMove)

        // FAVOR ушёл в сброс сразу, ход ещё у Ани
        assertEquals(discardBefore + 1, game.discardPile.size())
        assertEquals("Аня", game.getCurrentPlayer().name)

        val cardToGive = target.hand.first()
        val responseMove = Move(
            0, 2, MoveType.RESOLVE_PENDING,
            author = target, cardsPlayed = listOf(cardToGive)
        )
        service.playMove(id, responseMove)

        // После ответа ход ВСЁ ЕЩЁ у Ани — FAVOR не завершает ход
        assertEquals("Аня", game.getCurrentPlayer().name)
        assertTrue(author.hand.contains(cardToGive))
    }

    // Ответ не той цели отклоняется.
    @Test
    fun `wrong responder is rejected`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря", "Ваня"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.players[0]
        val target = game.players[1]

        val favor = Card(9999, CardType.FAVOR)
        author.addCard(favor)
        service.playMove(id, Move(
            0, 1, MoveType.PLAY_CARD,
            author = author, cardsPlayed = listOf(favor), target = target
        ))

        // Отвечает не Боря, а Ваня
        val wrong = game.players[2]
        val cardToGive = wrong.hand.first()
        val response = Move(
            0, 2, MoveType.RESOLVE_PENDING,
            author = wrong, cardsPlayed = listOf(cardToGive)
        )
        val result = service.playMove(id, response)

        assertTrue(result is ValidationResult.Rejected)
    }

    // Повторный RESOLVE_PENDING после завершения FAVOR отклоняется.
    @Test
    fun `second response without pending favor is rejected`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.players[0]
        val target = game.players[1]

        // Без FAVOR — нет pending
        val cardToGive = target.hand.first()
        val response = Move(
            0, 1, MoveType.RESOLVE_PENDING,
            author = target, cardsPlayed = listOf(cardToGive)
        )
        val result = service.playMove(id, response)

        assertTrue(result is ValidationResult.Rejected)
    }
}
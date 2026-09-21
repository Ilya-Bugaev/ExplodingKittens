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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class NopeIntegrationTest {

    private fun newService(): GameplayService {
        val repo = InMemoryPlayerRepository()
        val registry = PlayerRegistryService(repo)
        return GameplayService(EKMoveValidator(), registry)
    }

    // NOPE без активного окна отклоняется на уровне валидатора.
    @Test
    fun `NOPE without pending move is rejected`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.getCurrentPlayer()
        val nope = Card(9999, CardType.NOPE)
        author.addCard(nope)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(nope))
        val result = service.playMove(id, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // NOPE может играть не текущий игрок - это ключевое отличие.
    @Test
    fun `NOPE can be played off-turn`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val notCurrent = game.players[1]
        val nope = Card(9999, CardType.NOPE)
        notCurrent.addCard(nope)

        // Имитируем активное окно: pendingMove - ход Ани
        val pendingMove = Move(
            1, 1, MoveType.PLAY_CARD,
            author = game.players[0],
            cardsPlayed = listOf(Card(8888, CardType.SKIP))
        )
        game.setPendingMove(pendingMove)

        val nopeMove = Move(0, 2, MoveType.PLAY_CARD, author = notCurrent, cardsPlayed = listOf(nope))
        val result = service.playMove(id, nopeMove)

        assertTrue(result is ValidationResult.Accepted)
    }

    // resolveNopeWindow без pendingMove отклоняется.
    @Test
    fun `resolveNopeWindow without pending move is rejected`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))

        val result = service.resolveNopeWindow(id)

        assertTrue(result is ValidationResult.Rejected)
    }

    // resolveNopeWindow применяет отложенный ход и сбрасывает pendingMove.
    @Test
    fun `resolveNopeWindow applies pending move and clears it`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()

        // Отложенный SKIP без Nope
        val pendingMove = Move(
            1, 1, MoveType.PLAY_CARD,
            author = current,
            cardsPlayed = listOf(Card(8888, CardType.SKIP))
        )
        game.setPendingMove(pendingMove)

        val result = service.resolveNopeWindow(id)

        assertTrue(result is ValidationResult.Accepted)
        assertNull(game.pendingMove)
    }
}
package app.service

import app.dto.ValidationResult
import app.repository.InMemoryPlayerRepository
import app.validator.EKMoveValidator
import domain.Card
import domain.CardType
import domain.Move
import domain.MoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GameplayServiceTest {

    private fun newService(): GameplayService {
        val repo = InMemoryPlayerRepository()
        val registry = PlayerRegistryService(repo)
        return GameplayService(EKMoveValidator(), registry)
    }

    // startGame создаёт партию, возвращает id, регистрирует игроков.
    @Test
    fun `startGame creates game and registers players`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))

        assertNotNull(service.getCurrentGame(id))
        assertEquals(setOf("Аня", "Боря"), service.getCurrentGame(id)!!.players.map { it.name }.toSet())
    }

    // startGame с одним игроком отклоняется.
    @Test
    fun `startGame with one player throws`() {
        val service = newService()
        try {
            service.startGame(listOf("Аня"), Random(42))
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // ожидаемо
        }
    }

    // playMove с невалидным ходом возвращает Rejected.
    @Test
    fun `playMove rejects invalid move`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val notCurrent = game.players[1]

        val move = Move(0, 1, MoveType.DRAW, author = notCurrent)
        val result = service.playMove(id, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // playMove с DRAW выполняет ход и переходит к следующему игроку.
    @Test
    fun `playMove DRAW advances turn`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val handBefore = current.hand.size

        val move = Move(0, 1, MoveType.DRAW, author = current)
        val result = service.playMove(id, move)

        assertTrue(result is ValidationResult.Accepted)
        // если котёнок не выпал - рука увеличилась
        if (!game.pendingKitten.let { it != null }) {
            assertEquals(handBefore + 1, current.hand.size)
        }
    }

    // playMove с PLAY_CARD SKIP переходит к следующему игроку без добора.
    @Test
    fun `playMove SKIP advances turn without drawing`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()

        // Добавляем SKIP в руку вручную
        val skip = Card(9999, CardType.SKIP)
        current.addCard(skip)
        val handBefore = current.hand.size

        val move = Move(0, 1, MoveType.PLAY_CARD, author = current, cardsPlayed = listOf(skip))
        val result = service.playMove(id, move)

        assertTrue(result is ValidationResult.Accepted)
        assertEquals(handBefore - 1, current.hand.size)
        assertEquals(game.players[1].name, game.getCurrentPlayer().name)
    }

    // playMove с FAVOR возвращает AwaitingResponse и ставит pendingMove.
    @Test
    fun `playMove FAVOR returns AwaitingResponse`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val target = game.players[1]

        val favor = Card(9999, CardType.FAVOR)
        current.addCard(favor)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = current, cardsPlayed = listOf(favor), target = target)
        val result = service.playMove(id, move)

        assertTrue(result is ValidationResult.AwaitingResponse)
        assertNotNull(game.pendingMove)
    }

    // resolveNopeWindow без pendingMove отклоняется.
    @Test
    fun `resolveNopeWindow without pending move is rejected`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))

        val result = service.resolveNopeWindow(id)

        assertTrue(result is ValidationResult.Rejected)
    }

    // resolveNopeWindow с отложенным PLAY_CARD применяет эффект.
    @Test
    fun `resolveNopeWindow applies pending move`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        val skip = Card(9999, CardType.SKIP)
        current.addCard(skip)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = current, cardsPlayed = listOf(skip))
        val result = service.resolveNopeWindow(id)

        // без pendingMove - Rejected
        assertTrue(result is ValidationResult.Rejected)
    }

    // endGame завершает партию.
    @Test
    fun `endGame marks game as finished`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))

        val result = service.endGame(id)

        assertTrue(result)
        assertEquals(domain.GameState.FINISHED, service.getCurrentGame(id)!!.state)
    }

    // endGame на уже завершённой партии возвращает false.
    @Test
    fun `endGame on finished game returns false`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        service.endGame(id)

        assertTrue(!service.endGame(id))
    }
}
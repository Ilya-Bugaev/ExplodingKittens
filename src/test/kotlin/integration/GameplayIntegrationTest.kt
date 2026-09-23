package integration

import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.PlayerRegistryService
import app.validator.EKMoveValidator
import app.dto.ValidationResult
import domain.Card
import domain.CardType
import domain.Game
import domain.GameState
import domain.Move
import domain.MoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GameplayIntegrationTest {

    // Создаёт связанный набор зависимостей как в Main.kt.
    private fun newService(): GameplayService {
        val repo = InMemoryPlayerRepository()
        val registry = PlayerRegistryService(repo)
        return GameplayService(EKMoveValidator(), registry)
    }

    private fun startedGame(service: GameplayService, vararg names: String): Game {
        val id = service.startGame(names.toList(), Random(42))
        return service.getCurrentGame(id)!!
    }

    // Полный цикл: старт → регистрация игроков → ход → переход очереди.
    @Test
    fun `start, draw, turn advances`() {
        val service = newService()
        val game = startedGame(service, "Аня", "Боря", "Ваня")

        val first = game.getCurrentPlayer().name
        val move = Move(0, 1, MoveType.DRAW, author = game.getCurrentPlayer())
        val result = service.playMove(game.id, move)

        assertTrue(result is ValidationResult.Accepted)
        assertEquals("Боря", game.getCurrentPlayer().name)
        assertTrue(first == "Аня")
    }

    // SKIP завершает ход, карта уходит из руки в сброс.
    @Test
    fun `SKIP ends turn and moves card to discard`() {
        val service = newService()
        val game = startedGame(service, "Аня", "Боря")
        val current = game.getCurrentPlayer()
        val skip = Card(9999, CardType.SKIP)
        current.addCard(skip)
        val handBefore = current.hand.size

        val move = Move(0, 1, MoveType.PLAY_CARD, author = current, cardsPlayed = listOf(skip))
        service.playMove(game.id, move)
        service.resolveNopeWindow(game.id)   // ← добавить

        assertEquals(handBefore - 1, current.hand.size)
        assertEquals("Боря", game.getCurrentPlayer().name)
    }

    // SHUFFLE не завершает ход - игрок может продолжить.
    @Test
    fun `SHUFFLE keeps turn with current player`() {
        val service = newService()
        val game = startedGame(service, "Аня", "Боря")
        val current = game.getCurrentPlayer()
        val shuffle = Card(9999, CardType.SHUFFLE)
        current.addCard(shuffle)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = current, cardsPlayed = listOf(shuffle))
        service.playMove(game.id, move)
        service.resolveNopeWindow(game.id)   // ← добавить

        assertEquals("Аня", game.getCurrentPlayer().name)
    }

    // ATTACK передаёт ход следующему и выставляет attacksPending = 2.
    @Test
    fun `ATTACK sets attacksPending and advances turn`() {
        val service = newService()
        val game = startedGame(service, "Аня", "Боря", "Ваня")
        val current = game.getCurrentPlayer()
        val attack = Card(9999, CardType.ATTACK)
        current.addCard(attack)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = current, cardsPlayed = listOf(attack))
        service.playMove(game.id, move)
        service.resolveNopeWindow(game.id)   // ← добавить

        assertEquals(2, game.attacksPending)
        assertEquals("Боря", game.getCurrentPlayer().name)
    }

    // FAVOR возвращает AwaitingResponse и не завершает ход.
    @Test
    fun `FAVOR returns AwaitingResponse and sets pendingMove`() {
        val service = newService()
        val game = startedGame(service, "Аня", "Боря")
        val current = game.getCurrentPlayer()
        val target = game.players[1]
        val favor = Card(9999, CardType.FAVOR)
        current.addCard(favor)

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = current, cardsPlayed = listOf(favor), target = target
        )
        val result = service.playMove(game.id, move)

        assertTrue(result is ValidationResult.AwaitingResponse)
        assertNotNull(game.pendingMove)
    }

    // Пара одинаковых карт крадёт случайную карту у цели.
    @Test
    fun `two of a kind steals a card from target`() {
        val service = newService()
        val game = startedGame(service, "Аня", "Боря")
        val author = game.players[0]
        val target = game.players[1]
        val c1 = Card(9001, CardType.CAT_TACO)
        val c2 = Card(9002, CardType.CAT_TACO)
        author.addCard(c1)
        author.addCard(c2)
        val targetHandBefore = target.hand.size
        val authorHandBefore = author.hand.size

        val move = Move(
            0, 1, MoveType.PLAY_TWO_OF_A_KIND,
            author = author, target = target, cardsPlayed = listOf(c1, c2)
        )
        val result = service.playMove(game.id, move)

        assertTrue(result is ValidationResult.Accepted)
        assertEquals(targetHandBefore - 1, target.hand.size)
        // -2 сыгранные +1 украденная = -1
        assertEquals(authorHandBefore - 1, author.hand.size)
    }

    // Партия завершается через endGame, state переходит в FINISHED.
    @Test
    fun `endGame finishes the game`() {
        val service = newService()
        val game = startedGame(service, "Аня", "Боря")

        val ended = service.endGame(game.id)

        assertTrue(ended)
        assertEquals(GameState.FINISHED, game.state)
    }
}
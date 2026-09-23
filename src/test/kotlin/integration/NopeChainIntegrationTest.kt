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

class NopeChainIntegrationTest {

    private fun newService(): GameplayService {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        return GameplayService(EKMoveValidator(), registry)
    }

    // ATTACK открывает окно Nope, не применяя эффект.
    @Test
    fun `attack opens nope window without applying`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.players[0]
        val attack = Card(9999, CardType.ATTACK)
        author.addCard(attack)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(attack))
        val result = service.playMove(id, move)

        assertTrue(result is ValidationResult.AwaitingNope)
        assertEquals("Аня", game.getCurrentPlayer().name)  // ход не перешёл
        assertEquals(0, game.attacksPending)               // эффект не применён
    }

    // Один NOPE отменяет ATTACK.
    @Test
    fun `single nope cancels attack`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val anna = game.players[0]
        val boris = game.players[1]
        val attack = Card(9999, CardType.ATTACK)
        val nope = Card(8888, CardType.NOPE)
        anna.addCard(attack)
        boris.addCard(nope)

        // Аня играет ATTACK — окно открыто
        service.playMove(id, Move(0, 1, MoveType.PLAY_CARD, author = anna, cardsPlayed = listOf(attack)))

        // Боря играет NOPE
        val nopeMove = Move(
            0, 2, MoveType.PLAY_CARD,
            author = boris, cardsPlayed = listOf(nope),
            cancels = game.pendingMove
        )
        val r = service.playMove(id, nopeMove)
        assertTrue(r is ValidationResult.AwaitingNope)

        // Закрываем окно — NOPE побеждает
        val resolved = service.resolveNopeWindow(id)
        assertTrue(resolved is ValidationResult.Rejected)
        assertEquals(0, game.attacksPending)
    }

    // NOPE на NOPE восстанавливает ATTACK.
    @Test
    fun `nope on nope restores attack`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря", "Ваня"), Random(42))
        val game = service.getCurrentGame(id)!!
        val anna = game.players[0]
        val boris = game.players[1]
        val vanya = game.players[2]
        val attack = Card(9999, CardType.ATTACK)
        val nope1 = Card(8888, CardType.NOPE)
        val nope2 = Card(7777, CardType.NOPE)
        anna.addCard(attack)
        boris.addCard(nope1)
        vanya.addCard(nope2)

        service.playMove(id, Move(0, 1, MoveType.PLAY_CARD, author = anna, cardsPlayed = listOf(attack)))

        // Боря играет NOPE
        val nopeMove1 = Move(
            0, 2, MoveType.PLAY_CARD,
            author = boris, cardsPlayed = listOf(nope1),
            cancels = game.pendingMove
        )
        service.playMove(id, nopeMove1)

        // Ваня играет NOPE на NOPE
        val nopeMove2 = Move(
            0, 3, MoveType.PLAY_CARD,
            author = vanya, cardsPlayed = listOf(nope2),
            cancels = game.pendingMove
        )
        service.playMove(id, nopeMove2)

        // Закрываем — чётное количество NOPE, ATTACK работает
        val resolved = service.resolveNopeWindow(id)

        assertTrue(resolved is ValidationResult.Accepted)
        assertEquals(2, game.attacksPending)
    }

    // NOPE без активного окна отклоняется.
    @Test
    fun `nope without pending move is rejected`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.getCurrentPlayer()
        val nope = Card(8888, CardType.NOPE)
        author.addCard(nope)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(nope))
        val result = service.playMove(id, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // DEFUSE нельзя отменить NOPE-ом.
    @Test
    fun `nope cannot cancel defuse`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val anna = game.players[0]
        val boris = game.players[1]
        val defuse = anna.findCardsOfType(CardType.DEFUSE).first()
        val nope = Card(8888, CardType.NOPE)
        boris.addCard(nope)
        game.setPendingKitten(Card(6666, CardType.EXPLODING_KITTEN))

        // Аня играет DEFUSE — ждём Accepted (не AwaitingNope)
        val defuseMove = Move(
            0, 1, MoveType.PLAY_CARD,
            author = anna, cardsPlayed = listOf(defuse),
            placedKittenPosition = 0
        )
        val r1 = service.playMove(id, defuseMove)
        assertTrue(r1 is ValidationResult.Accepted)

        // Боря пытается NOPE — но окна нет (DEFUSE не открывает)
        val nopeMove = Move(0, 2, MoveType.PLAY_CARD, author = boris, cardsPlayed = listOf(nope))
        val r2 = service.playMove(id, nopeMove)
        assertTrue(r2 is ValidationResult.Rejected)
    }
}
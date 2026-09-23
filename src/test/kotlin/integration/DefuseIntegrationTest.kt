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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DefuseIntegrationTest {

    private fun newService(): GameplayService {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        return GameplayService(EKMoveValidator(), registry)
    }

    // DEFUSE возвращает котёнка в колоду на указанную позицию.
    @Test
    fun `defuse returns kitten to deck at given position`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.players[0]

        val kitten = Card(9999, CardType.EXPLODING_KITTEN)
        game.setPendingKitten(kitten)

        val defuse = author.findCardsOfType(CardType.DEFUSE).first()
        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author, cardsPlayed = listOf(defuse),
            placedKittenPosition = 5
        )
        val result = service.playMove(id, move)

        assertTrue(result is ValidationResult.Accepted)
        assertEquals(kitten, game.deck.peekTop(6).last())
        assertNull(game.pendingKitten)
        assertTrue(!author.hand.contains(defuse))
    }

    // После DEFUSE ход переходит к следующему игроку.
    @Test
    fun `defuse advances turn after resolving kitten`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.players[0]
        game.setPendingKitten(Card(9999, CardType.EXPLODING_KITTEN))

        val defuse = author.findCardsOfType(CardType.DEFUSE).first()
        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author, cardsPlayed = listOf(defuse),
            placedKittenPosition = 0
        )
        service.playMove(id, move)

        assertEquals("Боря", game.getCurrentPlayer().name)
    }

    // DEFUSE без вытянутого котёнка отклоняется.
    @Test
    fun `defuse without pending kitten is rejected`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.players[0]
        val defuse = author.findCardsOfType(CardType.DEFUSE).first()

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author, cardsPlayed = listOf(defuse),
            placedKittenPosition = 0
        )
        val result = service.playMove(id, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // Позиция вне колоды отклоняется.
    @Test
    fun `defuse with out-of-bounds position is rejected`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val author = game.players[0]
        game.setPendingKitten(Card(9999, CardType.EXPLODING_KITTEN))
        val defuse = author.findCardsOfType(CardType.DEFUSE).first()

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author, cardsPlayed = listOf(defuse),
            placedKittenPosition = game.deck.size + 10
        )
        val result = service.playMove(id, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // DRAW с вытянутым котёнком не переводит ход.
    @Test
    fun `drawing a kitten keeps turn with the current player`() {
        val service = newService()
        val id = service.startGame(listOf("Аня", "Боря"), Random(42))
        val game = service.getCurrentGame(id)!!
        val currentBefore = game.getCurrentPlayer().name

        val kitten = Card(9999, CardType.EXPLODING_KITTEN)
        game.deck.insertCardAtTop(kitten)

        val current = game.getCurrentPlayer()
        service.playMove(id, Move(0, 1, MoveType.DRAW, author = current))

        assertEquals(currentBefore, game.getCurrentPlayer().name)
        assertTrue(game.pendingKitten != null)
    }
}
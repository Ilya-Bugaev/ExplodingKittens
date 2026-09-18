package app.validator

import app.dto.ValidationResult
import domain.Game
import domain.Move
import domain.MoveType
import domain.Player
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random


class EKMoveValidatorTest {

    private val validator = EKMoveValidator()

    // Создаёт партию с фиксированным seed.
    private fun startedGame(vararg names: String): Game {
        val game = Game(id = 1)
        game.startGame(names.toList(), Random(42))
        return game
    }

    // Создаёт партию в состоянии SETUP без игроков.
    private fun setupGame(): Game = Game(id = 1)

    // Общие проверки

    // Ход в завершённой партии отклоняется.
    @Test
    fun `move in finished game is rejected`() {
        val game = startedGame("Аня", "Боря")
        game.finish()

        val move = Move(0, 1, MoveType.DRAW, author = game.players[0])
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // Ход до старта партии (кроме START) отклоняется.
    @Test
    fun `non-START move before start is rejected`() {
        val game = setupGame()
        val fakeAuthor = Player(0, "Аня")

        val move = Move(0, 0, MoveType.DRAW, author = fakeAuthor)
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // Ход без автора отклоняется.
    @Test
    fun `move without author is rejected`() {
        val game = startedGame("Аня", "Боря")
        val move = Move(0, 1, MoveType.DRAW, author = null)

        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
        assertTrue((result as ValidationResult.Rejected).errors.any { "author" in it })
    }

    // Ход выбывшего игрока отклоняется.
    @Test
    fun `move by eliminated author is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        author.eliminate()

        val move = Move(0, 1, MoveType.DRAW, author = author)
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // Ход не в свою очередь отклоняется.
    @Test
    fun `move by non-current player is rejected`() {
        val game = startedGame("Аня", "Боря")
        val notCurrent = game.players[1]

        val move = Move(0, 1, MoveType.DRAW, author = notCurrent)
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
        assertTrue((result as ValidationResult.Rejected).errors.any { "turn" in it })
    }

    // START

    // START с автором отклоняется.
    @Test
    fun `START with author is rejected`() {
        val game = setupGame()
        val author = Player(0, "Аня")

        val move = Move(0, 0, MoveType.START, author = author)
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // Первый START проходит.
    @Test
    fun `first START is accepted`() {
        val game = setupGame()
        val move = Move(0, 0, MoveType.START)

        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Accepted)
    }

    // Второй START отклоняется.
    @Test
    fun `second START is rejected`() {
        val game = startedGame("Аня", "Боря")
        val move = Move(0, 0, MoveType.START)

        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // DRAW

    // DRAW в свою очередь проходит.
    @Test
    fun `DRAW by current player is accepted`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]

        val move = Move(0, 1, MoveType.DRAW, author = author)
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Accepted)
    }

    // DRAW из пустой колоды отклоняется.
    @Test
    fun `DRAW from empty deck is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]

        // Опустошаем колоду вручную
        repeat(game.deck.size()) { game.deck.draw() }

        val move = Move(0, 1, MoveType.DRAW, author = author)
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
        assertTrue((result as ValidationResult.Rejected).errors.any { "deck" in it })
    }

    // PLAY_CARD пока проходит как заглушка — проверяем, что не падает
    @Test
    fun `PLAY_CARD is currently accepted as placeholder`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val card = author.hand.first()

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author,
            cardsPlayed = listOf(card)
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Accepted)
    }
}
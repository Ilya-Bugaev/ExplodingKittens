package app.validator

import app.dto.ValidationResult
import domain.Card
import domain.CardType
import domain.CardType.FAVOR
import domain.Game
import domain.Move
import domain.MoveType
import domain.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.collections.get
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
    fun `first START is AwaitingNope`() {
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
    fun `DRAW by current player is AwaitingNope`() {
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
        repeat(game.deck.size) { game.deck.draw() }

        val move = Move(0, 1, MoveType.DRAW, author = author)
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
        assertTrue((result as ValidationResult.Rejected).errors.any { "deck" in it })
    }

    // PLAY_CARD: карта не в руке — отклоняется.
    @Test
    fun `PLAY_CARD with card not in hand is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val notInHand = Card(9999, CardType.SKIP)

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author,
            cardsPlayed = listOf(notInHand)
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // PLAY_CARD: пустой список карт — отклоняется.
    @Test
    fun `PLAY_CARD with empty cards is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = emptyList())
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // PLAY_CARD: взрывного котёнка играть нельзя.
    @Test
    fun `PLAY_CARD exploding kitten is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val kitten = Card(9999, CardType.EXPLODING_KITTEN)
        author.addCard(kitten)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(kitten))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // PLAY_CARD: кошкокарту нельзя играть одиночно.
    @Test
    fun `PLAY_CARD cat card alone is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val catCard = Card(9999, CardType.CAT_TACO)
        author.addCard(catCard)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(catCard))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // PLAY_CARD: SKIP проходит, если карта в руке.
    @Test
    fun `PLAY_CARD SKIP is AwaitingNope when in hand`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val skip = Card(9999, CardType.SKIP)
        author.addCard(skip)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(skip))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.AwaitingNope)
    }

    // PLAY_CARD: SHUFFLE проходит, если карта в руке.
    @Test
    fun `PLAY_CARD SHUFFLE is AwaitingNope when in hand`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val shuffle = Card(9999, CardType.SHUFFLE)
        author.addCard(shuffle)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(shuffle))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.AwaitingNope)
    }

    // PLAY_CARD: SEE_FUTURE проходит, если карта в руке.
    @Test
    fun `PLAY_CARD SEE_FUTURE is AwaitingNope when in hand`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val seeFuture = Card(9999, CardType.SEE_FUTURE)
        author.addCard(seeFuture)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(seeFuture))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.AwaitingNope)
    }

    // PLAY_CARD: ATTACK проходит, если есть живой соперник.
    @Test
    fun `PLAY_CARD ATTACK is AwaitingNope when opponent alive`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val attack = Card(9999, CardType.ATTACK)
        author.addCard(attack)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(attack))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.AwaitingNope)
    }

    // DEFUSE без ожидающего котёнка — отклоняется.
    @Test
    fun `DEFUSE without pending kitten is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val defuse = author.findCardsOfType(CardType.DEFUSE).first()

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author,
            cardsPlayed = listOf(defuse),
            placedKittenPosition = 5
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // DEFUSE с ожидающим котёнком, но без позиции — отклоняется.
    @Test
    fun `DEFUSE without position is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val defuse = author.findCardsOfType(CardType.DEFUSE).first()
        game.setPendingKitten(Card(9999, CardType.EXPLODING_KITTEN))

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author,
            cardsPlayed = listOf(defuse)
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // DEFUSE с котёнком и корректной позицией — принимается.
    @Test
    fun `DEFUSE with pending kitten and position is AwaitingNope`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val defuse = author.findCardsOfType(CardType.DEFUSE).first()
        game.setPendingKitten(Card(9999, CardType.EXPLODING_KITTEN))

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author,
            cardsPlayed = listOf(defuse),
            placedKittenPosition = 5
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Accepted)
    }

    // DEFUSE с позицией за пределами колоды — отклоняется.
    @Test
    fun `DEFUSE with out-of-bounds position is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val defuse = author.findCardsOfType(CardType.DEFUSE).first()
        game.setPendingKitten(Card(9999, CardType.EXPLODING_KITTEN))

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author,
            cardsPlayed = listOf(defuse),
            placedKittenPosition = game.deck.size + 100
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // FAVOR без цели — отклоняется.
    @Test
    fun `FAVOR without target is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val favor = Card(9999, CardType.FAVOR)
        author.addCard(favor)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(favor))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // FAVOR с целью-автором — отклоняется.
    @Test
    fun `FAVOR targeting self is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val favor = Card(9999, FAVOR)
        author.addCard(favor)

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author,
            cardsPlayed = listOf(favor),
            target = author
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // FAVOR с выбывшей целью — отклоняется.
    @Test
    fun `FAVOR targeting eliminated player is rejected`() {
        val game = startedGame("Аня", "Боря", "Ваня")
        val author = game.players[0]
        val target = game.players[1]
        target.eliminate()
        val favor = Card(9999, FAVOR)
        author.addCard(favor)

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author,
            cardsPlayed = listOf(favor),
            target = target
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // FAVOR с живой целью — возвращает AwaitingResponse с responderId = target.id.
    @Test
    fun `FAVOR targeting alive opponent returns AwaitingResponse`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val target = game.players[1]
        val favor = Card(9999, FAVOR)
        author.addCard(favor)

        val move = Move(
            0, 1, MoveType.PLAY_CARD,
            author = author,
            cardsPlayed = listOf(favor),
            target = target
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.AwaitingResponse)
        assertEquals(target.id, (result as ValidationResult.AwaitingResponse).responderId)
    }

    // NOPE без активного окна — отклоняется.
    @Test
    fun `NOPE without pending move is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val nope = Card(9999, CardType.NOPE)
        author.addCard(nope)

        val move = Move(0, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(nope))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // NOPE на DRAW — отклоняется (DRAW не отменяется).
    @Test
    fun `NOPE on DRAW is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val nope = Card(9999, CardType.NOPE)
        author.addCard(nope)

        val drawMove = Move(1, 1, MoveType.DRAW, author = author)
        game.setPendingMove(drawMove)

        val move = Move(0, 2, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(nope))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // NOPE на DEFUSE — отклоняется (DEFUSE не отменяется).
    @Test
    fun `NOPE on DEFUSE is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val nope = Card(9999, CardType.NOPE)
        author.addCard(nope)
        val defuse = Card(8888, CardType.DEFUSE)

        val defuseMove = Move(1, 1, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(defuse))
        game.setPendingMove(defuseMove)

        val move = Move(0, 2, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(nope))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // NOPE на PLAY_CARD текущего игрока — принимается.
    @Test
    fun `NOPE on PLAY_CARD is AwaitingNope`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val nope = Card(9999, CardType.NOPE)
        author.addCard(nope)

        val skipMove = Move(
            1, 1, MoveType.PLAY_CARD,
            author = author,
            cardsPlayed = listOf(Card(8888, CardType.SKIP))
        )
        game.setPendingMove(skipMove)

        val move = Move(0, 2, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(nope))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.AwaitingNope)
    }

    // NOPE может играть НЕ текущий игрок — это ключевое отличие от всех остальных ходов.
    @Test
    fun `NOPE can be played by non-current player`() {
        val game = startedGame("Аня", "Боря")
        val notCurrent = game.players[1]  // сейчас ход Ани
        val nope = Card(9999, CardType.NOPE)
        notCurrent.addCard(nope)

        val skipMove = Move(
            1, 1, MoveType.PLAY_CARD,
            author = game.players[0],
            cardsPlayed = listOf(Card(8888, CardType.SKIP))
        )
        game.setPendingMove(skipMove)

        val move = Move(0, 2, MoveType.PLAY_CARD, author = notCurrent, cardsPlayed = listOf(nope))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.AwaitingNope)
    }

    // NOPE на NOPE — принимается (Nope можно отменить Nope-ом).
    @Test
    fun `NOPE on NOPE is AwaitingNope`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val nope = Card(9999, CardType.NOPE)
        author.addCard(nope)

        val previousNope = Move(
            1, 1, MoveType.PLAY_CARD,
            author = game.players[1],
            cardsPlayed = listOf(Card(8888, CardType.NOPE))
        )
        game.setPendingMove(previousNope)

        val move = Move(0, 2, MoveType.PLAY_CARD, author = author, cardsPlayed = listOf(nope))
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.AwaitingNope)
    }

    // PLAY_TWO_OF_A_KIND

    // Пара одинаковых карт с живой целью - принимается.
    @Test
    fun `two of a kind with valid target is AwaitingNope`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val target = game.players[1]
        val c1 = Card(9001, CardType.CAT_TACO)
        val c2 = Card(9002, CardType.CAT_TACO)
        author.addCard(c1)
        author.addCard(c2)

        val move = Move(
            0, 1, MoveType.PLAY_TWO_OF_A_KIND,
            author = author, target = target, cardsPlayed = listOf(c1, c2)
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Accepted)
    }

    // Пара карт разного типа - отклоняется.
    @Test
    fun `two of a kind with different types is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val target = game.players[1]
        val c1 = Card(9001, CardType.CAT_TACO)
        val c2 = Card(9002, CardType.CAT_BEARD)
        author.addCard(c1)
        author.addCard(c2)

        val move = Move(
            0, 1, MoveType.PLAY_TWO_OF_A_KIND,
            author = author, target = target, cardsPlayed = listOf(c1, c2)
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // Пара без цели - отклоняется.
    @Test
    fun `two of a kind without target is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val c1 = Card(9001, CardType.CAT_TACO)
        val c2 = Card(9002, CardType.CAT_TACO)
        author.addCard(c1)
        author.addCard(c2)

        val move = Move(
            0, 1, MoveType.PLAY_TWO_OF_A_KIND,
            author = author, cardsPlayed = listOf(c1, c2)
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // Три карты вместо двух - отклоняется.
    @Test
    fun `two of a kind with three cards is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val target = game.players[1]
        val c1 = Card(9001, CardType.CAT_TACO)
        val c2 = Card(9002, CardType.CAT_TACO)
        val c3 = Card(9003, CardType.CAT_TACO)
        author.addCard(c1); author.addCard(c2); author.addCard(c3)

        val move = Move(
            0, 1, MoveType.PLAY_TWO_OF_A_KIND,
            author = author, target = target, cardsPlayed = listOf(c1, c2, c3)
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

// PLAY_THREE_OF_A_KIND

    // Три одинаковые карты с запросом типа - принимается.
    @Test
    fun `three of a kind with requested type is AwaitingNope`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val target = game.players[1]
        val cards = listOf(
            Card(9001, CardType.CAT_TACO),
            Card(9002, CardType.CAT_TACO),
            Card(9003, CardType.CAT_TACO)
        )
        cards.forEach { author.addCard(it) }

        val move = Move(
            0, 1, MoveType.PLAY_THREE_OF_A_KIND,
            author = author, target = target,
            cardsPlayed = cards, requestedCardType = CardType.SKIP
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Accepted)
    }

    // Три одинаковые карты без requestedCardType - отклоняется.
    @Test
    fun `three of a kind without requested type is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val target = game.players[1]
        val cards = listOf(
            Card(9001, CardType.CAT_TACO),
            Card(9002, CardType.CAT_TACO),
            Card(9003, CardType.CAT_TACO)
        )
        cards.forEach { author.addCard(it) }

        val move = Move(
            0, 1, MoveType.PLAY_THREE_OF_A_KIND,
            author = author, target = target, cardsPlayed = cards
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

// PLAY_FIVE_DIFFERENT

    // Пять карт разных типов - принимается.
    @Test
    fun `five different is AwaitingNope with non-empty discard`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val cards = listOf(
            Card(9001, CardType.CAT_TACO),
            Card(9002, CardType.CAT_BEARD),
            Card(9003, CardType.CAT_HAIRY_POTATO),
            Card(9004, CardType.CAT_CATERMELON),
            Card(9005, CardType.CAT_RAINBOW_RALPHING)
        )
        cards.forEach { author.addCard(it) }
        game.discardPile.add(Card(9999, CardType.SKIP))

        val move = Move(
            0, 1, MoveType.PLAY_FIVE_DIFFERENT,
            author = author, cardsPlayed = cards
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Accepted)
    }

    // Пять карт с повторами - отклоняется.
    @Test
    fun `five different with duplicate types is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val cards = listOf(
            Card(9001, CardType.CAT_TACO),
            Card(9002, CardType.CAT_TACO),
            Card(9003, CardType.CAT_HAIRY_POTATO),
            Card(9004, CardType.CAT_CATERMELON),
            Card(9005, CardType.CAT_RAINBOW_RALPHING)
        )
        cards.forEach { author.addCard(it) }
        game.discardPile.add(Card(9999, CardType.SKIP))

        val move = Move(
            0, 1, MoveType.PLAY_FIVE_DIFFERENT,
            author = author, cardsPlayed = cards
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }

    // Пять разных карт при пустом сбросе - отклоняется.
    @Test
    fun `five different with empty discard is rejected`() {
        val game = startedGame("Аня", "Боря")
        val author = game.players[0]
        val cards = listOf(
            Card(9001, CardType.CAT_TACO),
            Card(9002, CardType.CAT_BEARD),
            Card(9003, CardType.CAT_HAIRY_POTATO),
            Card(9004, CardType.CAT_CATERMELON),
            Card(9005, CardType.CAT_RAINBOW_RALPHING)
        )
        cards.forEach { author.addCard(it) }

        val move = Move(
            0, 1, MoveType.PLAY_FIVE_DIFFERENT,
            author = author, cardsPlayed = cards
        )
        val result = validator.validate(game, move)

        assertTrue(result is ValidationResult.Rejected)
    }
}
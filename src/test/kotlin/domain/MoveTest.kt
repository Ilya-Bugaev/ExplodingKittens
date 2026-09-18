package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/*
Тесты Move: конструктор с дефолтами и контракт равенства.
*/
class MoveTest {

    // START без автора — author по умолчанию null.
    @Test
    fun `START move has no author by default`() {
        val move = Move(id = 1, turnNumber = 0, type = MoveType.START)
        assertNull(move.author)
    }

    // DRAW с автором и взятой картой.
    @Test
    fun `DRAW move carries author and drawn card`() {
        val player = Player(1, "Аня")
        val card = Card(10, CardType.NOPE)

        val move = Move(
            id = 2,
            turnNumber = 1,
            type = MoveType.DRAW,
            author = player,
            drawnCard = card
        )

        assertEquals(player, move.author)
        assertEquals(card, move.drawnCard)
    }

    // Move с одинаковыми полями равны.
    @Test
    fun `moves with same fields are equal`() {
        val card = Card(1, CardType.NOPE)
        val a = Move(1, 1, MoveType.PLAY_CARD, cardsPlayed = listOf(card))
        val b = Move(1, 1, MoveType.PLAY_CARD, cardsPlayed = listOf(card))
        assertEquals(a, b)
    }

    // Move с разными типами не равны.
    @Test
    fun `moves with different types are not equal`() {
        val a = Move(1, 1, MoveType.DRAW)
        val b = Move(1, 1, MoveType.PLAY_CARD)
        assertNotEquals(a, b)
    }

    // Поле cancels по умолчанию null.
    @Test
    fun `cancels is null by default`() {
        val move = Move(1, 1, MoveType.PLAY_CARD)
        assertNull(move.cancels)
    }
}
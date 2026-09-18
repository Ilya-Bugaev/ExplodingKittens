package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DeckTest {

    private fun card(id: Int, type: CardType = CardType.DEFUSE) = Card(id, type)

    // size / isEmpty

    @Test
    fun `new deck with no cards is empty`() {
        val deck = Deck()
        assertTrue(deck.isEmpty())
        assertEquals(0, deck.size())
    }

    @Test
    fun `new deck from list reports correct size`() {
        val deck = Deck(listOf(card(1), card(2), card(3)))
        assertEquals(3, deck.size())
        assertFalse(deck.isEmpty())
    }

    // draw
    @Test
    fun `draw returns top card`() {
        val top = card(1)
        val deck = Deck(listOf(top, card(2), card(3)))

        val drawn = deck.draw()

        assertEquals(top, drawn)
    }

    @Test
    fun `draw removes the card from deck`() {
        val deck = Deck(listOf(card(1), card(2), card(3)))

        deck.draw()

        assertEquals(2, deck.size())
        assertEquals(listOf(2, 3), deck.peekTop(2).map { it.id })
    }

    @Test
    fun `draw from empty deck throws`() {
        val deck = Deck()

        val ex = assertThrows(IllegalStateException::class.java) { deck.draw() }
        assertTrue(ex.message!!.contains("empty"))
    }

    @Test
    fun `drawing all cards one by one preserves order`() {
        val deck = Deck(listOf(card(1), card(2), card(3)))

        val ids = listOf(deck.draw().id, deck.draw().id, deck.draw().id)

        assertEquals(listOf(1, 2, 3), ids)
        assertTrue(deck.isEmpty())
    }

    // peekTop

    @Test
    fun `peekTop returns first N cards without removing them`() {
        val deck = Deck(listOf(card(1), card(2), card(3), card(4)))

        val peeked = deck.peekTop(3)

        assertEquals(listOf(1, 2, 3), peeked.map { it.id })
        assertEquals(4, deck.size())
    }

    @Test
    fun `peekTop with amount greater than deck size returns whole deck`() {
        val deck = Deck(listOf(card(1), card(2)))
        assertEquals(2, deck.peekTop(10).size)
    }

    @Test
    fun `peekTop with zero returns empty list`() {
        val deck = Deck(listOf(card(1), card(2)))
        assertTrue(deck.peekTop(0).isEmpty())
    }

    @Test
    fun `peekTop with negative amount throws`() {
        val deck = Deck(listOf(card(1)))
        assertThrows(IllegalArgumentException::class.java) { deck.peekTop(-1) }
    }

    // insertCardAt

    @Test
    fun `insertCardAt position 0 puts card on top`() {
        val deck = Deck(listOf(card(1), card(2)))

        deck.insertCardAt(0, card(99))

        assertEquals(listOf(99, 1, 2), deck.peekTop(3).map { it.id })
    }

    @Test
    fun `insertCardAt middle position keeps order`() {
        val deck = Deck(listOf(card(1), card(2), card(3)))

        deck.insertCardAt(1, card(99))

        assertEquals(listOf(1, 99, 2, 3), deck.peekTop(4).map { it.id })
    }

    @Test
    fun `insertCardAt position equal to size puts card at bottom`() {
        val deck = Deck(listOf(card(1), card(2)))

        deck.insertCardAt(2, card(99))

        assertEquals(listOf(1, 2, 99), deck.peekTop(3).map { it.id })
    }

    @Test
    fun `insertCardAt out of bounds throws`() {
        val deck = Deck(listOf(card(1)))
        assertThrows(IllegalArgumentException::class.java) { deck.insertCardAt(5, card(99)) }
    }

    // insertCardAtTop

    @Test
    fun `insertCardAtTop puts card on top`() {
        val deck = Deck(listOf(card(1), card(2)))

        deck.insertCardAtTop(card(99))

        assertEquals(99, deck.draw().id)
    }

    // shuffle

    @Test
    fun `shuffle with same seed produces same order`() {
        val cards = (1..10).map { card(it) }

        val deck1 = Deck(cards)
        val deck2 = Deck(cards)

        deck1.shuffle(Random(42))
        deck2.shuffle(Random(42))

        assertEquals(deck1.peekTop(10), deck2.peekTop(10))
    }

    @Test
    fun `shuffle preserves the multiset of cards`() {
        val cards = (1..10).map { card(it) }
        val deck = Deck(cards)

        deck.shuffle(Random(42))

        assertEquals((1..10).toSet(), deck.peekTop(10).map { it.id }.toSet())
    }

    @Test
    fun `shuffle of empty deck does not fail`() {
        val deck = Deck()
        deck.shuffle(Random(42))
        assertTrue(deck.isEmpty())
    }
}
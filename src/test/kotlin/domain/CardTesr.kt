package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardTest {

    @Test
    fun `cards with same id and type are equal`() {
        val a = Card(1, CardType.DEFUSE)
        val b = Card(1, CardType.DEFUSE)
        assertEquals(a, b)
    }

    @Test
    fun `cards with same id but different type are not equal`() {
        val a = Card(1, CardType.DEFUSE)
        val b = Card(1, CardType.NOPE)
        assertNotEquals(a, b)
    }

    @Test
    fun `exploding kitten is identified correctly`() {
        assertTrue(Card(1, CardType.EXPLODING_KITTEN).isExplodingKitten)
    }

    @Test
    fun `ordinary card is not an exploding kitten`() {
        assertFalse(Card(1, CardType.DEFUSE).isExplodingKitten)
    }
}
package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/*
Тесты игрока: рука, добавление/удаление карт, поиск по типу, выбывание.
*/
class PlayerTest {

    // Фабрика карт с указанием типа.
    private fun card(id: Int, type: CardType = CardType.DEFUSE) = Card(id, type)

    // Новый игрок жив, рука пуста.
    @Test
    fun `new player is alive with empty hand`() {
        val player = Player(1, "Аня")
        assertTrue(player.isAlive)
        assertTrue(player.hand.isEmpty())
    }

    // addCard кладёт карту в руку.
    @Test
    fun `addCard puts card into hand`() {
        val player = Player(1, "Аня")
        val card = card(1)

        player.addCard(card)

        assertEquals(listOf(card), player.hand)
    }

    // Порядок добавления сохраняется.
    @Test
    fun `multiple addCard preserve order`() {
        val player = Player(1, "Аня")
        player.addCard(card(1))
        player.addCard(card(2))
        player.addCard(card(3))

        assertEquals(listOf(1, 2, 3), player.hand.map { it.id })
    }

    // removeCard удаляет указанную карту и возвращает true.
    @Test
    fun `removeCard removes the card and returns true`() {
        val player = Player(1, "Аня")
        val card = card(1)
        player.addCard(card)

        val removed = player.removeCard(card)

        assertTrue(removed)
        assertTrue(player.hand.isEmpty())
    }

    // removeCard для карты, которой нет в руке, возвращает false.
    @Test
    fun `removeCard returns false if card not in hand`() {
        val player = Player(1, "Аня")
        player.addCard(card(1))

        val removed = player.removeCard(card(99))

        assertFalse(removed)
        assertEquals(1, player.hand.size)
    }

    // hasCardOfType возвращает true, если карта такого типа есть.
    @Test
    fun `hasCardOfType returns true when card present`() {
        val player = Player(1, "Аня")
        player.addCard(card(1, CardType.NOPE))

        assertTrue(player.hasCardOfType(CardType.NOPE))
    }

    // hasCardOfType возвращает false, если карты такого типа нет.
    @Test
    fun `hasCardOfType returns false when card absent`() {
        val player = Player(1, "Аня")
        player.addCard(card(1, CardType.NOPE))

        assertFalse(player.hasCardOfType(CardType.DEFUSE))
    }

    // findCardsOfType возвращает все карты указанного типа.
    @Test
    fun `findCardsOfType returns all matching cards`() {
        val player = Player(1, "Аня")
        player.addCard(card(1, CardType.NOPE))
        player.addCard(card(2, CardType.DEFUSE))
        player.addCard(card(3, CardType.NOPE))

        val found = player.findCardsOfType(CardType.NOPE)

        assertEquals(listOf(1, 3), found.map { it.id })
    }

    // findCardsOfType возвращает пустой список, если карт такого типа нет.
    @Test
    fun `findCardsOfType returns empty list when no matches`() {
        val player = Player(1, "Аня")
        player.addCard(card(1, CardType.NOPE))

        assertTrue(player.findCardsOfType(CardType.DEFUSE).isEmpty())
    }

    // eliminate помечает игрока выбывшим.
    @Test
    fun `eliminate marks player as not alive`() {
        val player = Player(1, "Аня")

        player.eliminate()

        assertFalse(player.isAlive)
    }

    // Повторный eliminate не меняет состояние.
    @Test
    fun `eliminate is idempotent`() {
        val player = Player(1, "Аня")

        player.eliminate()
        player.eliminate()

        assertFalse(player.isAlive)
    }

    // hand снаружи — read-only: попытка изменить бросает UnsupportedOperationException.
    @Test
    fun `hand is read-only from outside`() {
        val player = Player(1, "Аня")
        player.addCard(card(1))

        val hand = player.hand
        try {
            (hand as MutableList<Card>).add(card(99))
            throw AssertionError("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // ожидаемо — hand возвращает копию, а не внутренний список
        }
    }
}
package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/*
Тесты сброса: add, addAll, takeAnyCard, takeCardOfType, size.
Верх сброса — конец внутреннего списка.
*/
class DiscardPileTest {

    // Фабрика карт с указанием типа — для takeCardOfType нужен конкретный тип.
    private fun card(id: Int, type: CardType = CardType.DEFUSE) = Card(id, type)

    // Пустой сброс: size = 0, isEmpty = true.
    @Test
    fun `new discard pile is empty`() {
        val pile = DiscardPile()
        assertTrue(pile.isEmpty())
        assertEquals(0, pile.size())
    }

    // Сброс из списка возвращает точное количество карт.
    @Test
    fun `new discard pile from list reports correct size`() {
        val pile = DiscardPile(listOf(card(1), card(2)))
        assertEquals(2, pile.size())
    }

    // add увеличивает размер сброса на 1.
    @Test
    fun `add increases size by one`() {
        val pile = DiscardPile()
        pile.add(card(1))
        assertEquals(1, pile.size())
    }

    // addAll добавляет все карты.
    @Test
    fun `addAll adds all cards`() {
        val pile = DiscardPile()
        pile.addAll(listOf(card(1), card(2), card(3)))
        assertEquals(3, pile.size())
    }

    // takeAnyCard снимает верхнюю карту — последнюю положенную.
    @Test
    fun `takeAnyCard returns last added card`() {
        val pile = DiscardPile()
        pile.add(card(1))
        pile.add(card(2))
        pile.add(card(3))

        val taken = pile.takeAnyCard()

        assertEquals(3, taken?.id)
        assertEquals(2, pile.size())
    }

    // takeAnyCard из пустого сброса возвращает null.
    @Test
    fun `takeAnyCard from empty pile returns null`() {
        val pile = DiscardPile()
        assertNull(pile.takeAnyCard())
    }

    // takeCardOfType находит карту нужного типа и удаляет её из сброса.
    @Test
    fun `takeCardOfType returns card of requested type`() {
        val pile = DiscardPile()
        pile.add(card(1, CardType.DEFUSE))
        pile.add(card(2, CardType.NOPE))
        pile.add(card(3, CardType.ATTACK))

        val taken = pile.takeCardOfType(CardType.NOPE)

        assertEquals(2, taken?.id)
        assertEquals(2, pile.size())
    }

    // Если карты нужного типа нет — возвращает null, сброс не меняется.
    @Test
    fun `takeCardOfType returns null when no card of that type`() {
        val pile = DiscardPile()
        pile.add(card(1, CardType.DEFUSE))

        val taken = pile.takeCardOfType(CardType.NOPE)

        assertNull(taken)
        assertEquals(1, pile.size())
    }

    // Если карт нужного типа несколько — берётся первая найденная.
    @Test
    fun `takeCardOfType returns first matching card`() {
        val pile = DiscardPile()
        pile.add(card(1, CardType.NOPE))
        pile.add(card(2, CardType.NOPE))

        val taken = pile.takeCardOfType(CardType.NOPE)

        assertEquals(1, taken?.id)
        assertEquals(1, pile.size())
    }

    // takeCardOfType из пустого сброса возвращает null.
    @Test
    fun `takeCardOfType from empty pile returns null`() {
        val pile = DiscardPile()
        assertNull(pile.takeCardOfType(CardType.DEFUSE))
    }
}
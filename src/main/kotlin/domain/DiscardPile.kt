package domain

/*
Сброс - стопка сыгранных или сброшенных карт.

Соглашение: новые карты кладутся на верх стопки, то есть в конец
внутреннего списка. takeAnyCard() снимает верхнюю - последнюю положенную.
Это соответствует физической стопке: последняя брошенная карта лежит
наверху и её видно первой.
*/
class DiscardPile(initialCards: List<Card> = emptyList()) {

    private val cards: MutableList<Card> = initialCards.toMutableList()

    fun size(): Int = cards.size

    fun isEmpty(): Boolean = cards.isEmpty()

    /*
    Кладёт карту на верх сброса.
    */
    fun add(card: Card) {
        cards.add(card)
    }

    /*
    Кладёт несколько карт на верх сброса в указанном порядке.
    Используется при комбо: игрок сбрасывает сразу 2–3 карты.
    */
    fun addAll(cardsToAdd: List<Card>) {
        cards.addAll(cardsToAdd)
    }

    /*
    Снимает верхнюю карту сброса. Возвращает null, если сброс пуст.
    */
    fun takeAnyCard(): Card? = cards.removeLastOrNull()

    /*
    Находит и снимает карту указанного типа. Возвращает null, если
    такой карты нет.
    */
    fun takeCardOfType(type: CardType): Card? {
        val index = cards.indexOfFirst { it.type == type }
        return if (index >= 0) cards.removeAt(index) else null
    }
}
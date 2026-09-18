package domain

import kotlin.random.Random

/*
Колода добора. Верх колоды - начало внутреннего списка.

Все мутирующие операции инкапсулированы: снаружи доступны только
методы, возвращающие безопасные представления. Это позволяет
контролировать инварианты (например, нельзя взять карту из пустой колоды).
*/
class Deck(initialCards: List<Card> = emptyList()) {

    private val cards: MutableList<Card> = initialCards.toMutableList()

    fun size(): Int = cards.size

    fun isEmpty(): Boolean = cards.isEmpty()

    /*
    Берёт верхнюю карту и удаляет её из колоды.
    @throws IllegalStateException если колода пуста
    */
    fun draw(): Card {
        check(cards.isNotEmpty()) { "Cannot draw from an empty deck" }
        return cards.removeAt(0)
    }

    /*
    Возвращает верхние [amount] карт, не изменяя колоду.
    Если карт меньше, чем запрошено, вернёт всю колоду.

    @throws IllegalArgumentException если [amount] отрицательный
     */
    fun peekTop(amount: Int): List<Card> {
        require(amount >= 0) { "amount must be non-negative, was $amount" }
        return cards.take(amount)
    }

    /*
    Перемешивает колоду.

    @param random источник случайности; позволяет зафиксировать seed
     в тестах для воспроизводимости
     */
    fun shuffle(random: Random = Random.Default) {
        cards.shuffle(random)
    }

    /*
    Вставляет карту в указанную позицию. Позиция 0 — верх колоды,
    позиция [size] — низ колоды.

    @throws IllegalArgumentException если позиция вне допустимого диапазона
     */
    fun insertCardAt(position: Int, card: Card) {
        require(position in 0..cards.size) {
            "position must be in 0..${cards.size}, was $position"
        }
        cards.add(position, card)
    }

    /*
    Кладёт карту на верх колоды. Частный случай [insertCardAt] с позицией 0.
     */
    fun insertCardAtTop(card: Card) {
        cards.add(0, card)
    }
}
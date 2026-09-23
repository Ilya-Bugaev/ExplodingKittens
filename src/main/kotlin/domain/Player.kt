package domain

/*
Участник партии.
*/
class Player(
    val id: Int,
    val name: String
) {

    var isAlive: Boolean = true
        private set

    private val _hand: MutableList<Card> = mutableListOf()

    val hand: List<Card> get() = _hand.toList()

    fun addCard(card: Card) {
        _hand.add(card)
    }

    /*
    Удаляет карту из руки. Если карта не найдена - ничего не делает.
    Возвращает true, если карта была удалена.
    */
    fun removeCard(card: Card): Boolean = _hand.remove(card)

    /*
    Есть ли у игрока хотя бы одна карта указанного типа.
    */
    fun hasCardOfType(type: CardType): Boolean =
        _hand.any { it.type == type }

    /*
    Все карты указанного типа в руке игрока. Используется для комбинаций:
    например, для пары одинаковых карт нужно найти все карты одного типа.
    */
    fun findCardsOfType(type: CardType): List<Card> =
        _hand.filter { it.type == type }

    /*
    Помечает игрока выбывшим. Карты из руки не удаляются - они
    отправляются в сброс партии (это делает GameplayService).
    Повторный вызов ничего не меняет.
    */
    fun eliminate() {
        isAlive = false
    }
}
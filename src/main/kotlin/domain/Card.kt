package domain

/*
 Карта в игре. Идентифицируется по [id], тип определяется [type].

 Две карты с одинаковыми [id] и [type] считаются равными (сравнение
 по значению)
 */
data class Card(
    val id: Int,
    val type: CardType
) {
    /**
     * Взрывного котёнка нельзя держать в руке и нельзя разыгрывать как обычную карту.
     * Проверка вынесена в свойство, чтобы не дублировать её в валидаторе и UI.
     */
    val isExplodingKitten: Boolean
        get() = type == CardType.EXPLODING_KITTEN
}
package domain

import java.time.LocalDateTime

data class Move(
    val id: Int,
    val turnNumber: Int,
    val type: MoveType,
    val author: Player? = null,
    val target: Player? = null,
    val cardsPlayed: List<Card> = emptyList(),
    val drawnCard: Card? = null,
    val receivedCard: Card? = null,
    val requestedCardType: CardType? = null,
    val placedKittenPosition: Int? = null,
    val eliminated: Boolean = false,
    val cancels: Move? = null,
    val initialHands: Map<Int, List<Card>>? = null,
    val initialDeckOrder: List<Card>? = null
)

/*
Завершает ли данный ход очередь текущего игрока.
Ход завершается после DRAW, а также после PLAY_CARD с картами SKIP или ATTACK.
Остальные ходы (SHUFFLE, SEE_FUTURE, FAVOR, комбинации, NOPE, DEFUSE)
оставляют очередь за текущим игроком.
*/
val Move.endsTurn: Boolean
    get() = when (type) {
        MoveType.DRAW -> true
        MoveType.PLAY_CARD ->
            cardsPlayed.singleOrNull()?.type in setOf(
                CardType.SKIP,
                CardType.ATTACK,
                CardType.DEFUSE
            )
        else -> false
    }
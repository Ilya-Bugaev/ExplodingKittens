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
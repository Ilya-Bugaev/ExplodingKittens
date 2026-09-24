package app.storage

import kotlinx.serialization.Serializable

@Serializable
data class CardRecord(
    val id: Int,
    val type: String
)

@Serializable
data class MoveRecord(
    val id: Int,
    val turnNumber: Int,
    val type: String,
    val authorName: String? = null,
    val targetName: String? = null,
    val cardsPlayed: List<CardRecord> = emptyList(),
    val drawnCard: CardRecord? = null,
    val receivedCard: CardRecord? = null,
    val requestedCardType: String? = null,
    val placedKittenPosition: Int? = null,
    val eliminated: Boolean = false,
    val initialHands: Map<Int, List<CardRecord>>? = null,
    val initialDeckOrder: List<CardRecord>? = null
)

@Serializable
data class GameRecord(
    val gameId: Int,
    val playerNames: List<String>,
    val winnerName: String? = null,
    val turnsPlayed: Int = 0,
    val moves: List<MoveRecord> = emptyList()
)

@Serializable
data class HistoryData(
    val games: List<GameRecord> = emptyList()
)
package ui.gui

/*
Снимок состояния партии для отображения в GUI.
*/
data class UiState(
    val gameId: Int? = null,
    val state: String = "нет активной партии",
    val players: List<PlayerView> = emptyList(),
    val currentPlayerName: String = "",
    val deckSize: Int = 0,
    val discardSize: Int = 0,
    val attacksPending: Int = 0,
    val turnNumber: Int = 0,
    val pendingKitten: Boolean = false,
    val isFinished: Boolean = false,
    val winnerName: String? = null,
    val currentHand: List<CardView> = emptyList(),
    val registeredPlayers: List<String> = emptyList(),
    val errorMessage: String? = null,
    val discardPile: List<CardView> = emptyList(),
    val awaitingFavorResponse: FavorResponseInfo? = null
)

data class PlayerView(
    val id: Int,
    val name: String,
    val isAlive: Boolean,
    val handSize: Int,
    val isCurrent: Boolean
)

data class CardView(
    val id: Int,
    val type: String,
    val displayName: String
)

data class FavorResponseInfo(
    val responderId: Int,
    val responderName: String,
    val responderHand: List<CardView>
)
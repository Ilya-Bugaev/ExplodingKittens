package ui.gui

/*
Снимок состояния партии для отображения в GUI.
Плоский DTO: никаких ссылок на домен, только примитивы и строки.
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
    val discardPile: List<CardView> = emptyList(),
    val awaitingFavorResponse: FavorResponseInfo? = null,
    val awaitingNope: NopeInfo? = null,
    val peekedCards: List<CardView>? = null,
    val errorMessage: String? = null
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

/*
Информация об ожидании ответа на FAVOR.
*/
data class FavorResponseInfo(
    val responderId: Int,
    val responderName: String,
    val responderHand: List<CardView>
)

/*
Информация об открытом окне Nope.
*/
data class NopeInfo(
    val pendingMoveDescription: String,
    val eligiblePlayers: List<EligibleNopePlayer>
)

data class EligibleNopePlayer(
    val playerId: Int,
    val playerName: String,
    val nopeCardId: Int
)
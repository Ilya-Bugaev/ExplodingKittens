package ui.gui

import app.dto.ValidationResult
import app.service.GameplayService
import app.service.PlayerRegistryService
import domain.Card
import domain.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/*
ViewModel для игрового экрана. Готовит UiState из домена,
обрабатывает команды пользователя, вызывает сервисы.

Compose-независим: использует только StateFlow из kotlinx-coroutines.
Тестируется без UI.
*/
class MainViewModel(
    private val gameplay: GameplayService,
    private val registry: PlayerRegistryService
) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var activeGameId: Int? = null

    init {
        refreshRegisteredPlayers()
    }

    /*
    Создаёт новую партию с указанными игроками.
    При успехе загружает состояние игры в UiState.
    */
    fun newGame(playerNames: List<String>): Boolean {
        if (playerNames.size !in 2..5) {
            _state.value = _state.value.copy(errorMessage = "Нужно от 2 до 5 игроков")
            return false
        }
        return try {
            val id = gameplay.startGame(playerNames, Random.Default)
            activeGameId = id
            refreshState()
            refreshRegisteredPlayers()
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "Ошибка создания партии: ${e.message}")
            false
        }
    }

    /*
    Подаёт ход в сервис, обновляет состояние.
    Возвращает результат валидации для UI (показать ошибку или окно Nope).
    */
    fun submitMove(move: domain.Move): ValidationResult {
        val id = activeGameId
            ?: return ValidationResult.Rejected(listOf("нет активной партии"))

        val result = gameplay.playMove(id, move)
        if (result is ValidationResult.Rejected) {
            _state.value = _state.value.copy(errorMessage = result.errors.joinToString("; "))
        }
        refreshState()
        return result
    }

    /*
    Закрывает окно Nope (все игроки отказались или сыграли).
    */
    fun resolveNopeWindow(): ValidationResult {
        val id = activeGameId
            ?: return ValidationResult.Rejected(listOf("нет активной партии"))

        val result = gameplay.resolveNopeWindow(id)
        refreshState()
        return result
    }

    /*
Взять карту из колоды. Собирает Move с текущим игроком.
Возвращает результат валидации.
*/
    fun drawCard(): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val current = game.getCurrentPlayer()

        val move = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.DRAW,
            author = current
        )
        return submitMove(move)
    }

    /*
    Сыграть карту с руки. Собирает Move с текущим игроком.
    При необходимости передаётся цель и запрашиваемый тип карты.
    */
    fun playCard(cardId: Int, targetPlayerId: Int? = null, requestedCardType: domain.CardType? = null): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val current = game.getCurrentPlayer()
        val card = current.hand.firstOrNull { it.id == cardId }
            ?: return ValidationResult.Rejected(listOf("карта не в руке"))

        val move = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.PLAY_CARD,
            author = current,
            target = targetPlayerId?.let { id -> game.players.firstOrNull { it.id == id } },
            cardsPlayed = listOf(card),
            requestedCardType = requestedCardType
        )
        return submitMove(move)
    }

    /*
FAVOR: сыграть FAVOR с указанием цели.
*/
    fun playFavor(cardId: Int, targetPlayerId: Int): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val current = game.getCurrentPlayer()
        val card = current.hand.firstOrNull { it.id == cardId }
            ?: return ValidationResult.Rejected(listOf("карта не в руке"))
        val target = game.players.firstOrNull { it.id == targetPlayerId }
            ?: return ValidationResult.Rejected(listOf("цель не найдена"))

        val move = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.PLAY_CARD,
            author = current,
            target = target,
            cardsPlayed = listOf(card)
        )
        return submitMove(move)
    }

    /*
    Пара одинаковых карт: украсть случайную у цели.
    */
    fun playTwoOfAKind(cardIds: List<Int>, targetPlayerId: Int): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val current = game.getCurrentPlayer()
        val cards = cardIds.mapNotNull { id -> current.hand.firstOrNull { it.id == id } }
        if (cards.size != 2) return ValidationResult.Rejected(listOf("нужно две карты"))
        val target = game.players.firstOrNull { it.id == targetPlayerId }
            ?: return ValidationResult.Rejected(listOf("цель не найдена"))

        val move = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.PLAY_TWO_OF_A_KIND,
            author = current,
            target = target,
            cardsPlayed = cards
        )
        return submitMove(move)
    }

    /*
    Тройка одинаковых: забрать у цели карту указанного типа.
    */
    fun playThreeOfAKind(
        cardIds: List<Int>,
        targetPlayerId: Int,
        requestedType: domain.CardType
    ): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val current = game.getCurrentPlayer()
        val cards = cardIds.mapNotNull { id -> current.hand.firstOrNull { it.id == id } }
        if (cards.size != 3) return ValidationResult.Rejected(listOf("нужно три карты"))
        val target = game.players.firstOrNull { it.id == targetPlayerId }
            ?: return ValidationResult.Rejected(listOf("цель не найдена"))

        val move = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.PLAY_THREE_OF_A_KIND,
            author = current,
            target = target,
            cardsPlayed = cards,
            requestedCardType = requestedType
        )
        return submitMove(move)
    }

    /*
    Пять разных: взять карту из сброса.
    */
    fun playFiveDifferent(cardIds: List<Int>): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val current = game.getCurrentPlayer()
        val cards = cardIds.mapNotNull { id -> current.hand.firstOrNull { it.id == id } }
        if (cards.size != 5) return ValidationResult.Rejected(listOf("нужно пять карт"))

        val move = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.PLAY_FIVE_DIFFERENT,
            author = current,
            cardsPlayed = cards
        )
        return submitMove(move)
    }

    private fun activeGame(): domain.Game? =
        activeGameId?.let { gameplay.getCurrentGame(it) }

    private fun noActiveGame(): ValidationResult =
        ValidationResult.Rejected(listOf("нет активной партии")).also {
            _state.value = _state.value.copy(errorMessage = "нет активной партии")
        }

    /*
    Загружает уже существующую партию по id (например, из истории).
    */
    fun loadGame(gameId: Int): Boolean {
        val game = gameplay.getCurrentGame(gameId) ?: return false
        activeGameId = gameId
        refreshState()
        return true
    }

    fun endGame(): Boolean {
        val id = activeGameId ?: return false
        val result = gameplay.endGame(id)
        refreshState()
        return result
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun refreshRegisteredPlayers() {
        _state.value = _state.value.copy(
            registeredPlayers = registry.getKnownPlayerNames()
        )
    }

    private fun refreshState() {
        val id = activeGameId
        if (id == null) {
            _state.value = UiState(registeredPlayers = registry.getKnownPlayerNames())
            return
        }
        val game = gameplay.getCurrentGame(id)
        if (game == null) {
            activeGameId = null
            _state.value = UiState(registeredPlayers = registry.getKnownPlayerNames())
            return
        }
        _state.value = buildUiState(game, registry.getKnownPlayerNames())
    }

    private fun buildUiState(game: Game, registeredPlayers: List<String>): UiState {
        val current = game.getCurrentPlayer()
        return UiState(
            gameId = game.id,
            state = game.state.name,
            players = game.players.map {
                PlayerView(
                    id = it.id,
                    name = it.name,
                    isAlive = it.isAlive,
                    handSize = it.hand.size,
                    isCurrent = it.id == current.id
                )
            },
            currentPlayerName = current.name,
            deckSize = game.deck.size,
            discardSize = game.discardPile.size(),
            attacksPending = game.attacksPending,
            turnNumber = game.turnsPlayed,
            pendingKitten = game.pendingKitten != null,
            isFinished = game.state == domain.GameState.FINISHED,
            winnerName = game.winner?.name,
            currentHand = current.hand.map { it.toView() },
            registeredPlayers = registeredPlayers
        )
    }

    private fun Card.toView(): CardView = CardView(
        id = id,
        type = type.name,
        displayName = type.displayName
    )
}
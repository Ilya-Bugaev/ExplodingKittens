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
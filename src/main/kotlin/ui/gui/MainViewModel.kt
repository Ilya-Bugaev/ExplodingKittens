package ui.gui

import app.dto.ValidationResult
import app.service.GameplayService
import app.service.PlayerRegistryService
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
    */
    fun newGame(playerNames: List<String>): Boolean {
        if (playerNames.size !in 2..5) {
            _state.value = _state.value.copy(errorMessage = "Нужно от 2 до 5 игроков")
            return false
        }

        // Завершить предыдущую партию, если она была не закончена
        activeGameId?.let { previousId ->
            val previous = gameplay.getCurrentGame(previousId)
            if (previous != null && previous.state != domain.GameState.FINISHED) {
                gameplay.endGame(previousId)
            }
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
    */
    fun submitMove(move: domain.Move): ValidationResult {
        val id = activeGameId
            ?: return failWithMessage(listOf("нет активной партии"))

        val result = gameplay.playMove(id, move)
        if (result is ValidationResult.Rejected) {
            _state.value = _state.value.copy(errorMessage = result.errors.joinToString("; "))
        }
        refreshState()
        return result
    }

    /*
    Закрывает окно Nope.
    */
    fun resolveNopeWindow(): ValidationResult {
        val id = activeGameId
            ?: return failWithMessage(listOf("нет активной партии"))

        val result = gameplay.resolveNopeWindow(id)
        if (result is ValidationResult.Rejected) {
            _state.value = _state.value.copy(errorMessage = result.errors.joinToString("; "))
        }
        refreshState()
        return result
    }

    /*
    Загружает уже существующую партию по id.
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

    // ---------- Действия ----------

    /*
    Взять карту из колоды.
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
    Сыграть карту с руки без цели.
    */
    fun playCard(cardId: Int): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val current = game.getCurrentPlayer()
        val card = current.hand.firstOrNull { it.id == cardId }
            ?: return failWithMessage(listOf("карта не в руке"))

        val move = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.PLAY_CARD,
            author = current,
            cardsPlayed = listOf(card)
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
            ?: return failWithMessage(listOf("карта не в руке"))
        val target = game.players.firstOrNull { it.id == targetPlayerId }
            ?: return failWithMessage(listOf("цель не найдена"))

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
    DEFUSE: сыграть DEFUSE и вернуть котёнка на указанную позицию.
    */
    fun submitDefuse(cardId: Int, position: Int): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val current = game.getCurrentPlayer()
        val card = current.hand.firstOrNull { it.id == cardId }
            ?: return failWithMessage(listOf("карта не в руке"))

        val move = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.PLAY_CARD,
            author = current,
            cardsPlayed = listOf(card),
            placedKittenPosition = position
        )
        return submitMove(move)
    }

    /*
    Пара одинаковых: украсть случайную у цели.
    */
    fun playTwoOfAKind(cardIds: List<Int>, targetPlayerId: Int): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val current = game.getCurrentPlayer()
        val cards = cardIds.mapNotNull { id -> current.hand.firstOrNull { it.id == id } }
        if (cards.size != 2) return failWithMessage(listOf("нужно две карты"))
        val target = game.players.firstOrNull { it.id == targetPlayerId }
            ?: return failWithMessage(listOf("цель не найдена"))

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
        if (cards.size != 3) return failWithMessage(listOf("нужно три карты"))
        val target = game.players.firstOrNull { it.id == targetPlayerId }
            ?: return failWithMessage(listOf("цель не найдена"))

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
    Пять разных: взять из сброса выбранную карту.
    Если receivedCardId == null - берётся верхняя (обратная совместимость).
    */
    fun playFiveDifferent(cardIds: List<Int>, receivedCardId: Int? = null): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val current = game.getCurrentPlayer()
        val cards = cardIds.mapNotNull { id -> current.hand.firstOrNull { it.id == id } }
        if (cards.size != 5) return failWithMessage(listOf("нужно пять карт"))

        val receivedCard = receivedCardId?.let { id ->
            game.discardPile.peekAll().firstOrNull { it.id == id }
        }

        val move = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.PLAY_FIVE_DIFFERENT,
            author = current,
            cardsPlayed = cards,
            receivedCard = receivedCard
        )
        return submitMove(move)
    }

    /*
    Ответ цели FAVOR: какую карту отдать.
    */
    fun submitFavorResponse(cardId: Int): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val pending = game.pendingMove
            ?: return failWithMessage(listOf("нет ожидающего хода"))
        val responder = pending.target
            ?: return failWithMessage(listOf("цель не определена"))
        val card = responder.hand.firstOrNull { it.id == cardId }
            ?: return failWithMessage(listOf("карта не в руке"))

        val move = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.RESOLVE_PENDING,
            author = responder,
            cardsPlayed = listOf(card)
        )
        return submitMove(move)
    }

    fun playNope(playerId: Int, cardId: Int): ValidationResult {
        val game = activeGame() ?: return noActiveGame()
        val player = game.players.firstOrNull { it.id == playerId }
            ?: return failWithMessage(listOf("игрок не найден"))
        val card = player.hand.firstOrNull { it.id == cardId }
            ?: return failWithMessage(listOf("карта не в руке"))

        val nopeMove = domain.Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = domain.MoveType.PLAY_CARD,
            author = player,
            cardsPlayed = listOf(card),
            cancels = game.pendingMove
        )
        return submitMove(nopeMove)
    }

    /*
    Никто не хочет играть NOPE — закрыть окно и разрешить цепочку.
    */
    fun declineNope(): ValidationResult = resolveNopeWindow()

    private fun activeGame(): Game? =
        activeGameId?.let { gameplay.getCurrentGame(it) }

    private fun noActiveGame(): ValidationResult =
        failWithMessage(listOf("нет активной партии"))

    /*
    Возвращает Rejected и одновременно кладёт сообщение в errorMessage,
    чтобы UI показал его пользователю.
    */
    private fun failWithMessage(errors: List<String>): ValidationResult {
        _state.value = _state.value.copy(errorMessage = errors.joinToString("; "))
        return ValidationResult.Rejected(errors)
    }

    private fun refreshRegisteredPlayers() {
        _state.value = _state.value.copy(
            registeredPlayers = registry.getKnownPlayerNames()
        )
    }

    /*
    Перестраивает UiState из домена, сохраняя текущий errorMessage.
    */
    private fun refreshState() {
        val preservedError = _state.value.errorMessage
        val id = activeGameId
        if (id == null) {
            _state.value = UiState(registeredPlayers = registry.getKnownPlayerNames())
                .copy(errorMessage = preservedError)
            return
        }
        val game = gameplay.getCurrentGame(id)
        if (game == null) {
            activeGameId = null
            _state.value = UiState(registeredPlayers = registry.getKnownPlayerNames())
                .copy(errorMessage = preservedError)
            return
        }
        _state.value = buildUiState(game, registry.getKnownPlayerNames())
            .copy(errorMessage = preservedError)
    }

    private fun buildUiState(game: Game, registeredPlayers: List<String>): UiState {
        val current = game.getCurrentPlayer()
        val awaitingFavor = game.pendingMove?.let { pending ->
            val pendingCard = pending.cardsPlayed.singleOrNull()
            if (pendingCard?.type == domain.CardType.FAVOR) {
                pending.target?.let { responder ->
                    FavorResponseInfo(
                        responderId = responder.id,
                        responderName = responder.name,
                        responderHand = responder.hand.map { it.toView() }
                    )
                }
            } else null
        }

        val awaitingNope = game.pendingMove?.let { pending ->
            val pendingCard = pending.cardsPlayed.singleOrNull()
            if (pendingCard != null && pendingCard.type != domain.CardType.DEFUSE) {
                val eligible = game.getAlivePlayers()
                    .filter { it.id != pending.author?.id }
                    .mapNotNull { p ->
                        p.findCardsOfType(domain.CardType.NOPE).firstOrNull()?.let { nopeCard ->
                            EligibleNopePlayer(p.id, p.name, nopeCard.id)
                        }
                    }
                val description = buildNopeDescription(pending)
                NopeInfo(description, eligible)
            } else null
        }

        val peekedCards = game.lastPeekedCards?.map { it.toView() }
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
            registeredPlayers = registeredPlayers,
            discardPile = game.discardPile.peekAll().map { it.toView() },
            awaitingFavorResponse = awaitingFavor,
            awaitingNope = awaitingNope,
            peekedCards = peekedCards
        )
    }

    private fun domain.Card.toView(): CardView = CardView(
        id = id,
        type = type.name,
        displayName = type.displayName
    )

    /*
    Строит текст: "{Имя} сыграл {Карта}" или "{Имя} сыграл NOPE".
    */
    private fun buildNopeDescription(pending: domain.Move): String {
        val author = pending.author?.name ?: "?"
        val card = pending.cardsPlayed.singleOrNull()
        val cardName = card?.type?.displayName ?: pending.type.name
        return "$author сыграл $cardName"
    }

    fun dismissPeekedCards() {
        val id = activeGameId ?: return
        gameplay.getCurrentGame(id)?.clearLastPeekedCards()
        refreshState()
    }
}
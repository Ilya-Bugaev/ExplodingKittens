package app.service

import app.dto.ValidationResult
import app.storage.toSummary
import app.validator.IMoveValidator
import domain.CardType
import domain.Game
import domain.Move
import domain.MoveType
import domain.endsTurn
import kotlin.random.Random

class GameplayService(
    private val validator: IMoveValidator,
    private val playerRegistry: PlayerRegistryService,
    private val historyService: HistoryService? = null,
    private val statisticsService: StatisticsService? = null
) {
    private val games: MutableMap<Int, Game> = mutableMapOf()
    private var nextGameId: Int = 1

    /*
    Создаёт новую партию. Регистрирует игроков в реестре (если ещё не там),
    создаёт Game и возвращает его id.
    */
    fun startGame(playerNames: List<String>, random: Random = Random.Default): Int {
        require(playerNames.size in 2..5) {
            "player count must be 2..5, was ${playerNames.size}"
        }
        playerNames.forEach { playerRegistry.registerPlayer(it) }

        val game = Game(nextGameId)
        game.startGame(playerNames, random)
        games[nextGameId] = game
        return nextGameId++
    }

    /*
    Обрабатывает ход. Валидирует, применяет эффект, обновляет состояние.
    Возвращает результат валидации:
      - Rejected - ход отклонён;
      - Accepted - ход применён;
      - AwaitingNope - ход отложен, открыто окно Nope;
      - AwaitingResponse - ход отложен, ждём ответа игрока.
    */
    fun playMove(gameId: Int, move: Move): ValidationResult {
        val game = games[gameId]
            ?: return ValidationResult.Rejected(listOf("game $gameId not found"))

        return when (val result = validator.validate(game, move)) {
            is ValidationResult.Rejected -> result

            is ValidationResult.Accepted -> {
                applyEffect(game, result.move)
                game.addMove(result.move)
                when {
                    game.isFinished() -> {
                        game.finish()
                        persistFinishedGame(game)
                    }
                    game.pendingKitten != null -> Unit  // ждём DEFUSE, ход не переходит
                    result.move.endsTurn -> game.advanceTurn(forAttack = isAttackMove(result.move))
                }
                result
            }

            is ValidationResult.AwaitingNope -> {
                game.setPendingMove(result.pendingMove)
                result
            }

            is ValidationResult.AwaitingResponse -> {
                applyImmediateEffect(game, result.pendingMove)
                game.setPendingMove(result.pendingMove)
                result
            }
        }
    }

    /*
    Закрывает окно Nope. Если последний pendingMove - это NOPE,
    действие отменяется. Иначе - эффект применяется.

    Факт прихода NOPE обрабатывается playMove ДО вызова этого метода.
    Здесь только разрешение цепочки.
    */
    fun resolveNopeWindow(gameId: Int): ValidationResult {
        val game = games[gameId]
            ?: return ValidationResult.Rejected(listOf("game $gameId not found"))

        val pending = game.pendingMove
            ?: return ValidationResult.Rejected(listOf("no pending move"))

        game.clearPendingMove()

        val canceledByNope = pending.cardsPlayed.singleOrNull()?.type == CardType.NOPE
        if (canceledByNope) {
            return ValidationResult.Rejected(listOf("action was canceled by NOPE"))
        }

        applyEffect(game, pending)
        game.addMove(pending)
        when {
            game.isFinished() -> {
                game.finish()
                persistFinishedGame(game)
            }
            game.pendingKitten != null -> Unit
            pending.endsTurn -> game.advanceTurn(forAttack = isAttackMove(pending))
        }
        return ValidationResult.Accepted(pending)
    }

    fun endGame(gameId: Int): Boolean {
        val game = games[gameId] ?: return false
        if (game.state == domain.GameState.FINISHED) return false
        game.finish()
        persistFinishedGame(game)
        return true
    }

    fun getCurrentGame(gameId: Int): Game? = games[gameId]

    fun getActiveGameIds(): Set<Int> = games.keys.toSet()

    /*
    Определяет, является ли ход розыгрышем карты ATTACK. Используется
    для передачи флага forAttack в advanceTurn - следующий игрок
    получит 2 хода.
    */
    private fun isAttackMove(move: Move): Boolean =
        move.type == MoveType.PLAY_CARD &&
                move.cardsPlayed.singleOrNull()?.type == CardType.ATTACK

    /*
    Сохраняет завершённую партию в историю и обновляет статистику.
    Если сервисы не подключены (шаг 2 без персистентности) - no-op.
    */
    private fun persistFinishedGame(game: Game) {
        historyService?.saveGame(game)
        statisticsService?.updateStats(game.toSummary())
    }

    /*
    Применяет эффект хода к партии. Для START - no-op.
    Для остальных типов - обновляет колоду, руку, сброс и т.д.
    */
    private fun applyEffect(game: Game, move: Move) {
        when (move.type) {
            MoveType.START -> Unit
            MoveType.DRAW -> applyDraw(game, move)
            MoveType.PLAY_CARD -> applyPlayCard(game, move)
            MoveType.PLAY_TWO_OF_A_KIND -> applyTwoOfAKind(game, move)
            MoveType.PLAY_THREE_OF_A_KIND -> applyThreeOfAKind(game, move)
            MoveType.PLAY_FIVE_DIFFERENT -> applyFiveDifferent(game, move)
            MoveType.RESOLVE_PENDING -> applyResolvePending(game, move)
        }
    }

    /*
    Применяет немедленную часть эффекта хода, ожидающего ответа.
    Для FAVOR - карта уходит из руки в сброс сразу, а передача
    приходит позже через RESOLVE_PENDING.
    */
    private fun applyImmediateEffect(game: Game, move: Move) {
        val author = move.author ?: return
        move.cardsPlayed.forEach {
            author.removeCard(it)
            game.discardPile.add(it)
        }
    }

    private fun applyDraw(game: Game, move: Move) {
        val author = move.author ?: return
        val drawn = game.deck.draw()
        if (drawn.isExplodingKitten) {
            game.setPendingKitten(drawn)
        } else {
            author.addCard(drawn)
        }
    }

    private fun applyPlayCard(game: Game, move: Move) {
        val author = move.author ?: return
        val card = move.cardsPlayed.singleOrNull() ?: return
        author.removeCard(card)

        when (card.type) {
            CardType.SKIP -> game.discardPile.add(card)
            CardType.SHUFFLE -> {
                game.discardPile.add(card)
                game.deck.shuffle()
            }
            CardType.SEE_FUTURE -> game.discardPile.add(card)
            CardType.ATTACK -> game.discardPile.add(card)
            CardType.DEFUSE -> {
                game.discardPile.add(card)
                val kitten = game.pendingKitten
                if (kitten != null) {
                    val position = move.placedKittenPosition ?: 0
                    game.deck.insertCardAt(position, kitten)
                    game.clearPendingKitten()
                }
            }
            CardType.NOPE -> game.discardPile.add(card)
            CardType.FAVOR -> game.discardPile.add(card)
            else -> game.discardPile.add(card)
        }
    }

    private fun applyTwoOfAKind(game: Game, move: Move) {
        val author = move.author ?: return
        val target = move.target ?: return
        move.cardsPlayed.forEach { author.removeCard(it); game.discardPile.add(it) }

        val stolen = target.hand.randomOrNull() ?: return
        target.removeCard(stolen)
        author.addCard(stolen)
    }

    private fun applyThreeOfAKind(game: Game, move: Move) {
        val author = move.author ?: return
        val target = move.target ?: return
        val requested = move.requestedCardType ?: return
        move.cardsPlayed.forEach { author.removeCard(it); game.discardPile.add(it) }

        val stolen = target.findCardsOfType(requested).firstOrNull() ?: return
        target.removeCard(stolen)
        author.addCard(stolen)
    }

    private fun applyFiveDifferent(game: Game, move: Move) {
        val author = move.author ?: return
        move.cardsPlayed.forEach { author.removeCard(it); game.discardPile.add(it) }

        val taken = game.discardPile.takeAnyCard() ?: return
        author.addCard(taken)
    }

    /*
    RESOLVE_PENDING: цель FAVOR отдаёт карту. Карта уходит из её руки
    в руку автора FAVOR. Отложенный ход добавляется в историю,
    pendingMove сбрасывается.
    */
    private fun applyResolvePending(game: Game, move: Move) {
        val responder = move.author ?: return
        val card = move.cardsPlayed.singleOrNull() ?: return
        val pending = game.pendingMove ?: return

        responder.removeCard(card)

        val pendingCard = pending.cardsPlayed.singleOrNull()
        if (pendingCard?.type == CardType.FAVOR) {
            val receiver = pending.author ?: return
            receiver.addCard(card)
        }

        game.addMove(pending)
        game.clearPendingMove()
    }
}
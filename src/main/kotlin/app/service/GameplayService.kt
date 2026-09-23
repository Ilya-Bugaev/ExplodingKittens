package app.service

import app.dto.ValidationResult
import app.storage.toSummary
import app.validator.IMoveValidator
import domain.Card
import domain.CardType
import domain.Game
import domain.Move
import domain.MoveType
import domain.Player
import domain.endsTurn
import kotlin.random.Random

/*
Оркестратор партий. Хранит активные игры в памяти, валидирует ходы
и применяет их эффекты. На шаге 2 - только in-memory; на шаге 4
добавится сохранение через IHistoryRepository.
*/
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

                // Если DRAW привёл к выбыванию автора - отметить в Move
                val eliminated = result.move.type == MoveType.DRAW &&
                        result.move.author?.isAlive == false
                val storedMove = if (eliminated) result.move.copy(eliminated = true) else result.move
                game.addMove(storedMove)

                when {
                    game.isFinished() -> {
                        game.finish()
                        persistFinishedGame(game)
                    }
                    game.pendingKitten != null -> Unit
                    storedMove.endsTurn -> game.advanceTurn(forAttack = isAttackMove(storedMove))
                }
                result
            }

            is ValidationResult.AwaitingNope -> {
                val previousPending = game.pendingMove
                val withChain = if (result.pendingMove.cancels == null && previousPending != null) {
                    result.pendingMove.copy(cancels = previousPending)
                } else {
                    result.pendingMove
                }
                game.setPendingMove(withChain)
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
    */
    fun resolveNopeWindow(gameId: Int): ValidationResult {
        val game = games[gameId]
            ?: return ValidationResult.Rejected(listOf("game $gameId not found"))

        val chain = collectNopeChain(game)
        if (chain.isEmpty()) {
            return ValidationResult.Rejected(listOf("no pending move"))
        }

        game.clearPendingMove()

        val nopeCount = chain.count { move ->
            move.cardsPlayed.singleOrNull()?.type == CardType.NOPE
        }

        if (nopeCount % 2 == 1) {
            return ValidationResult.Rejected(listOf("action was canceled by NOPE"))
        }

        val original = chain.last()
        applyEffect(game, original)
        game.addMove(original)
        when {
            game.isFinished() -> {
                game.finish()
                persistFinishedGame(game)
            }
            game.pendingKitten != null -> Unit
            original.endsTurn -> game.advanceTurn(forAttack = isAttackMove(original))
        }
        return ValidationResult.Accepted(original)
    }

    private fun collectNopeChain(game: Game): List<Move> {
        val chain = mutableListOf<Move>()
        var current: Move? = game.pendingMove
        while (current != null) {
            chain.add(current)
            current = current.cancels
        }
        return chain
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
    Определяет, является ли ход розыгрышем карты ATTACK.
    */
    private fun isAttackMove(move: Move): Boolean =
        move.type == MoveType.PLAY_CARD &&
                move.cardsPlayed.singleOrNull()?.type == CardType.ATTACK

    /*
    Сохраняет завершённую партию в историю и обновляет статистику.
    Если сервисы не подключены - no-op.
    */
    private fun persistFinishedGame(game: Game) {
        historyService?.saveGame(game)
        statisticsService?.updateStats(game.toSummary())
    }

    /*
    Применяет эффект хода к партии.
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
    Для FAVOR - карта уходит из руки в сброс сразу.
    */
    private fun applyImmediateEffect(game: Game, move: Move) {
        val author = move.author ?: return
        move.cardsPlayed.forEach {
            author.removeCard(it)
            game.discardPile.add(it)
        }
    }

    /*
    Взятие карты. Если выпал Exploding Kitten:
      - с DEFUSE в руке - ждём розыгрыша DEFUSE (pendingKitten);
      - без DEFUSE - игрок выбывает немедленно.
    */
    private fun applyDraw(game: Game, move: Move) {
        val author = move.author ?: return
        val drawn = game.deck.draw()
        if (drawn.isExplodingKitten) {
            if (author.hasCardOfType(CardType.DEFUSE)) {
                game.setPendingKitten(drawn)
            } else {
                eliminatePlayer(game, author, drawn)
            }
        } else {
            author.addCard(drawn)
        }
    }

    /*
    Выбывание игрока, вытянувшего Exploding Kitten без DEFUSE.
    Карты руки и котёнок уходят в сброс. pendingKitten не выставляется.
    */
    private fun eliminatePlayer(game: Game, player: Player, kitten: Card) {
        player.hand.toList().forEach { card ->
            player.removeCard(card)
            game.discardPile.add(card)
        }
        game.discardPile.add(kitten)
        player.eliminate()
        game.clearPendingKitten()
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
            CardType.SEE_FUTURE -> {
                game.discardPile.add(card)
                val peeked = game.deck.peekTop(3)
                game.setLastPeekedCards(peeked)
            }
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

    /*
    Пять разных: взять из сброса выбранную карту.
    Если receivedCard не задан - берётся верхняя.
    */
    private fun applyFiveDifferent(game: Game, move: Move) {
        val author = move.author ?: return
        move.cardsPlayed.forEach { author.removeCard(it); game.discardPile.add(it) }

        val taken = move.receivedCard?.let { game.discardPile.takeCardById(it.id) }
            ?: game.discardPile.takeAnyCard()
            ?: return
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
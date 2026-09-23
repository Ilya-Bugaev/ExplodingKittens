package app.service

import app.dto.ValidationResult
import app.storage.toSummary
import app.validator.IMoveValidator
import domain.Card
import domain.CardType
import domain.Game
import domain.GameState
import domain.Move
import domain.MoveType
import domain.Player
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
    Создаёт новую партию. Регистрирует игроков, синхронизирует
    nextGameId с существующей историей (чтобы не перезаписывать
    партии из прошлых сессий), возвращает id.
    */
    fun startGame(playerNames: List<String>, random: Random = Random.Default): Int {
        require(playerNames.size in 2..5) {
            "player count must be 2..5, was ${playerNames.size}"
        }
        playerNames.forEach { playerRegistry.registerPlayer(it) }

        historyService?.let {
            val maxExisting = it.getAllFinishedGames().maxOfOrNull { s -> s.gameId } ?: 0
            if (maxExisting >= nextGameId) {
                nextGameId = maxExisting + 1
            }
        }

        val game = Game(nextGameId)
        game.startGame(playerNames, random)
        games[nextGameId] = game
        return nextGameId++
    }

    /*
    Обрабатывает ход: валидирует, применяет эффект, обновляет состояние.
    */
    fun playMove(gameId: Int, move: Move): ValidationResult {
        val game = games[gameId]
            ?: return ValidationResult.Rejected(listOf("game $gameId not found"))

        return when (val result = validator.validate(game, move)) {
            is ValidationResult.Rejected -> result

            is ValidationResult.Accepted -> {
                applyEffect(game, result.move)

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
    Закрывает окно Nope. Все карты цепочки (NOPE и оригинал) уходят
    в сброс. Если NOPE сыграно нечётное число раз — оригинал отменён,
    эффект не применяется. Если чётное — эффект применяется.
    */
    fun resolveNopeWindow(gameId: Int): ValidationResult {
        val game = games[gameId]
            ?: return ValidationResult.Rejected(listOf("game $gameId not found"))

        val chain = collectNopeChain(game)
        if (chain.isEmpty()) {
            return ValidationResult.Rejected(listOf("no pending move"))
        }
        game.clearPendingMove()

        val nopeCount = chain.count { m ->
            m.cardsPlayed.singleOrNull()?.type == CardType.NOPE
        }

        // Все карты цепочки уходят из рук в сброс, ходы попадают в историю.
        // Идём в хронологическом порядке — от оригинала к последнему NOPE.
        chain.reversed().forEach { m ->
            m.author?.let { author ->
                m.cardsPlayed.forEach { card ->
                    if (author.removeCard(card)) {
                        game.discardPile.add(card)
                    }
                }
            }
            game.addMove(m)
        }

        if (nopeCount % 2 == 1) {
            return ValidationResult.Rejected(listOf("action was canceled by NOPE"))
        }

        val original = chain.last()
        applyEffectOnly(game, original)
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

    fun endGame(gameId: Int): Boolean {
        val game = games[gameId] ?: return false
        if (game.state == GameState.FINISHED) return false
        game.finish()
        persistFinishedGame(game)
        return true
    }

    fun getCurrentGame(gameId: Int): Game? = games[gameId]

    fun getActiveGameIds(): Set<Int> = games.keys.toSet()

    // ===================== Приватные помощники =====================

    /*
    Собирает цепочку ходов от последнего pendingMove до исходного.
    Возвращает [последний NOPE, ..., исходный ход].
    */
    private fun collectNopeChain(game: Game): List<Move> {
        val chain = mutableListOf<Move>()
        var current: Move? = game.pendingMove
        while (current != null) {
            chain.add(current)
            current = current.cancels
        }
        return chain
    }

    private fun isAttackMove(move: Move): Boolean =
        move.type == MoveType.PLAY_CARD &&
                move.cardsPlayed.singleOrNull()?.type == CardType.ATTACK

    /*
    Сохраняет завершённую партию в историю и обновляет статистику.
    No-op если сервисы не подключены.
    */
    private fun persistFinishedGame(game: Game) {
        historyService?.saveGame(game)
        statisticsService?.updateStats(game.toSummary())
    }

    /*
    Применяет эффект хода и перемещает карты из руки в сброс.
    Используется в обычном потоке playMove → Accepted.
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
    Применяет только эффект хода, без перемещения карт из руки в сброс.
    Используется в resolveNopeWindow — там карты цепочки уже сброшены.
    */
    private fun applyEffectOnly(game: Game, move: Move) {
        if (move.type != MoveType.PLAY_CARD) {
            applyEffect(game, move)
            return
        }
        val card = move.cardsPlayed.singleOrNull() ?: return
        when (card.type) {
            CardType.SHUFFLE -> game.deck.shuffle()
            CardType.SEE_FUTURE -> game.setLastPeekedCards(game.deck.peekTop(3))
            CardType.ATTACK -> Unit  // эффект через advanceTurn(forAttack = true)
            CardType.SKIP -> Unit
            CardType.DEFUSE -> {
                val kitten = game.pendingKitten
                if (kitten != null) {
                    val position = move.placedKittenPosition ?: 0
                    game.deck.insertCardAt(position, kitten)
                    game.clearPendingKitten()
                }
            }
            else -> Unit
        }
    }

    /*
    Для FAVOR: карта уходит из руки в сброс сразу, но передача
    приходит позже через RESOLVE_PENDING.
    */
    private fun applyImmediateEffect(game: Game, move: Move) {
        val author = move.author ?: return
        move.cardsPlayed.forEach {
            if (author.removeCard(it)) {
                game.discardPile.add(it)
            }
        }
    }

    /*
    DRAW. Если выпал Exploding Kitten:
      - с DEFUSE в руке — ждём DEFUSE через pendingKitten;
      - без DEFUSE — игрок выбывает немедленно.
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
    Выбывание игрока, вытянувшего котёнка без DEFUSE.
    Карты руки и котёнок уходят в сброс.
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

    /*
    PLAY_CARD, применённый немедленно (без окна Nope).
    Сюда попадает DEFUSE. ATTACK/SKIP/SHUFFLE/SEE_FUTURE идут через
    resolveNopeWindow и applyEffectOnly.
    */
    private fun applyPlayCard(game: Game, move: Move) {
        val author = move.author ?: return
        val card = move.cardsPlayed.singleOrNull() ?: return
        if (author.removeCard(card)) {
            game.discardPile.add(card)
        }

        when (card.type) {
            CardType.DEFUSE -> {
                val kitten = game.pendingKitten
                if (kitten != null) {
                    val position = move.placedKittenPosition ?: 0
                    game.deck.insertCardAt(position, kitten)
                    game.clearPendingKitten()
                }
            }
            else -> Unit
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

        val taken = move.receivedCard?.let { game.discardPile.takeCardById(it.id) }
            ?: game.discardPile.takeAnyCard()
            ?: return
        author.addCard(taken)
    }

    /*
    RESOLVE_PENDING: цель FAVOR отдаёт карту. Карта уходит из её руки
    в руку автора FAVOR. Отложенный ход добавляется в историю.
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
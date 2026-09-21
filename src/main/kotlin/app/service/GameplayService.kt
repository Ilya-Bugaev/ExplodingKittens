package app.service

import app.dto.ValidationResult
import app.validator.IMoveValidator
import domain.CardType
import domain.Game
import domain.Move
import domain.MoveType
import kotlin.random.Random

/*
Оркестратор партий. Хранит активные игры в памяти, валидирует ходы
и применяет их эффекты
*/
class GameplayService(
    private val validator: IMoveValidator,
    private val playerRegistry: PlayerRegistryService
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
                if (game.isFinished()) {
                    game.finish()
                } else if (shouldEndTurn(result.move)) {
                    game.advanceTurn()
                }
                result
            }

            is ValidationResult.AwaitingNope -> {
                game.setPendingMove(result.pendingMove)
                result
            }

            is ValidationResult.AwaitingResponse -> {
                game.setPendingMove(result.pendingMove)
                result
            }
        }
    }

    /*
    Закрывает окно Nope. Если pendingMove - это Nope, действие
    отменяется. Иначе - эффект применяется.
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
        if (game.isFinished()) {
            game.finish()
        } else if (shouldEndTurn(pending)) {
            game.advanceTurn()
        }
        return ValidationResult.Accepted(pending)
    }

    fun endGame(gameId: Int): Boolean {
        val game = games[gameId] ?: return false
        if (game.state == domain.GameState.FINISHED) return false
        game.finish()
        return true
    }

    fun getCurrentGame(gameId: Int): Game? = games[gameId]

    fun getActiveGameIds(): Set<Int> = games.keys.toSet()

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
            MoveType.RESOLVE_PENDING -> Unit
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
            CardType.ATTACK -> {
                game.discardPile.add(card)
                game.setAttacksPending(2)
            }
            CardType.DEFUSE -> {
                game.discardPile.add(card)
                val kitten = game.pendingKitten
                if (kitten != null) {
                    val position = move.placedKittenPosition ?: 0
                    game.deck.insertCardAt(position, kitten)
                    game.clearPendingKitten()
                }
            }
            CardType.NOPE -> {
                game.discardPile.add(card)
            }
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
    Определяет, завершает ли ход данный Move.
    Ход завершается после DRAW, а также после PLAY_CARD с картами
    SKIP или ATTACK. Все остальные ходы оставляют ход у текущего игрока.
    */
    private fun shouldEndTurn(move: Move): Boolean {
        if (move.type == MoveType.DRAW) return true
        if (move.type != MoveType.PLAY_CARD) return false
        val card = move.cardsPlayed.singleOrNull() ?: return false
        return card.type == CardType.SKIP || card.type == CardType.ATTACK
    }
}
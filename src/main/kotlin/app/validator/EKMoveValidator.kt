package app.validator

import app.dto.ValidationResult
import domain.CardType
import domain.Game
import domain.GameState
import domain.Move
import domain.MoveType

class EKMoveValidator : IMoveValidator {

    override fun validate(game: Game, move: Move): ValidationResult {
        checkCommon(game, move)?.let { return it }

        return when (move.type) {
            MoveType.START -> validateStart(game, move)
            MoveType.DRAW -> validateDraw(game, move)
            MoveType.PLAY_CARD -> validatePlayCard(game, move)
            MoveType.PLAY_TWO_OF_A_KIND -> validateTwoOfAKind(game, move)
            MoveType.PLAY_THREE_OF_A_KIND -> validateThreeOfAKind(game, move)
            MoveType.PLAY_FIVE_DIFFERENT -> validateFiveDifferent(game, move)
            MoveType.RESOLVE_PENDING -> validateResolvePending(game, move)
        }
    }

    /*
    Общие проверки, применимые к любому ходу.
    Возвращает Rejected, если ход нарушает общие инварианты, иначе null.
    */
    private fun checkCommon(game: Game, move: Move): ValidationResult.Rejected? {
        if (game.state == GameState.FINISHED) {
            return ValidationResult.Rejected(listOf("game is already finished"))
        }
        if (game.state != GameState.IN_PROGRESS && move.type != MoveType.START) {
            return ValidationResult.Rejected(listOf("game is not in progress"))
        }

        if (move.type == MoveType.START) {
            if (move.author != null) {
                return ValidationResult.Rejected(listOf("START move must not have an author"))
            }
            return null
        }

        val author = move.author
            ?: return ValidationResult.Rejected(listOf("move must have an author"))
        if (!author.isAlive) {
            return ValidationResult.Rejected(listOf("author is eliminated"))
        }
        if (author.id != game.getCurrentPlayer().id) {
            return ValidationResult.Rejected(listOf("not author's turn"))
        }
        return null
    }

    /*
    START - системное событие первичной раздачи.
    Проверяет, что это первый Move в истории партии.
    */
    private fun validateStart(game: Game, move: Move): ValidationResult {
        if (game.moves.isNotEmpty()) {
            return ValidationResult.Rejected(listOf("START must be the first move"))
        }
        return ValidationResult.Accepted(move)
    }

    /*
    DRAW - взять верхнюю карту из колоды.
    Проверяет, что колода не пуста. Остальное (что произошло при
    вытягивании карты - котёнок или обычная) - задача GameplayService.
    */
    private fun validateDraw(game: Game, move: Move): ValidationResult {
        if (game.deck.isEmpty()) {
            return ValidationResult.Rejected(listOf("cannot draw from empty deck"))
        }
        return ValidationResult.Accepted(move)
    }

    private fun validatePlayCard(game: Game, move: Move): ValidationResult {
        val author = move.author
            ?: return ValidationResult.Rejected(listOf("author is required"))

        if (move.cardsPlayed.size != 1) {
            return ValidationResult.Rejected(listOf("PLAY_CARD must contain exactly one card"))
        }

        val card = move.cardsPlayed.first()

        if (!author.hand.contains(card)) {
            return ValidationResult.Rejected(listOf("card is not in author's hand"))
        }

        if (card.isExplodingKitten) {
            return ValidationResult.Rejected(listOf("exploding kitten cannot be played"))
        }

        if (card.type.isCatCard) {
            return ValidationResult.Rejected(listOf("cat cards cannot be played alone"))
        }

        return when (card.type) {
            CardType.ATTACK -> validateAttack(game, move)
            CardType.SKIP -> ValidationResult.Accepted(move)
            CardType.SHUFFLE -> ValidationResult.Accepted(move)
            CardType.SEE_FUTURE -> ValidationResult.Accepted(move)
            CardType.DEFUSE -> validateDefuse(game, move)
            CardType.FAVOR -> validateFavor(game, move)
            CardType.NOPE -> ValidationResult.Rejected(listOf("NOPE handling is not implemented yet"))
            CardType.EXPLODING_KITTEN -> ValidationResult.Rejected(listOf("exploding kitten cannot be played"))
            CardType.CAT_BEARD,
            CardType.CAT_TACO,
            CardType.CAT_HAIRY_POTATO,
            CardType.CAT_CATERMELON,
            CardType.CAT_RAINBOW_RALPHING ->
                ValidationResult.Rejected(listOf("cat cards cannot be played alone"))
        }
    }

    /*
    ATTACK требует наличия хотя бы одного живого игрока после автора.
    Иначе эффект карты некуда применить.
    */
    private fun validateAttack(game: Game, move: Move): ValidationResult {
        val aliveOthers = game.getAlivePlayers().filter { it.id != move.author?.id }
        if (aliveOthers.isEmpty()) {
            return ValidationResult.Rejected(listOf("no alive players to attack"))
        }
        return ValidationResult.Accepted(move)
    }

    /*
DEFUSE играется только тогда, когда игрок вытянул Exploding Kitten.
Проверяет, что партия находится в состоянии ожидания обезвреживания,
и что указана корректная позиция для возврата котёнка в колоду.
*/
    private fun validateDefuse(game: Game, move: Move): ValidationResult {
        if (game.pendingKitten == null) {
            return ValidationResult.Rejected(
                listOf("DEFUSE can only be played when an exploding kitten is pending")
            )
        }

        val position = move.placedKittenPosition
            ?: return ValidationResult.Rejected(
                listOf("DEFUSE must specify placedKittenPosition")
            )

        if (position !in 0..game.deck.size) {
            return ValidationResult.Rejected(
                listOf("placedKittenPosition must be in 0..${game.deck.size}, was $position")
            )
        }

        return ValidationResult.Accepted(move)
    }

    /*
    FAVOR требует указать другого живого игрока. После розыгрыша
    партия ждёт ответа цели — какой картой она поделится.
    Валидатор возвращает AwaitingResponse с идентификатором цели.
    */
    private fun validateFavor(game: Game, move: Move): ValidationResult {
        val target = move.target
            ?: return ValidationResult.Rejected(listOf("FAVOR requires a target player"))

        if (target.id == move.author?.id) {
            return ValidationResult.Rejected(listOf("FAVOR target must be another player"))
        }

        if (!target.isAlive) {
            return ValidationResult.Rejected(listOf("FAVOR target is eliminated"))
        }

        return ValidationResult.AwaitingResponse(move, responderId = target.id)
    }

    private fun validateTwoOfAKind(game: Game, move: Move): ValidationResult {
        // TODO: реализовать в будущем
        return ValidationResult.Accepted(move)
    }

    private fun validateThreeOfAKind(game: Game, move: Move): ValidationResult {
        // TODO: реализовать в будущем
        return ValidationResult.Accepted(move)
    }

    private fun validateFiveDifferent(game: Game, move: Move): ValidationResult {
        // TODO: реализовать в будущем
        return ValidationResult.Accepted(move)
    }

    private fun validateResolvePending(game: Game, move: Move): ValidationResult {
        // TODO: реализовать в будущем
        return ValidationResult.Accepted(move)
    }
}
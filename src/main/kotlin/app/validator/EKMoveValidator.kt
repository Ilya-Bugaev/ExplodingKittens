package app.validator

import app.dto.ValidationResult
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
        // TODO: реализовать в будущем
        return ValidationResult.Accepted(move)
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
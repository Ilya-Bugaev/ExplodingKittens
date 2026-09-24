package app.validator

import app.dto.ValidationResult
import domain.Game
import domain.Move

/*
Проверяет, можно ли применить ход к текущему состоянию партии.
*/
interface IMoveValidator {
    fun validate(game: Game, move: Move): ValidationResult
}
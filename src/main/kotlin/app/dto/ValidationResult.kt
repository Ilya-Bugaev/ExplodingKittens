package app.dto

import domain.Move

/*
Результат валидации хода.

Sealed-иерархия из четырёх вариантов. Каждый вариант содержит
только те поля, которые имеют смысл:
  - Accepted - ход корректен и может быть применён;
  - Rejected - ход отклонён, есть список ошибок;
  - AwaitingNope - ход предварительно принят, открыто окно Nope,
    эффект будет применён после resolveNopeWindow;
  - AwaitingResponse - ход требует ответа конкретного игрока
    (Favor, тройка), ожидается ответ responderId.
*/
sealed class ValidationResult {

    /*
    Ход корректен и может быть применён немедленно.
    */
    data class Accepted(val move: Move) : ValidationResult()

    /*
    Ход отклонён. errors - список нарушенных правил.
    Пустой список недопустим: если ход отклонён, должна быть причина.
    */
    data class Rejected(val errors: List<String>) : ValidationResult() {
        init {
            require(errors.isNotEmpty()) {
                "Rejected must contain at least one error"
            }
        }
    }

    /*
    Ход предварительно принят, открыто окно Nope.
    Эффект будет применён только после resolveNopeWindow.
    Ответить Nope может любой живой игрок, кроме автора хода.
    */
    data class AwaitingNope(val pendingMove: Move) : ValidationResult()

    /*
    Ход требует ответа конкретного игрока.
    Используется для Favor и комбинации «три одинаковые».
    */
    data class AwaitingResponse(
        val pendingMove: Move,
        val responderId: Int
    ) : ValidationResult()
}
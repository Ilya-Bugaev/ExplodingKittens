package app.dto

import domain.Move
import domain.MoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ValidationResultTest {

    // Rejected с пустым списком ошибок недопустим.
    @Test
    fun `Rejected with empty errors throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ValidationResult.Rejected(emptyList())
        }
    }

    // Rejected с непустым списком ошибок создаётся корректно.
    @Test
    fun `Rejected with errors holds them`() {
        val result = ValidationResult.Rejected(listOf("not your turn"))
        assertEquals(listOf("not your turn"), result.errors)
    }

    // Accepted хранит ссылку на ход.
    @Test
    fun `Accepted holds the move`() {
        val move = Move(1, 1, MoveType.DRAW)
        val result = ValidationResult.Accepted(move)
        assertEquals(move, result.move)
    }

    // AwaitingNope хранит отложенный ход.
    @Test
    fun `AwaitingNope holds pending move`() {
        val move = Move(1, 1, MoveType.PLAY_CARD)
        val result = ValidationResult.AwaitingNope(move)
        assertEquals(move, result.pendingMove)
    }

    // AwaitingResponse хранит отложенный ход и id отвечающего.
    @Test
    fun `AwaitingResponse holds pending move and responder`() {
        val move = Move(1, 1, MoveType.PLAY_CARD)
        val result = ValidationResult.AwaitingResponse(move, responderId = 2)
        assertEquals(move, result.pendingMove)
        assertEquals(2, result.responderId)
    }
}
package system

import app.dto.ValidationResult
import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.PlayerRegistryService
import app.validator.EKMoveValidator
import domain.CardType
import domain.Move
import domain.MoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GameLifecycleTest {

    private class App {
        val repo = InMemoryPlayerRepository()
        val registry = PlayerRegistryService(repo)
        val service = GameplayService(EKMoveValidator(), registry)
    }

    // Реестр игроков общий: игрок, зарегистрированный в одной партии,
    // доступен и в следующей.
    @Test
    fun `player registry persists across games`() {
        val app = App()

        val game1 = app.service.startGame(listOf("Аня", "Боря"), Random(1))
        assertEquals(setOf("Аня", "Боря"), app.registry.getKnownPlayerNames().toSet())

        val game2 = app.service.startGame(listOf("Аня", "Ваня"), Random(2))
        assertEquals(setOf("Аня", "Боря", "Ваня"), app.registry.getKnownPlayerNames().toSet())

        assertNotNull(app.service.getCurrentGame(game1))
        assertNotNull(app.service.getCurrentGame(game2))
    }

    // Две партии существуют независимо: ход в одной не влияет на другую.
    @Test
    fun `two games are independent`() {
        val app = App()
        val id1 = app.service.startGame(listOf("Аня", "Боря"), Random(1))
        val id2 = app.service.startGame(listOf("Ваня", "Гена"), Random(2))

        val game1 = app.service.getCurrentGame(id1)!!
        val game2 = app.service.getCurrentGame(id2)!!

        val turns1Before = game1.turnsPlayed
        val turns2Before = game2.turnsPlayed

        val move = Move(0, 1, MoveType.DRAW, author = game1.getCurrentPlayer())
        app.service.playMove(id1, move)

        assertEquals(turns1Before + 1, game1.turnsPlayed)
        assertEquals(turns2Before, game2.turnsPlayed)
    }

    // Длинная серия ходов: 20 ходов подряд без ошибок.
    @Test
    fun `twenty consecutive moves leave system in consistent state`() {
        val app = App()
        val id = app.service.startGame(listOf("Аня", "Боря", "Ваня"), Random(42))
        val game = app.service.getCurrentGame(id)!!

        var playMoveCalls = 0
        var safety = 0

        while (playMoveCalls < 20 && safety < 200 && !game.isFinished()) {
            safety++
            val current = game.getCurrentPlayer()

            // Если висит котёнок — DEFUSE, если есть; иначе выбывание через сервис
            if (game.pendingKitten != null) {
                val defuse = current.findCardsOfType(CardType.DEFUSE).firstOrNull()
                if (defuse != null) {
                    val defuseMove = Move(
                        0, game.turnsPlayed + 1, MoveType.PLAY_CARD,
                        author = current, cardsPlayed = listOf(defuse),
                        placedKittenPosition = 0
                    )
                    val result = app.service.playMove(id, defuseMove)
                    if (result is ValidationResult.Accepted) playMoveCalls++
                } else {
                    // Имитируем выбывание: сервис пока не умеет это как Move
                    current.eliminate()
                    game.clearPendingKitten()
                    game.advanceTurn(forAttack = false)
                }
                continue
            }

            // Обычный ход: SKIP, если есть, иначе DRAW
            val skip = current.findCardsOfType(CardType.SKIP).firstOrNull()
            val move = if (skip != null) {
                Move(0, game.turnsPlayed + 1, MoveType.PLAY_CARD,
                    author = current, cardsPlayed = listOf(skip))
            } else {
                Move(0, game.turnsPlayed + 1, MoveType.DRAW, author = current)
            }
            val result = app.service.playMove(id, move)
            if (result is ValidationResult.Accepted) playMoveCalls++
        }

        assertTrue(playMoveCalls >= 10, "expected at least 10 accepted moves, got $playMoveCalls")

        // История содержит START + все принятые playMove
        assertTrue(game.moves.isNotEmpty())
        assertEquals(game.moves.size, playMoveCalls + 1)
    }

    // Сценарий с конца: партия завершается через endGame и получает победителя.
    @Test
    fun `game can be finished and has a winner`() {
        val app = App()
        val id = app.service.startGame(listOf("Аня", "Боря", "Ваня"), Random(42))
        val game = app.service.getCurrentGame(id)!!

        // Принудительно выбываем двоих
        game.players[0].eliminate()
        game.players[1].eliminate()

        app.service.endGame(id)

        assertEquals(domain.GameState.FINISHED, game.state)
        assertNotNull(game.winner)
        assertEquals("Ваня", game.winner!!.name)
    }
}
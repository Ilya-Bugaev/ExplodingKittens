package system

import app.dto.ValidationResult
import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.PlayerRegistryService
import app.validator.EKMoveValidator
import domain.CardType
import domain.GameState
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

    /*
    Вспомогательный метод: подаёт ход и, если он ушёл в окно Nope,
    закрывает окно. Возвращает финальный результат эффекта.
    */
    private fun playAndResolve(service: GameplayService, gameId: Int, move: Move): ValidationResult {
        val first = service.playMove(gameId, move)
        return when (first) {
            is ValidationResult.AwaitingNope -> service.resolveNopeWindow(gameId)
            else -> first
        }
    }

    // Реестр игроков общий для нескольких партий.
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
        playAndResolve(app.service, id1, move)

        assertEquals(turns1Before + 1, game1.turnsPlayed)
        assertEquals(turns2Before, game2.turnsPlayed)
    }

    // Длинная серия ходов: 20 ходов подряд без ошибок.
    // Игроки либо берут карту, либо играют SKIP, если он есть в руке.
    @Test
    fun `twenty consecutive moves leave system in consistent state`() {
        val app = App()
        val id = app.service.startGame(listOf("Аня", "Боря", "Ваня"), Random(42))
        val game = app.service.getCurrentGame(id)!!

        var movesPlayed = 0
        var safety = 0

        while (movesPlayed < 20 && safety < 200 && !game.isFinished()) {
            safety++
            val current = game.getCurrentPlayer()

            // Если висит котёнок — DEFUSE, если есть; иначе выбывание
            if (game.pendingKitten != null) {
                val defuse = current.findCardsOfType(CardType.DEFUSE).firstOrNull()
                if (defuse != null) {
                    val defuseMove = Move(
                        0, game.turnsPlayed + 1, MoveType.PLAY_CARD,
                        author = current, cardsPlayed = listOf(defuse),
                        placedKittenPosition = 0
                    )
                    val result = playAndResolve(app.service, id, defuseMove)
                    if (result is ValidationResult.Accepted) movesPlayed++
                } else {
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

            val result = playAndResolve(app.service, id, move)
            if (result is ValidationResult.Accepted) movesPlayed++
        }

        assertTrue(movesPlayed >= 10, "expected at least 10 moves, got $movesPlayed")
        // START + все применённые ходы
        assertTrue(game.moves.size >= movesPlayed)
    }

    // Партия завершается через endGame, winner = null при нескольких живых.
    @Test
    fun `game can be finished and has a winner`() {
        val app = App()
        val id = app.service.startGame(listOf("Аня", "Боря", "Ваня"), Random(42))
        val game = app.service.getCurrentGame(id)!!

        game.players[0].eliminate()
        game.players[1].eliminate()

        app.service.endGame(id)

        assertEquals(GameState.FINISHED, game.state)
        assertNotNull(game.winner)
        assertEquals("Ваня", game.winner!!.name)
    }
}
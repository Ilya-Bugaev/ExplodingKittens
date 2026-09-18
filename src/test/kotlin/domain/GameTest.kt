package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/*
Тесты Game: старт партии, состояние, текущий игрок, живые игроки,
завершение, продвижение хода, добавление Move.
*/
class GameTest {

    // Вспомогательный метод: создаёт партию с фиксированным seed.
    private fun newGame(vararg names: String): Game {
        val game = Game(id = 1)
        game.startGame(names.toList(), Random(42))
        return game
    }

    // startGame создаёт игроков и переводит партию в IN_PROGRESS.
    @Test
    fun `startGame creates players and sets state to IN_PROGRESS`() {
        val game = newGame("Аня", "Боря", "Ваня")

        assertEquals(GameState.IN_PROGRESS, game.state)
        assertEquals(3, game.players.size)
        assertEquals(listOf("Аня", "Боря", "Ваня"), game.players.map { it.name })
    }

    // Все игроки живы сразу после старта.
    @Test
    fun `all players are alive after start`() {
        val game = newGame("Аня", "Боря", "Ваня")
        assertTrue(game.players.all { it.isAlive })
    }

    // У каждого игрока 8 карт: 7 из колоды + 1 Defuse.
    @Test
    fun `each player has 8 cards after deal`() {
        val game = newGame("Аня", "Боря", "Ваня")
        game.players.forEach { player ->
            assertEquals(8, player.hand.size)
        }
    }

    // У каждого игрока гарантированно есть Defuse.
    @Test
    fun `each player has at least one Defuse after deal`() {
        val game = newGame("Аня", "Боря", "Ваня")
        game.players.forEach { player ->
            assertTrue(player.hasCardOfType(CardType.DEFUSE))
        }
    }

    // В колоде ровно (playerCount - 1) Exploding Kittens.
    @Test
    fun `deck contains correct number of exploding kittens`() {
        val game = newGame("Аня", "Боря", "Ваня")
        val kittens = game.deck.peekTop(game.deck.size())
            .count { it.type == CardType.EXPLODING_KITTEN }
        assertEquals(2, kittens)
    }

    // Игроков меньше двух - ошибка.
    @Test
    fun `startGame with fewer than 2 players throws`() {
        val game = Game(id = 1)
        assertThrows(IllegalArgumentException::class.java) {
            game.startGame(listOf("Аня"), Random(42))
        }
    }

    // Игроков больше пяти - ошибка.
    @Test
    fun `startGame with more than 5 players throws`() {
        val game = Game(id = 1)
        assertThrows(IllegalArgumentException::class.java) {
            game.startGame(listOf("A", "B", "C", "D", "E", "F"), Random(42))
        }
    }

    // Повторный startGame на уже начатой партии - ошибка.
    @Test
    fun `startGame twice throws`() {
        val game = newGame("Аня", "Боря")
        assertThrows(IllegalStateException::class.java) {
            game.startGame(listOf("A", "B"), Random(42))
        }
    }

    // Первый ход - у первого игрока.
    @Test
    fun `current player is first after start`() {
        val game = newGame("Аня", "Боря", "Ваня")
        assertEquals("Аня", game.getCurrentPlayer().name)
    }

    // getAlivePlayers возвращает всех, пока никто не выбыл.
    @Test
    fun `getAlivePlayers returns all after start`() {
        val game = newGame("Аня", "Боря", "Ваня")
        assertEquals(3, game.getAlivePlayers().size)
    }

    // Выбывшие игроки не попадают в список живых.
    @Test
    fun `getAlivePlayers excludes eliminated`() {
        val game = newGame("Аня", "Боря", "Ваня")
        game.players[1].eliminate()
        assertEquals(listOf("Аня", "Ваня"), game.getAlivePlayers().map { it.name })
    }

    // Партия не закончена, пока больше одного живого.
    @Test
    fun `game is not finished while multiple players alive`() {
        val game = newGame("Аня", "Боря", "Ваня")
        assertFalse(game.isFinished())
    }

    // Партия закончена, когда остался один живой.
    @Test
    fun `game is finished when only one player alive`() {
        val game = newGame("Аня", "Боря", "Ваня")
        game.players[0].eliminate()
        game.players[1].eliminate()
        assertTrue(game.isFinished())
    }

    // finish переводит state в FINISHED и назначает winner.
    @Test
    fun `finish sets state and winner`() {
        val game = newGame("Аня", "Боря", "Ваня")
        game.players[0].eliminate()
        game.players[1].eliminate()

        game.finish()

        assertEquals(GameState.FINISHED, game.state)
        assertNotNull(game.winner)
        assertEquals("Ваня", game.winner!!.name)
    }

    // advanceTurn переходит к следующему живому игроку.
    @Test
    fun `advanceTurn moves to next player`() {
        val game = newGame("Аня", "Боря", "Ваня")
        game.advanceTurn()
        assertEquals("Боря", game.getCurrentPlayer().name)
    }

    // advanceTurn пропускает выбывших игроков.
    @Test
    fun `advanceTurn skips eliminated players`() {
        val game = newGame("Аня", "Боря", "Ваня")
        game.players[1].eliminate()
        game.advanceTurn()
        assertEquals("Ваня", game.getCurrentPlayer().name)
    }

    // advanceTurn увеличивает turnsPlayed.
    @Test
    fun `advanceTurn increments turnsPlayed`() {
        val game = newGame("Аня", "Боря")
        game.advanceTurn()
        assertEquals(1, game.turnsPlayed)
    }

    // При активной атаке ход остаётся на текущем игроке.
    @Test
    fun `advanceTurn keeps current player when attacks pending`() {
        val game = newGame("Аня", "Боря", "Ваня")
        game.setAttacksPending(2)

        game.advanceTurn()

        assertEquals("Аня", game.getCurrentPlayer().name)
        assertEquals(1, game.attacksPending)
    }

    // Отрицательное значение attacksPending - ошибка.
    @Test
    fun `setAttacksPending with negative value throws`() {
        val game = newGame("Аня", "Боря")
        assertThrows(IllegalArgumentException::class.java) {
            game.setAttacksPending(-1)
        }
    }

    // addMove присваивает уникальные последовательные id и сохраняет порядок.
    @Test
    fun `addMove assigns sequential ids`() {
        val game = newGame("Аня", "Боря")
        val m1 = game.addMove(Move(0, 0, MoveType.START))
        val m2 = game.addMove(Move(0, 1, MoveType.DRAW, author = game.players[0]))

        assertEquals(1, m1.id)
        assertEquals(2, m2.id)
        assertEquals(2, game.moves.size)
    }

    // Новый Game без startGame: state = SETUP, игроков нет, winner = null.
    @Test
    fun `new game is in SETUP state with no players`() {
        val game = Game(id = 1)
        assertEquals(GameState.SETUP, game.state)
        assertTrue(game.players.isEmpty())
        assertNull(game.winner)
    }
}
package integration

import app.repository.IHistoryRepository
import app.repository.IPlayerRepository
import app.repository.IStatisticsRepository
import app.service.GameplayService
import app.service.HistoryService
import app.service.PlayerRegistryService
import app.service.StatisticsService
import app.storage.sql.Database
import app.storage.sql.SqlHistoryRepository
import app.storage.sql.SqlPlayerRepository
import app.storage.sql.SqlStatisticsRepository
import app.validator.EKMoveValidator
import domain.Card
import domain.CardType
import domain.Move
import domain.MoveType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SqlFullGameFlowTest {

    private lateinit var db: Database
    private lateinit var playerRepo: IPlayerRepository
    private lateinit var historyRepo: IHistoryRepository
    private lateinit var statsRepo: IStatisticsRepository
    private lateinit var gameplay: GameplayService
    private lateinit var history: HistoryService
    private lateinit var stats: StatisticsService

    @BeforeEach
    fun setUp() {
        db = Database.inMemory()
        playerRepo = SqlPlayerRepository(db)
        historyRepo = SqlHistoryRepository(db)
        statsRepo = SqlStatisticsRepository(db)

        val registry = PlayerRegistryService(playerRepo)
        history = HistoryService(historyRepo)
        stats = StatisticsService(statsRepo, registry)
        gameplay = GameplayService(EKMoveValidator(), registry, history, stats)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    // Партия сохраняется в SQL-историю при завершении.
    @Test
    fun `finished game is saved to sql history`() {
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))
        gameplay.endGame(id)

        val record = history.getRecord(id)
        assertNotNull(record)
        assertEquals(listOf("Аня", "Боря"), record!!.playerNames)
    }

    // Статистика обновляется при завершении партии.
    @Test
    fun `finished game updates sql statistics`() {
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))
        gameplay.endGame(id)

        val allStats = stats.getAllPlayerStats()
        assertEquals(2, allStats.size)
        assertEquals(setOf("Аня", "Боря"), allStats.map { it.name }.toSet())
    }

    // Победитель фиксируется в истории.
    @Test
    fun `winner is recorded in sql history`() {
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))
        val game = gameplay.getCurrentGame(id)!!
        game.players[1].eliminate()
        gameplay.endGame(id)

        assertEquals("Аня", history.getRecord(id)?.winnerName)
    }

    // Ходы партии сохраняются в SQL.
    @Test
    fun `moves are stored in sql`() {
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))
        val game = gameplay.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()

        // DRAW
        gameplay.playMove(id, Move(0, 1, MoveType.DRAW, author = current))
        gameplay.endGame(id)

        val record = history.getRecord(id)!!
        assertTrue(record.moves.isNotEmpty())
        assertEquals("START", record.moves.first().type)
        assertTrue(record.moves.any { it.type == "DRAW" })
    }

    // Партия с комбинацией: карты сохраняются в JSON-поле.
    @Test
    fun `combo cards are stored in sql`() {
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))
        val game = gameplay.getCurrentGame(id)!!
        val anna = game.players[0]
        val boris = game.players[1]
        val c1 = Card(9001, CardType.CAT_TACO)
        val c2 = Card(9002, CardType.CAT_TACO)
        anna.addCard(c1)
        anna.addCard(c2)

        val comboMove = Move(
            0, 1, MoveType.PLAY_TWO_OF_A_KIND,
            author = anna, target = boris, cardsPlayed = listOf(c1, c2)
        )
        gameplay.playMove(id, comboMove)
        gameplay.endGame(id)

        val record = history.getRecord(id)!!
        val combo = record.moves.firstOrNull { it.type == "PLAY_TWO_OF_A_KIND" }
        assertNotNull(combo)
        assertEquals(2, combo!!.cardsPlayed.size)
    }

    // Три партии подряд: история содержит три записи.
    @Test
    fun `three consecutive games are stored`() {
        repeat(3) {
            val id = gameplay.startGame(listOf("Аня", "Боря"), Random(it))
            gameplay.endGame(id)
        }

        assertEquals(3, history.getAllRecords().size)
    }

    // gamesPlayed накапливается для обоих игроков.
    @Test
    fun `gamesPlayed accumulates across games`() {
        repeat(3) {
            val id = gameplay.startGame(listOf("Аня", "Боря"), Random(it))
            gameplay.endGame(id)
        }

        stats.getAllPlayerStats().forEach {
            assertEquals(3, it.gamesPlayed)
        }
    }

    // Выбывание игрока: eliminated сохраняется в Move.
    @Test
    fun `eliminated flag is stored in move`() {
        val id = gameplay.startGame(listOf("Аня", "Боря"), Random(42))
        val game = gameplay.getCurrentGame(id)!!
        val current = game.getCurrentPlayer()
        current.findCardsOfType(CardType.DEFUSE).forEach { current.removeCard(it) }
        game.deck.insertCardAtTop(Card(9999, CardType.EXPLODING_KITTEN))

        gameplay.playMove(id, Move(0, 1, MoveType.DRAW, author = current))
        gameplay.endGame(id)

        val record = history.getRecord(id)!!
        val drawMove = record.moves.firstOrNull { it.type == "DRAW" && it.eliminated }
        assertNotNull(drawMove)
    }
}
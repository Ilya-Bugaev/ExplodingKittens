package app.service

import app.dto.GameSummary
import app.repository.InMemoryPlayerRepository
import app.storage.JsonStatisticsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StatisticsServiceTest {

    @TempDir
    lateinit var tempDir: File

    private fun service(): Pair<StatisticsService, PlayerRegistryService> {
        val registry = PlayerRegistryService(InMemoryPlayerRepository())
        val stats = StatisticsService(
            JsonStatisticsRepository(File(tempDir, "stats.json")),
            registry
        )
        return stats to registry
    }

    // Первая партия: gamesPlayed = 1 у обоих, wins = 1 у победителя.
    @Test
    fun `first game updates gamesPlayed and wins`() {
        val (stats, registry) = service()
        registry.registerPlayer("Аня")
        registry.registerPlayer("Боря")

        stats.updateStats(
            GameSummary(
                gameId = 1,
                playerNames = listOf("Аня", "Боря"),
                winnerName = "Аня",
                turnsPlayed = 10
            )
        )

        val anna = stats.getPlayerStats(registry.findPlayerId("Аня")!!)!!
        val boris = stats.getPlayerStats(registry.findPlayerId("Боря")!!)!!

        assertEquals(1, anna.wins)
        assertEquals(1, anna.gamesPlayed)
        assertEquals(0, boris.wins)
        assertEquals(1, boris.gamesPlayed)
    }

    // Вторая партия с другим победителем увеличивает оба счётчика.
    @Test
    fun `second game adds to existing stats`() {
        val (stats, registry) = service()
        registry.registerPlayer("Аня")
        registry.registerPlayer("Боря")
        val annaId = registry.findPlayerId("Аня")!!
        val borisId = registry.findPlayerId("Боря")!!

        stats.updateStats(GameSummary(1, listOf("Аня", "Боря"), "Аня", 10))
        stats.updateStats(GameSummary(2, listOf("Аня", "Боря"), "Боря", 12))

        assertEquals(1, stats.getPlayerStats(annaId)!!.wins)
        assertEquals(2, stats.getPlayerStats(annaId)!!.gamesPlayed)
        assertEquals(1, stats.getPlayerStats(borisId)!!.wins)
        assertEquals(2, stats.getPlayerStats(borisId)!!.gamesPlayed)
    }

    // winRate пересчитывается автоматически.
    @Test
    fun `winRate is recalculated`() {
        val (stats, registry) = service()
        registry.registerPlayer("Аня")
        registry.registerPlayer("Боря")
        val annaId = registry.findPlayerId("Аня")!!

        stats.updateStats(GameSummary(1, listOf("Аня", "Боря"), "Аня", 10))
        stats.updateStats(GameSummary(2, listOf("Аня", "Боря"), "Боря", 12))

        assertEquals(0.5, stats.getPlayerStats(annaId)!!.winRate, 0.001)
    }

    // getLeaderboard сортирует по wins, затем по winRate.
    @Test
    fun `leaderboard sorts by wins`() {
        val (stats, registry) = service()
        registry.registerPlayer("Аня")
        registry.registerPlayer("Боря")
        registry.registerPlayer("Ваня")

        stats.updateStats(GameSummary(1, listOf("Аня", "Боря"), "Аня", 10))
        stats.updateStats(GameSummary(2, listOf("Аня", "Ваня"), "Аня", 11))
        stats.updateStats(GameSummary(3, listOf("Боря", "Ваня"), "Боря", 12))

        val leaders = stats.getLeaderboard()

        assertEquals(listOf("Аня", "Боря", "Ваня"), leaders.map { it.name })
    }

    // Неизвестный игрок не попадает в статистику.
    @Test
    fun `unknown player is ignored`() {
        val (stats, _) = service()
        stats.updateStats(GameSummary(1, listOf("Незнакомец"), "Незнакомец", 5))
        assertEquals(0, stats.getAllPlayerStats().size)
    }

    // getPlayerStats для неизвестного игрока возвращает null.
    @Test
    fun `getPlayerStats returns null for unknown`() {
        val (stats, _) = service()
        assertNull(stats.getPlayerStats(999))
    }
}
package ui.gui

import app.dto.GameSummary
import app.repository.InMemoryPlayerRepository
import app.service.PlayerRegistryService
import app.service.StatisticsService
import app.storage.JsonStatisticsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LeaderboardViewModelTest {

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

    // Пустая статистика — пустой список.
    @Test
    fun `empty stats yields empty leaderboard`() {
        val (stats, _) = service()
        val vm = LeaderboardViewModel(stats)
        assertTrue(vm.state.value.players.isEmpty())
    }

    // После партии лидерборд содержит игроков.
    @Test
    fun `leaderboard contains players after game`() {
        val (stats, registry) = service()
        registry.registerPlayer("Аня")
        registry.registerPlayer("Боря")
        stats.updateStats(GameSummary(1, listOf("Аня", "Боря"), "Аня", 10))

        val vm = LeaderboardViewModel(stats)

        assertEquals(2, vm.state.value.players.size)
    }

    // Игроки отсортированы по победам.
    @Test
    fun `leaderboard sorted by wins`() {
        val (stats, registry) = service()
        registry.registerPlayer("Аня")
        registry.registerPlayer("Боря")
        registry.registerPlayer("Ваня")
        stats.updateStats(GameSummary(1, listOf("Аня", "Боря"), "Аня", 10))
        stats.updateStats(GameSummary(2, listOf("Аня", "Ваня"), "Аня", 11))
        stats.updateStats(GameSummary(3, listOf("Боря", "Ваня"), "Боря", 12))

        val vm = LeaderboardViewModel(stats)

        assertEquals(listOf("Аня", "Боря", "Ваня"), vm.state.value.players.map { it.name })
    }

    // refresh подхватывает новые данные.
    @Test
    fun `refresh updates leaderboard`() {
        val (stats, registry) = service()
        registry.registerPlayer("Аня")
        val vm = LeaderboardViewModel(stats)
        assertTrue(vm.state.value.players.isEmpty())

        stats.updateStats(GameSummary(1, listOf("Аня"), "Аня", 5))
        vm.refresh()

        assertEquals(1, vm.state.value.players.size)
    }
}
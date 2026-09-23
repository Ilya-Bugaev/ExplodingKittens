import app.service.GameplayService
import app.service.HistoryService
import app.service.PlayerRegistryService
import app.service.StatisticsService
import app.storage.JsonHistoryRepository
import app.storage.JsonPlayerRepository
import app.storage.JsonStatisticsRepository
import app.validator.EKMoveValidator
import ui.console.ConsoleGameScreen
import java.io.File

/*
Консольная точка входа. Запускается через Main с аргументом "console":
./gradlew run --args="console"
*/
fun runConsoleApp() {
    val dataDir = File("data").apply { mkdirs() }

    val playerRepo = JsonPlayerRepository(File(dataDir, "players.json"))
    val historyRepo = JsonHistoryRepository(File(dataDir, "history.json"))
    val statsRepo = JsonStatisticsRepository(File(dataDir, "stats.json"))

    val registry = PlayerRegistryService(playerRepo)
    val history = HistoryService(historyRepo)
    val stats = StatisticsService(statsRepo, registry)

    val validator = EKMoveValidator()
    val gameplay = GameplayService(validator, registry, history, stats)

    val screen = ConsoleGameScreen(gameplay, registry)
    screen.start()
}
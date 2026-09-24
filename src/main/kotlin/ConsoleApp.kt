import app.service.GameplayService
import app.service.HistoryService
import app.service.PlayerRegistryService
import app.service.StatisticsService
import app.storage.sql.Database
import app.storage.sql.SqlHistoryRepository
import app.storage.sql.SqlPlayerRepository
import app.storage.sql.SqlStatisticsRepository
import app.validator.EKMoveValidator
import ui.console.ConsoleGameScreen
import java.io.File

/*
Консольная точка входа. Запускается через Main с аргументом "console":
./gradlew run --args="console"
*/
fun runConsoleApp() {
    val dataDir = File("data").apply { mkdirs() }
    val database = Database.open(File(dataDir, "tracker.db"))

    val registry = PlayerRegistryService(SqlPlayerRepository(database))
    val history = HistoryService(SqlHistoryRepository(database))
    val stats = StatisticsService(SqlStatisticsRepository(database), registry)
    val gameplay = GameplayService(EKMoveValidator(), registry, history, stats)

    val screen = ConsoleGameScreen(gameplay, registry)
    screen.start()

    database.close()
}
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.service.GameplayService
import app.service.HistoryService
import app.service.PlayerRegistryService
import app.service.StatisticsService
import app.storage.sql.Database
import app.storage.sql.SqlHistoryRepository
import app.storage.sql.SqlPlayerRepository
import app.storage.sql.SqlStatisticsRepository
import app.validator.EKMoveValidator
import ui.gui.GuiApp
import ui.gui.HistoryViewModel
import ui.gui.LeaderboardViewModel
import ui.gui.MainViewModel
import java.io.File

fun main(args: Array<String>) {
    if (args.contains("console")) {
        runConsoleApp()
        return
    }

    val dataDir = File("data").apply { mkdirs() }
    val database = Database.open(File(dataDir, "tracker.db"))

    val playerRepo = SqlPlayerRepository(database)
    val historyRepo = SqlHistoryRepository(database)
    val statsRepo = SqlStatisticsRepository(database)

    val registry = PlayerRegistryService(playerRepo)
    val history = HistoryService(historyRepo)
    val stats = StatisticsService(statsRepo, registry)
    val gameplay = GameplayService(EKMoveValidator(), registry, history, stats)

    val gameViewModel = MainViewModel(gameplay, registry)
    val historyViewModel = HistoryViewModel(history)
    val leaderboardViewModel = LeaderboardViewModel(stats)

    application {
        Window(
            onCloseRequest = {
                database.close()
                exitApplication()
            },
            title = "Exploding Kittens Tracker"
        ) {
            MaterialTheme {
                GuiApp(gameViewModel, historyViewModel, leaderboardViewModel)
            }
        }
    }
}
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.HistoryService
import app.service.PlayerRegistryService
import app.service.StatisticsService
import app.storage.JsonHistoryRepository
import app.storage.JsonPlayerRepository
import app.storage.JsonStatisticsRepository
import app.validator.EKMoveValidator
import ui.gui.GuiApp
import ui.gui.GuiGameScreen
import ui.gui.HistoryViewModel
import ui.gui.MainViewModel
import java.io.File

fun main(args: Array<String>) {
    if (args.contains("console")) {
        runConsoleApp()
        return
    }

    val dataDir = File("data").apply { mkdirs() }

    val playerRepo = JsonPlayerRepository(File(dataDir, "players.json"))
    val historyRepo = JsonHistoryRepository(File(dataDir, "history.json"))
    val statsRepo = JsonStatisticsRepository(File(dataDir, "stats.json"))

    val registry = PlayerRegistryService(playerRepo)
    val history = HistoryService(historyRepo)
    val stats = StatisticsService(statsRepo, registry)
    val gameplay = GameplayService(EKMoveValidator(), registry, history, stats)

    val viewModel = MainViewModel(gameplay, registry)

    val gameViewModel = MainViewModel(gameplay, registry)
    val historyViewModel = HistoryViewModel(history)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Exploding Kittens Tracker"
        ) {
            MaterialTheme {
                GuiApp(gameViewModel, historyViewModel)
            }
        }
    }
}
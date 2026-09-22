import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.PlayerRegistryService
import app.storage.JsonPlayerRepository
import app.validator.EKMoveValidator
import ui.console.ConsoleGameScreen
import java.io.File

fun main() {
    val dataDir = File("data").apply { mkdirs() }
    val playerRepo = JsonPlayerRepository(File(dataDir, "players.json"))
    val registry = PlayerRegistryService(playerRepo)
    val validator = EKMoveValidator()
    val gameplay = GameplayService(validator, registry)

    val screen = ConsoleGameScreen(gameplay, registry)
    screen.start()
}
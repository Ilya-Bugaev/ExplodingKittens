import app.repository.InMemoryPlayerRepository
import app.service.GameplayService
import app.service.PlayerRegistryService
import app.validator.EKMoveValidator
import ui.console.ConsoleGameScreen

fun main() {
    val playerRepo = InMemoryPlayerRepository()
    val registry = PlayerRegistryService(playerRepo)
    val validator = EKMoveValidator()
    val gameplay = GameplayService(validator, registry)

    val screen = ConsoleGameScreen(gameplay, registry)
    screen.start()
}
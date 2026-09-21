package ui.console

import app.dto.ValidationResult
import app.service.GameplayService
import app.service.PlayerRegistryService
import domain.CardType
import domain.Game
import domain.GameState
import domain.Move
import domain.MoveType

/*
Консольный экран для ведения партии.

Меню:
  1. Зарегистрировать игрока
  2. Показать список игроков
  3. Начать новую партию
  4. Взять карту (DRAW)
  5. Сыграть карту (PLAY_CARD)
  6. Показать состояние партии
  7. Завершить партию
  0. Выход

Ход игрока не завершается после SHUFFLE, SEE_FUTURE, FAVOR и комбинаций -
игрок может продолжать играть карты. Завершают ход только DRAW, SKIP
и ATTACK. Экран сообщает об этом явно.

Отложенные состояния (Nope, ожидание ответа) обрабатываются
внутри соответствующих сценариев.
*/
class ConsoleGameScreen(
    private val gameplay: GameplayService,
    private val registry: PlayerRegistryService
) : ConsoleBaseScreen(), IGameScreen {

    private var activeGameId: Int? = null

    override fun start() {
        while (true) {
            printMenu()
            when (readInt("Выбор")) {
                1 -> registerPlayer()
                2 -> showPlayers()
                3 -> startNewGame()
                4 -> drawCard()
                5 -> playCard()
                6 -> showGameState()
                7 -> endGame()
                0 -> {
                    printLine("Выход.")
                    return
                }
                else -> printLine("Неизвестная команда.")
            }
        }
    }

    private fun printMenu() {
        printSeparator()
        printLine("=== Exploding Kittens Tracker ===")
        printLine("1. Зарегистрировать игрока")
        printLine("2. Показать список игроков")
        printLine("3. Начать новую партию")
        printLine("4. Взять карту (DRAW) - завершает ход")
        printLine("5. Сыграть карту")
        printLine("6. Показать состояние партии")
        printLine("7. Завершить партию")
        printLine("0. Выход")
        printSeparator()
    }

    private fun registerPlayer() {
        val name = readLine("Имя игрока")
        if (name.isBlank()) {
            printLine("Имя не может быть пустым.")
            return
        }
        val id = registry.registerPlayer(name)
        printLine("Игрок '$name' зарегистрирован (id=$id).")
    }

    private fun showPlayers() {
        val names = registry.getKnownPlayerNames()
        if (names.isEmpty()) {
            printLine("Список пуст.")
        } else {
            printLine("Игроки: ${names.joinToString(", ")}")
        }
    }

    private fun startNewGame() {
        val namesStr = readLine("Имена игроков через запятую (2–5)")
        val names = namesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (names.size !in 2..5) {
            printLine("Нужно от 2 до 5 игроков.")
            return
        }
        try {
            val id = gameplay.startGame(names)
            activeGameId = id
            printLine("Партия #$id создана. Игроки: ${names.joinToString(", ")}.")
            printLine(gameplay.getCurrentGame(id)!!.getInitialSetup())
            showGameState()
        } catch (e: IllegalArgumentException) {
            printLine("Ошибка: ${e.message}")
        }
    }

    private fun drawCard() {
        val game = requireActiveGame() ?: return

        // Если висит непогашенный котёнок, сначала требуем DEFUSE
        if (game.pendingKitten != null) {
            printLine("⚠ Вытянут взрывной котёнок. Сначала сыграйте DEFUSE (пункт 5) или завершите партию.")
            return
        }

        val current = game.getCurrentPlayer()
        val move = Move(0, game.turnsPlayed + 1, MoveType.DRAW, author = current)
        handleResult(gameplay.playMove(game.id, move), game)
    }

    private fun playCard() {
        val game = requireActiveGame() ?: return
        val current = game.getCurrentPlayer()

        if (current.hand.isEmpty()) {
            printLine("Рука пуста.")
            return
        }

        printLine("Ваша рука (${current.name}):")
        current.hand.forEachIndexed { index, card ->
            printLine("  ${index + 1}. ${card.type} (id=${card.id})")
        }

        val index = readInt("Номер карты (0 - отмена)")
        if (index == null || index == 0) return
        val card = current.hand.getOrNull(index - 1)
        if (card == null) {
            printLine("Неверный номер.")
            return
        }

        val move = Move(
            id = 0,
            turnNumber = game.turnsPlayed + 1,
            type = MoveType.PLAY_CARD,
            author = current,
            cardsPlayed = listOf(card)
        )
        handleResult(gameplay.playMove(game.id, move), game)
    }

    private fun showGameState() {
        val game = requireActiveGame() ?: return
        printSeparator()
        printLine("Партия #${game.id} - ${game.state}")
        printLine("Ход №${game.turnsPlayed}, сейчас ходит: ${game.getCurrentPlayer().name}")

        if (game.attacksPending > 0) {
            printLine("⚠ Висит атак: ${game.attacksPending} (текущий игрок ходит ещё столько раз)")
        }
        if (game.pendingKitten != null) {
            printLine("⚠ ВЫТЯНУТ ВЗРЫВНОЙ КОТЁНОК! Сыграйте DEFUSE или игрок выбывает.")
        }

        printLine("Колода: ${game.deck.size} карт, сброс: ${game.discardPile.size()} карт")
        game.players.forEach { player ->
            val status = if (player.isAlive) "жив" else "выбыл"
            val marker = if (player.id == game.getCurrentPlayer().id) " ← ходит" else ""
            printLine("  ${player.name} ($status): ${player.hand.size} карт в руке$marker")
        }
        printSeparator()
    }

    private fun endGame() {
        val id = activeGameId
        if (id == null) {
            printLine("Активной партии нет.")
            return
        }
        gameplay.endGame(id)
        printLine("Партия #$id завершена.")
        activeGameId = null
    }

    private fun requireActiveGame(): Game? {
        val id = activeGameId
        if (id == null) {
            printLine("Сначала начните партию (пункт 3).")
            return null
        }
        val game = gameplay.getCurrentGame(id)
        if (game == null) {
            printLine("Партия не найдена.")
            activeGameId = null
            return null
        }
        return game
    }

    /*
    Обрабатывает результат playMove.

    При Accepted:
      - если партия завершена - сообщаем победителя;
      - если ход перешёл к следующему игроку - показываем новое состояние;
      - если ход остался у текущего (SHUFFLE, SEE_FUTURE, FAVOR, комбинации) -
        сообщаем, что можно продолжать.
    */
    private fun handleResult(result: ValidationResult, game: Game) {
        when (result) {
            is ValidationResult.Accepted -> handleAccepted(game)

            is ValidationResult.Rejected -> {
                printLine("✗ Ход отклонён: ${result.errors.joinToString("; ")}")
            }

            is ValidationResult.AwaitingNope -> {
                handleNopeWindow(game)
            }

            is ValidationResult.AwaitingResponse -> {
                handleAwaitingResponse(game, result.responderId)
            }
        }
    }

    private fun handleAccepted(game: Game) {
        printLine("✓ Ход принят.")

        if (game.isFinished() || game.state == GameState.FINISHED) {
            printLine("🏆 Партия завершена! Победитель: ${game.winner?.name ?: "-"}")
            activeGameId = null
            return
        }

        // Если сейчас у игрока висит котёнок - сообщаем и не переключаем
        if (game.pendingKitten != null) {
            printLine("⚠ Вытянут взрывной котёнок. Сыграйте DEFUSE (пункт 5).")
            return
        }

        val currentName = game.getCurrentPlayer().name
        printLine("Ход продолжается: $currentName может сыграть ещё карту или взять карту (пункт 4).")
    }

    /*
    Открывает окно Nope. Спрашивает, хочет ли кто-то сыграть NOPE.
    Если да - просит имя и запускает ход NOPE. Если нет - закрывает
    окно через resolveNopeWindow.
    */
    private fun handleNopeWindow(game: Game) {
        printLine("Ход ждёт отмены. Есть ли желающие сыграть NOPE?")
        val answer = readLine("Сыграть NOPE? (y/n)")

        if (!answer.equals("y", ignoreCase = true)) {
            handleResult(gameplay.resolveNopeWindow(game.id), game)
            return
        }

        val name = readLine("Имя игрока, играющего NOPE")
        val player = game.players.firstOrNull { it.name == name && it.isAlive }
        if (player == null) {
            printLine("Игрок не найден или выбыл.")
            return
        }

        val nopeCard = player.findCardsOfType(CardType.NOPE).firstOrNull()
        if (nopeCard == null) {
            printLine("У игрока нет карты NOPE.")
            return
        }

        val nopeMove = Move(
            0, game.turnsPlayed + 1, MoveType.PLAY_CARD,
            author = player, cardsPlayed = listOf(nopeCard)
        )
        handleResult(gameplay.playMove(game.id, nopeMove), game)
    }
    
    private fun handleAwaitingResponse(game: Game, responderId: Int) {
        val responder = game.players.firstOrNull { it.id == responderId }
        printLine("Ход ждёт ответа от ${responder?.name ?: "игрока $responderId"}.")
        printLine("(В упрощённой консольной версии ответ передаётся автоматически.)")
        handleResult(gameplay.resolveNopeWindow(game.id), game)
    }
}
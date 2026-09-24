package ui.console

import app.dto.ValidationResult
import app.service.GameplayService
import app.service.PlayerRegistryService
import domain.Card
import domain.CardType
import domain.Game
import domain.GameState
import domain.Move
import domain.MoveType
import domain.Player

class ConsoleGameScreen(
    private val gameplay: GameplayService,
    private val registry: PlayerRegistryService
) : ConsoleBaseScreen() {

    private var activeGameId: Int? = null

    fun start() {
        while (true) {
            printMenu()
            when (readInt("Выбор")) {
                1 -> registerPlayer()
                2 -> showPlayers()
                3 -> startNewGame()
                4 -> makeMove()
                5 -> showGameState()
                6 -> endGame()
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
        printLine("4. Сделать ход")
        printLine("5. Показать состояние партии")
        printLine("6. Завершить партию")
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
        if (names.isEmpty()) printLine("Список пуст.")
        else printLine("Игроки: ${names.joinToString(", ")}")
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
            printLine("Партия #$id создана.")
            showGameState()
        } catch (e: IllegalArgumentException) {
            printLine("Ошибка: ${e.message}")
        }
    }

    private fun endGame() {
        val id = activeGameId ?: run { printLine("Активной партии нет."); return }
        gameplay.endGame(id)
        printLine("Партия #$id завершена.")
        activeGameId = null
    }

    private fun makeMove() {
        val game = activeGame() ?: return
        if (game.state == GameState.FINISHED) {
            printLine("Партия завершена.")
            return
        }

        printSeparator()
        printLine("Ход игрока: ${game.getCurrentPlayer().name}")
        printLine("1. Взять карту (DRAW)")
        printLine("2. Сыграть карту")
        printLine("3. Сыграть комбинацию")
        printLine("0. Назад")

        when (readInt("Выбор")) {
            1 -> drawCard(game)
            2 -> playCard(game)
            3 -> playCombination(game)
            0 -> return
            else -> printLine("Неизвестная команда.")
        }
    }

    private fun drawCard(game: Game) {
        if (game.pendingKitten != null) {
            printLine("⚠ Вытянут котёнок. Сначала сыграйте DEFUSE.")
            return
        }
        val current = game.getCurrentPlayer()
        val move = Move(0, game.turnsPlayed + 1, MoveType.DRAW, author = current)
        handleResult(gameplay.playMove(game.id, move), game)
    }

    private fun playCard(game: Game) {
        val current = game.getCurrentPlayer()
        if (current.hand.isEmpty()) {
            printLine("Рука пуста.")
            return
        }
        printHand(current)
        val index = readInt("Номер карты (0 — отмена)") ?: return
        if (index == 0) return
        val card = current.hand.getOrNull(index - 1) ?: run {
            printLine("Неверный номер.")
            return
        }

        when (card.type) {
            CardType.FAVOR -> playFavor(game, card)
            CardType.DEFUSE -> playDefuse(game, card)
            else -> playPlainCard(game, card)
        }
    }

    private fun playPlainCard(game: Game, card: Card) {
        val current = game.getCurrentPlayer()
        val move = Move(
            0, game.turnsPlayed + 1, MoveType.PLAY_CARD,
            author = current, cardsPlayed = listOf(card)
        )
        handleResult(gameplay.playMove(game.id, move), game)
    }

    private fun playFavor(game: Game, card: Card) {
        val current = game.getCurrentPlayer()
        val opponents = game.players.filter { it.isAlive && it.id != current.id }
        if (opponents.isEmpty()) {
            printLine("Нет живых соперников.")
            return
        }
        printLine("Кому сыграть FAVOR?")
        opponents.forEachIndexed { i, p -> printLine("  ${i + 1}. ${p.name}") }
        val idx = readInt("Номер цели (0 — отмена)") ?: return
        if (idx == 0) return
        val target = opponents.getOrNull(idx - 1) ?: run {
            printLine("Неверный номер.")
            return
        }
        val move = Move(
            0, game.turnsPlayed + 1, MoveType.PLAY_CARD,
            author = current, cardsPlayed = listOf(card), target = target
        )
        handleResult(gameplay.playMove(game.id, move), game)
    }

    private fun playDefuse(game: Game, card: Card) {
        val current = game.getCurrentPlayer()
        printLine("Куда положить котёнка? 0 — верх, ${game.deck.size} — низ.")
        val position = readInt("Позиция") ?: return
        val move = Move(
            0, game.turnsPlayed + 1, MoveType.PLAY_CARD,
            author = current, cardsPlayed = listOf(card),
            placedKittenPosition = position
        )
        handleResult(gameplay.playMove(game.id, move), game)
    }

    private fun playCombination(game: Game) {
        val current = game.getCurrentPlayer()
        if (current.hand.isEmpty()) {
            printLine("Рука пуста.")
            return
        }
        printHand(current)
        printLine("Введите номера карт через запятую (например, 1,3):")
        val input = readLine("Карты")
        val ids = input.split(",").mapNotNull { it.trim().toIntOrNull() }
            .mapNotNull { idx -> current.hand.getOrNull(idx - 1)?.id }
        if (ids.isEmpty()) {
            printLine("Не выбрано ни одной карты.")
            return
        }

        val cards = ids.mapNotNull { id -> current.hand.firstOrNull { it.id == id } }
        when (cards.size) {
            2 -> playPair(game, cards)
            3 -> playTriple(game, cards)
            5 -> playFiveDifferent(game, cards)
            else -> printLine("Комбинация из ${cards.size} карт не поддерживается (нужно 2, 3 или 5).")
        }
    }

    private fun playPair(game: Game, cards: List<Card>) {
        val current = game.getCurrentPlayer()
        val opponents = game.players.filter { it.isAlive && it.id != current.id }
        if (opponents.isEmpty()) {
            printLine("Нет живых соперников.")
            return
        }
        printLine("У кого украсть?")
        opponents.forEachIndexed { i, p -> printLine("  ${i + 1}. ${p.name}") }
        val idx = readInt("Номер цели (0 — отмена)") ?: return
        if (idx == 0) return
        val target = opponents.getOrNull(idx - 1) ?: return

        val move = Move(
            0, game.turnsPlayed + 1, MoveType.PLAY_TWO_OF_A_KIND,
            author = current, target = target, cardsPlayed = cards
        )
        handleResult(gameplay.playMove(game.id, move), game)
    }

    private fun playTriple(game: Game, cards: List<Card>) {
        val current = game.getCurrentPlayer()
        val opponents = game.players.filter { it.isAlive && it.id != current.id }
        if (opponents.isEmpty()) {
            printLine("Нет живых соперников.")
            return
        }
        printLine("У кого забрать карту?")
        opponents.forEachIndexed { i, p -> printLine("  ${i + 1}. ${p.name}") }
        val idx = readInt("Номер цели (0 — отмена)") ?: return
        if (idx == 0) return
        val target = opponents.getOrNull(idx - 1) ?: return

        printLine("Какой тип карты запросить?")
        val type = readCardType() ?: return

        val move = Move(
            0, game.turnsPlayed + 1, MoveType.PLAY_THREE_OF_A_KIND,
            author = current, target = target, cardsPlayed = cards,
            requestedCardType = type
        )
        handleResult(gameplay.playMove(game.id, move), game)
    }

    private fun playFiveDifferent(game: Game, cards: List<Card>) {
        val current = game.getCurrentPlayer()
        val discard = game.discardPile.peekAll()
        if (discard.isEmpty()) {
            printLine("Сброс пуст — нечего брать.")
            return
        }
        printLine("Какую карту взять из сброса?")
        discard.forEachIndexed { i, c -> printLine("  ${i + 1}. ${c.type.displayName} (id=${c.id})") }
        val idx = readInt("Номер карты (0 — отмена)") ?: return
        if (idx == 0) return
        val card = discard.getOrNull(idx - 1) ?: return

        val move = Move(
            0, game.turnsPlayed + 1, MoveType.PLAY_FIVE_DIFFERENT,
            author = current, cardsPlayed = cards, receivedCard = card
        )
        handleResult(gameplay.playMove(game.id, move), game)
    }

    private fun handleResult(result: ValidationResult, game: Game) {
        when (result) {
            is ValidationResult.Accepted -> {
                game.lastPeekedCards?.let { peeked ->
                    printLine("Верхние 3 карты колоды:")
                    peeked.forEachIndexed { i, c ->
                        printLine("  ${i + 1}. ${c.type.displayName}")
                    }
                    game.clearLastPeekedCards()
                }
                if (game.isFinished()) {
                    printLine("🏆 Партия завершена. Победитель: ${game.winner?.name ?: "—"}")
                    activeGameId = null
                } else if (game.pendingKitten != null) {
                    printLine("⚠ Вытянут котёнок. Сыграйте DEFUSE.")
                } else {
                    printLine("✓ Ход принят.")
                    printLine("Ход перешёл к: ${game.getCurrentPlayer().name}")
                }
            }

            is ValidationResult.Rejected -> {
                printLine("✗ Ход отклонён: ${result.errors.joinToString("; ")}")
            }

            is ValidationResult.AwaitingNope -> handleNopeWindow(game)

            is ValidationResult.AwaitingResponse -> handleFavorResponse(game, result.responderId)
        }
    }

    /*
    Окно NOPE: спрашивает каждого игрока с NOPE, играет ли он.
    Цепочка продолжается: после NOPE окно открывается снова.
    */
    private fun handleNopeWindow(game: Game) {
        while (true) {
            val pending = game.pendingMove
            if (pending == null) {
                printLine("Окно NOPE закрыто.")
                return
            }
            val desc = buildChainDescription(pending)
            printLine(desc)

            val candidates = game.getAlivePlayers()
                .filter { it.id != pending.author?.id && it.hasCardOfType(CardType.NOPE) }

            printLine("Кто сыграет NOPE?")
            if (candidates.isEmpty()) printLine("  (у всех пусто)")
            candidates.forEachIndexed { i, p -> printLine("  ${i + 1}. ${p.name}") }
            printLine("  0. Никто не играет")

            val idx = readInt("Выбор") ?: return
            if (idx == 0) {
                val resolved = gameplay.resolveNopeWindow(game.id)
                handleResult(resolved, game)
                return
            }
            val player = candidates.getOrNull(idx - 1)
            if (player == null) {
                printLine("Неверный номер.")
                continue
            }
            val nopeCard = player.findCardsOfType(CardType.NOPE).first()
            val nopeMove = Move(
                0, game.turnsPlayed + 1, MoveType.PLAY_CARD,
                author = player, cardsPlayed = listOf(nopeCard),
                cancels = pending
            )
            val r = gameplay.playMove(game.id, nopeMove)
            if (r !is ValidationResult.AwaitingNope) {
                printLine("Ошибка NOPE: ${(r as? ValidationResult.Rejected)?.errors?.joinToString()}")
                return
            }
            // цикл продолжается — снова показываем окно
        }
    }

    private fun buildChainDescription(pending: Move): String {
        val chain = mutableListOf<Move>()
        var cur: Move? = pending
        while (cur != null) { chain.add(cur); cur = cur.cancels }
        return chain.reversed().joinToString(" → ") { m ->
            val author = m.author?.name ?: "?"
            val card = m.cardsPlayed.singleOrNull()?.type?.displayName ?: m.type.name
            "$author сыграл $card"
        }
    }

    /*
    Ответ цели FAVOR: спрашивает, какую карту отдать.
    */
    private fun handleFavorResponse(game: Game, responderId: Int) {
        val pending = game.pendingMove
        if (pending == null) {
            printLine("Нет ожидающего FAVOR.")
            return
        }
        val responder = game.players.firstOrNull { it.id == responderId }
        if (responder == null) {
            printLine("Игрок не найден.")
            return
        }
        if (responder.hand.isEmpty()) {
            printLine("${responder.name}: рука пуста.")
            return
        }
        printLine("${responder.name}, выбери карту для передачи:")
        printHand(responder)
        val idx = readInt("Номер карты") ?: return
        val card = responder.hand.getOrNull(idx - 1) ?: run {
            printLine("Неверный номер.")
            return
        }
        val move = Move(
            0, game.turnsPlayed + 1, MoveType.RESOLVE_PENDING,
            author = responder, cardsPlayed = listOf(card)
        )
        handleResult(gameplay.playMove(game.id, move), game)
    }

    private fun printHand(player: Player) {
        printLine("Рука ${player.name}:")
        player.hand.forEachIndexed { i, c ->
            printLine("  ${i + 1}. ${c.type.displayName} (id=${c.id})")
        }
    }

    private fun readCardType(): CardType? {
        val types = CardType.values().filter { it != CardType.EXPLODING_KITTEN }
        types.forEachIndexed { i, t -> printLine("  ${i + 1}. ${t.displayName}") }
        val idx = readInt("Номер типа (0 — отмена)") ?: return null
        if (idx == 0) return null
        return types.getOrNull(idx - 1)
    }

    private fun showGameState() {
        val game = activeGame() ?: return
        printSeparator()
        printLine("Партия #${game.id} — ${game.state}")
        printLine("Ход №${game.turnsPlayed}, ходит: ${game.getCurrentPlayer().name}")
        if (game.attacksPending > 0) {
            printLine("⚠ Висит атак: ${game.attacksPending}")
        }
        if (game.pendingKitten != null) {
            printLine("⚠ Вытянут взрывной котёнок — нужен DEFUSE")
        }
        printLine("Колода: ${game.deck.size}, сброс: ${game.discardPile.size()}")
        game.players.forEach { p ->
            val status = if (p.isAlive) "жив" else "выбыл"
            val marker = if (p.id == game.getCurrentPlayer().id) " ← ходит" else ""
            printLine("  ${p.name} ($status): ${p.hand.size} карт$marker")
        }
        printSeparator()
    }

    private fun activeGame(): Game? {
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
}
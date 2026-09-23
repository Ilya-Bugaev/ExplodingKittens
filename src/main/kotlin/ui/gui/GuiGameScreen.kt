package ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
Тип запрашиваемой комбинации.
*/
private enum class ComboType { PAIR, TRIPLE, FIVE }

/*
Запрос на комбинацию. Хранит, какие карты выбраны и на каком шаге
уточнения находится пользователь.
targetId == null → ещё не выбрана цель (для пары/тройки).
*/
private data class ComboRequest(
    val type: ComboType,
    val cardIds: Set<Int>,
    val targetId: Int? = null
)

/*
Игровой экран на Compose.

Обрабатывает интерактивные сценарии:
  - новая партия (выбор игроков);
  - FAVOR (выбор цели, затем ответ цели картой);
  - DEFUSE (выбор позиции для возврата котёнка);
  - обычные карты (отправка сразу);
  - комбинации: пара, тройка, пятёрка разных.

Когда партия завершена, показывает результат и кнопку «Новая партия».
*/
@Composable
fun GuiGameScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()

    var showNewGameDialog by remember { mutableStateOf(false) }
    var pendingFavorCardId by remember { mutableStateOf<Int?>(null) }
    var pendingDefuseCardId by remember { mutableStateOf<Int?>(null) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedCards by remember { mutableStateOf(setOf<Int>()) }
    var comboRequest by remember { mutableStateOf<ComboRequest?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(state)
        PlayersList(state)

        when {
            // Партия идёт — игровой режим
            state.gameId != null && !state.isFinished -> {
                GameStatus(state)
                CurrentHand(
                    state = state,
                    selectionMode = selectionMode,
                    selectedCards = selectedCards,
                    onToggleSelection = { id ->
                        selectedCards = if (id in selectedCards) selectedCards - id else selectedCards + id
                    },
                    onPlayCard = { card ->
                        when (card.type) {
                            "FAVOR" -> pendingFavorCardId = card.id
                            "DEFUSE" -> pendingDefuseCardId = card.id
                            else -> viewModel.playCard(card.id)
                        }
                    }
                )

                if (selectionMode) {
                    ComboButtons(
                        selectedCount = selectedCards.size,
                        onCombo = { type -> comboRequest = ComboRequest(type, selectedCards) },
                        onCancel = {
                            selectionMode = false
                            selectedCards = emptySet()
                        }
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.drawCard() }) {
                            Text("Взять карту")
                        }
                        Button(onClick = { selectionMode = true }) {
                            Text("Комбинация")
                        }
                        Button(onClick = { viewModel.endGame() }) {
                            Text("Завершить партию")
                        }
                    }
                }
            }

            // Партия завершена — показываем результат и кнопку новой партии
            state.gameId != null && state.isFinished -> {
                GameStatus(state)
                Button(onClick = { showNewGameDialog = true }) {
                    Text("Новая партия")
                }
            }

            // Партия ещё не начиналась
            else -> {
                Text("Партия не начата.")
                Button(onClick = { showNewGameDialog = true }) {
                    Text("Начать партию")
                }
            }
        }

        state.errorMessage?.let { message ->
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            }
        }
    }

    if (showNewGameDialog) {
        NewGameDialog(
            registeredPlayers = state.registeredPlayers,
            onDismiss = { showNewGameDialog = false },
            onConfirm = { names ->
                if (viewModel.newGame(names)) showNewGameDialog = false
            }
        )
    }

    pendingFavorCardId?.let { cardId ->
        TargetPickerDialog(
            title = "Кому сыграть FAVOR?",
            players = state.players.filter { it.isAlive && !it.isCurrent },
            onDismiss = { pendingFavorCardId = null },
            onPick = { targetId ->
                viewModel.playFavor(cardId, targetId)
                pendingFavorCardId = null
            }
        )
    }

    pendingDefuseCardId?.let { cardId ->
        DefusePositionDialog(
            deckSize = state.deckSize,
            onDismiss = { pendingDefuseCardId = null },
            onConfirm = { position ->
                viewModel.submitDefuse(cardId, position)
                pendingDefuseCardId = null
            }
        )
    }

    state.awaitingFavorResponse?.let { info ->
        FavorResponseDialog(
            responderName = info.responderName,
            hand = info.responderHand,
            onCardSelected = { cardId -> viewModel.submitFavorResponse(cardId) }
        )
    }

    comboRequest?.let { request ->
        when (request.type) {
            ComboType.PAIR -> PairDialog(
                players = state.players.filter { it.isAlive && !it.isCurrent },
                onDismiss = { comboRequest = null },
                onConfirm = { targetId ->
                    viewModel.playTwoOfAKind(request.cardIds.toList(), targetId)
                    comboRequest = null
                    selectionMode = false
                    selectedCards = emptySet()
                }
            )

            ComboType.TRIPLE -> if (request.targetId == null) {
                TripleTargetDialog(
                    players = state.players.filter { it.isAlive && !it.isCurrent },
                    onDismiss = { comboRequest = null },
                    onPick = { targetId ->
                        comboRequest = request.copy(targetId = targetId)
                    }
                )
            } else {
                TripleTypeDialog(
                    onDismiss = { comboRequest = null },
                    onPick = { typeName ->
                        val type = domain.CardType.valueOf(typeName)
                        viewModel.playThreeOfAKind(
                            request.cardIds.toList(),
                            request.targetId!!,
                            type
                        )
                        comboRequest = null
                        selectionMode = false
                        selectedCards = emptySet()
                    }
                )
            }

            ComboType.FIVE -> FivePickDialog(
                discardPile = state.discardPile,
                onDismiss = { comboRequest = null },
                onPick = { cardId ->
                    viewModel.playFiveDifferent(request.cardIds.toList(), cardId)
                    comboRequest = null
                    selectionMode = false
                    selectedCards = emptySet()
                }
            )
        }
    }

    state.awaitingNope?.let { info ->
        NopeDialog(
            description = info.pendingMoveDescription,
            eligible = info.eligiblePlayers,
            onPlay = { playerId, cardId -> viewModel.playNope(playerId, cardId) },
            onDecline = { viewModel.declineNope() }
        )
    }
}

@Composable
private fun Header(state: UiState) {
    Text(
        text = "Exploding Kittens Tracker",
        style = MaterialTheme.typography.headlineSmall
    )
    Text(
        text = "Состояние: ${state.state}",
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun PlayersList(state: UiState) {
    Text("Игроки", style = MaterialTheme.typography.titleMedium)
    if (state.players.isEmpty()) {
        Text("— пока никого —")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.players.forEach { player ->
            val marker = if (player.isCurrent) " ← ходит" else ""
            val status = if (player.isAlive) "жив" else "выбыл"
            Text("${player.name} ($status): ${player.handSize} карт$marker")
        }
    }
}

@Composable
private fun GameStatus(state: UiState) {
    Text(
        text = "Ход №${state.turnNumber} · колода: ${state.deckSize} · сброс: ${state.discardSize}",
        style = MaterialTheme.typography.bodyMedium
    )
    if (state.attacksPending > 0) {
        Text(
            text = "⚠ АТАКА: ${state.currentPlayerName} ходит ещё ${state.attacksPending} раз",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium
        )
    }
    if (state.pendingKitten) {
        Text(
            text = "Вытянут взрывной котёнок — нужен DEFUSE",
            color = MaterialTheme.colorScheme.error
        )
    }
    if (state.isFinished) {
        val text = if (state.winnerName != null)
            "🏆 Победитель: ${state.winnerName}"
        else
            "Партия завершена без победителя"
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CurrentHand(
    state: UiState,
    selectionMode: Boolean,
    selectedCards: Set<Int>,
    onToggleSelection: (Int) -> Unit,
    onPlayCard: (CardView) -> Unit
) {
    Text("Рука ${state.currentPlayerName}", style = MaterialTheme.typography.titleMedium)
    if (state.currentHand.isEmpty()) {
        Text("— рука пуста —")
        return
    }
    LazyColumn(
        modifier = Modifier.height(250.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(state.currentHand) { card ->
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectionMode) {
                        Checkbox(
                            checked = card.id in selectedCards,
                            onCheckedChange = { onToggleSelection(card.id) }
                        )
                    }
                    Text(
                        text = "${card.displayName} (id=${card.id})",
                        modifier = Modifier.weight(1f)
                    )
                    if (!selectionMode) {
                        Button(onClick = { onPlayCard(card) }) {
                            Text("Сыграть")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComboButtons(
    selectedCount: Int,
    onCombo: (ComboType) -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Выбрано: $selectedCount",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onCombo(ComboType.PAIR) },
                enabled = selectedCount == 2
            ) { Text("Пара") }
            Button(
                onClick = { onCombo(ComboType.TRIPLE) },
                enabled = selectedCount == 3
            ) { Text("Тройка") }
            Button(
                onClick = { onCombo(ComboType.FIVE) },
                enabled = selectedCount == 5
            ) { Text("Пятёрка") }
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
    }
}

@Composable
private fun NewGameDialog(
    registeredPlayers: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая партия") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (registeredPlayers.isNotEmpty()) {
                    Text("Известные игроки:")
                    registeredPlayers.forEach { name ->
                        TextButton(onClick = {
                            selected = if (name in selected) selected - name else selected + name
                        }) {
                            Text(if (name in selected) "✓ $name" else "  $name")
                        }
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Новый игрок (через запятую)") },
                    modifier = Modifier.fillMaxWidth()
                )
                val allNames = selected + input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                Text(
                    "Выбрано: ${allNames.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val names = (selected + input.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    .distinct()
                onConfirm(names)
            }) {
                Text("Начать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun TargetPickerDialog(
    title: String,
    players: List<PlayerView>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (players.isEmpty()) {
                    Text("Нет живых соперников.")
                } else {
                    players.forEach { player ->
                        TextButton(onClick = { onPick(player.id) }) {
                            Text(player.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun FavorResponseDialog(
    responderName: String,
    hand: List<CardView>,
    onCardSelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* нельзя закрыть */ },
        title = { Text("$responderName, отдай карту") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hand.isEmpty()) {
                    Text("Рука пуста. Нечего отдать.")
                } else {
                    hand.forEach { card ->
                        TextButton(onClick = { onCardSelected(card.id) }) {
                            Text(card.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun DefusePositionDialog(
    deckSize: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember { mutableStateOf("0") }
    val position = input.toIntOrNull()
    val valid = position != null && position in 0..deckSize

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Куда вернуть котёнка?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("В колоде сейчас $deckSize карт.")
                Text("0 — верх колоды, $deckSize — низ.")
                OutlinedTextField(
                    value = input,
                    onValueChange = { new -> input = new.filter { it.isDigit() } },
                    label = { Text("Позиция") },
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!valid) {
                    Text(
                        "Введите число от 0 до $deckSize",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { position?.let { onConfirm(it) } },
                enabled = valid
            ) { Text("Подтвердить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun PairDialog(
    players: List<PlayerView>,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Украсть случайную карту") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("У кого украсть?")
                players.forEach { player ->
                    TextButton(onClick = { onConfirm(player.id) }) {
                        Text(player.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun TripleTargetDialog(
    players: List<PlayerView>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Шаг 1: у кого забрать карту?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                players.forEach { player ->
                    TextButton(onClick = { onPick(player.id) }) {
                        Text(player.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun TripleTypeDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val types = listOf(
        "DEFUSE", "NOPE", "ATTACK", "SKIP", "FAVOR",
        "SHUFFLE", "SEE_FUTURE",
        "CAT_BEARD", "CAT_TACO", "CAT_HAIRY_POTATO",
        "CAT_CATERMELON", "CAT_RAINBOW_RALPHING"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Шаг 2: какой тип карты запросить?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                types.forEach { name ->
                    TextButton(onClick = { onPick(name) }) {
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun FivePickDialog(
    discardPile: List<CardView>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Какую карту взять из сброса?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (discardPile.isEmpty()) {
                    Text("Сброс пуст.")
                } else {
                    discardPile.forEach { card ->
                        TextButton(onClick = { onPick(card.id) }) {
                            Text("${card.displayName} (id=${card.id})")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/*
Диалог окна Nope: кто-то сыграл карту, нужно спросить всех живых,
не хотят ли они её отменить. Показываем только тех, у кого есть NOPE.
*/
@Composable
private fun NopeDialog(
    description: String,
    eligible: List<EligibleNopePlayer>,
    onPlay: (Int, Int) -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* нельзя закрыть без решения */ },
        title = { Text("Окно NOPE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(description)
                if (eligible.isEmpty()) {
                    Text("Ни у кого нет карты NOPE.")
                } else {
                    Text("Кто сыграет NOPE?")
                    eligible.forEach { player ->
                        TextButton(onClick = { onPlay(player.playerId, player.nopeCardId) }) {
                            Text("NOPE — ${player.playerName}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDecline) { Text("Никто не играет") }
        },
        dismissButton = {}
    )
}
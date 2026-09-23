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
Игровой экран. Отображает UiState, отправляет команды в MainViewModel.
Никакой логики - только UI.
*/
@Composable
fun GuiGameScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    var showNewGameDialog by remember { mutableStateOf(false) }
    var pendingFavorCardId by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(state)
        PlayersList(state)

        if (state.gameId != null) {
            GameStatus(state)
            CurrentHand(state) { card ->
                if (card.type == "FAVOR") {
                    pendingFavorCardId = card.id
                } else {
                    viewModel.playCard(card.id)
                }
            }
            ActionButtons(state, viewModel)
        } else {
            Text("Партия не начата.")
            Button(onClick = { showNewGameDialog = true }) {
                Text("Начать партию")
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
            text = "⚠ Висит атак: ${state.attacksPending}",
            color = MaterialTheme.colorScheme.error
        )
    }
    if (state.pendingKitten) {
        Text(
            text = "⚠ Вытянут взрывной котёнок — нужен DEFUSE",
            color = MaterialTheme.colorScheme.error
        )
    }
    if (state.isFinished) {
        Text(
            text = "Победитель: ${state.winnerName ?: "—"}",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun CurrentHand(
    state: UiState,
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
                    Text(
                        text = "${card.displayName} (id=${card.id})",
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onPlayCard(card) }) {
                        Text("Сыграть")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(state: UiState, viewModel: MainViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.drawCard() }) {
            Text("Взять карту")
        }

        Button(onClick = { viewModel.endGame() }) {
            Text("Завершить партию")
        }
    }
}

/*
Диалог создания партии. Список известных игроков как чекбоксы
плюс поле для нового имени.
*/
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
                Text(
                    "Выбрано: ${(selected + input.split(",").map { it.trim() }.filter { it.isNotEmpty() }).joinToString(", ")}",
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

/*
Универсальный диалог выбора целевого игрока. Используется для FAVOR,
пар и троек.
*/
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
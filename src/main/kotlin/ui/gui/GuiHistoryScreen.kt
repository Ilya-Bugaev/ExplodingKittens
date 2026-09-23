package ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
Экран истории завершённых партий.
*/
@Composable
fun GuiHistoryScreen(viewModel: HistoryViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "История партий",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { viewModel.refresh() }) {
                Text("Обновить")
            }
        }

        if (state.games.isEmpty()) {
            Text("Пока нет завершённых партий.")
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.games) { game ->
                Card {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Партия #${game.gameId}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Игроки: ${game.players}")
                        Text("Победитель: ${game.winner}")
                        Text("Ходов: ${game.turns}")
                    }
                }
            }
        }

        state.errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}
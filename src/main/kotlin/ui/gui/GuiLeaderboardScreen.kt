package ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
Экран таблицы лидеров. Игроки отсортированы по победам,
затем по проценту побед.
*/
@Composable
fun GuiLeaderboardScreen(viewModel: LeaderboardViewModel) {
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
                text = "Лидеры",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { viewModel.refresh() }) {
                Text("Обновить")
            }
        }

        if (state.players.isEmpty()) {
            Text("Пока нет статистики. Сыграйте партию.")
            return@Column
        }

        HeaderRow()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.players) { player ->
                PlayerRow(player)
            }
        }

        state.errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("#", modifier = Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium)
        Text("Имя", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text("Партий", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelMedium)
        Text("Побед", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelMedium)
        Text("Winrate", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlayerRow(player: PlayerStatsView) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("#", modifier = Modifier.width(32.dp))
            Text(player.name, modifier = Modifier.weight(1f))
            Text("${player.gamesPlayed}", modifier = Modifier.width(70.dp))
            Text("${player.wins}", modifier = Modifier.width(60.dp))
            Text(
                "%.0f%%".format(player.winRate * 100),
                modifier = Modifier.width(80.dp)
            )
        }
    }
}
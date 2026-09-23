package ui.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
Оболочка приложения: верхняя панель навигации и текущий экран.
*/
@Composable
fun GuiApp(
    gameViewModel: MainViewModel,
    historyViewModel: HistoryViewModel
) {
    var screen by remember { mutableStateOf(AppScreen.GAME) }

    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            NavButton(
                label = "Игра",
                selected = screen == AppScreen.GAME,
                onClick = { screen = AppScreen.GAME }
            )
            NavButton(
                label = "История",
                selected = screen == AppScreen.HISTORY,
                onClick = {
                    historyViewModel.refresh()
                    screen = AppScreen.HISTORY
                }
            )
        }

        when (screen) {
            AppScreen.GAME -> GuiGameScreen(gameViewModel)
            AppScreen.HISTORY -> GuiHistoryScreen(historyViewModel)
        }
    }
}

@Composable
private fun NavButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = if (selected)
            ButtonDefaults.buttonColors()
        else
            ButtonDefaults.outlinedButtonColors(),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
package app.dto

/*
Краткая сводка о партии для списка в UI и агрегации статистики.
Полные данные о ходах - в GameRecord (storage-слой).
*/
data class GameSummary(
    val gameId: Int,
    val playerNames: List<String>,
    val winnerName: String?,
    val turnsPlayed: Int
)
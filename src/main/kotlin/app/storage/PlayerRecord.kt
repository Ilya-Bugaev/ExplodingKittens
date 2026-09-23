package app.storage

import kotlinx.serialization.Serializable

/*
Плоский DTO игрока для хранения в JSON.
Домен Player содержит поведение (addCard, eliminate) и ссылки на Card —
сериализовать его напрямую нельзя. PlayerRecord хранит только то,
что нужно для восстановления между запусками: id и имя.
*/
@Serializable
data class PlayerRecord(
    val id: Int,
    val name: String
)

/*
Контейнер для файла players.json: список игроков и nextId.
nextId хранится явно, чтобы после перезапуска новые игроки
не получали id, конфликтующие с уже сохранёнными.
*/
@Serializable
data class PlayerRegistryData(
    val nextId: Int = 1,
    val players: List<PlayerRecord> = emptyList()
)
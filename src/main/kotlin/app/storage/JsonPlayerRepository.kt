package app.storage

import app.repository.IPlayerRepository
import kotlinx.serialization.json.Json
import java.io.File

/*
Реализация IPlayerRepository с хранением в JSON-файле.
Используется в продакшене, чтобы реестр игроков сохранялся
между запусками приложения.
*/
class JsonPlayerRepository(
    private val file: File
) : IPlayerRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private var data: PlayerRegistryData = load()

    override fun register(name: String): Int {
        val existing = data.players.firstOrNull { it.name == name }
        if (existing != null) return existing.id

        val id = data.nextId
        data = data.copy(
            nextId = id + 1,
            players = data.players + PlayerRecord(id, name)
        )
        save()
        return id
    }

    override fun findIdByName(name: String): Int? =
        data.players.firstOrNull { it.name == name }?.id

    override fun findAll(): List<String> =
        data.players.map { it.name }

    /*
    Загружает состояние из файла. Если файла нет - возвращает
    пустой реестр. Если файл повреждён - тоже возвращает пустой,
    чтобы приложение не падало на старте.
    */
    private fun load(): PlayerRegistryData {
        if (!file.exists()) return PlayerRegistryData()
        return try {
            json.decodeFromString(PlayerRegistryData.serializer(), file.readText())
        } catch (e: Exception) {
            PlayerRegistryData()
        }
    }

    /*
    Сохраняет состояние в файл. Директория создаётся, если её нет.
    */
    private fun save() {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(PlayerRegistryData.serializer(), data))
    }
}
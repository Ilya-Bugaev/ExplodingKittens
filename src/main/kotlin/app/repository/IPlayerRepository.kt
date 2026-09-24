package app.repository

// Хранилище известных игроков
interface IPlayerRepository {
    fun register(name: String): Int
    fun findIdByName(name: String): Int?
    fun findAll(): List<String>
}
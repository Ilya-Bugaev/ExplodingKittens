package app.service

import app.repository.IPlayerRepository

// Реестр игроков
class PlayerRegistryService(
    private val repo: IPlayerRepository
) {
    fun registerPlayer(name: String): Int {
        require(name.isNotBlank()) { "player name must not be blank" }
        return repo.register(name)
    }

    fun findPlayerId(name: String): Int? = repo.findIdByName(name)

    fun getKnownPlayerNames(): List<String> = repo.findAll()
}
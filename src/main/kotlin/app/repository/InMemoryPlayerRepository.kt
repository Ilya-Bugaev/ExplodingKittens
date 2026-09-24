package app.repository

/*
Простая реализация репозитория игроков в памяти
*/
class InMemoryPlayerRepository : IPlayerRepository {

    private val nameToId: MutableMap<String, Int> = mutableMapOf()
    private var nextId: Int = 1

    override fun register(name: String): Int {
        nameToId[name]?.let { return it }
        val id = nextId++
        nameToId[name] = id
        return id
    }

    override fun findIdByName(name: String): Int? = nameToId[name]

    override fun findAll(): List<String> = nameToId.keys.toList()
}
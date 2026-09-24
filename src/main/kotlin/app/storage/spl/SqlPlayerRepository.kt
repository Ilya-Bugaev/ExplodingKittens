package app.storage.sql

import app.repository.IPlayerRepository

/*
SQLite-реализация IPlayerRepository.
Таблица players: id (auto-increment), name (unique).
*/
class SqlPlayerRepository(
    private val db: Database
) : IPlayerRepository {

    /*
    Регистрирует игрока. Если имя уже есть — возвращает
    существующий id.
    */
    override fun register(name: String): Int {
        val existing = findIdByName(name)
        if (existing != null) return existing

        return db.withConnection { conn ->
            conn.prepareStatement(
                "INSERT INTO players(name) VALUES (?)",
                java.sql.Statement.RETURN_GENERATED_KEYS
            ).use { st ->
                st.setString(1, name)
                st.executeUpdate()
                st.generatedKeys.use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
    }

    override fun findIdByName(name: String): Int? =
        db.withConnection { conn ->
            conn.prepareStatement("SELECT id FROM players WHERE name = ?").use { st ->
                st.setString(1, name)
                st.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt("id") else null
                }
            }
        }

    override fun findAll(): List<String> =
        db.withConnection { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT name FROM players ORDER BY id").use { rs ->
                    buildList {
                        while (rs.next()) add(rs.getString("name"))
                    }
                }
            }
        }
}
package app.storage.sql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DatabaseTest {

    @TempDir
    lateinit var tempDir: File

    // In-memory БД создаётся и открывается без ошибок.
    @Test
    fun `in-memory database opens successfully`() {
        val db = Database.inMemory()
        db.use {
            assertNotNull(it.withConnection { conn -> conn })
        }
    }

    // Файловая БД создаёт файл при открытии.
    @Test
    fun `file database creates file`() {
        val file = File(tempDir, "test.db")
        assertTrue(!file.exists())

        Database.open(file).use { }

        assertTrue(file.exists())
    }

    // Файловая БД создаёт вложенную директорию при необходимости.
    @Test
    fun `file database creates parent directories`() {
        val file = File(tempDir, "nested/dir/test.db")

        Database.open(file).use { }

        assertTrue(file.exists())
    }

    // Схема содержит все нужные таблицы.
    @Test
    fun `schema contains all tables`() {
        val db = Database.inMemory()
        db.use {
            val tables = db.withConnection { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table'"
                    ).use { rs ->
                        buildList {
                            while (rs.next()) add(rs.getString(1))
                        }
                    }
                }
            }
            assertTrue(tables.contains("players"))
            assertTrue(tables.contains("games"))
            assertTrue(tables.contains("game_players"))
            assertTrue(tables.contains("moves"))
            assertTrue(tables.contains("player_stats"))
        }
    }

    // Повторное открытие той же БД не падает.
    @Test
    fun `reopening database does not fail`() {
        val file = File(tempDir, "test.db")

        Database.open(file).use { }
        Database.open(file).use { }
    }

    // Пустая БД: во всех таблицах 0 записей.
    @Test
    fun `new database has empty tables`() {
        val db = Database.inMemory()
        db.use {
            val count = db.withConnection { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT COUNT(*) FROM players").use { rs ->
                        rs.next()
                        rs.getInt(1)
                    }
                }
            }
            assertEquals(0, count)
        }
    }
}
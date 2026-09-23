package app.storage.sql

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SqlPlayerRepositoryTest {

    private lateinit var db: Database
    private lateinit var repo: SqlPlayerRepository

    @BeforeEach
    fun setUp() {
        db = Database.inMemory()
        repo = SqlPlayerRepository(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    // Новый репозиторий пуст.
    @Test
    fun `empty repository returns empty list`() {
        assertTrue(repo.findAll().isEmpty())
    }

    // Регистрация возвращает id, начиная с 1.
    @Test
    fun `register returns sequential ids`() {
        assertEquals(1, repo.register("Аня"))
        assertEquals(2, repo.register("Боря"))
    }

    // Повторная регистрация того же имени возвращает тот же id.
    @Test
    fun `register same name twice returns same id`() {
        val first = repo.register("Аня")
        val second = repo.register("Аня")
        assertEquals(first, second)
        assertEquals(1, repo.findAll().size)
    }

    // findIdByName находит зарегистрированного игрока.
    @Test
    fun `findIdByName returns id for known player`() {
        val id = repo.register("Аня")
        assertEquals(id, repo.findIdByName("Аня"))
    }

    // findIdByName возвращает null для неизвестного.
    @Test
    fun `findIdByName returns null for unknown`() {
        assertNull(repo.findIdByName("Незнакомец"))
    }

    // findAll возвращает имена в порядке регистрации.
    @Test
    fun `findAll returns names in insertion order`() {
        repo.register("Аня")
        repo.register("Боря")
        repo.register("Ваня")
        assertEquals(listOf("Аня", "Боря", "Ваня"), repo.findAll())
    }

    /* Данные сохраняются между двумя экземплярами репозитория
    на одной и той же БД.
    */
    @Test
    fun `data persists between repository instances on same db`() {
        repo.register("Аня")
        val repo2 = SqlPlayerRepository(db)
        assertTrue(repo2.findAll().contains("Аня"))
    }
}
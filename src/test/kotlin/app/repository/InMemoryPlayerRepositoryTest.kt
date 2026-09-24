package app.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryPlayerRepositoryTest {

    // Новый репозиторий пуст.
    @Test
    fun `new repository is empty`() {
        val repo = InMemoryPlayerRepository()
        assertTrue(repo.findAll().isEmpty())
    }

    // Регистрация выдаёт последовательные id.
    @Test
    fun `register assigns sequential ids`() {
        val repo = InMemoryPlayerRepository()
        assertEquals(1, repo.register("Аня"))
        assertEquals(2, repo.register("Боря"))
    }

    // Повторная регистрация возвращает тот же id.
    @Test
    fun `register same name twice returns same id`() {
        val repo = InMemoryPlayerRepository()
        val first = repo.register("Аня")
        val second = repo.register("Аня")
        assertEquals(first, second)
    }

    // findIdByName находит зарегистрированного игрока.
    @Test
    fun `findIdByName returns id for known player`() {
        val repo = InMemoryPlayerRepository()
        val id = repo.register("Аня")
        assertEquals(id, repo.findIdByName("Аня"))
    }

    // findIdByName возвращает null для неизвестного имени.
    @Test
    fun `findIdByName returns null for unknown`() {
        val repo = InMemoryPlayerRepository()
        assertNull(repo.findIdByName("Неизвестный"))
    }
}
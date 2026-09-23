package app.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JsonPlayerRepositoryTest {

    // JUnit 5 создаёт временную директорию для каждого теста и удаляет после.
    @TempDir
    lateinit var tempDir: File

    private fun repo(): JsonPlayerRepository =
        JsonPlayerRepository(File(tempDir, "players.json"))

    // Новый репозиторий на пустом файле - пустой.
    @Test
    fun `empty file yields empty repository`() {
        val repo = repo()
        assertTrue(repo.findAll().isEmpty())
    }

    // Регистрация возвращает id, начиная с 1.
    @Test
    fun `register returns sequential ids`() {
        val repo = repo()
        assertEquals(1, repo.register("Аня"))
        assertEquals(2, repo.register("Боря"))
    }

    // Повторная регистрация того же имени возвращает тот же id.
    @Test
    fun `register same name returns same id`() {
        val repo = repo()
        val first = repo.register("Аня")
        val second = repo.register("Аня")
        assertEquals(first, second)
        assertEquals(1, repo.findAll().size)
    }

    // findIdByName находит игрока.
    @Test
    fun `findIdByName returns id`() {
        val repo = repo()
        val id = repo.register("Аня")
        assertEquals(id, repo.findIdByName("Аня"))
    }

    // findIdByName возвращает null для неизвестного.
    @Test
    fun `findIdByName returns null for unknown`() {
        val repo = repo()
        assertNull(repo.findIdByName("Незнакомец"))
    }

    // Данные сохраняются между двумя экземплярами репозитория
    // (эмулирует перезапуск приложения).
    @Test
    fun `data persists between repository instances`() {
        val file = File(tempDir, "players.json")
        val repo1 = JsonPlayerRepository(file)
        repo1.register("Аня")
        repo1.register("Боря")

        val repo2 = JsonPlayerRepository(file)

        assertEquals(setOf("Аня", "Боря"), repo2.findAll().toSet())
    }

    // nextId сохраняется: новые игроки получают новые id после перезапуска.
    @Test
    fun `nextId persists between restarts`() {
        val file = File(tempDir, "players.json")
        JsonPlayerRepository(file).apply {
            register("Аня")
            register("Боря")
        }

        val repo2 = JsonPlayerRepository(file)
        val newId = repo2.register("Ваня")

        assertEquals(3, newId)
    }

    // Повреждённый файл не роняет приложение - репозиторий пустой.
    @Test
    fun `corrupted file yields empty repository`() {
        val file = File(tempDir, "players.json")
        file.writeText("{ это не JSON")

        val repo = JsonPlayerRepository(file)

        assertTrue(repo.findAll().isEmpty())
        // при этом новую запись можно добавить - файл перезапишется
        assertEquals(1, repo.register("Аня"))
    }
}
package app.service

import app.repository.InMemoryPlayerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PlayerRegistryServiceTest {

    // Регистрация возвращает id.
    @Test
    fun `registerPlayer returns id`() {
        val service = PlayerRegistryService(InMemoryPlayerRepository())
        assertEquals(1, service.registerPlayer("Аня"))
    }

    // Пустое имя отклоняется.
    @Test
    fun `registerPlayer with blank name throws`() {
        val service = PlayerRegistryService(InMemoryPlayerRepository())
        assertThrows(IllegalArgumentException::class.java) {
            service.registerPlayer("   ")
        }
    }

    // getKnownPlayerNames возвращает список имён.
    @Test
    fun `getKnownPlayerNames returns registered names`() {
        val service = PlayerRegistryService(InMemoryPlayerRepository())
        service.registerPlayer("Аня")
        service.registerPlayer("Боря")
        assertEquals(setOf("Аня", "Боря"), service.getKnownPlayerNames().toSet())
    }

    // findPlayerId возвращает id или null.
    @Test
    fun `findPlayerId returns id or null`() {
        val service = PlayerRegistryService(InMemoryPlayerRepository())
        val id = service.registerPlayer("Аня")
        assertEquals(id, service.findPlayerId("Аня"))
        assertEquals(null, service.findPlayerId("Неизвестный"))
    }
}
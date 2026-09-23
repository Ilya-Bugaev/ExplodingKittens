package app.storage.sql

import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class Database(private val connection: Connection) : Closeable {

    init {
        connection.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
        }
        createSchema()
    }

    /*
    Выполняет блок с доступом к соединению.
    */
    fun <T> withConnection(block: (Connection) -> T): T = block(connection)

    override fun close() {
        connection.close()
    }

    private fun createSchema() {
        connection.createStatement().use { st ->
            st.executeUpdate(PLAYERS_TABLE)
            st.executeUpdate(GAMES_TABLE)
            st.executeUpdate(GAME_PLAYERS_TABLE)
            st.executeUpdate(MOVES_TABLE)
            st.executeUpdate(PLAYER_STATS_TABLE)
            st.executeUpdate(MOVES_GAME_INDEX)
            st.executeUpdate(GAME_PLAYERS_GAME_INDEX)
        }
    }

    companion object {
        /*
        Открывает файловую БД. Директория создаётся автоматически.
        */
        fun open(file: File): Database {
            file.parentFile?.mkdirs()
            val conn = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
            return Database(conn)
        }

        /*
        Открывает in-memory БД. Используется в тестах — быстро,
        изолированно, без временных файлов.
        */
        fun inMemory(): Database {
            val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
            return Database(conn)
        }
    }
}

private const val PLAYERS_TABLE = """
    CREATE TABLE IF NOT EXISTS players (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE
    )
"""

private const val GAMES_TABLE = """
    CREATE TABLE IF NOT EXISTS games (
        id INTEGER PRIMARY KEY,
        winner_name TEXT,
        turns_played INTEGER NOT NULL DEFAULT 0
    )
"""

private const val GAME_PLAYERS_TABLE = """
    CREATE TABLE IF NOT EXISTS game_players (
        game_id INTEGER NOT NULL,
        player_id INTEGER NOT NULL,
        player_name TEXT NOT NULL,
        PRIMARY KEY (game_id, player_id),
        FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
    )
"""

private const val MOVES_TABLE = """
    CREATE TABLE IF NOT EXISTS moves (
        row_id INTEGER PRIMARY KEY AUTOINCREMENT,
        game_id INTEGER NOT NULL,
        move_number INTEGER NOT NULL,
        turn_number INTEGER NOT NULL,
        type TEXT NOT NULL,
        author_name TEXT,
        target_name TEXT,
        cards_played_json TEXT NOT NULL DEFAULT '[]',
        drawn_card_json TEXT,
        received_card_json TEXT,
        requested_card_type TEXT,
        placed_kitten_position INTEGER,
        eliminated INTEGER NOT NULL DEFAULT 0,
        initial_hands_json TEXT,
        initial_deck_order_json TEXT,
        FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
    )
"""

private const val PLAYER_STATS_TABLE = """
    CREATE TABLE IF NOT EXISTS player_stats (
        player_id INTEGER PRIMARY KEY,
        name TEXT NOT NULL,
        wins INTEGER NOT NULL DEFAULT 0,
        games_played INTEGER NOT NULL DEFAULT 0,
        win_rate REAL NOT NULL DEFAULT 0.0,
        rating REAL NOT NULL DEFAULT 0.0
    )
"""

private const val MOVES_GAME_INDEX =
    "CREATE INDEX IF NOT EXISTS idx_moves_game ON moves(game_id)"

private const val GAME_PLAYERS_GAME_INDEX =
    "CREATE INDEX IF NOT EXISTS idx_game_players_game ON game_players(game_id)"
package app.storage.sql

import app.dto.GameSummary
import app.repository.IHistoryRepository
import app.storage.CardRecord
import app.storage.GameRecord
import app.storage.MoveRecord
import app.storage.toRecord
import app.storage.toSummary
import domain.Game
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.Statement

/*
SQLite-реализация IHistoryRepository.

Хранит партии в трёх таблицах:
  - games — метаданные партии (id, победитель, число ходов);
  - game_players — участники (порядок сохраняется через rowid);
  - moves — все ходы, вложенные карты сериализуются в JSON.

saveGame выполняется в одной транзакции: сначала DELETE старых
записей (для случая повторного сохранения той же партии), затем
INSERT заново. Это идемпотентно: повторный saveGame не дублирует.
*/
class SqlHistoryRepository(
    private val db: Database
) : IHistoryRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun saveGame(game: Game) {
        val record = game.toRecord()
        db.withConnection { conn ->
            conn.autoCommit = false
            try {
                deleteGame(conn, record.gameId)
                insertGame(conn, record)
                insertPlayers(conn, record)
                insertMoves(conn, record)
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    override fun getRecord(gameId: Int): GameRecord? =
        db.withConnection { conn ->
            val gameRow = loadGameRow(conn, gameId) ?: return@withConnection null
            loadGameRecord(conn, gameRow)
        }

    override fun getAllRecords(): List<GameRecord> =
        db.withConnection { conn ->
            val games = loadAllGameRows(conn)
            games.map { loadGameRecord(conn, it) }
        }

    override fun getAllFinishedGames(): List<GameSummary> =
        getAllRecords().map { it.toSummary() }

    // ============ Запись ============

    private fun deleteGame(conn: Connection, gameId: Int) {
        conn.prepareStatement("DELETE FROM games WHERE id = ?").use { st ->
            st.setInt(1, gameId)
            st.executeUpdate()
        }
    }

    private fun insertGame(conn: Connection, record: GameRecord) {
        conn.prepareStatement(
            "INSERT INTO games(id, winner_name, turns_played) VALUES (?, ?, ?)"
        ).use { st ->
            st.setInt(1, record.gameId)
            if (record.winnerName != null) st.setString(2, record.winnerName) else st.setNull(2, java.sql.Types.VARCHAR)
            st.setInt(3, record.turnsPlayed)
            st.executeUpdate()
        }
    }

    private fun insertPlayers(conn: Connection, record: GameRecord) {
        conn.prepareStatement(
            "INSERT INTO game_players(game_id, player_id, player_name) VALUES (?, ?, ?)"
        ).use { st ->
            record.playerNames.forEachIndexed { index, name ->
                st.setInt(1, record.gameId)
                st.setInt(2, index)
                st.setString(3, name)
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    private fun insertMoves(conn: Connection, record: GameRecord) {
        conn.prepareStatement(
            """
            INSERT INTO moves(
                game_id, move_number, turn_number, type,
                author_name, target_name,
                cards_played_json, drawn_card_json, received_card_json,
                requested_card_type, placed_kitten_position, eliminated,
                initial_hands_json, initial_deck_order_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { st ->
            record.moves.forEachIndexed { index, move ->
                st.setInt(1, record.gameId)
                st.setInt(2, index)
                st.setInt(3, move.turnNumber)
                st.setString(4, move.type)
                setNullableString(st, 5, move.authorName)
                setNullableString(st, 6, move.targetName)
                st.setString(7, json.encodeToString(ListSerializer(CardRecord.serializer()), move.cardsPlayed))
                setNullableString(st, 8, move.drawnCard?.let { json.encodeToString(CardRecord.serializer(), it) })
                setNullableString(st, 9, move.receivedCard?.let { json.encodeToString(CardRecord.serializer(), it) })
                setNullableString(st, 10, move.requestedCardType)
                if (move.placedKittenPosition != null) st.setInt(11, move.placedKittenPosition) else st.setNull(11, java.sql.Types.INTEGER)
                st.setInt(12, if (move.eliminated) 1 else 0)
                setNullableString(st, 13, move.initialHands?.let {
                    json.encodeToString(MapSerializer(Int.serializer(), ListSerializer(CardRecord.serializer())), it)
                })
                setNullableString(st, 14, move.initialDeckOrder?.let {
                    json.encodeToString(ListSerializer(CardRecord.serializer()), it)
                })
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    private fun setNullableString(st: java.sql.PreparedStatement, index: Int, value: String?) {
        if (value != null) st.setString(index, value) else st.setNull(index, java.sql.Types.VARCHAR)
    }

    // ============ Чтение ============

    private data class GameRow(
        val id: Int,
        val winnerName: String?,
        val turnsPlayed: Int
    )

    private data class MoveRow(
        val moveNumber: Int,
        val turnNumber: Int,
        val type: String,
        val authorName: String?,
        val targetName: String?,
        val cardsPlayedJson: String,
        val drawnCardJson: String?,
        val receivedCardJson: String?,
        val requestedCardType: String?,
        val placedKittenPosition: Int?,
        val eliminated: Boolean,
        val initialHandsJson: String?,
        val initialDeckOrderJson: String?
    )

    private fun loadGameRow(conn: Connection, gameId: Int): GameRow? =
        conn.prepareStatement(
            "SELECT id, winner_name, turns_played FROM games WHERE id = ?"
        ).use { st ->
            st.setInt(1, gameId)
            st.executeQuery().use { rs ->
                if (rs.next()) {
                    GameRow(
                        id = rs.getInt("id"),
                        winnerName = rs.getString("winner_name"),
                        turnsPlayed = rs.getInt("turns_played")
                    )
                } else null
            }
        }

    private fun loadAllGameRows(conn: Connection): List<GameRow> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT id, winner_name, turns_played FROM games ORDER BY id").use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            GameRow(
                                id = rs.getInt("id"),
                                winnerName = rs.getString("winner_name"),
                                turnsPlayed = rs.getInt("turns_played")
                            )
                        )
                    }
                }
            }
        }

    private fun loadGameRecord(conn: Connection, game: GameRow): GameRecord {
        val players = loadPlayers(conn, game.id)
        val moves = loadMoves(conn, game.id)
        return GameRecord(
            gameId = game.id,
            playerNames = players,
            winnerName = game.winnerName,
            turnsPlayed = game.turnsPlayed,
            moves = moves
        )
    }

    private fun loadPlayers(conn: Connection, gameId: Int): List<String> =
        conn.prepareStatement(
            "SELECT player_name FROM game_players WHERE game_id = ? ORDER BY player_id"
        ).use { st ->
            st.setInt(1, gameId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(rs.getString("player_name"))
                }
            }
        }

    private fun loadMoves(conn: Connection, gameId: Int): List<MoveRecord> =
        conn.prepareStatement(
            """
            SELECT move_number, turn_number, type, author_name, target_name,
                   cards_played_json, drawn_card_json, received_card_json,
                   requested_card_type, placed_kitten_position, eliminated,
                   initial_hands_json, initial_deck_order_json
            FROM moves WHERE game_id = ? ORDER BY move_number
            """.trimIndent()
        ).use { st ->
            st.setInt(1, gameId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            MoveRecord(
                                id = rs.getInt("move_number") + 1,
                                turnNumber = rs.getInt("turn_number"),
                                type = rs.getString("type"),
                                authorName = rs.getString("author_name"),
                                targetName = rs.getString("target_name"),
                                cardsPlayed = json.decodeFromString(
                                    ListSerializer(CardRecord.serializer()),
                                    rs.getString("cards_played_json")
                                ),
                                drawnCard = rs.getString("drawn_card_json")?.let {
                                    json.decodeFromString(CardRecord.serializer(), it)
                                },
                                receivedCard = rs.getString("received_card_json")?.let {
                                    json.decodeFromString(CardRecord.serializer(), it)
                                },
                                requestedCardType = rs.getString("requested_card_type"),
                                placedKittenPosition = rs.getInt("placed_kitten_position").takeIf { !rs.wasNull() },
                                eliminated = rs.getInt("eliminated") == 1,
                                initialHands = rs.getString("initial_hands_json")?.let {
                                    json.decodeFromString(
                                        MapSerializer(Int.serializer(), ListSerializer(CardRecord.serializer())),
                                        it
                                    )
                                },
                                initialDeckOrder = rs.getString("initial_deck_order_json")?.let {
                                    json.decodeFromString(ListSerializer(CardRecord.serializer()), it)
                                }
                            )
                        )
                    }
                }
            }
        }
}
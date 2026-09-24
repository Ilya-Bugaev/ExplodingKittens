package app.storage.sql

import app.dto.PlayerStats
import app.repository.IStatisticsRepository

class SqlStatisticsRepository(
    private val db: Database
) : IStatisticsRepository {

    override fun getPlayerStats(playerId: Int): PlayerStats? =
        db.withConnection { conn ->
            conn.prepareStatement(
                "SELECT player_id, name, wins, games_played, win_rate, rating " +
                        "FROM player_stats WHERE player_id = ?"
            ).use { st ->
                st.setInt(1, playerId)
                st.executeQuery().use { rs ->
                    if (rs.next()) {
                        PlayerStats(
                            playerId = rs.getInt("player_id"),
                            name = rs.getString("name"),
                            wins = rs.getInt("wins"),
                            gamesPlayed = rs.getInt("games_played"),
                            winRate = rs.getDouble("win_rate"),
                            rating = rs.getDouble("rating")
                        )
                    } else null
                }
            }
        }

    override fun getAllPlayerStats(): List<PlayerStats> =
        db.withConnection { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT player_id, name, wins, games_played, win_rate, rating " +
                            "FROM player_stats ORDER BY player_id"
                ).use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                PlayerStats(
                                    playerId = rs.getInt("player_id"),
                                    name = rs.getString("name"),
                                    wins = rs.getInt("wins"),
                                    gamesPlayed = rs.getInt("games_played"),
                                    winRate = rs.getDouble("win_rate"),
                                    rating = rs.getDouble("rating")
                                )
                            )
                        }
                    }
                }
            }
        }

    override fun updateStats(stats: PlayerStats) {
        db.withConnection { conn ->
            conn.prepareStatement(
                """
                INSERT INTO player_stats(player_id, name, wins, games_played, win_rate, rating)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET
                    name = excluded.name,
                    wins = excluded.wins,
                    games_played = excluded.games_played,
                    win_rate = excluded.win_rate,
                    rating = excluded.rating
                """.trimIndent()
            ).use { st ->
                st.setInt(1, stats.playerId)
                st.setString(2, stats.name)
                st.setInt(3, stats.wins)
                st.setInt(4, stats.gamesPlayed)
                st.setDouble(5, stats.winRate)
                st.setDouble(6, stats.rating)
                st.executeUpdate()
            }
        }
    }
}
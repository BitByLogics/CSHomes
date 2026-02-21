package net.bitbylogic.cshomes.database.impl

import com.github.benmanes.caffeine.cache.Caffeine
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.bitbylogic.cshomes.CSHomes
import net.bitbylogic.cshomes.database.Database
import net.bitbylogic.cshomes.home.PlayerHome
import java.io.File
import java.sql.Connection
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level


class SQLiteDatabase(val plugin: CSHomes) : Database {

    companion object {
        const val CREATE_PLAYER_HOMES_TABLE = """
            CREATE TABLE IF NOT EXISTS player_homes (
                player_id TEXT NOT NULL,
                name TEXT NOT NULL,
                server_name TEXT NOT NULL,
                world TEXT NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                z REAL NOT NULL,
                created_at INTEGER NOT NULL,
                last_used INTEGER NOT NULL,
                PRIMARY KEY (player_id, name)
            );
        """

        const val CREATE_SERVER_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_player_homes_server ON player_homes(server_name);"

        const val GET_HOME_NAMES = "SELECT name FROM player_homes WHERE player_id = ?;"

        const val SELECT_HOME = "SELECT * FROM player_homes WHERE player_id = ? AND name = ?;"
        const val SAVE_HOME = "INSERT OR REPLACE INTO player_homes(player_id, name, server_name, world, x, y, z, created_at, last_used) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);"
        const val DELETE_HOME = "DELETE FROM player_homes WHERE player_id = ? AND name = ?;"
    }

    private val homeCache = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<UUID, MutableList<PlayerHome>>()

    private val homeNameCache = Caffeine.newBuilder()
        .expireAfterAccess(30, TimeUnit.SECONDS)
        .build<UUID, List<String>>()

    private lateinit var dataSource: HikariDataSource
    private val dbExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun connect() {
        try {
            val databaseFile = File(plugin.dataFolder, "database.db")
            databaseFile.parentFile?.mkdirs()
            if (!databaseFile.exists()) databaseFile.createNewFile()

            val config = HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"
                maximumPoolSize = 1
                connectionTimeout = 60_000
                maxLifetime = 30 * 60_000
                leakDetectionThreshold = 60_000
                connectionInitSql = """
                    PRAGMA journal_mode=WAL;
                    PRAGMA synchronous=NORMAL;
                    PRAGMA foreign_keys=ON;
                    PRAGMA busy_timeout=10000;
                """.trimIndent()
            }

            dataSource = HikariDataSource(config)

            dataSource.connection.use { conn ->
                conn.createStatement().use { it.executeUpdate(CREATE_PLAYER_HOMES_TABLE) }
                conn.createStatement().use { it.executeUpdate(CREATE_SERVER_INDEX) }
            }

            plugin.logger.info("Successfully connected to SQLite database!")
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Error connecting to SQLite database", e)
        }
    }

    override fun disconnect() {
        try {
            if (!dataSource.isClosed) {
                dataSource.close()
                dbExecutor.shutdown()
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Error disconnecting from SQLite database", e)
        }
    }

    override fun getConnection(): Connection = dataSource.connection

    override fun findHome(playerId: UUID, name: String): CompletableFuture<PlayerHome?> {
        return CompletableFuture.supplyAsync({
            val cached = homeCache.getIfPresent(playerId)?.find { it.name == name }

            if (cached != null) {
                return@supplyAsync cached
            }

            dataSource.connection.use { connection ->
                connection.prepareStatement(SELECT_HOME).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.setString(2, name)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            val home = PlayerHome(
                                playerId = UUID.fromString(resultSet.getString("player_id")),
                                name = resultSet.getString("name"),
                                serverName = resultSet.getString("server_name"),
                                world = resultSet.getString("world"),
                                x = resultSet.getDouble("x"),
                                y = resultSet.getDouble("y"),
                                z = resultSet.getDouble("z"),
                                createdAt = resultSet.getLong("created_at"),
                                lastUsed = resultSet.getLong("last_used")
                            )

                            updateCache(home)
                            return@supplyAsync home
                        }
                    }
                }
            }

            null
        }, dbExecutor)
    }

    override fun getHomeNames(playerId: UUID): List<String> {
        homeNameCache.getIfPresent(playerId)?.let { cached ->
            return cached
        }

        homeCache.getIfPresent(playerId)?.let { homes ->
            val names = homes.map { it.name }
            homeNameCache.put(playerId, names)
            return names
        }

        dbExecutor.execute {
            try {
                val names = mutableListOf<String>()

                dataSource.connection.use { connection ->
                    connection.prepareStatement(GET_HOME_NAMES).use { statement ->
                        statement.setString(1, playerId.toString())
                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                names.add(resultSet.getString("name"))
                            }
                        }
                    }
                }

                homeNameCache.put(playerId, names)
            } catch (ex: Exception) {
                plugin.logger.severe("Failed to load home names for $playerId: ${ex.message}")
            }
        }

        return emptyList()
    }

    override fun saveHome(home: PlayerHome): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            dataSource.connection.use { connection ->
                connection.prepareStatement(SAVE_HOME).use { statement ->
                    statement.setString(1, home.playerId.toString())
                    statement.setString(2, home.name)
                    statement.setString(3, home.serverName)
                    statement.setString(4, home.world)
                    statement.setDouble(5, home.x)
                    statement.setDouble(6, home.y)
                    statement.setDouble(7, home.z)
                    statement.setLong(8, home.createdAt)
                    statement.setLong(9, home.lastUsed)
                    statement.executeUpdate()
                }
            }

            homeNameCache.getIfPresent(home.playerId)?.let { names ->
                val newNames = names.toMutableList()
                newNames.add(home.name)

                homeNameCache.put(home.playerId, newNames)
            }

            homeCache.getIfPresent(home.playerId)?.let { homes ->
                homes.removeIf { it.name == home.name }
                homes.add(home)

                homeCache.put(home.playerId, homes)
            }
        }, dbExecutor)
    }

    override fun removeHome(home: PlayerHome): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            dataSource.connection.use { connection ->
                connection.prepareStatement(DELETE_HOME).use { statement ->
                    statement.setString(1, home.playerId.toString())
                    statement.setString(2, home.name)
                    statement.executeUpdate()
                }
            }

            homeNameCache.asMap().computeIfPresent(home.playerId) { _, list ->
                list.filter { it != home.name }.toMutableList()
            }

            updateCache(home)
        }, dbExecutor)
    }

    override fun invalidate(playerId: UUID) {
        homeNameCache.invalidate(playerId)
        homeCache.invalidate(playerId)
    }

    private fun updateCache(home: PlayerHome) {
        homeCache.getIfPresent(home.playerId)?.let { homes ->
            homes.removeIf { it.name == home.name }

            if (homes.isEmpty()) {
                homeCache.invalidate(home.playerId)
                return
            }

            homeCache.put(home.playerId, homes)
        }
    }

}
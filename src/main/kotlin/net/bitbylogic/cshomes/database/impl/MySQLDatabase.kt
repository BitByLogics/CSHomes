package net.bitbylogic.cshomes.database.impl

import com.github.benmanes.caffeine.cache.Caffeine
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.bitbylogic.cshomes.CSHomes
import net.bitbylogic.cshomes.database.Database
import net.bitbylogic.cshomes.home.PlayerHome
import java.sql.Connection
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class MySQLDatabase(
    val plugin: CSHomes,
    private val host: String,
    private val port: Int,
    private val database: String,
    private val username: String,
    private val password: String
) : Database {

    companion object {
        const val CREATE_PLAYER_HOMES_TABLE = """
            CREATE TABLE IF NOT EXISTS player_homes (
                player_id VARCHAR(36) NOT NULL,
                name VARCHAR(32) NOT NULL,
                server_name VARCHAR(64) NOT NULL,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                created_at BIGINT NOT NULL,
                last_used BIGINT NOT NULL,
                PRIMARY KEY (player_id, name)
            );
        """

        const val CREATE_SERVER_INDEX = "CREATE INDEX IF NOT EXISTS idx_player_homes_server ON player_homes(server_name);"

        const val GET_HOME_NAMES = "SELECT name FROM player_homes WHERE player_id = ?;"

        const val SELECT_HOME = "SELECT * FROM player_homes WHERE player_id = ? AND name = ?;"
        const val SAVE_HOME = """
            INSERT INTO player_homes(player_id, name, server_name, world, x, y, z, created_at, last_used)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                server_name=VALUES(server_name),
                world=VALUES(world),
                x=VALUES(x),
                y=VALUES(y),
                z=VALUES(z),
                created_at=VALUES(created_at),
                last_used=VALUES(last_used);
        """
        const val DELETE_HOME = "DELETE FROM player_homes WHERE player_id = ? AND name = ?;"
    }

    private val homeCache = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<UUID, MutableList<PlayerHome>>()

    private val homeNameCache = Caffeine.newBuilder()
        .expireAfterAccess(30, TimeUnit.SECONDS)
        .build<UUID, List<String>>()

    private lateinit var dataSource: HikariDataSource
    private val dbExecutor: ExecutorService = Executors.newFixedThreadPool(4)

    override fun connect() {
        try {
            val config = HikariConfig().apply {
                jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&socketTimeout=5000&connectTimeout=5000"
                username = this@MySQLDatabase.username
                password = this@MySQLDatabase.password
                maximumPoolSize = 10
                connectionTimeout = 30_000
                validationTimeout = 5_000
                leakDetectionThreshold = 60_000
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            }

            dataSource = HikariDataSource(config)

            dataSource.connection.use { conn ->
                conn.createStatement().use { it.executeUpdate(CREATE_PLAYER_HOMES_TABLE) }
                conn.createStatement().use { it.executeUpdate(CREATE_SERVER_INDEX) }
            }

            plugin.logger.info("Successfully connected to MySQL database!")
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Error connecting to MySQL database", e)
        }
    }

    override fun disconnect() {
        try {
            if (!dataSource.isClosed) {
                dataSource.close()
                dbExecutor.shutdown()
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Error disconnecting from MySQL database", e)
        }
    }

    override fun getConnection(): Connection = dataSource.connection

    override fun findHome(playerId: UUID, name: String): CompletableFuture<PlayerHome?> {
        return CompletableFuture.supplyAsync({
            val cached = homeCache.getIfPresent(playerId)?.find { it.name == name }
            if (cached != null) return@supplyAsync cached

            dataSource.connection.use { conn ->
                conn.prepareStatement(SELECT_HOME).use { stmt ->
                    stmt.setString(1, playerId.toString())
                    stmt.setString(2, name)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val home = PlayerHome(
                                playerId = UUID.fromString(rs.getString("player_id")),
                                name = rs.getString("name"),
                                serverName = rs.getString("server_name"),
                                world = rs.getString("world"),
                                x = rs.getDouble("x"),
                                y = rs.getDouble("y"),
                                z = rs.getDouble("z"),
                                createdAt = rs.getLong("created_at"),
                                lastUsed = rs.getLong("last_used")
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
                    connection.prepareStatement(SQLiteDatabase.Companion.GET_HOME_NAMES).use { statement ->
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
            dataSource.connection.use { conn ->
                conn.prepareStatement(SAVE_HOME).use { stmt ->
                    stmt.setString(1, home.playerId.toString())
                    stmt.setString(2, home.name)
                    stmt.setString(3, home.serverName)
                    stmt.setString(4, home.world)
                    stmt.setDouble(5, home.x)
                    stmt.setDouble(6, home.y)
                    stmt.setDouble(7, home.z)
                    stmt.setLong(8, home.createdAt)
                    stmt.setLong(9, home.lastUsed)
                    stmt.executeUpdate()
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
            dataSource.connection.use { conn ->
                conn.prepareStatement(DELETE_HOME).use { stmt ->
                    stmt.setString(1, home.playerId.toString())
                    stmt.setString(2, home.name)
                    stmt.executeUpdate()
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
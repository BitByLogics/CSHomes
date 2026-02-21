package net.bitbylogic.cshomes

import net.bitbylogic.cshomes.command.CSHomesCommand
import net.bitbylogic.cshomes.command.DelHomeCommand
import net.bitbylogic.cshomes.command.HomeCommand
import net.bitbylogic.cshomes.command.SetHomeCommand
import net.bitbylogic.cshomes.database.Database
import net.bitbylogic.cshomes.database.impl.MySQLDatabase
import net.bitbylogic.cshomes.database.impl.SQLiteDatabase
import net.bitbylogic.cshomes.listener.PlayerListener
import net.bitbylogic.cshomes.teleport.PendingTeleportManager
import net.bitbylogic.cshomes.teleport.TeleportService
import net.bitbylogic.cshomes.teleport.redis.TeleportConfirmationListener
import net.bitbylogic.cshomes.teleport.redis.TeleportRequestListener
import net.bitbylogic.rps.RedisManager
import net.bitbylogic.rps.client.RedisClient
import org.bukkit.plugin.java.JavaPlugin
import java.util.*


class CSHomes : JavaPlugin() {

    lateinit var serverName: String

    lateinit var database: Database
    lateinit var redisManager: RedisManager
    lateinit var redisClient: RedisClient

    lateinit var pendingTeleportManager: PendingTeleportManager
    lateinit var teleportService: TeleportService

    override fun onEnable() {
        saveDefaultConfig()

        serverName = config.getString("Server-Name", "Server") ?: "Server"

        pendingTeleportManager = PendingTeleportManager(this)

        setupDatabase()
        setupRedis()

        teleportService = TeleportService(pendingTeleportManager)

        val homeCommand = HomeCommand(this)
        val delHomeCommand = DelHomeCommand(this)

        getCommand("home")?.let { command ->
            command.setExecutor(homeCommand)
            command.tabCompleter = homeCommand
        }

        getCommand("delhome")?.let { command ->
            command.setExecutor(delHomeCommand)
            command.tabCompleter = delHomeCommand
        }

        getCommand("cshomes")?.setExecutor(CSHomesCommand(this))
        getCommand("sethome")?.setExecutor(SetHomeCommand(this))

        server.messenger.registerOutgoingPluginChannel(this, "BungeeCord")

        server.pluginManager.registerEvents(PlayerListener(this), this)
    }

    override fun onDisable() {
        cleanup()
    }

    fun reload() {
        reloadConfig()

        cleanup()

        serverName = config.getString("Server-Name", "Server") ?: "Server"

        setupDatabase()
        setupRedis()
    }

    private fun setupDatabase() {
        val type = config.getString("Database-Details.Type", "sqlite") ?: "sqlite"

        this.database = when (type.lowercase(Locale.ROOT)) {
            "sqlite" -> SQLiteDatabase(this)
            "mysql" -> {
                val host = config.getString("Database-Details.Host", "localhost") ?: "localhost"
                val port = config.getInt("Database-Details.Port", 3306)
                val database = config.getString("Database-Details.Database", "cshomes") ?: "cshomes"
                val username = config.getString("Database-Details.Username", "root") ?: "root"
                val password = config.getString("Database-Details.Password", "") ?: ""

                MySQLDatabase(this, host, port, database, username, password)
            }
            else -> SQLiteDatabase(this)
        }

        database.connect()
    }

    private fun setupRedis() {
        val host = config.getString("Redis-Details.Host", "localhost") ?: "localhost"
        val port = config.getInt("Redis-Details.Port", 6379)
        val password = config.getString("Redis-Details.Password", "") ?: ""

        this.redisManager = RedisManager(host, port, password, serverName)
        this.redisClient = redisManager.registerClient(serverName)

        redisClient.registerListener(TeleportRequestListener(this, pendingTeleportManager))
        redisClient.registerListener(TeleportConfirmationListener(this))
    }

    private fun cleanup() {
        database.disconnect()

        if (redisManager.redissonClient != null) {
            redisManager.redissonClient.shutdown()
        }
    }

}

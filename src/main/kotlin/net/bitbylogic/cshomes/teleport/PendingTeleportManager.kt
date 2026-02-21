package net.bitbylogic.cshomes.teleport

import com.github.benmanes.caffeine.cache.Caffeine
import net.bitbylogic.cshomes.CSHomes
import net.bitbylogic.cshomes.home.PlayerHome
import net.bitbylogic.cshomes.util.ServerUtil.Companion.thenAcceptSync
import java.util.*
import java.util.concurrent.TimeUnit

class PendingTeleportManager(val plugin: CSHomes) {

    private val pendingTeleports = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .build<UUID, PlayerHome>()

    fun add(playerId: UUID, homeName: String) {
        plugin.database.findHome(playerId, homeName).thenAcceptSync(plugin) { home ->
            if (home == null) {
                plugin.logger.warning("Cannot fulfill home teleport request for player $playerId as home $homeName was not found.")
                return@thenAcceptSync
            }

            pendingTeleports.put(playerId, home)
        }
    }

    fun pop(playerId: UUID): PlayerHome? = pendingTeleports.getIfPresent(playerId)?.also {
        pendingTeleports.invalidate(playerId)
    }

    fun hasPending(playerId: UUID): Boolean = pendingTeleports.getIfPresent(playerId) != null

}
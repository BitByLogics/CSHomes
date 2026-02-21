package net.bitbylogic.cshomes.database

import net.bitbylogic.cshomes.home.PlayerHome
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface Database {

    fun connect()

    fun disconnect()

    fun getConnection(): Connection

    fun findHome(playerId: UUID, name: String): CompletableFuture<PlayerHome?>

    fun getHomeNames(playerId: UUID): List<String>

    fun saveHome(home: PlayerHome): CompletableFuture<Void>

    fun removeHome(home: PlayerHome): CompletableFuture<Void>

    fun invalidate(playerId: UUID)

}
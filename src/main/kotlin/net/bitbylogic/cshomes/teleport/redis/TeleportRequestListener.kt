package net.bitbylogic.cshomes.teleport.redis

import net.bitbylogic.cshomes.CSHomes
import net.bitbylogic.cshomes.teleport.PendingTeleportManager
import net.bitbylogic.rps.listener.ListenerComponent
import net.bitbylogic.rps.listener.RedisMessageListener
import java.util.*

class TeleportRequestListener(val plugin: CSHomes, val pendingTeleportManager: PendingTeleportManager) :
    RedisMessageListener("home_teleport_request") {

    override fun onReceive(listenerComponent: ListenerComponent?) {
        listenerComponent?.let { component ->
            val playerId = component.getData("player_id", UUID::class.java) ?: return@let
            val homeName = component.getData("home_name", String::class.java) ?: return@let

            pendingTeleportManager.add(playerId, homeName)

            plugin.redisClient.sendListenerMessage(
                ListenerComponent(component.source.serverId, "home_teleport_confirmation").addData(
                    "player_id",
                    playerId
                )
            )
        }
    }

}
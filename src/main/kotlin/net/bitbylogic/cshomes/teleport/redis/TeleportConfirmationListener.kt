package net.bitbylogic.cshomes.teleport.redis

import net.bitbylogic.cshomes.CSHomes
import net.bitbylogic.cshomes.util.PlayerUtil
import net.bitbylogic.rps.listener.ListenerComponent
import net.bitbylogic.rps.listener.RedisMessageListener
import org.bukkit.Bukkit
import java.util.*

class TeleportConfirmationListener(val plugin: CSHomes) : RedisMessageListener("home_teleport_confirmation") {

    override fun onReceive(listenerComponent: ListenerComponent?) {
        listenerComponent?.let { component ->
            val playerId = component.getData("player_id", UUID::class.java) ?: return@let
            val player = Bukkit.getPlayer(playerId) ?: return@let

            PlayerUtil.sendPlayerToServer(plugin, player, component.source.serverId)
        }
    }

}
package net.bitbylogic.cshomes.listener

import net.bitbylogic.cshomes.CSHomes
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerListener(val plugin: CSHomes) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.teleportService.handleJoin(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        plugin.database.invalidate(event.player.uniqueId)
    }

}
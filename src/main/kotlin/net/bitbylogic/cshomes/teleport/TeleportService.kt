package net.bitbylogic.cshomes.teleport

import com.tcoded.folialib.FoliaLib
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player

class TeleportService(val foliaLib: FoliaLib, val pendingTeleportManager: PendingTeleportManager) {

    fun handleJoin(player: Player) {
        val home = pendingTeleportManager.pop(player.uniqueId) ?: return
        val world: World = Bukkit.getWorld(home.world) ?: return
        val location = Location(world, home.x, home.y, home.z)

        home.lastUsed = System.currentTimeMillis()
        pendingTeleportManager.plugin.database.saveHome(home)

        foliaLib.scheduler.teleportAsync(player, location)
    }

}
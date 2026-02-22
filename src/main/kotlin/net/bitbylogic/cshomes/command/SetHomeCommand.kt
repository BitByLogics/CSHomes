package net.bitbylogic.cshomes.command

import net.bitbylogic.cshomes.CSHomes
import net.bitbylogic.cshomes.home.PlayerHome
import net.bitbylogic.cshomes.util.MessageUtil
import net.bitbylogic.cshomes.util.ServerUtil.Companion.thenAcceptSync
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SetHomeCommand(private val plugin: CSHomes) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Player-Only").orEmpty()) }
            return true
        }

        if (!sender.hasPermission("cshomes.set")) {
            sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.No-Permission").orEmpty()) }
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Set-Home-Usage").orEmpty()) }
            return true
        }

        val homeName = args[0]

        plugin.database.findHome(sender.uniqueId, homeName).thenAcceptSync(plugin, sender) { home ->
            if (home != null) {
                sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Home-Exists").orEmpty()) }
                return@thenAcceptSync
            }

            val location: Location = sender.location
            val worldName = location.world?.name ?: return@thenAcceptSync

            val newHome = PlayerHome(
                playerId = sender.uniqueId,
                name = homeName,
                serverName = plugin.serverName,
                world = worldName,
                x = location.x,
                y = location.y,
                z = location.z,
                createdAt = System.currentTimeMillis(),
                lastUsed = System.currentTimeMillis()
            )

            plugin.database.saveHome(newHome).thenRun {
                sender.sendMessage(
                    MessageUtil.deserialize(plugin.config.getString("Messages.Home-Set").orEmpty())
                )
            }
        }

        return true
    }

}
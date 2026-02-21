package net.bitbylogic.cshomes.command

import net.bitbylogic.cshomes.CSHomes
import net.bitbylogic.cshomes.util.MessageUtil
import net.bitbylogic.cshomes.util.ServerUtil.Companion.thenAcceptSync
import net.bitbylogic.rps.listener.ListenerComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.util.StringUtil

class HomeCommand(val plugin: CSHomes) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("cshomes.home")) {
            sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.No-Permission").orEmpty()) }
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Command-Usage").orEmpty()) }
            return false
        }

        if (sender !is Player) {
            sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Player-Only").orEmpty()) }
            return false
        }

        val homeName = args[0]

        plugin.database.findHome(sender.uniqueId, homeName).thenAcceptSync(plugin) { home ->
            if (home == null) {
                sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Home-Not-Found").orEmpty()) }
                return@thenAcceptSync
            }

            if (!home.serverName.equals(plugin.serverName, true)) {
                sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Teleporting").orEmpty()) }
                plugin.redisClient.sendListenerMessage(ListenerComponent(home.serverName, "home_teleport_request").addData("player_id", sender.uniqueId).addData("home_name", homeName))
                return@thenAcceptSync
            }

            val world: World = Bukkit.getWorld(home.world) ?: return@thenAcceptSync
            val location = Location(world, home.x, home.y, home.z)

            home.lastUsed = System.currentTimeMillis()
            plugin.database.saveHome(home)

            sender.teleport(location)
        }

        return false
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String?>? {
        if (sender !is Player || args.isEmpty()) {
            return emptyList()
        }

        val homeNames = plugin.database.getHomeNames(sender.uniqueId)
        val completions = mutableListOf<String>()

        return StringUtil.copyPartialMatches(
            args[args.size - 1],
            homeNames,
            completions
        )
    }

}
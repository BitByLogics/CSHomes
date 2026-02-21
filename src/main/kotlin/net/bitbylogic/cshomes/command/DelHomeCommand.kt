package net.bitbylogic.cshomes.command

import net.bitbylogic.cshomes.CSHomes
import net.bitbylogic.cshomes.util.MessageUtil
import net.bitbylogic.cshomes.util.ServerUtil.Companion.thenAcceptSync
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.util.StringUtil

class DelHomeCommand(private val plugin: CSHomes) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Player-Only").orEmpty()) }
            return true
        }

        if (!sender.hasPermission("cshomes.delete")) {
            sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.No-Permission").orEmpty()) }
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(
                MessageUtil.deserialize(plugin.config.getString("Messages.Del-Home-Usage").orEmpty())
            )
            return true
        }

        val homeName = args[0]

        plugin.database.findHome(sender.uniqueId, homeName).thenAcceptSync(plugin) { home ->
            if (home == null) {
                sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Home-Not-Found").orEmpty()) }
                return@thenAcceptSync
            }

            plugin.database.removeHome(home).thenRun {
                sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Home-Deleted").orEmpty()) }
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String>? {
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
package net.bitbylogic.cshomes.command

import net.bitbylogic.cshomes.CSHomes
import net.bitbylogic.cshomes.util.MessageUtil
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class CSHomesCommand(private val plugin: CSHomes) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("cshomes.admin")) {
            sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.No-Permission").orEmpty()) }
            return true
        }

        plugin.reload()
        sender.sendMessage { MessageUtil.deserialize(plugin.config.getString("Messages.Plugin-Reloaded").orEmpty()) }

        return true
    }

}
package net.bitbylogic.cshomes.util

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture

class ServerUtil {

    companion object {
        fun <T> CompletableFuture<T>.thenAcceptSync(plugin: JavaPlugin, consumer: (T) -> Unit): CompletableFuture<Void> {
            return this.thenAccept { value ->
                Bukkit.getScheduler().runTask(plugin, Runnable { consumer(value) })
            }
        }
    }

}
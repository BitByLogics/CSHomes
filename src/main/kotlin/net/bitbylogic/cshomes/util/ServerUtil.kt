package net.bitbylogic.cshomes.util

import net.bitbylogic.cshomes.CSHomes
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class ServerUtil {

    companion object {
        fun <T> CompletableFuture<T>.thenAcceptSync(plugin: CSHomes, player: Player, consumer: (T) -> Unit): CompletableFuture<Void> {
            return this.thenAccept { value ->
                plugin.foliaLib.scheduler.runAtEntity(player) { consumer(value) }
            }
        }
    }

}
package net.bitbylogic.cshomes.util

import net.bitbylogic.cshomes.CSHomes
import org.bukkit.entity.Player
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class PlayerUtil {

    companion object {
        fun sendPlayerToServer(plugin: CSHomes, player: Player, server: String) {
            val byteStream = ByteArrayOutputStream()
            val outStream = DataOutputStream(byteStream)

            outStream.writeUTF("Connect");
            outStream.writeUTF(server);

            player.sendPluginMessage(plugin, "BungeeCord", byteStream.toByteArray());

            byteStream.close();
            outStream.close();
        }
    }

}
package net.bitbylogic.cshomes.home

import java.util.UUID

data class PlayerHome(
    val playerId: UUID,
    val name: String,
    val serverName: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val createdAt: Long = System.currentTimeMillis(),
    var lastUsed: Long = System.currentTimeMillis()
) {
}
package net.bitbylogic.cshomes.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags

object MessageUtil {

    private val MINI_MESSAGE: MiniMessage = MiniMessage.builder()
        .tags(
            TagResolver.builder()
                .resolver(StandardTags.defaults())
                .build()
        ).build()

    fun deserialize(message: String, vararg placeholders: TagResolver.Single): Component {
        return MINI_MESSAGE.deserialize(message, *placeholders)
    }

}
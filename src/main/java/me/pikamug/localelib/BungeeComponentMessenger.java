package me.pikamug.localelib;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Sends messages using the BungeeCord Chat API ({@code net.md_5.bungee.api.chat}), which every
 * Spigot/Paper/Purpur server has bundled since 1.13 - {@code spigot-api} itself depends on it, so
 * this is always safe to use. This is the universal implementation used whenever
 * {@link AdventureComponentMessenger} isn't available (i.e. on everything except Paper and its
 * forks).
 */
final class BungeeComponentMessenger implements ComponentMessenger {

    @Override
    public void send(final Player player, final List<MessageSegment> segments) {
        final BaseComponent[] components = new BaseComponent[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            components[i] = toComponent(segments.get(i));
        }
        player.spigot().sendMessage(components);
    }

    private BaseComponent toComponent(final MessageSegment segment) {
        final BaseComponent component = segment.isTranslatable()
                ? new TranslatableComponent(segment.getContent())
                : new TextComponent(segment.getContent());
        if (segment.getColor() != null) {
            // Accepts both legacy color names (e.g. "red") and hex strings (e.g. "#ff0000") -
            // exactly the two formats MessageSegment#getColor() ever produces.
            component.setColor(ChatColor.of(segment.getColor()));
        }
        if (segment.isBold()) {
            component.setBold(true);
        }
        if (segment.isItalic()) {
            component.setItalic(true);
        }
        if (segment.isUnderline()) {
            component.setUnderlined(true);
        }
        if (segment.isStrikethrough()) {
            component.setStrikethrough(true);
        }
        if (segment.isObfuscated()) {
            component.setObfuscated(true);
        }
        return component;
    }
}

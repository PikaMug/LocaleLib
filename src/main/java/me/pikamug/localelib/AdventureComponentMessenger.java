package me.pikamug.localelib;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Sends messages using the real Adventure API ({@code net.kyori.adventure}), available on Paper
 * and its forks (Purpur, etc.) but not on plain Spigot/CraftBukkit.
 *
 * <p><b>Loading safety:</b> this class must never be referenced by type - import, field, method
 * signature, {@code instanceof}, or {@code new} - from {@link LocaleManager} or any other class
 * that is always loaded. {@code LocaleManager} only ever loads it via
 * {@code Class.forName("me.pikamug.localelib.AdventureComponentMessenger")}, and only after first
 * confirming, via a separate check that references no Adventure types at all, that the running
 * server's {@code Player} class actually implements {@code net.kyori.adventure.audience.Audience}.
 * That ordering is what keeps every other server safe: the JVM never attempts to resolve this
 * class - and transitively, the Adventure classes it references - unless that check has already
 * passed. Do not add a direct reference to this class anywhere else in the codebase.
 */
final class AdventureComponentMessenger implements ComponentMessenger {

    @Override
    public void send(final Player player, final List<MessageSegment> segments) {
        Component message = Component.empty();
        for (final MessageSegment segment : segments) {
            message = message.append(toComponent(segment));
        }
        ((Audience) player).sendMessage(message);
    }

    private Component toComponent(final MessageSegment segment) {
        Component component = segment.isTranslatable()
                ? Component.translatable(segment.getContent())
                : Component.text(segment.getContent());
        if (segment.getColor() != null) {
            final TextColor color = resolveColor(segment.getColor());
            if (color != null) {
                component = component.color(color);
            }
        }
        if (segment.isBold()) {
            component = component.decorate(TextDecoration.BOLD);
        }
        if (segment.isItalic()) {
            component = component.decorate(TextDecoration.ITALIC);
        }
        if (segment.isUnderline()) {
            component = component.decorate(TextDecoration.UNDERLINED);
        }
        if (segment.isStrikethrough()) {
            component = component.decorate(TextDecoration.STRIKETHROUGH);
        }
        if (segment.isObfuscated()) {
            component = component.decorate(TextDecoration.OBFUSCATED);
        }
        return component;
    }

    /**
     * Resolves either a hex string (e.g. {@code "#ff0000"}) or a legacy Minecraft color name
     * (e.g. {@code "red"}) - the two formats {@link MessageSegment#getColor()} ever produces.
     */
    private static TextColor resolveColor(final String color) {
        if (color.startsWith("#")) {
            return TextColor.fromHexString(color);
        }
        return NamedTextColor.NAMES.value(color);
    }
}

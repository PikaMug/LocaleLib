package me.pikamug.localelib;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Sends a parsed, formatted message to a player as a real chat component, instead of building a
 * raw JSON string and dispatching it through a console "tellraw" command. Package-private:
 * internal implementation detail, not part of the library's public API.
 *
 * <p>Implementations must be safe to construct on every supported server. The one implementation
 * that isn't ({@link AdventureComponentMessenger}, which depends on a library only present on
 * Paper and its forks) is never referenced by type from here or from {@link LocaleManager} - see
 * that class's Javadoc for how it's loaded safely.
 */
interface ComponentMessenger {
    void send(Player player, List<MessageSegment> segments);
}

package me.pikamug.localelib;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Sanity tests for the hardcoded legacy translation key tables in {@link LocaleKeys}. These only
 * exercise the plain map-building getters (no Bukkit API calls), so they run without a live
 * server or any mocking. {@link LocaleKeys#loadTranslations()} and its helpers are not covered
 * here since they call into the Bukkit API.
 */
public class LocaleKeysTest {

    @Test
    public void getBlockKeys_hasNoNullEntries() {
        assertNoNullEntries(LocaleKeys.getBlockKeys());
    }

    @Test
    public void getBlockKeys_containsKnownEntries() {
        Map<String, String> blocks = LocaleKeys.getBlockKeys();
        assertEquals("tile.air.name", blocks.get("AIR"));
        assertEquals("tile.dirt.name", blocks.get("DIRT"));
        assertEquals("tile.chest.name", blocks.get("CHEST"));
    }

    @Test
    public void getItemKeys_hasNoNullEntries() {
        assertNoNullEntries(LocaleKeys.getItemKeys());
    }

    @Test
    public void getItemKeys_containsKnownEntries() {
        Map<String, String> items = LocaleKeys.getItemKeys();
        assertEquals("item.apple.name", items.get("APPLE"));
        assertEquals("item.diamond.name", items.get("DIAMOND"));
        assertEquals("item.bow.name", items.get("BOW"));
    }

    @Test
    public void getEntityKeys_hasNoNullEntries() {
        assertNoNullEntries(LocaleKeys.getEntityKeys());
    }

    @Test
    public void getEntityKeys_containsKnownEntries() {
        Map<String, String> entities = LocaleKeys.getEntityKeys();
        assertEquals("entity.Creeper.name", entities.get("CREEPER"));
        assertEquals("entity.Zombie.name", entities.get("ZOMBIE"));
    }

    @Test
    public void getPotionKeys_hasNoNullEntries() {
        assertNoNullEntries(LocaleKeys.getPotionKeys());
    }

    @Test
    public void getPotionKeys_containsKnownEntries() {
        Map<String, String> potions = LocaleKeys.getPotionKeys();
        assertEquals("potion.effect.water", potions.get("WATER"));
        assertEquals("potion.effect.regeneration", potions.get("REGEN"));
    }

    private static void assertNoNullEntries(Map<String, String> map) {
        assertNotNull("map should not be null", map);
        assertFalse("map should not be empty", map.isEmpty());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            assertNotNull("null key found in map", entry.getKey());
            assertNotNull("null value for key: " + entry.getKey(), entry.getValue());
        }
    }
}

/*
 * MIT License
 *
 * Copyright (c) 2019 PikaMug
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.pikamug.localelib;

import org.apache.commons.lang.NullArgumentException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class LocaleManager{
    private static final Pattern rgbPattern = Pattern.compile("§x(§[0-9a-fA-F]){6}");
    private static Class<?> craftMagicNumbers = null;
    private static Class<?> itemClazz = null;
    private static boolean oldVersion = false;
    private static boolean hasBasePotionData = false;
    private static boolean hasRepackagedNms = false;
    private static boolean isPost1dot18 = false;
    private final Map<String, String> oldBlocks = LocaleKeys.getBlockKeys();
    private final Map<String, String> oldItems = LocaleKeys.getItemKeys();
    private final Map<String, String> oldPotions1dot8 = LocaleKeys.getPotionKeys1dot8();
    private final Map<String, String> oldPotions = LocaleKeys.getPotionKeys();
    private final Map<String, String> oldLingeringPotions = LocaleKeys.getLingeringPotionKeys();
    private final Map<String, String> oldSplashPotions = LocaleKeys.getSplashPotionKeys();
    private final Map<String, String> oldEntities = LocaleKeys.getEntityKeys();
    private Map<String, String> englishTranslations;
    
    public LocaleManager() {
        oldVersion = isBelow113();
        if (Material.getMaterial("LINGERING_POTION") != null) {
            // Bukkit version is 1.9+
            hasBasePotionData = true;
        }
        if (Material.getMaterial("AMETHYST_CLUSTER") != null) {
            // Bukkit version is 1.17+
            hasRepackagedNms = true;
        }
        if (Material.getMaterial("MUSIC_DISC_OTHERSIDE") != null) {
            // Bukkit version is 1.18+ (for NMS Item#getName)
            isPost1dot18 = true;
        }
        final String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        try {
            craftMagicNumbers = Class.forName("org.bukkit.craftbukkit.{v}.util.CraftMagicNumbers".replace("{v}", version));
            if (hasRepackagedNms) {
                itemClazz = Class.forName("net.minecraft.world.item.Item");
            } else {
                itemClazz = Class.forName("net.minecraft.server.{v}.Item".replace("{v}", version));
            }
        } catch (final ClassNotFoundException e) {
            e.printStackTrace();
        }
        try {
            englishTranslations = LocaleKeys.loadTranslations();
            if (englishTranslations.isEmpty()) {
                Bukkit.getLogger().warning("The English locale couldn't be found or loaded!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Send message with item name translated to the client's locale.
     * ItemStack is required. This method supports 1.8 potions.<p>
     *
     * Message should contain {@code <item>} string for replacement by
     * this method (along with applicable {@code <enchantment>} and/or
     * {@code <level>} strings).
     *
     * @param player The player whom the message is to be sent to
     * @param message The message to be sent to the player
     * @param itemStack The item to be translated
     */
    public boolean sendMessage(final Player player, final String message, final ItemStack itemStack) {
        if (player == null || itemStack == null) {
            return false;
        }
        if (!hasBasePotionData) {
            final Potion potion = Potion.fromItemStack(itemStack);
            String msg = message;
            if (potion.isSplash()) {
                msg = message.replace("<item>", "<prefix> <item>")
                        .replace("<prefix>", translate(message, "potion.prefix.grenade", "<prefix>"));
            }
            String potionName = "item.potion.name";
            if (potion.getType().getEffectType() != null) {
                potionName = oldPotions1dot8.get(potion.getType().getEffectType().getName());
            }
            msg = msg.replace("<item>", translate(message, potionName, "<item>"));

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + formatName(player) + " [\"" + msg + "\"]");
            return true;
        }
        return sendMessage(player, message, itemStack.getType(), itemStack.getDurability(), itemStack.getEnchantments(), itemStack.getItemMeta());
    }
    
    /**
     * Send message with item name translated to the client's locale.
     * Material is required. Durability arg is arbitrary for 1.13+
     * and can be ignored by setting to a value less than 0.
     * Enchantments & meta are optional and may be left null or empty,
     * but note that most Potions use meta for 1.13+.<p>
     * 
     * Message should contain {@code <item>} string for replacement by
     * this method (along with applicable {@code <enchantment>} and/or 
     * {@code <level>} strings).
     * 
     * @param player The player whom the message is to be sent to
     * @param message The message to be sent to the player
     * @param material Material for the item being translated
     * @param durability Durability for the item being translated
     * @param enchantments Enchantments for the item being translated
     * @param meta ItemMeta for the item being translated
     */
    public boolean sendMessage(final Player player, final String message, final Material material, final short durability, 
           Map<Enchantment, Integer> enchantments, final ItemMeta meta) {
        if (player == null || material == null) {
            return false;
        }
        String matKey;
        try {
            matKey = queryMaterial(material, durability, meta);
        } catch (final Exception ex) {
            Bukkit.getLogger().severe("[LocaleLib] Unable to query Material: " + material.name());
            return false;
        }
        if (meta instanceof EnchantmentStorageMeta) {
            enchantments = ((EnchantmentStorageMeta)meta).getStoredEnchants();
        }
        final Collection<String> enchantKeys = queryEnchantments(enchantments).values();
        final Collection<String> lvlKeys = queryLevels(enchantments).values();
        String msg = message.replace("<item>", translate(message, matKey, "<item>"));
        for (final String ek : enchantKeys) {
            msg = msg.replaceFirst("<enchantment>", translate(msg, ek, "<enchantment>"));
        }
        for (final String lk : lvlKeys) {
            msg = msg.replaceFirst("<level>",  translate(msg, lk, "<level>"));
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + formatName(player) + " [\"" + msg + "\"]");
        return true;
    }
    
    /**
     * Send message with item name translated to the client's locale.
     * Material is required. Durability arg is arbitrary for 1.13+
     * and can be ignored by setting to a value less than 0.
     * Enchantments are optional and may be left null or empty.<p>
     * 
     * Message should contain {@code <item>} string for replacement by
     * this method (along with applicable {@code <enchantment>} and/or 
     * {@code <level>} strings).
     * 
     * @param player The player whom the message is to be sent to
     * @param message The message to be sent to the player
     * @param material Material for the item being translated
     * @param durability Durability for the item being translated
     * @param enchantments Enchantments for the item being translated
     */
    public boolean sendMessage(final Player player, final String message, final Material material, final short durability,
            final Map<Enchantment, Integer> enchantments) {
        return sendMessage(player, message, material, durability, enchantments, null);
    }
    
    /**
     * Send message with enchantments translated to the client's locale.
     * Map of Enchantment+level is required.
     * 
     * Message should contain {@code <item>} string for replacement by
     * this method (along with applicable {@code <enchantment>} and/or 
     * {@code <level>} strings).
     * 
     * @param player The player whom the message is to be sent to
     * @param message The message to be sent to the player
     * @param enchantments Enchantments for the item being translated
     */
    public boolean sendMessage(final Player player, final String message, final Map<Enchantment, Integer> enchantments) {
        if (player == null || message == null || enchantments == null) {
            return false;
        }
        final Collection<String> enchantKeys = queryEnchantments(enchantments).values();
        final Collection<String> levelKeys = queryLevels(enchantments).values();
        String msg = message;
        if (!enchantKeys.isEmpty()) {
            for (final String ek : enchantKeys) {
                msg = message.replaceFirst("<enchantment>", translate(message, ek, "<enchantment>"));
            }
            for (final String lk : levelKeys) {
                msg = message.replaceFirst("<level>",  translate(message, lk, "<level>"));
            }
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + formatName(player) + " [\"" + msg + "\"]");
        return true;
    }
    
    /**
     * Send message with entity name translated to the client's locale.
     * EntityType is required.<p>
     * 
     * Message should contain {@code <mob>}string for replacement by 
     * this method.
     * 
     * @param player The player whom the message is to be sent to
     * @param message The message to be sent to the player
     * @param type The entity type to be translated
     * @param extra Career, Ocelot, Rabbit, or TropicalFish type if applicable
     */
    public boolean sendMessage(final Player player, final String message, final EntityType type, final String extra) {
        if (player == null || message == null || type == null) {
            return false;
        }
        final String key = queryEntityType(type, extra);
        final String msg = message.replace("<mob>", translate(message, key, "<mob>"));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + formatName(player) + " [\"" + msg + "\"]");
        return true;
    }

    /**
     * Gets the key name of the specified entity as it would appear in a Minecraft lang file.
     *
     * @param entity the entity to check
     * @return the raw key
     * @throws NullArgumentException if specified entity parameter is null
     */
    public String queryEntity(final Entity entity) throws NullArgumentException {
        if (entity == null) {
            throw new NullArgumentException("[LocaleLib] Entity cannot be null");
        }
        String extra = null;
        if (entity instanceof Villager) {
            Villager villager = (Villager) entity;
            extra = villager.getProfession().name();
        } else if (entity instanceof Ocelot) {
            Ocelot ocelot = (Ocelot) entity;
            extra = ocelot.getCatType().name();
        } else if (entity instanceof Rabbit) {
            Rabbit rabbit = (Rabbit) entity;
            if (rabbit.getRabbitType().equals(Rabbit.Type.THE_KILLER_BUNNY)) { // Only type with translation
                extra = rabbit.getRabbitType().name();
            }
        }
        if (!oldVersion) {
            if (entity instanceof TropicalFish) {
                TropicalFish tropicalFish = (TropicalFish) entity;
                extra = tropicalFish.getPattern().name();
            }
        }
        return queryEntityType(entity.getType(), extra);
    }

    /**
     * Gets the key name of the specified entity type as it would appear in a Minecraft lang file.
     * Extra data is optional and may be left null or empty.
     *
     * @param entityType the entity type to check
     * @param extra the extra data to check, i.e. name of Profession
     * @return the raw key
     * @throws NullArgumentException if specified entity type parameter is null
     */
    public String queryEntityType(final EntityType entityType, final String extra) throws NullArgumentException {
        if (entityType == null) {
            throw new NullArgumentException("[LocaleLib] EntityType cannot be null");
        }
        String key = "";
        if (oldVersion) {
            if (entityType.name().equals("VILLAGER") && extra != null && Profession.valueOf(extra) != null) {
                key = oldEntities.get(entityType.name() + "." + Profession.valueOf(extra).name());
            } else if (entityType.name().equals("OCELOT") && extra != null && Ocelot.Type.valueOf(extra) != null) {
                key = oldEntities.get(entityType.name() + "." + Ocelot.Type.valueOf(extra).name());
            } else if (entityType.name().equals("RABBIT") && extra != null && Rabbit.Type.valueOf(extra) != null
                    && Rabbit.Type.valueOf(extra).equals(Rabbit.Type.THE_KILLER_BUNNY)) {
                key = oldEntities.get(entityType.name() + "." + Rabbit.Type.valueOf(extra).name());
            } else {
                key = oldEntities.get(entityType.name());
            }
        } else {
            if (entityType.name().equals("MUSHROOM_COW")) {
                key = "entity.minecraft.mooshroom";
            } else if (entityType.name().equals("SNOWMAN")) {
                key = "entity.minecraft.snow_golem";
            } else if (entityType.name().equals("PIG_ZOMBIE")) {
                key = "entity.minecraft.zombie_pigman";
            } else if (entityType.name().equals("VILLAGER") && extra != null && Profession.valueOf(extra) != null) {
                key = "entity.minecraft.villager." + Profession.valueOf(extra).name();
            } else if (entityType.name().equals("RABBIT") && extra != null && Rabbit.Type.valueOf(extra) != null
                    && Rabbit.Type.valueOf(extra).equals(Rabbit.Type.THE_KILLER_BUNNY)) {
                key = "entity.minecraft.killer_bunny";
            } else if (entityType.name().equals("TROPICAL_FISH") && extra != null) {
                if (TropicalFish.Pattern.valueOf(extra) != null) {
                    key = "entity.minecraft.tropical_fish.type." + TropicalFish.Pattern.valueOf(extra);
                } else {
                    try {
                        int value = Integer.parseInt(extra);
                        if (value >= 0 && value < 22) {
                            key = "entity.minecraft.tropical_fish.predefined." + extra;
                        }
                    } catch (NumberFormatException nfe) {
                        // Do nothing
                    }
                }
            } else {
                key = "entity.minecraft." + entityType.toString().toLowerCase();
            }
        }
        return key;
    }

    /**
     * Gets the key name of the specified item as it would appear in a Minecraft lang file.
     *
     * @param itemStack the item to check
     * @return the raw key
     * @throws NullArgumentException if specified item parameter is null
     */
    @SuppressWarnings("deprecation")
    public String queryItemStack(final ItemStack itemStack) throws NullArgumentException {
        if (itemStack == null) {
            throw new NullArgumentException("[LocaleLib] ItemStack cannot be null");
        }
        return queryMaterial(itemStack.getType(), itemStack.getDurability(), itemStack.getItemMeta());
    }
    
    /**
     * Gets the key name of the specified material as it would appear in a Minecraft lang file.
     * 
     * @param material the material to check
     * @return the raw key
     * @throws IllegalArgumentException if an item with that material could not be found
     */
    public String queryMaterial(final Material material) throws IllegalArgumentException {
        return queryMaterial(material, (short) 0, null);
    }
    
    /**
     * Gets the key name of the specified material as it would appear in a Minecraft lang file.
     * 
     * @param material the material to check
     * @param durability the durability to check
     * @param meta the item meta data to check
     * @return the raw key
     * @throws IllegalArgumentException if an item with that material and durability could not be found
     * @throws NullArgumentException if the specified material parameter is null
     */
    @SuppressWarnings("deprecation")
    public String queryMaterial(final Material material, final short durability, final ItemMeta meta)
            throws IllegalArgumentException, NullArgumentException {
        if (material == null) {
            throw new NullArgumentException("[LocaleLib] Material cannot be null");
        }
        String matKey = "";
        if (oldVersion) {
            if (material.isBlock()) {
                if (durability >= 0 && oldBlocks.containsKey(material.name() + "." + durability)) {
                    matKey = oldBlocks.get(material.name() + "." + durability);
                } else if (oldBlocks.containsKey(material.name())) {
                    matKey = oldBlocks.get(material.name());
                } else {
                    throw new IllegalArgumentException("[LocaleLib] Block not found: " + material.name() + "." + durability);
                }
            } else {
                final ItemStack i = new ItemStack(material, 1, durability);
                if (durability >= 0 && i.getItemMeta() instanceof PotionMeta) {
                    if (hasBasePotionData) {
                        if (material.equals(Material.POTION)) {
                            matKey = oldPotions.get(((PotionMeta)i.getItemMeta()).getBasePotionData().getType().name());
                        } else if (material.equals(Material.LINGERING_POTION)) {
                            matKey = oldLingeringPotions.get(((PotionMeta)i.getItemMeta()).getBasePotionData().getType().name());
                        } else if (material.equals(Material.SPLASH_POTION)) {
                            matKey = oldSplashPotions.get(((PotionMeta)i.getItemMeta()).getBasePotionData().getType().name());
                        }
                    }
                } else if (durability >= 0 && oldItems.containsKey(material.name() + "." + durability)) {
                    matKey = oldItems.get(material.name() + "." + durability);
                } else if (oldItems.containsKey(material.name())) {
                    matKey = oldItems.get(material.name());
                } else {
                    throw new IllegalArgumentException("[LocaleLib] Item not found: " + material.name() + "." + durability);
                }
            }
        } else {
            if (material.isBlock() && material.createBlockData() instanceof Ageable) {
                matKey = "block.minecraft." + material.name().toLowerCase();
            } else {
                try {
                    final Method m = craftMagicNumbers.getDeclaredMethod("getItem", material.getClass());
                    m.setAccessible(true);
                    final Object item = m.invoke(craftMagicNumbers, material);
                    if (item == null) {
                        throw new IllegalArgumentException(material.name() + " material could not be queried!");
                    }
                    matKey = (String) itemClazz.getMethod(isPost1dot18 ? "a" : "getName").invoke(item);
                    if (meta instanceof PotionMeta) {
                        matKey += ".effect." + ((PotionMeta)meta).getBasePotionData().getType().name().toLowerCase()
                                .replace("regen", "regeneration").replace("speed", "swiftness").replace("jump", "leaping")
                                .replace("instant_heal", "healing").replace("instant_damage", "harming");
                    }
                } catch (final Exception ex) {
                    ex.printStackTrace();
                    throw new IllegalArgumentException("[LocaleLib] Unable to query Material: " + material.name());
                }
            }
        }
        return matKey;
    }
    
    /**
     * Gets the key name of the specified enchantments as it would appear in a Minecraft lang file.
     * 
     * @param enchantments Enchantments to get the keys of
     * @return the raw keys of the enchantments
     */
    @SuppressWarnings("deprecation")
    public Map<Enchantment, String> queryEnchantments(final Map<Enchantment, Integer> enchantments) {
        final Map<Enchantment, String> enchantKeys = new HashMap<>();
        if (enchantments == null || enchantments.isEmpty()) {
            return enchantKeys;
        }
        if (oldVersion) {
            for (final Enchantment e : enchantments.keySet()) {
                enchantKeys.put(e, "enchantment." + e.getName().toLowerCase().replace("_", ".")
                    .replace("environmental", "all").replace("protection", "protect"));
            }
        } else {
            for (final Enchantment e : enchantments.keySet()) {
                final String str = e.toString();
                enchantKeys.put(e, "enchantment.minecraft." + str.substring(str.indexOf(":") + 1, str.indexOf(",")));
            }
        }
        return enchantKeys;
    }
    
    /**
     * Gets the key name of the specified enchantment levels as it would appear in a Minecraft lang file.
     * 
     * @param enchantments Enchantment levels to get the keys of
     * @return the raw keys of the enchantment levels
     */
    public Map<Integer, String> queryLevels(final Map<Enchantment, Integer> enchantments) {
        final Map<Integer, String> lvlKeys = new HashMap<>();
        if (enchantments == null || enchantments.isEmpty()) {
            return lvlKeys;
        }
        for (final Integer i : enchantments.values()) {
            lvlKeys.put(i, "enchantment.level." + i);
        }
        return lvlKeys;
    }
    
    /**
     * Gets the display name of the specified material as it would appear in a Minecraft lang file.
     * 
     * @param key the raw key for the object name
     * @return the display name of the specified key within the server locale file
     */
    public String toServerLocale(final String key) {
        return englishTranslations.getOrDefault(key, "<none>");
    }


    /**
     * Translate with respect to color codes.
     *
     * @param message The message to be sent to the player
     * @param key the raw key for the object name
     * @param placeholder <item>, <enchantment>, <level> or <mob>
     * @return the text to replace the placeholder in the message
     */
    private String translate(final String message, final String key, final String placeholder) {
        String replacement = "\",{\"translate\":\"" + key + "\"";
        final String text = message.split(placeholder)[0];
        if (text.contains("§")) {
            final Matcher matcher = rgbPattern.matcher(text);
            if (matcher.find()) {
                replacement += ", \"color\":\"" + "#" + matcher.group().replace("§", "").substring(1) + "\"";
            } else {
                final ChatColor color = ChatColor.getByChar(ChatColor.getLastColors(text).replace("§", ""));
                if (color != null) {
                    final String colorName = color.name();
                    replacement += ", \"color\":\"" + colorName.toLowerCase() + "\"";
                }
            }
        }
        replacement += "},\"";
        return replacement;
    }

    /**
     * Format player name according to server's Bukkit version
     *
     * @param player Player whose name to format
     * @return Formatted name
     */
    private String formatName(Player player) {
        if (!isBelow113()) {
            // Better Geyser/Floodgate compatibility
            return "\"" + player.getName() + "\"";
        } else {
            return player.getName();
        }
    }

    /**
     * Checks whether the server's Bukkit version supports use of the ItemMeta#getBasePotionData method.
     * 
     * @return true if Bukkit version is at 1.9 or above
     */
    public boolean hasBasePotionData() {
        return hasBasePotionData;
    }

    /**
     * Checks whether the server's Bukkit version uses the post-1.16.5 package scheme.
     *
     * @return true if Bukkit version is at 1.17 or above
     */
    public boolean hasRepackagedNms() {
        return hasRepackagedNms;
    }
    
    /**
     * Checks whether the server's Bukkit version is below 1.13.
     * 
     * @return true if Bukkit version is at 1.12.2 or below
     */
    public boolean isBelow113() {
        return _isBelow113(Bukkit.getServer().getBukkitVersion().split("-")[0]);
    }
    
    private boolean _isBelow113(final String bukkitVersion) {
        if (bukkitVersion.matches("^[0-9.]+$")) {
            switch(bukkitVersion) {
            case "1.12.2" :
            case "1.12.1" :
            case "1.12" :
            case "1.11.2" :
            case "1.11.1" :
            case "1.11" :
            case "1.10.2" :
            case "1.10.1" :
            case "1.10" :
            case "1.9.4" :
            case "1.9.3" :
            case "1.9.2" :
            case "1.9.1" :
            case "1.9" :
            case "1.8.9" :
            case "1.8.8" :
            case "1.8.7" :
            case "1.8.6" :
            case "1.8.5" :
            case "1.8.4" :
            case "1.8.3" :
            case "1.8.2" :
            case "1.8.1" :
            case "1.8" :
            case "1.7.10" :
            case "1.7.9" :
            case "1.7.2" :
                return true;
            default:
                // Bukkit version is 1.13+ or unsupported
                return false;
            }
        }
        Bukkit.getLogger().severe("LocaleLib received invalid Bukkit version " + bukkitVersion);
        return false;
    }
}

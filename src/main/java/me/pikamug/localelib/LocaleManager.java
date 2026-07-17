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
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class LocaleManager{
    private static final Pattern hexTokenPattern = Pattern.compile("%#([0-9a-fA-F]{6})%");
    private static final Pattern ampHexPattern = Pattern.compile("(?i)&#[0-9a-fA-F]{6}");
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
        final String packageName = Bukkit.getServer().getClass().getPackage().getName();
        try {
            if (packageName.equals("org.bukkit.craftbukkit")) {
                // Bukkit version is 1.20.5+
                craftMagicNumbers = Class.forName("org.bukkit.craftbukkit.util.CraftMagicNumbers");
                itemClazz = Class.forName("net.minecraft.world.item.Item");
            } else {
                final String version = packageName.split("\\.")[3];
                craftMagicNumbers = Class.forName("org.bukkit.craftbukkit.{v}.util.CraftMagicNumbers".replace("{v}",
                        version));
                if (hasRepackagedNms) {
                    itemClazz = Class.forName("net.minecraft.world.item.Item");
                } else {
                    itemClazz = Class.forName("net.minecraft.server.{v}.Item".replace("{v}", version));
                }
            }
        } catch (final ClassNotFoundException e) {
            e.printStackTrace();
        }
        try {
            englishTranslations = LocaleKeys.loadTranslations();
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
        final String convertedMessage = convertFormattingTokens(message);
        if (!hasBasePotionData && itemStack.getType().equals(Material.POTION)) {
            final Potion potion = Potion.fromItemStack(itemStack);
            String msg = convertedMessage;
            String prefixKey = null;
            if (potion.isSplash()) {
                prefixKey = "potion.prefix.grenade";
            }
            String potionName = "item.potion.name";
            if (potion.getType().getEffectType() != null) {
                potionName = oldPotions1dot8.get(potion.getType().getEffectType().getName());
            }
            final String json = buildTellrawJson(msg, new String[]{"<prefix>", "<item>"},
                    new String[]{prefixKey, potionName});
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + formatName(player) + " " + json);
            return true;
        }
        return sendMessage(player, message, itemStack.getType(), itemStack.getDurability(), itemStack.getEnchantments(),
                itemStack.getItemMeta());
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
        final String convertedMessage = convertFormattingTokens(message);
        String matKey;
        try {
            matKey = queryMaterial(material, durability, meta);
        } catch (final Exception ex) {
            Bukkit.getLogger().severe("[LocaleLib] Unable to query Material: " + material.name());
            ex.printStackTrace();
            return false;
        }
        if (meta instanceof EnchantmentStorageMeta) {
            enchantments = ((EnchantmentStorageMeta)meta).getStoredEnchants();
        }
        final Collection<String> enchantKeys = queryEnchantments(enchantments).values();
        final Collection<String> lvlKeys = queryLevels(enchantments).values();
        final int totalPlaceholders = 1 + enchantKeys.size() + lvlKeys.size();
        final String[] placeholders = new String[totalPlaceholders];
        final String[] translateKeys = new String[totalPlaceholders];
        placeholders[0] = "<item>";
        translateKeys[0] = matKey;
        int idx = 1;
        for (final String ek : enchantKeys) {
            placeholders[idx] = "<enchantment>";
            translateKeys[idx] = ek;
            idx++;
        }
        for (final String lk : lvlKeys) {
            placeholders[idx] = "<level>";
            translateKeys[idx] = lk;
            idx++;
        }
        final String json = buildTellrawJson(convertedMessage, placeholders, translateKeys);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + formatName(player) + " " + json);
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
        final String convertedMessage = convertFormattingTokens(message);
        final Collection<String> enchantKeys = queryEnchantments(enchantments).values();
        final Collection<String> levelKeys = queryLevels(enchantments).values();
        if (!enchantKeys.isEmpty()) {
            final int totalPlaceholders = enchantKeys.size() + levelKeys.size();
            final String[] placeholders = new String[totalPlaceholders];
            final String[] translateKeys = new String[totalPlaceholders];
            int idx = 0;
            for (final String ek : enchantKeys) {
                placeholders[idx] = "<enchantment>";
                translateKeys[idx] = ek;
                idx++;
            }
            for (final String lk : levelKeys) {
                placeholders[idx] = "<level>";
                translateKeys[idx] = lk;
                idx++;
            }
            final String json = buildTellrawJson(convertedMessage, placeholders, translateKeys);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + formatName(player) + " " + json);
        }
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
        final String convertedMessage = convertFormattingTokens(message);
        final String key = queryEntityType(type, extra);
        final String json = buildTellrawJson(convertedMessage, new String[]{"<mob>"}, new String[]{key});
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + formatName(player) + " " + json);
        return true;
    }

    /**
     * Gets the key name of the specified entity as it would appear in a Minecraft lang file.
     *
     * @param entity the entity to check
     * @return the raw key
     * @throws IllegalArgumentException if specified entity parameter is null
     */
    public String queryEntity(final Entity entity) throws IllegalArgumentException {
        if (entity == null) {
            throw new IllegalArgumentException("[LocaleLib] Entity cannot be null");
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
     * @throws IllegalArgumentException if specified entity type parameter is null
     */
    public String queryEntityType(final EntityType entityType, final String extra) throws IllegalArgumentException {
        if (entityType == null) {
            throw new IllegalArgumentException("[LocaleLib] EntityType cannot be null");
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
     * @throws IllegalArgumentException if specified item parameter is null
     */
    @SuppressWarnings("deprecation")
    public String queryItemStack(final ItemStack itemStack) throws IllegalArgumentException {
        if (itemStack == null) {
            throw new IllegalArgumentException("[LocaleLib] ItemStack cannot be null");
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
     * @param meta the item metadata to check
     * @return the raw key
     * @throws IllegalArgumentException if the specified material parameter is null or item/block cannot be found
     */
    @SuppressWarnings("deprecation")
    public String queryMaterial(final Material material, final short durability, final ItemMeta meta)
            throws IllegalArgumentException {
        if (material == null) {
            throw new IllegalArgumentException("[LocaleLib] Material cannot be null");
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
                i.setItemMeta(meta);
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
                    final Method itemMethod = craftMagicNumbers.getDeclaredMethod("getItem", material.getClass());
                    itemMethod.setAccessible(true);
                    final Object item = itemMethod.invoke(craftMagicNumbers, material);
                    if (item == null) {
                        throw new IllegalArgumentException(material.name() + " material could not be queried!");
                    }
                    final Method keyMethod = resolveMethod(itemClazz, "getDescriptionId", "a", "getName", "j", "l");
                    if (keyMethod == null) {
                        throw new IllegalArgumentException("Could not get description ID for " + itemClazz.getName());
                    }
                    matKey = (String) keyMethod.invoke(item);
                    if (meta instanceof PotionMeta) {
                        matKey += ".effect." + ((PotionMeta)meta).getBasePotionData().getType().name().toLowerCase()
                                .replace("speed", "swiftness").replace("jump", "leaping")
                                .replace("instant_heal", "healing").replace("instant_damage", "harming");
                        if (!matKey.contains("regeneration")) {
                            matKey = matKey.replace("regen", "regeneration");
                        }
                    }
                } catch (final Exception ex) {
                    throw new IllegalArgumentException("[LocaleLib] Unable to query Material: " + material.name(), ex);
                }
            }
        }
        return matKey;
    }

    private static Method resolveMethod(Class<?> clazz, String... mappings) {
        for (String mapping : mappings) {
            try {
                Method declaredMethod = clazz.getDeclaredMethod(mapping);
                declaredMethod.setAccessible(true);
                return declaredMethod;
            } catch (NoSuchMethodException e) {
                // Do nothing
            }
        }
        return null;
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
                enchantKeys.put(e, "enchantment.minecraft." + str.substring(str.indexOf(":") + 1,
                        str.indexOf("]")).split(", ")[0]);
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
     * Convert common plugin formatting tokens to Minecraft's internal section-sign format.
     * Handles {@code %#RRGGBB%} and {@code &#RRGGBB} hex color tokens, as well as
     * {@code &amp;} color codes.
     *
     * @param message the raw message containing formatting tokens
     * @return the message with tokens converted to section-sign codes
     */
    private String convertFormattingTokens(final String message) {
        String result = message;
        // Convert %#RRGGBB% to §x§R§R§G§G§B§B
        final Matcher hexMatcher = hexTokenPattern.matcher(result);
        final StringBuffer hexSb = new StringBuffer();
        while (hexMatcher.find()) {
            final String hex = hexMatcher.group(1);
            final String replacement = "\u00A7x\u00A7" + hex.charAt(0) + "\u00A7" + hex.charAt(1)
                    + "\u00A7" + hex.charAt(2) + "\u00A7" + hex.charAt(3)
                    + "\u00A7" + hex.charAt(4) + "\u00A7" + hex.charAt(5);
            hexMatcher.appendReplacement(hexSb, replacement);
        }
        hexMatcher.appendTail(hexSb);
        result = hexSb.toString();
        // Convert &#RRGGBB to §x§R§R§G§G§B§B
        final Matcher ampMatcher = ampHexPattern.matcher(result);
        final StringBuffer ampSb = new StringBuffer();
        while (ampMatcher.find()) {
            final String hex = ampMatcher.group().substring(2);
            final String replacement = "\u00A7x\u00A7" + hex.charAt(0) + "\u00A7" + hex.charAt(1)
                    + "\u00A7" + hex.charAt(2) + "\u00A7" + hex.charAt(3)
                    + "\u00A7" + hex.charAt(4) + "\u00A7" + hex.charAt(5);
            ampMatcher.appendReplacement(ampSb, replacement);
        }
        ampMatcher.appendTail(ampSb);
        return ampSb.toString();
    }

    /**
     * Build a tellraw JSON array from a message containing section-sign formatting codes
     * and placeholder strings. Hex colors ({@code §x§R§R§G§G§B§B}) are converted to
     * proper JSON {@code "color":"#RRGGBB"} properties, and placeholders are converted
     * to {@code {"translate":"key"}} components.
     *
     * @param message the message with section-sign codes and placeholder strings
     * @param placeholders the placeholder strings to replace (e.g. {@code <item>})
     * @param translateKeys the corresponding translation keys for each placeholder
     * @return a tellraw JSON array string
     */
    private String buildTellrawJson(String message, final String[] placeholders, final String[] translateKeys) {
        final StringBuilder json = new StringBuilder("[");
        final StringBuilder segment = new StringBuilder();
        int i = 0;
        String color = null;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;
        boolean obfuscated = false;
        boolean componentStarted = false;
        int placeholderIdx = 0;

        while (i < message.length()) {
            // Check for placeholder match
            if (placeholders != null && placeholderIdx < placeholders.length
                    && message.startsWith(placeholders[placeholderIdx], i)) {
                // Flush current text segment
                if (segment.length() > 0) {
                    if (componentStarted) {
                        json.append(",");
                    }
                    appendTextComponent(json, segment, color, bold, italic, underline, strikethrough, obfuscated);
                    segment.setLength(0);
                    componentStarted = true;
                }
                // Build translate component with inherited color
                if (componentStarted) {
                    json.append(",");
                }
                json.append("{\"translate\":\"").append(translateKeys[placeholderIdx]).append("\"");
                if (color != null) {
                    json.append(",\"color\":\"").append(color).append("\"");
                }
                if (bold) {
                    json.append(",\"bold\":true");
                }
                if (italic) {
                    json.append(",\"italic\":true");
                }
                if (underline) {
                    json.append(",\"underline\":true");
                }
                if (strikethrough) {
                    json.append(",\"strikethrough\":true");
                }
                if (obfuscated) {
                    json.append(",\"obfuscated\":true");
                }
                json.append("}");
                componentStarted = true;
                i += placeholders[placeholderIdx].length();
                placeholderIdx++;
                continue;
            }

            if (message.charAt(i) == '\u00A7' && i + 1 < message.length()) {
                final char code = Character.toLowerCase(message.charAt(i + 1));

                if (code == 'x' && i + 13 < message.length()) {
                    // Try to parse hex color: §x§R§R§G§G§B§B
                    boolean validHex = true;
                    for (int j = 2; j <= 13; j += 2) {
                        if (message.charAt(i + j) != '\u00A7') {
                            validHex = false;
                            break;
                        }
                        final char hexDigit = Character.toLowerCase(message.charAt(i + j + 1));
                        if (!((hexDigit >= '0' && hexDigit <= '9') || (hexDigit >= 'a' && hexDigit <= 'f'))) {
                            validHex = false;
                            break;
                        }
                    }
                    if (validHex) {
                        // Flush current segment before color change
                        if (segment.length() > 0) {
                            if (componentStarted) {
                                json.append(",");
                            }
                            appendTextComponent(json, segment, color, bold, italic, underline, strikethrough, obfuscated);
                            segment.setLength(0);
                            componentStarted = true;
                        }
                        // Extract hex color
                        final StringBuilder hex = new StringBuilder("#");
                        for (int j = 0; j < 6; j++) {
                            hex.append(Character.toLowerCase(message.charAt(i + 3 + j * 2)));
                        }
                        color = hex.toString();
                        i += 14; // Skip §x§R§R§G§G§B§B
                    } else {
                        segment.append(message.charAt(i));
                        i++;
                    }
                } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    // Standard color code: flush and update color
                    if (segment.length() > 0) {
                        if (componentStarted) {
                            json.append(",");
                        }
                        appendTextComponent(json, segment, color, bold, italic, underline, strikethrough, obfuscated);
                        segment.setLength(0);
                        componentStarted = true;
                    }
                    color = getMinecraftColorName(code);
                    i += 2;
                } else if (code == 'l') {
                    bold = true;
                    i += 2;
                } else if (code == 'o') {
                    italic = true;
                    i += 2;
                } else if (code == 'n') {
                    underline = true;
                    i += 2;
                } else if (code == 'm') {
                    strikethrough = true;
                    i += 2;
                } else if (code == 'k') {
                    obfuscated = true;
                    i += 2;
                } else if (code == 'r') {
                    // Reset: flush and clear all formatting
                    if (segment.length() > 0) {
                        if (componentStarted) {
                            json.append(",");
                        }
                        appendTextComponent(json, segment, color, bold, italic, underline, strikethrough, obfuscated);
                        segment.setLength(0);
                        componentStarted = true;
                    }
                    color = null;
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                    obfuscated = false;
                    i += 2;
                } else {
                    segment.append(message.charAt(i));
                    i++;
                }
            } else {
                segment.append(message.charAt(i));
                i++;
            }
        }

        // Flush remaining segment
        if (segment.length() > 0) {
            if (componentStarted) {
                json.append(",");
            }
            appendTextComponent(json, segment, color, bold, italic, underline, strikethrough, obfuscated);
        }

        json.append("]");
        return json.toString();
    }

    /**
     * Append a JSON text component to the builder for the given text segment.
     */
    private void appendTextComponent(final StringBuilder json, final StringBuilder text,
            final String color, final boolean bold, final boolean italic,
            final boolean underline, final boolean strikethrough, final boolean obfuscated) {
        final String escaped = text.toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
        json.append("{\"text\":\"").append(escaped).append("\"");
        if (color != null) {
            json.append(",\"color\":\"").append(color).append("\"");
        }
        if (bold) {
            json.append(",\"bold\":true");
        }
        if (italic) {
            json.append(",\"italic\":true");
        }
        if (underline) {
            json.append(",\"underline\":true");
        }
        if (strikethrough) {
            json.append(",\"strikethrough\":true");
        }
        if (obfuscated) {
            json.append(",\"obfuscated\":true");
        }
        json.append("}");
    }

    /**
     * Map a standard Minecraft color code character to its JSON color name.
     */
    private static String getMinecraftColorName(final char code) {
        switch (code) {
            case '0': return "black";
            case '1': return "dark_blue";
            case '2': return "dark_green";
            case '3': return "dark_aqua";
            case '4': return "dark_red";
            case '5': return "dark_purple";
            case '6': return "gold";
            case '7': return "gray";
            case '8': return "dark_gray";
            case '9': return "blue";
            case 'a': return "green";
            case 'b': return "aqua";
            case 'c': return "red";
            case 'd': return "light_purple";
            case 'e': return "yellow";
            case 'f': return "white";
            default: return null;
        }
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
        if (bukkitVersion.matches("^[0-9A-Za-z]+(\\.[0-9A-Za-z]+)*$")) {
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
        Bukkit.getLogger().severe("[LocaleLib] Received invalid Bukkit version " + bukkitVersion);
        return false;
    }
}

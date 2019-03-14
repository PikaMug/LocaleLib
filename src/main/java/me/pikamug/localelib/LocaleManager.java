package me.pikamug.localelib;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Villager.Career;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

public class LocaleManager{
	private static Class<?> craftMagicNumbers = null;
	private static Class<?> itemClazz = null;
	private static boolean oldVersion = false;
	private static boolean hasBasePotionData = false;
	private Map<String, String> oldBlocks = LocaleKeys.getBlockKeys();
	private Map<String, String> oldItems = LocaleKeys.getItemKeys();
	private Map<String, String> oldPotions = LocaleKeys.getPotionKeys();
	private Map<String, String> oldLingeringPotions = LocaleKeys.getLingeringPotionKeys();
	private Map<String, String> oldSplashPotions = LocaleKeys.getSplashPotionKeys();
	private Map<String, String> oldEntities = LocaleKeys.getEntityKeys();
	
	public LocaleManager() {
		oldVersion = isBelow113();
		if (Material.getMaterial("LINGERING_POTION") != null) {
			// Bukkit version is 1.9+
			hasBasePotionData = true;
		}
		String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
	    try {
	        craftMagicNumbers = Class.forName("org.bukkit.craftbukkit.{v}.util.CraftMagicNumbers".replace("{v}", version));
	        itemClazz = Class.forName("net.minecraft.server.{v}.Item".replace("{v}", version));
	    } catch (ClassNotFoundException e) {
	        e.printStackTrace();
	    }
	}
	
	/**
	 * Send message with item name translated to the client's locale.
	 * Material is required. Durability arg is arbitrary for 1.13+
	 * and can be ignored by setting to a value less than 0.
	 * Enchantments are optional and may be left null or empty.<p>
	 * 
	 * Message should contain {@code <item>} string for replacement by
	 * this method (along with applicable {@code <enchantment>} strings).
	 * 
	 * @param player The player whom the message is to be sent to
	 * @param message The message to be sent to the player
	 * @param material The item to be translated
	 * @param durability Durability for the item being translated
	 * @param enchantments Enchantments for the item being translated
	 */
	@SuppressWarnings("deprecation")
	public boolean sendMessage(Player player, String message, Material material, short durability, Map<Enchantment, Integer> enchantments) {
		if (material == null) {
			return false;
		}
		String matKey = "";
		String[] enchKeys = enchantments != null ? new String[enchantments.size()] : null;
		if (oldVersion) {
			if (material.isBlock()) {
				if (durability >= 0 && oldBlocks.containsKey(material.name() + "." + durability)) {
					matKey = oldBlocks.get(material.name() + "." + durability);
				} else if (oldBlocks.containsKey(material.name())) {
					matKey = oldBlocks.get(material.name());
				} else {
					Bukkit.getLogger().severe("[LocaleLib] Block not found: " + material.name() + "." + durability);
					return false;
				}
			} else {
				ItemStack i = new ItemStack(material, 1, durability);
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
					Bukkit.getLogger().severe("[LocaleLib] Item not found: " + material.name() + "." + durability);
					return false;
				}
			}
			if (enchantments != null && !enchantments.isEmpty()) {
				int count = 0;
				for (Enchantment e : enchantments.keySet()) {
					enchKeys[count] = "enchantment." + e.getName().toLowerCase().replace("_", ".")
						.replace("environmental", "all").replace("protection", "protect");
					count++;
				}
			}
		} else {
			try {
				matKey = queryMaterial(material);
			} catch (Exception ex) {
				Bukkit.getLogger().severe("[LocaleLib] Unable to query Material: " + material.name());
				return false;
			}
			if (enchantments != null && !enchantments.isEmpty()) {
				int count = 0;
				for (Enchantment e : enchantments.keySet()) {
					enchKeys[count] = "enchantment.minecraft." + e.toString().toLowerCase();
					count++;
				}
			}
		}
		String msg = message.replace("<item>", "\",{\"translate\":\"" + matKey + "\"},\"");
		if (enchKeys != null && enchKeys.length > 0) {
			for (String ek : enchKeys) {
				msg.replaceFirst("<enchantment>", "\",{\"translate\":\"" + ek + "\"},\"");
			}
		}
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " [\"" + msg + "\"]");
		return true;
	}
	
	/**
	 * Send message with enchantments translated to the client's locale.
	 * Map of Enchantment+level is required.
	 * 
	 * Message should contain one {@code <enchantment>} string for each
	 * replacement by this method.
	 * 
	 * @param player The player whom the message is to be sent to
	 * @param message The message to be sent to the player
	 * @param enchantments Enchantments for the item being translated
	 */
	@SuppressWarnings("deprecation")
	public boolean sendMessage(Player player, String message, Map<Enchantment, Integer> enchantments) {
		if (enchantments == null) {
			return false;
		}
		String[] enchKeys = enchantments != null ? new String[enchantments.size()] : null;
		if (oldVersion) {
			if (enchantments != null && !enchantments.isEmpty()) {
				int count = 0;
				for (Enchantment e : enchantments.keySet()) {
					enchKeys[count] = "enchantment." + e.getName().toLowerCase().replace("_", ".")
						.replace("environmental", "all").replace("protection", "protect");
					count++;
				}
			}
		} else {
			if (enchantments != null && !enchantments.isEmpty()) {
				int count = 0;
				for (Enchantment e : enchantments.keySet()) {
					enchKeys[count] = "enchantment.minecraft." + e.toString().toLowerCase();
					count++;
				}
			}
		}
		String msg = message;
		if (enchKeys != null && enchKeys.length > 0) {
			for (String ek : enchKeys) {
				msg.replaceFirst("<enchantment>", "\",{\"translate\":\"" + ek + "\"},\"");
			}
		}
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " [\"" + msg + "\"]");
		return true;
	}
	
	/**
	 * Send message with entity name translated to the client's locale.
	 * EntityType is required.<p>
	 * 
	 * Message should contain {@code <mob>}
	 * string for replacement by this method.
	 * 
	 * @param player The player whom the message is to be sent to
	 * @param message The message to be sent to the player
	 * @param type The entity type to be translated
	 * @param extra Career, Ocelot, or Rabbit type if applicable
	 */
	public boolean sendMessage(Player player, String message, EntityType type, String extra) {
		if (type == null ) {
			return false;
		}
		String key = "";
		if (oldVersion) {
			if (type.name().equals("VILLAGER") && Career.valueOf(extra) != null) {
				key = oldEntities.get(type.name() + "." + Career.valueOf(extra).name());
			} else if (type.name().equals("OCELOT") && Ocelot.Type.valueOf(extra) != null){
				key = oldEntities.get(type.name() + "." + Ocelot.Type.valueOf(extra).name());
			} else if (type.name().equals("RABBIT") && Rabbit.Type.valueOf(extra).equals(Rabbit.Type.THE_KILLER_BUNNY)) {
				key = oldEntities.get(type.name() + "." + Rabbit.Type.valueOf(extra).name());
			} else {
				key = oldEntities.get(type.name());
			}
		} else {
			key = "entity.minecraft." + type.toString().toLowerCase();
		}
		String msg = message.replace("<mob>", "\",{\"translate\":\"" + key + "\"},\"");
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " [\"" + msg + "\"]");
		return true;
	}
	
	/**
	 * Gets the key name of the specified material as it would appear in a Minecraft lang file.
	 * 
	 * @param material the material to check
	 * @return the raw key
	 * @throws IllegalArgumentException if an item with that material could not be found
	 */
	public String queryMaterial(Material material) throws IllegalArgumentException{
	    try {
	    	Object item = null;
	    	Method m = craftMagicNumbers.getDeclaredMethod("getItem", material.getClass());
	    	m.setAccessible(true);
	    	item = m.invoke(craftMagicNumbers, material);
	    	if (item == null) {
	    		throw new IllegalArgumentException(material.name() + " material could not be queried!");
	    	}                          
	    	String name = (String) itemClazz.getMethod("getName").invoke(item);
	    	return name;
	    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
	    	e.printStackTrace();
	    }
	    return null;
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
	 * Checks whether the server's Bukkit version is below 1.13.
	 * 
	 * @return true if Bukkit version is at 1.12.2 or below
	 */
	public boolean isBelow113() {
		return _isBelow113(Bukkit.getServer().getBukkitVersion().split("-")[0]);
	}
	
	private boolean _isBelow113(String bukkitVersion) {
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
		Bukkit.getLogger().severe("Quests received invalid Bukkit version " + bukkitVersion);
		return false;
	}
}
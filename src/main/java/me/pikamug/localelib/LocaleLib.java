package me.pikamug.localelib;

import org.bukkit.plugin.java.JavaPlugin;

public class LocaleLib extends JavaPlugin {
	private LocaleManager manager;
	
	@Override
	public void onEnable() {
		manager = new LocaleManager();
	}
	
	@Override
	public void onDisable() {
		
	}
	
	public LocaleManager getLocaleManager() {
		return manager;
	}
}

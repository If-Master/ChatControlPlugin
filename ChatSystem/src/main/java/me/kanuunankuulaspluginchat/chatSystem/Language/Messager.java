package me.kanuunankuulaspluginchat.chatSystem.Language;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Messager {
    private String language;
    private Plugin plugin;
    private LanguageManager languageManager;

    public Messager(JavaPlugin plugin, LanguageManager languageManager) {
        this.plugin = plugin;
        this.languageManager = languageManager;
    }

    private String GetLanguage() {
        FileConfiguration config = plugin.getConfig();
        String configLanguage = config.getString("language", "en");

        if (LanguageManager.isLanguageSupported(configLanguage)) {
            return configLanguage;
        } else {
            plugin.getLogger().warning("Language '" + configLanguage + "' is not supported. Falling back to English.");
            return "en";
        }

    }

    private String GetLanguageKey() {
        GetLanguage();
        language = GetLanguage();
        return GetLanguage();
    }
    public void sendMessage(Player player, String key) {
        String languagekey = GetLanguageKey();

        String text = languageManager.get(key, languagekey);

        player.sendMessage(text);
    }

    public void Broadcast() {

    }


}

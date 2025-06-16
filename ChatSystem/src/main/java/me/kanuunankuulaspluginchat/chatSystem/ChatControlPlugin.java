package me.kanuunankuulaspluginchat.chatSystem;

import me.kanuunankuulaspluginchat.chatSystem.Commands.ChatCommandExecutor;
import me.kanuunankuulaspluginchat.chatSystem.Commands.ChatCommandTabCompleter;
import me.kanuunankuulaspluginchat.chatSystem.compatibility.UniversalCompatibilityManager;
import me.kanuunankuulaspluginchat.chatSystem.listeners.ChatEventListener;
import me.kanuunankuulaspluginchat.chatSystem.managers.ChatManager;
import me.kanuunankuulaspluginchat.chatSystem.managers.UserProfileManager;
import me.kanuunankuulaspluginchat.chatSystem.storage.StorageManager;
import me.kanuunankuulaspluginchat.chatSystem.util.GroupAmount;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatControlPlugin extends JavaPlugin {
    private static StorageManager storageManager;
    private static ChatControlPlugin instance;
    private static Chat vaultChat;
    private static ChatManager chatManager;
    private static UserProfileManager profileManager;
    private static GroupAmount groupAmount;
    private static UniversalCompatibilityManager compatibilityManager;

    public static StorageManager getStorageManager() {
        return storageManager;
    }

    public static ChatManager getChatManager() {
        return chatManager;
    }

    public static GroupAmount groupAmount() {
        return groupAmount;
    }

    public static UserProfileManager getProfileManager() {
        return profileManager;
    }

    public static ChatControlPlugin getInstance() {
        return instance;
    }

    public static Chat getVaultChat() {
        return vaultChat;
    }

    @Override
    public void onEnable() {
        instance = this;

        compatibilityManager = new UniversalCompatibilityManager(this);
        profileManager = new UserProfileManager(this);
        storageManager = new StorageManager(this);
        groupAmount = new GroupAmount();

        saveDefaultConfig();


        chatManager = new ChatManager(profileManager, storageManager, this, compatibilityManager);

        PluginCommand chatCommand = getCommand("chat");
        if (chatCommand != null) {
            chatCommand.setExecutor(new ChatCommandExecutor());
            chatCommand.setTabCompleter(new ChatCommandTabCompleter(profileManager));
        } else {
            getLogger().severe("Failed to register /chat command.");
        }

        if (!setupVaultChat()) {
            getLogger().info("EssentialsChat not found, using internal chat formatter.");
        } else {
            getLogger().info("Successfully hooked into EssentialsChat via Vault!");
        }

        getServer().getPluginManager().registerEvents(
                new ChatEventListener(this, chatManager, profileManager, compatibilityManager), this
        );

        getLogger().info("ChatSystem has been enabled with " +
                compatibilityManager.getServerType().name() + " compatibility!");
    }

    @Override
    public void onDisable() {
        if (profileManager != null) {
            profileManager.saveAllProfiles();
        }

        if (storageManager != null) {
            storageManager.closeConnection();
        }

        getLogger().info("ChatControlPlugin has been disabled.");
    }

    private boolean setupVaultChat() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        if (rsp != null) {
            vaultChat = rsp.getProvider();
            return true;
        }
        return false;
    }
}

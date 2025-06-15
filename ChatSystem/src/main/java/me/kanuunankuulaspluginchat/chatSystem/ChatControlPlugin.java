package me.kanuunankuulaspluginchat.chatSystem;

import me.kanuunankuulaspluginchat.chatSystem.Commands.ChatCommandExecutor;
import me.kanuunankuulaspluginchat.chatSystem.Commands.ChatCommandTabCompleter;
import me.kanuunankuulaspluginchat.chatSystem.listeners.ChatEventListener;
import me.kanuunankuulaspluginchat.chatSystem.managers.ChatManager;
import me.kanuunankuulaspluginchat.chatSystem.managers.UserProfileManager;
import me.kanuunankuulaspluginchat.chatSystem.storage.StorageManager;
import me.kanuunankuulaspluginchat.chatSystem.util.GroupAmount;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.chat.Chat;

import org.bukkit.plugin.RegisteredServiceProvider;

public class ChatControlPlugin extends JavaPlugin {
    private static StorageManager storageManager;
    private static ChatControlPlugin instance;
    private static Chat vaultChat;
    private static ChatManager chatManager;
    private static UserProfileManager profileManager;
    private static GroupAmount groupAmount;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        profileManager = new UserProfileManager(this);
        storageManager = new StorageManager(this);
        groupAmount = new GroupAmount();


        chatManager = new ChatManager(profileManager, storageManager, this);

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
                new ChatEventListener(this, chatManager, profileManager), this
        );

        getLogger().info("ChatControlPlugin has been enabled.");
    }

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

    public static ChatControlPlugin getInstance() {
        return instance;
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

    public static Chat getVaultChat() {
        return vaultChat;
    }
}
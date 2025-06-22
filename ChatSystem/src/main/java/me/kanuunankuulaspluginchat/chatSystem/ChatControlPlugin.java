package me.kanuunankuulaspluginchat.chatSystem;

import me.kanuunankuulaspluginchat.chatSystem.Commands.ChatCommandExecutor;
import me.kanuunankuulaspluginchat.chatSystem.Commands.ChatCommandTabCompleter;
import me.kanuunankuulaspluginchat.chatSystem.Language.LanguageManager;
import me.kanuunankuulaspluginchat.chatSystem.Language.Messager;
import me.kanuunankuulaspluginchat.chatSystem.compatibility.UniversalCompatibilityManager;
import me.kanuunankuulaspluginchat.chatSystem.compatibility.UpdateChecker;
import me.kanuunankuulaspluginchat.chatSystem.listeners.ChatEventListener;
import me.kanuunankuulaspluginchat.chatSystem.managers.ChatManager;
import me.kanuunankuulaspluginchat.chatSystem.managers.UserProfileManager;
import me.kanuunankuulaspluginchat.chatSystem.storage.StorageManager;
import me.kanuunankuulaspluginchat.chatSystem.util.GroupAmount;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
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
    private static Messager messager;
    private static LanguageManager languagemanager;
    private String language;

    private UpdateChecker updateChecker;

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

        ChatControlPlugin.profileManager = new UserProfileManager(this);
        ChatControlPlugin.groupAmount = new GroupAmount();
        ChatControlPlugin.languagemanager = new LanguageManager(this);
        ChatControlPlugin.storageManager = new StorageManager(this, languagemanager);
        ChatControlPlugin.compatibilityManager = new UniversalCompatibilityManager(this, languagemanager);

        messager = new Messager(this, languagemanager);

        saveDefaultConfig();
        if (profileManager == null) getLogger().severe("profileManager is null!");
        if (storageManager == null) getLogger().severe("storageManager is null!");
        if (compatibilityManager == null) getLogger().severe("compatibilityManager is null!");
        if (messager == null) getLogger().severe("messager is null!");
        if (languagemanager == null) getLogger().severe("languagemanager is null!");

        UpdateChecker.setupConfig(this);
        updateChecker = new UpdateChecker(this, compatibilityManager, languagemanager);



        try {
            chatManager = new ChatManager(profileManager, storageManager, this, compatibilityManager, messager, languagemanager);
        } catch (Exception e) {
            getLogger().severe("Failed to create ChatManager: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        PluginCommand chatCommand = getCommand("chat");
        if (chatCommand != null) {
            chatCommand.setExecutor(new ChatCommandExecutor(messager, this));
            chatCommand.setTabCompleter(new ChatCommandTabCompleter(profileManager));
        } else {
            String languagekey = GetLanguageKey();
            String broadcastMsg = LanguageManager.get("Logger_failed_3", languagekey);

            getLogger().severe(broadcastMsg);
        }

        if (!setupVaultChat()) {
            String languagekey = GetLanguageKey();
            String broadcastMsg = LanguageManager.get("Logger_failed_4", languagekey);

            getLogger().info(broadcastMsg);
        } else {
            String languagekey = GetLanguageKey();
            String broadcastMsg = LanguageManager.get("Logger_Success_3", languagekey);

            getLogger().info(broadcastMsg);
        }

        registerUpdateCommands();

        getServer().getPluginManager().registerEvents(
                new ChatEventListener(this, chatManager, profileManager, compatibilityManager, messager), this
        );


        String languagekey = GetLanguageKey();
        String Msg1 = LanguageManager.get("Compatibility_Text_1", languagekey);
        String Msg2 = LanguageManager.get("Compatibility_Text_2", languagekey);

        getLogger().info(Msg1 +
                compatibilityManager.getServerType().name() + Msg2);

    }

    private String GetLanguage() {
        FileConfiguration config = this.getConfig();
        String configLanguage = config.getString("language", "en");

        if (LanguageManager.isLanguageSupported(configLanguage)) {
            return configLanguage;
        } else {
            this.getLogger().warning("Language '" + configLanguage + "' is not supported. Falling back to English.");
            return "en";
        }

    }
    private String GetLanguageKey() {
        GetLanguage();
        language = GetLanguage();
        return GetLanguage();
    }

    private void registerUpdateCommands() {
        PluginCommand updateCheckCommand = getCommand("cg_updatecheck");
        if (updateCheckCommand != null) {
            updateCheckCommand.setExecutor((sender, command, label, args) -> {
                if (!sender.hasPermission("chatsystem.admin") && !sender.isOp()) {
                    sender.sendMessage("§cYou don't have permission to use this command.");
                    return true;
                }

                sender.sendMessage("§6Checking for updates...");

                updateChecker.checkForUpdates(result -> {
                    compatibilityManager.runTask(() -> {
                        if (result.hasError()) {
                            String languagekey = GetLanguageKey();
                            String Msg = LanguageManager.get("Error_Update_1", languagekey);
                            sender.sendMessage(Msg + result.getError());
                        } else if (result.hasUpdate()) {
                            String languagekey = GetLanguageKey();
                            String Msg = LanguageManager.get("Success_Update_1", languagekey);
                            String Msg2 = LanguageManager.get("Success_Update_2", languagekey);
                            sender.sendMessage(Msg + result.getLatestVersion() +
                                    Msg2 + result.getCurrentVersion() + "§7)");

                            messager.sendMessage((Player) sender, "Success_Update_3");
                        } else {
                            String languagekey = GetLanguageKey();
                            String Msg = LanguageManager.get("Info_Update_1", languagekey);
                            sender.sendMessage(Msg + result.getCurrentVersion());
                        }
                    });
                });

                return true;
            });
            String languagekey = GetLanguageKey();

            String broadcastMsg = LanguageManager.get("Logger_Success_2", languagekey);
            getLogger().info(broadcastMsg);
        } else {
            String languagekey = GetLanguageKey();
            String broadcastMsg = LanguageManager.get("Logger_failed_2", languagekey);
            getLogger().warning(broadcastMsg);
        }

        PluginCommand updateCommand = getCommand("cg_update");
        if (updateCommand != null) {
            updateCommand.setExecutor((sender, command, label, args) -> {
                if (!sender.hasPermission("chatsystem.admin") && !sender.isOp()) {
                    sender.sendMessage("§cYou don't have permission to use this command.");
                    return true;
                }

                if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
                    messager.sendMessage((Player) sender, "Download_Warning_Info_1");
                    messager.sendMessage((Player) sender, "Download_Warning_Info_2");
                    messager.sendMessage((Player) sender, "Download_Warning_Info_3");
                    sender.sendMessage("");
                    messager.sendMessage((Player) sender, "Download_Warning_Info_4");
                    return true;
                }

                sender.sendMessage("§6Downloading update...");
                sender.sendMessage("§7This may take a few moments...");

                updateChecker.downloadUpdate(result -> {
                    compatibilityManager.runTask(() -> {
                        if (result.isSuccess()) {
                            messager.sendMessage((Player) sender, "Download_Succesfull_Info_1");
                            messager.sendMessage((Player) sender, "Download_Succesfull_Info_2");
                            sender.sendMessage("");
                            messager.sendMessage((Player) sender, "Download_Succesfull_Info_3");
                            sender.sendMessage("");
                            messager.sendMessage((Player) sender, "Download_Succesfull_Info_4");


                            String languagekey = GetLanguageKey();

                            String broadcastMsg = LanguageManager.get("Download_Success_Broadcast", languagekey);
                            compatibilityManager.broadcastToPermission("bukkit.command.op", broadcastMsg);

                        } else {
                            String languagekey = GetLanguageKey();

                            String message = LanguageManager.get("Download_Failed_Info_1", languagekey);
                            sender.sendMessage(message + " " + result.getMessage());
                            messager.sendMessage((Player) sender, "Download_Failed_Info_2");
                        }
                    });
                });

                return true;
            });

            updateCommand.setTabCompleter((sender, command, alias, args) -> {
                if (args.length == 1 && sender.hasPermission("chatsystem.admin")) {
                    return java.util.Arrays.asList("confirm");
                }
                return java.util.Collections.emptyList();
            });

            String languagekey = GetLanguageKey();

            String message = LanguageManager.get("Logger_Sucess_1", languagekey);

            getLogger().info(message);
        } else {
            String languagekey = GetLanguageKey();

            String message = LanguageManager.get("Logger_failed_1", languagekey);
            getLogger().warning(message);
        }
    }

    @Override
    public void onDisable() {
        if (profileManager != null) {
            profileManager.saveAllProfiles();
        }

        if (storageManager != null) {
            storageManager.closeConnection();
        }

        String languagekey = GetLanguageKey();

        String message = LanguageManager.get("Logger_disabled_1", languagekey);
        getLogger().info(message);
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

package me.kanuunankuulaspluginchat.chatSystem.managers;

import me.kanuunankuulaspluginchat.chatSystem.ChatControlPlugin;
import me.kanuunankuulaspluginchat.chatSystem.models.UserChatProfile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class UserProfileManager {

    private final ChatControlPlugin plugin;
    private final Map<UUID, UserChatProfile> loadedProfiles = new ConcurrentHashMap<>();
    private final File profilesDirectory;

    public UserProfileManager(ChatControlPlugin plugin) {
        this.plugin = plugin;
        this.profilesDirectory = new File(plugin.getDataFolder(), "profiles");

        if (!profilesDirectory.exists()) {
            profilesDirectory.mkdirs();
        }
    }

    public UserChatProfile getProfile(UUID playerId) {
        return loadedProfiles.computeIfAbsent(playerId, id -> {
            UserChatProfile profile = loadProfileFromFile(id);
            if (profile == null) {
                profile = new UserChatProfile();
                saveProfileToFile(id, profile);
            }
            return profile;
        });
    }

    public void loadProfile(UUID playerId) {
        if (!loadedProfiles.containsKey(playerId)) {
            CompletableFuture.runAsync(() -> {
                UserChatProfile profile = loadProfileFromFile(playerId);
                if (profile == null) {
                    profile = new UserChatProfile();
                }
                loadedProfiles.put(playerId, profile);
            });
        }
    }

    public void saveProfile(UUID playerId) {
        UserChatProfile profile = loadedProfiles.get(playerId);
        if (profile != null) {
            CompletableFuture.runAsync(() -> saveProfileToFile(playerId, profile));
        }
    }

    public void unloadProfile(UUID playerId) {
        loadedProfiles.remove(playerId);
    }

    public void saveAllProfiles() {
        CompletableFuture.runAsync(() -> {
            for (Map.Entry<UUID, UserChatProfile> entry : loadedProfiles.entrySet()) {
                saveProfileToFile(entry.getKey(), entry.getValue());
            }
            plugin.getLogger().info("Saved " + loadedProfiles.size() + " user profiles.");
        });
    }

    private UserChatProfile loadProfileFromFile(UUID playerId) {
        File profileFile = new File(profilesDirectory, playerId.toString() + ".yml");

        if (!profileFile.exists()) {
            return null;
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(profileFile);
            UserChatProfile profile = new UserChatProfile();

            if (config.contains("joinedChats")) {
                for (String chat : config.getStringList("joinedChats")) {
                    profile.joinChat(chat);
                }
            }

            if (config.contains("currentChat")) {
                profile.setCurrentChat(config.getString("currentChat"));
            }

            if (config.contains("hiddenChats")) {
                for (String chat : config.getStringList("hiddenChats")) {
                    profile.hideChat(chat);
                }
            }

            if (config.contains("chatNotifications")) {
                profile.setChatNotifications(config.getBoolean("chat.default-notifications", true));
            }

            if (config.contains("chatSounds")) {
                profile.setChatSounds(config.getBoolean("chat.default-sounds", false));
            }

            if (config.contains("messageCounts")) {
                for (String chat : config.getConfigurationSection("messageCounts").getKeys(false)) {
                    int count = config.getInt("messageCounts." + chat);
                    for (int i = 0; i < count; i++) {
                        profile.recordMessage(chat);
                    }
                }
            }

            if (config.contains("lastMessageTimes")) {
                for (String chat : config.getConfigurationSection("lastMessageTimes").getKeys(false)) {
                    long time = config.getLong("lastMessageTimes." + chat);
                    if (time > System.currentTimeMillis() - 86400000) {
                        profile.recordMessage(chat);
                    }
                }
            }

            return profile;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load profile for " + playerId + ": " + e.getMessage());
            return null;
        }
    }

    private void saveProfileToFile(UUID playerId, UserChatProfile profile) {
        File profileFile = new File(profilesDirectory, playerId.toString() + ".yml");

        try {
            FileConfiguration config = new YamlConfiguration();

            config.set("joinedChats", profile.getJoinedChats().toArray(new String[0]));

            config.set("currentChat", profile.getCurrentChat());

            config.set("hiddenChats", profile.getHiddenChats().toArray(new String[0]));

            config.set("chatNotifications", profile.isChatNotificationsEnabled());
            config.set("chatSounds", profile.isChatSoundsEnabled());

            for (String chat : profile.getJoinedChats()) {
                int count = profile.getMessageCount(chat);
                if (count > 0) {
                    config.set("messageCounts." + chat, count);
                }
            }

            for (String chat : profile.getJoinedChats()) {
                long time = profile.getLastMessageTime(chat);
                if (time > 0) {
                    config.set("lastMessageTimes." + chat, time);
                }
            }

            config.set("profileCreated", profile.getProfileCreated());

            config.save(profileFile);

        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save profile for " + playerId + ": " + e.getMessage());
        }
    }

    public int getTotalLoadedProfiles() {
        return loadedProfiles.size();
    }

    public void clearCache() {
        loadedProfiles.clear();
    }

    public void printProfileStats() {
        Bukkit.getLogger().info("=== Profile Manager Statistics ===");
        Bukkit.getLogger().info("Loaded profiles: " + loadedProfiles.size());

        int totalFiles = profilesDirectory.listFiles() != null ? profilesDirectory.listFiles().length : 0;
        Bukkit.getLogger().info("Total profile files: " + totalFiles);

        int totalMessages = 0;
        int totalChats = 0;

        for (UserChatProfile profile : loadedProfiles.values()) {
            totalMessages += profile.getTotalMessageCount();
            totalChats += profile.getJoinedChats().size();
        }

        Bukkit.getLogger().info("Total messages tracked: " + totalMessages);
        Bukkit.getLogger().info("Average chats per user: " + (loadedProfiles.size() > 0 ? totalChats / loadedProfiles.size() : 0));
    }
}

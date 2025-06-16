package me.kanuunankuulaspluginchat.chatSystem.util;

import me.kanuunankuulaspluginchat.chatSystem.ChatControlPlugin;
import me.kanuunankuulaspluginchat.chatSystem.storage.StorageManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class GroupAmount {

    public static int getMaxChannelsForPlayer(Player player) {
        if (player.hasPermission("chat.channels.unlimited")) {
            return -1;
        }

        ChatControlPlugin plugin = ChatControlPlugin.getInstance();
        int maxChannels = plugin.getConfig().getInt("chat.max-channels-per-player", 0);

        ConfigurationSection permLimits = plugin.getConfig().getConfigurationSection("chat.permission-channel-limits");
        if (permLimits != null) {
            for (String permission : permLimits.getKeys(false)) {
                if (player.hasPermission(permission)) {
                    int limit = permLimits.getInt(permission);
                    if (limit > maxChannels || limit == -1) {
                        maxChannels = limit;
                    }
                }
            }
        }

        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("chat.channels.limit." + i)) {
                return Math.max(maxChannels, i);
            }
        }

        return maxChannels;
    }

    public static CompletableFuture<Boolean> canPlayerCreateChannel(Player player) {
        int maxChannels = getMaxChannelsForPlayer(player);

        if (maxChannels == -1) {
            return CompletableFuture.completedFuture(true);
        }

        return getCurrentChannelCount(player).thenApply(currentChannels -> {
            return currentChannels < maxChannels;
        });
    }

    public static CompletableFuture<Integer> getCurrentChannelCount(Player player) {
        ChatControlPlugin plugin = ChatControlPlugin.getInstance();
        StorageManager storageManager = ChatControlPlugin.getStorageManager();

        return storageManager.getPlayerChannelCount(player.getUniqueId());
    }

    public static CompletableFuture<Void> incrementPlayerChannelCount(Player player) {
        ChatControlPlugin plugin = ChatControlPlugin.getInstance();
        StorageManager storageManager = ChatControlPlugin.getStorageManager();

        return storageManager.incrementPlayerChannelCount(player.getUniqueId());
    }

    public static CompletableFuture<Void> decrementPlayerChannelCount(Player player) {
        ChatControlPlugin plugin = ChatControlPlugin.getInstance();
        StorageManager storageManager = ChatControlPlugin.getStorageManager();

        return storageManager.decrementPlayerChannelCount(player.getUniqueId());
    }

    public static CompletableFuture<Void> recordChannelCreation(Player player, String channelName) {
        ChatControlPlugin plugin = ChatControlPlugin.getInstance();
        StorageManager storageManager = ChatControlPlugin.getStorageManager();

        return storageManager.recordChannelCreation(player.getUniqueId(), channelName);
    }

    public static CompletableFuture<Void> recordChannelDeletion(Player player, String channelName) {
        ChatControlPlugin plugin = ChatControlPlugin.getInstance();
        StorageManager storageManager = ChatControlPlugin.getStorageManager();

        return storageManager.recordChannelDeletion(player.getUniqueId(), channelName);
    }
}

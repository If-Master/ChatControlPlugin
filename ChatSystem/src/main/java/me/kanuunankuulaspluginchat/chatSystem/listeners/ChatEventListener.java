package me.kanuunankuulaspluginchat.chatSystem.listeners;

import me.kanuunankuulaspluginchat.chatSystem.ChatControlPlugin;
import me.kanuunankuulaspluginchat.chatSystem.compatibility.UniversalCompatibilityManager;
import me.kanuunankuulaspluginchat.chatSystem.Language.Messager;
import me.kanuunankuulaspluginchat.chatSystem.managers.ChatManager;
import me.kanuunankuulaspluginchat.chatSystem.managers.UserProfileManager;
import me.kanuunankuulaspluginchat.chatSystem.models.ChatChannel;
import me.kanuunankuulaspluginchat.chatSystem.models.UserChatProfile;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.CompletableFuture;

public class ChatEventListener implements Listener {

    private final ChatControlPlugin plugin;
    private final ChatManager chatManager;
    private final UserProfileManager profileManager;
    private final UniversalCompatibilityManager compatibilityManager;
    private final Messager messager;

    public ChatEventListener(ChatControlPlugin plugin, ChatManager chatManager, UserProfileManager profileManager, UniversalCompatibilityManager universalCompatibilityManager, Messager Messager) {
        this.plugin = plugin;
        this.messager = Messager;
        this.chatManager = chatManager;
        this.profileManager = profileManager;
        this.compatibilityManager = universalCompatibilityManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        event.setCancelled(true);

        ChatControlPlugin.getStorageManager().isUserBanned(player.getUniqueId()).thenAccept(isBanned -> {
            if (isBanned) {
                compatibilityManager.runPlayerTask(player, () -> {
                    messager.sendMessage(player, "Chat_Warnings_31");
                });
                return;
            }

            processChatMessage(player, message);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();
        Bukkit.getLogger().info(command);

        if (command.startsWith("/msg") || command.startsWith("/tell") ||
                command.startsWith("/whisper") || command.startsWith("/w") ||
                command.startsWith("/ewhisper") || command.startsWith("/ew") ||
                command.startsWith("/emsg") || command.startsWith("/etell") ||
                command.startsWith("/r") || command.startsWith("/er") ||
                command.startsWith("/reply") || command.startsWith("/ereply") ||
                command.startsWith("/emessage") || command.startsWith("/epm") ||
                command.startsWith("/message") || command.startsWith("/pm")) {

            if (areChatsGloballyFrozen() && !player.hasPermission("chat.admin")) {
                event.setCancelled(true);
                compatibilityManager.runPlayerTask(player, () -> {
                    messager.sendMessage(player, "Chat_Warnings_114");
                });
                return;
            }

            event.setCancelled(true);

            ChatControlPlugin.getStorageManager().isUserBanned(player.getUniqueId())
                    .thenCompose(isBanned -> {
                        if (isBanned) {
                            compatibilityManager.runPlayerTask(player, () -> {
                                messager.sendMessage(player, "Chat_Warnings_31");
                            });
                            return CompletableFuture.completedFuture("banned");
                        }
                        return ChatControlPlugin.getStorageManager().getChatPermission(player.getUniqueId(), "public");
                    })
                    .thenAccept(result -> {
                        if ("banned".equals(result)) {
                            return;
                        }

                        if ("muted".equals(result)) {
                            compatibilityManager.runPlayerTask(player, () -> {
                                messager.sendMessage(player, "Chat_Warnings_115");
                            });
                            return;
                        }

                        compatibilityManager.runPlayerTask(player, () -> {
                            String originalCommand = event.getMessage();
                            Bukkit.dispatchCommand(player, originalCommand.substring(1));
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Error checking permissions for command: " + throwable.getMessage());
                        compatibilityManager.runPlayerTask(player, () -> {
                            messager.sendMessage(player, "Chat_Warnings_116");
                        });
                        return null;
                    });
        }
    }

    /**
     * Check if chats are globally frozen by checking if all channels are frozen
     */
    private boolean areChatsGloballyFrozen() {
        java.util.Collection<ChatChannel> channels = ChatManager.getChannels().values();

        if (channels.isEmpty()) {
            return false;
        }

        return channels.stream().allMatch(ChatChannel::isFrozen);
    }

    private void processChatMessage(Player player, String message) {
        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());
        String currentChat = profile.getCurrentChat();

        ChatChannel channel = ChatManager.getChannel(currentChat);
        if (channel == null) {
            currentChat = "public";
            channel = ChatManager.getChannel("public");
            profile.setCurrentChat("public");
        }

        final String finalChatName = currentChat;
        final ChatChannel finalChannel = channel;

        if (!finalChannel.canPlayerSpeak(player)) {
            compatibilityManager.runPlayerTask(player, () -> {
                messager.sendMessage(player, "Chat_Warnings_117");
            });
            return;
        }

        ChatControlPlugin.getStorageManager().getChatPermission(player.getUniqueId(), finalChatName)
                .thenCompose(permission -> {
                    if ("muted".equals(permission)) {
                        compatibilityManager.runPlayerTask(player, () ->
                                messager.sendMessage(player, "Chat_Warnings_118"));
                        return CompletableFuture.completedFuture(null);
                    }

                    return ChatControlPlugin.getStorageManager().isUserInChat(player.getUniqueId(), finalChatName);
                })
                .thenAccept(isInChat -> {
                    if (isInChat == null) {
                        return;
                    }

                    if (!isInChat && !finalChatName.equals("public")) {
                        compatibilityManager.runPlayerTask(player, () ->
                                messager.sendMessage(player, "Chat_Warnings_119"));
                        return;
                    }

                    compatibilityManager.runPlayerTask(player, () -> {
                        String formattedMessage = formatMessage(player, message, finalChatName);
                        sendMessageToChannel(finalChannel, player, formattedMessage, message, finalChatName);

                        ChatControlPlugin.getStorageManager().logChatMessage(finalChatName, player.getName(), message);
                        profile.recordMessage(finalChatName);
                    });
                })
                .exceptionally(throwable -> {
                    if (throwable != null) {
                        plugin.getLogger().severe("Error processing chat message: " + throwable.getMessage());
                        compatibilityManager.runPlayerTask(player, () ->
                                messager.sendMessage(player, "Chat_Warnings_120"));
                    }
                    return null;
                });
    }

    private String formatMessage(Player player, String message, String chatName) {
        Chat vaultChat = ChatControlPlugin.getVaultChat();
        String prefix = "";
        String suffix = "";

        if (vaultChat != null) {
            prefix = vaultChat.getPlayerPrefix(player);
            suffix = vaultChat.getPlayerSuffix(player);

            if (prefix == null) prefix = "";
            if (suffix == null) suffix = "";
        }

        ChatChannel channel = ChatManager.getChannel(chatName);
        String channelPrefix = channel != null ? channel.getDisplayPrefix() : "";

        return String.format("%s%s%s%s§f: %s",
                channelPrefix,
                prefix,
                player.getDisplayName(),
                suffix,
                message
        );
    }

    private void sendMessageToChannel(ChatChannel channel, Player sender, String formattedMessage, String rawMessage, String chatName) {
        ChatControlPlugin.getStorageManager().getChatMembers(chatName).thenAccept(members -> {
            compatibilityManager.runTask(() -> {
                int recipientCount = 0;

                if ("public".equals(chatName)) {
                    for (Player recipient : Bukkit.getOnlinePlayers()) {
                        UserChatProfile recipientProfile = profileManager.getProfile(recipient.getUniqueId());

                        boolean shouldReceive = recipientProfile.isChatNotificationsEnabled() || sender.equals(recipient);

                        if (recipientProfile.isChatHidden("public")) {
                            shouldReceive = false;
                        }

                        if (!channel.canPlayerReceive(recipient)) {
                            shouldReceive = false;
                        }

                        if (shouldReceive) {
                            recipient.sendMessage(formattedMessage);
                            recipientCount++;
                        }
                    }
                } else {
                    for (Player recipient : Bukkit.getOnlinePlayers()) {
                        boolean shouldReceive = shouldReceiveMessage(recipient, channel, sender, chatName, members);

                        if (shouldReceive) {
                            recipient.sendMessage(formattedMessage);
                            recipientCount++;
                        }
                    }
                }

                Bukkit.getConsoleSender().sendMessage(formattedMessage);
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("=== ERROR IN getChatMembers ===");
            plugin.getLogger().severe("Error: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });
    }

    private boolean shouldReceiveMessage(Player recipient, ChatChannel channel, Player sender, String chatName, java.util.Set<java.util.UUID> chatMembers) {
        UserChatProfile profile = profileManager.getProfile(recipient.getUniqueId());

        if (sender.equals(recipient)) {
            return true;
        }

        if (!profile.isChatNotificationsEnabled()) {
            return false;
        }

        if (profile.isChatHidden(channel.getName())) {
            return false;
        }

        if (!channel.canPlayerReceive(recipient)) {
            return false;
        }

        if ("public".equals(chatName)) {
            return true;
        }

        boolean isMember = chatMembers != null && chatMembers.contains(recipient.getUniqueId());
        return isMember;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        profileManager.loadProfile(player.getUniqueId());

        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());
        if (!profile.isInChat("public")) {
            profile.joinChat("public");
        }

        profile.setCurrentChat("public");


        ChatControlPlugin.getStorageManager().addUserToChat(player.getUniqueId(), "public");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        profileManager.saveProfile(player.getUniqueId());
        profileManager.unloadProfile(player.getUniqueId());
    }
}

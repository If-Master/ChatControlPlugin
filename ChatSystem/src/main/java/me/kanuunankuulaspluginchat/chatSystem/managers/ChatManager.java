package me.kanuunankuulaspluginchat.chatSystem.managers;

import me.kanuunankuulaspluginchat.chatSystem.compatibility.UniversalCompatibilityManager;
import me.kanuunankuulaspluginchat.chatSystem.models.ChatChannel;
import me.kanuunankuulaspluginchat.chatSystem.models.UserChatProfile;
import me.kanuunankuulaspluginchat.chatSystem.storage.StorageManager;
import me.kanuunankuulaspluginchat.chatSystem.util.GroupAmount;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;



public class ChatManager {

    private static final Map<String, ChatChannel> channels = new ConcurrentHashMap<>();
    private static UserProfileManager profileManager;
    private static StorageManager storageManager;
    private static Plugin chatControlPlugin;
    private static UniversalCompatibilityManager compatibilityManager;
    private static Boolean displayusername;
    private static boolean globallyFrozen = false;


    public ChatManager(UserProfileManager profileManager, StorageManager storageManager, Plugin plugin, UniversalCompatibilityManager compatibilityManager) {
        if (profileManager == null) {
            throw new IllegalArgumentException("UserProfileManager cannot be null");
        }
        if (storageManager == null) {
            throw new IllegalArgumentException("StorageManager cannot be null");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (compatibilityManager == null) {
            throw new IllegalArgumentException("UniversalCompatibilityManager cannot be null");
        }

        ChatManager.profileManager = profileManager;
        ChatManager.storageManager = storageManager;
        ChatManager.chatControlPlugin = plugin;
        ChatManager.compatibilityManager = compatibilityManager;

        initializeDefaultChannels();
        displayusername = chatControlPlugin.getConfig().getBoolean("Display_Admin");
    }

    public static ChatChannel getChannel(String name) {
        return channels.get(name.toLowerCase());
    }
    public static UniversalCompatibilityManager getCompatibilityManager() {
        return compatibilityManager;
    }

    public static void displaycommands(Player player) {
        player.sendMessage("§6=== Chat Commands ===");
        player.sendMessage("§e/chat help §7- Show this help menu");
        player.sendMessage("§e/chat public §7- Switch to public chat");
        player.sendMessage("§e/chat join <name> §7- Join a chat channel");
        player.sendMessage("§e/chat leave <name> §7- Leave a chat channel");
        player.sendMessage("§e/chat create <name> §7- Create a new chat channel");
        player.sendMessage("§e/chat select <name> §7- Switch to a chat channel");
        player.sendMessage("§e/chat invite <player> <channel> §7- Invite a player to a channel");
        player.sendMessage("§e/chat hide <channel> §7- Hide/unhide a channel");


        if (player.hasPermission("chat.admin")) {
            player.sendMessage("§c=== Admin Commands ===");
            player.sendMessage("§e/chat staff §7- Switch to staff chat");
            player.sendMessage("§e/chat mute <player> <channel> §7- Mute a player in channel");
            player.sendMessage("§e/chat unmute <player> <channel> §7- Unmute a player in channel");
            player.sendMessage("§e/chat ban <player>  §7- Ban a player from all channel");
            player.sendMessage("§e/chat unban <player> §7- Unban a player from all chats");
            player.sendMessage("§e/chat investigation <player> §7- Toggle investigation mode");
            player.sendMessage("§e/chat freeze §7- Toggle chat freeze mode");
            player.sendMessage("§e/chat clear §7- Clear chat history");
        }
        if (player.hasPermission("chat.dev")) {
            player.sendMessage("§e/chat dev §7- Switch to dev chat");

        }
        player.sendMessage("§e=== Channel Management ===");
        player.sendMessage("§e/chat kick <player> <channel> §7- Kick a player from your channel");
        player.sendMessage("§e/chat transfer <player> <channel> §7- Transfer your channel ownership");
        player.sendMessage("§e/chat trust <player> <channel> §7- Trust a player in your channel");
        player.sendMessage("§e/chat manager <player> <channel> §7- Make player a manager in your channel");
        player.sendMessage("§e/chat unblock <player> <channel> §7- Unblock a player from your channel");

    }

    public static void createChat(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /chat create <name>");
            return;
        }

        String chatName = args[1].toLowerCase();

        storageManager.isUserBanned(player.getUniqueId()).thenAccept(isBanned -> {
            if (isBanned) {
                player.sendMessage("§cYou are banned from creating chat channels!");
                return;
            }

            GroupAmount.canPlayerCreateChannel(player).thenAccept(canCreate -> {
                if (canCreate) {
                    if (channels.containsKey(chatName)) {
                        player.sendMessage("§cA chat channel with that name already exists!");
                        return;
                    }
                    if (chatName.length() > 16) {
                        player.sendMessage("§cChat name must be 16 characters or less!");
                        return;
                    }

                    String description = "Custom chat channel created by " + player.getName();
                    ChatChannel channel = new ChatChannel(chatName, "§7[§e" + chatName + "§7] ", true, player.getUniqueId(), description, null);

                    channels.put(chatName, channel);
                    storageManager.saveChannel(channel);

                    storageManager.setChatPermission(player.getUniqueId(), chatName, "owner", null);

                    GroupAmount.recordChannelCreation(player, chatName);

                    UserChatProfile profile = profileManager.getProfile(player.getUniqueId());
                    profile.joinChat(chatName);

                    storageManager.addUserToChat(player.getUniqueId(), chatName);

                    player.sendMessage("§aSuccessfully created chat channel: §e" + chatName);
                    player.sendMessage("§7You have been automatically added to the channel.");
                } else {
                    player.sendMessage("§cYou've reached your channel limit!");
                }
            });
        });
    }

    public static void joinChat(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /chat join <name>");
            return;
        }

        String chatName = args[1].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            player.sendMessage("§cThat chat channel doesn't exist!");
            return;
        }

        storageManager.isUserBanned(player.getUniqueId()).thenAccept(isBanned -> {
            if (isBanned) {
                storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
                    if (!"owner".equals(permission)) {
                        player.sendMessage("§cYou are banned from joining chat channels!");
                        return;
                    }

                    storageManager.isUserBlockedFromChannel(player.getUniqueId(), chatName).thenAccept(isBlocked -> {
                                if (isBlocked) {
                                    player.sendMessage("§cYou cannot join this channel! You need to be re-invited.");
                                    return;
                                }

                                if (!channel.canPlayerJoin(player) && !player.hasPermission("chat.forcejoin")) {
                                    player.sendMessage("§cYou don't have permission to join that chat!");
                                    return;
                                }

                        joinChatInternal(player, chatName, channel);
                    });
                });
                return;
            }

            if (!channel.canPlayerJoin(player) && !player.hasPermission("chat.forcejoin")) {
                player.sendMessage("§cYou don't have permission to join that chat!");
                return;
            }

            joinChatInternal(player, chatName, channel);
        });
    }

    public static void unblockUser(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /chat unblock <player> <channel>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            player.sendMessage("§cThat chat channel doesn't exist!");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if (!"owner".equals(permission) && !"manager".equals(permission) && !player.hasPermission("chat.admin")) {
                player.sendMessage("§cYou don't have permission to unblock users from this channel!");
                return;
            }

            storageManager.isUserBlockedFromChannel(target.getUniqueId(), chatName).thenAccept(isBlocked -> {
                if (!isBlocked) {
                    player.sendMessage("§c" + target.getName() + " is not blocked from channel " + chatName + "!");
                    return;
                }

                storageManager.unblockUserFromChannel(target.getUniqueId(), chatName);

                player.sendMessage("§aSuccessfully unblocked §e" + target.getName() + "§a from channel §e" + chatName);
                target.sendMessage("§aYou have been unblocked from channel §e" + chatName + "§a by §e" + player.getName());
                target.sendMessage("§7You can now join this channel again.");
            });
        });
    }

    private static void joinChatInternal(Player player, String chatName, ChatChannel channel) {
        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        storageManager.isUserInChat(player.getUniqueId(), chatName).thenAccept(isInChat -> {
            if (isInChat) {
                profile.setCurrentChat(chatName);
                player.sendMessage("§aSuccessfully switched to chat channel: §e" + chatName);
                return;
            }

            profile.joinChat(chatName);
            storageManager.addUserToChat(player.getUniqueId(), chatName);
            player.sendMessage("§aSuccessfully joined chat channel: §e" + chatName);
        });
    }

    public static void leaveChat(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /chat leave <name>");
            return;
        }

        String chatName = args[1].toLowerCase();

        if (chatName.equals("public")) {
            player.sendMessage("§cYou cannot leave the public chat!");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if ("owner".equals(permission)) {
                player.sendMessage("§cYou cannot leave a channel you own! Use /chat transfer to transfer ownership first.");
                return;
            }

            UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

            storageManager.isUserInChat(player.getUniqueId(), chatName).thenAccept(isInChat -> {
                if (!isInChat) {
                    player.sendMessage("§cYou're not in that chat channel!");
                    return;
                }

                profile.leaveChat(chatName);
                storageManager.removeUserFromChat(player.getUniqueId(), chatName);
                storageManager.removeChatPermission(player.getUniqueId(), chatName);
                storageManager.blockUserFromChannel(player.getUniqueId(), chatName, player.getUniqueId(), "Left channel voluntarily");

                player.sendMessage("§aSuccessfully left chat channel: §e" + chatName);
                player.sendMessage("§7Note: You will need to be re-invited to join this channel again.");
            });
        });
    }

    public static void selectChat(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /chat select <name>");
            return;
        }

        String chatName = args[1].toLowerCase();
        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        storageManager.isUserBanned(player.getUniqueId()).thenAccept(isBanned -> {
            if (isBanned) {
                storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
                    if (!"owner".equals(permission)) {
                        player.sendMessage("§cYou are banned from using chat channels!");
                        return;
                    }

                    selectChatInternal(player, chatName, profile);
                });
                return;
            }

            selectChatInternal(player, chatName, profile);
        });
    }

    private static void selectChatInternal(Player player, String chatName, UserChatProfile profile) {
        storageManager.isUserInChat(player.getUniqueId(), chatName).thenAccept(isInChat -> {
            if (!isInChat) {
                if ("staff".equalsIgnoreCase(chatName) && player.hasPermission("chat.staff")) {
                    player.sendMessage("§cYou're not in that chat channel! Use /chat " + chatName + " .");
                } else if ("dev".equalsIgnoreCase(chatName) && player.hasPermission("chat.dev")) {
                    player.sendMessage("§cYou're not in that chat channel! Use /chat " + chatName + " .");
                } else {
                    player.sendMessage("§cYou're not in that chat channel! Use /chat join " + chatName + " first.");
                    return;
                }
            }

            profile.setCurrentChat(chatName);
            player.sendMessage("§aSuccessfully switched to chat channel: §e" + chatName);
        });
    }

    public static void inviteToChat(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /chat invite <player> <channel>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            player.sendMessage("§cThat chat channel doesn't exist!");
            return;
        }

        storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
            if (isBanned) {
                player.sendMessage("§c" + target.getName() + " is banned from chat channels!");
                return;
            }

            storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
                if (permission == null || (!permission.equals("owner") && !permission.equals("manager") && !player.hasPermission("chat.admin"))) {
                    player.sendMessage("§cYou don't have permission to invite players to this chat!");
                    return;
                }

                storageManager.isUserInChat(target.getUniqueId(), chatName).thenAccept(isInChat -> {
                    if (isInChat) {
                        player.sendMessage("§c" + target.getName() + " is already in that chat channel!");
                        return;
                    }

                    UserChatProfile targetProfile = profileManager.getProfile(target.getUniqueId());
                    targetProfile.joinChat(chatName);
                    storageManager.addUserToChat(target.getUniqueId(), chatName);

                    player.sendMessage("§aSuccessfully invited §e" + target.getName() + "§a to §e" + chatName);
                    target.sendMessage("§a" + player.getName() + " §ahas invited you to chat channel: §e" + chatName);
                    target.sendMessage("§7Use §e/chat select " + chatName + "§7 to switch to it.");
                });
            });
        });
    }

    public static void joinPublicChat(Player player) {
        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());
        profile.setCurrentChat("public");
        player.sendMessage("§aSuccessfully switched to public chat.");
    }


    public static Map<String, ChatChannel> getChannels() {
        return channels; // or whatever your channels collection is called
    }


    public static void joinStaffChat(Player player) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (!player.hasPermission("chat.staff")) {
            player.sendMessage("§cYou don't have permission to access staff chat!");
            return;
        }

        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        storageManager.isUserInChat(player.getUniqueId(), "staff").thenAccept(isInChat -> {
            if (!isInChat) {
                profile.joinChat("staff");
                storageManager.addUserToChat(player.getUniqueId(), "staff");
            }

            profile.setCurrentChat("staff");
            player.sendMessage("§aSuccessfully switched to staff chat.");
        });
    }

    public static void joinDevChat(Player player) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (!player.hasPermission("chat.dev")) {
            player.sendMessage("§cYou don't have permission to access dev chat!");
            return;
        }

        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        storageManager.isUserInChat(player.getUniqueId(), "dev").thenAccept(isInChat -> {
            if (!isInChat) {
                profile.joinChat("dev");
                storageManager.addUserToChat(player.getUniqueId(), "dev");
            }

            profile.setCurrentChat("dev");
            player.sendMessage("§aSuccessfully switched to dev chat.");
        });
    }

    public static void hideChat(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /chat hide <channel>");
            return;
        }

        String chatName = args[1].toLowerCase();
        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        storageManager.isUserInChat(player.getUniqueId(), chatName).thenAccept(isInChat -> {
            if (!isInChat) {
                player.sendMessage("§cYou're not in that chat channel!");
                return;
            }

            profile.toggleChatVisibility(chatName);

            if (profile.isChatHidden(chatName)) {
                player.sendMessage("§aHidden chat channel: §e" + chatName);
            } else {
                player.sendMessage("§aUnhidden chat channel: §e" + chatName);
            }
        });
    }

    public static boolean isGloballyFrozen() {
        return globallyFrozen;
    }

    public static void setGloballyFrozen(boolean frozen) {
        globallyFrozen = frozen;
    }

    public static void freezeChats(Player player) {
        if (!player.hasPermission("chat.admin")) {
            player.sendMessage("§cYou don't have permission to freeze chats!");
            return;
        }

        boolean shouldFreeze = !globallyFrozen;
        setGloballyFrozen(shouldFreeze);

        for (ChatChannel channel : channels.values()) {
            channel.setFrozen(shouldFreeze);
        }

        String status = shouldFreeze ? "frozen" : "unfrozen";
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage("§c[ADMIN] §eAll chat channels have been " + status + " by " + player.getName());
        }
    }

    public static void clearChat(Player player, String[] args) {
        if (!player.hasPermission("chat.admin")) {
            player.sendMessage("§cYou don't have permission to clear chats!");
            return;
        }

        String chatName = args.length > 1 ? args[1].toLowerCase() : "public";

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UserChatProfile profile = profileManager.getProfile(onlinePlayer.getUniqueId());
            if (profile.isInChat(chatName)) {
                for (int i = 0; i < 100; i++) {
                    onlinePlayer.sendMessage("");
                }
                onlinePlayer.sendMessage("§c[ADMIN] §eChat cleared by " + player.getName());
            }
        }
    }

    public static void transferOwnership(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /chat transfer <player> <channel>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            player.sendMessage("§cThat chat channel doesn't exist!");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if (!"owner".equals(permission) && !player.hasPermission("chat.admin")) {
                player.sendMessage("§cYou're not the owner of this channel!");
                return;
            }

            storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
                if (isBanned) {
                    player.sendMessage("§c" + target.getName() + " is banned and cannot own channels!");
                    return;
                }

                storageManager.removeChatPermission(player.getUniqueId(), chatName);
                storageManager.setChatPermission(target.getUniqueId(), chatName, "owner", player.getUniqueId());

                channel.setOwner(target.getUniqueId());

                storageManager.addUserToChat(target.getUniqueId(), chatName);

                player.sendMessage("§aSuccessfully transferred ownership of §e" + chatName + "§a to §e" + target.getName());
                target.sendMessage("§a" + player.getName() + " §ahas transferred ownership of §e" + chatName + "§a to you!");
            });
        });
    }

    public static void trustUser(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /chat trust <player> <channel>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            player.sendMessage("§cThat chat channel doesn't exist!");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if (!"owner".equals(permission) && !"manager".equals(permission) && !player.hasPermission("chat.admin")) {
                player.sendMessage("§cYou don't have permission to trust users in this channel!");
                return;
            }

            storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
                if (isBanned) {
                    player.sendMessage("§c" + target.getName() + " is banned and cannot be trusted!");
                    return;
                }

                storageManager.setChatPermission(target.getUniqueId(), chatName, "trusted", player.getUniqueId());
                storageManager.addUserToChat(target.getUniqueId(), chatName);

                player.sendMessage("§aSuccessfully trusted §e" + target.getName() + "§a in channel §e" + chatName);
                target.sendMessage("§aYou have been trusted in channel §e" + chatName + "§a by §e" + player.getName());
            });
        });
    }

    public static void assignManager(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /chat manager <player> <channel>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            player.sendMessage("§cThat chat channel doesn't exist!");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if (!"owner".equals(permission) && !player.hasPermission("chat.admin")) {
                player.sendMessage("§cOnly the channel owner can assign managers!");
                return;
            }

            storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
                if (isBanned) {
                    player.sendMessage("§c" + target.getName() + " is banned and cannot be a manager!");
                    return;
                }

                storageManager.setChatPermission(target.getUniqueId(), chatName, "manager", player.getUniqueId());
                storageManager.addUserToChat(target.getUniqueId(), chatName);

                player.sendMessage("§aSuccessfully made §e" + target.getName() + "§a a manager of §e" + chatName);
                target.sendMessage("§aYou have been made a manager of channel §e" + chatName + "§a by §e" + player.getName());
            });
        });
    }

    public static void muteUser(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /chat mute <player> <channel>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            player.sendMessage("§cThat chat channel doesn't exist!");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if (!"owner".equals(permission) && !"manager".equals(permission) && !player.hasPermission("chat.admin")) {
                compatibilityManager.runPlayerTask(player, () ->
                        player.sendMessage("§cYou don't have permission to mute users in this channel!"));
                return;
            }

            storageManager.setChatPermission(target.getUniqueId(), chatName, "muted", player.getUniqueId())
                    .thenRun(() -> {
                        compatibilityManager.runPlayerTask(player, () -> {
//                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mute " + player.getName());
                            player.sendMessage("§aSuccessfully muted §e" + target.getName() + "§a in channel §e" + chatName);
                            if (displayusername) {
                                target.sendMessage("§cYou have been muted in channel §e" + chatName + "§c by §e" + player.getName());
                            } else {
                                target.sendMessage("§cYou have been muted in channel §e" + chatName);
                            }
                        });
                    });
        });
    }

    public static void unmute(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /chat unmute <player> <channel>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            player.sendMessage("§cThat chat channel doesn't exist!");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName)
                .thenCompose(permission -> {
                    if (!"owner".equals(permission) && !"manager".equals(permission) && !player.hasPermission("chat.admin")) {
                        compatibilityManager.runPlayerTask(player, () ->
                                player.sendMessage("§cYou don't have permission to unmute users in this channel!"));
                        return CompletableFuture.completedFuture(null);
                    }

                    return storageManager.getChatPermission(target.getUniqueId(), chatName);
                })
                .thenCompose(targetPermission -> {
                    if (targetPermission == null) {
                        return CompletableFuture.completedFuture(null);
                    }

                    if (!"muted".equals(targetPermission)) {
                        compatibilityManager.runPlayerTask(player, () ->
                                player.sendMessage("§c" + target.getName() + " is not muted in channel " + chatName + "!"));
                        return CompletableFuture.completedFuture(null);
                    }

                    return storageManager.unmutePlayer(target.getUniqueId(), chatName);
                })
                .thenRun(() -> {
                    compatibilityManager.runPlayerTask(player, () -> {
                        player.sendMessage("§aSuccessfully unmuted §e" + target.getName() + "§a in channel §e" + chatName);
                        if (displayusername) {
                            target.sendMessage("§aYou have been unmuted in channel §e" + chatName + "§a by §e" + player.getName());
                        } else {
                            target.sendMessage("§aYou have been unmuted in channel §e" + chatName);
                        }
//                        String command = "mute " + target.getName() + " 1s";
//                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command + player.getName());

                    });
                })
                .exceptionally(throwable -> {
                    if (throwable != null) {
                        compatibilityManager.severe("Error during unmute operation: " + throwable.getMessage());
                        compatibilityManager.runPlayerTask(player, () ->
                                player.sendMessage("§cAn error occurred while unmuting the player."));
                    }
                    return null;
                });
    }

    public static void kickUser(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /chat kick <player> <channel>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            player.sendMessage("§cThat chat channel doesn't exist!");
            return;
        }

        storageManager.getChatPermission(target.getUniqueId(), chatName).thenAccept(targetPermission -> {
            if ("owner".equals(targetPermission)) {
                player.sendMessage("§cYou cannot kick the owner of the channel!");
                return;
            }

            storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
                if (!"owner".equals(permission) && !"manager".equals(permission) && !player.hasPermission("chat.admin")) {
                    player.sendMessage("§cYou don't have permission to kick users from this channel!");
                    return;
                }

                storageManager.removeUserFromChat(target.getUniqueId(), chatName);
                storageManager.removeChatPermission(target.getUniqueId(), chatName);

                storageManager.blockUserFromChannel(target.getUniqueId(), chatName, player.getUniqueId(), "Kicked from channel");


                UserChatProfile targetProfile = profileManager.getProfile(target.getUniqueId());
                targetProfile.leaveChat(chatName);

                player.sendMessage("§aSuccessfully kicked §e" + target.getName() + "§a from channel §e" + chatName);
                target.sendMessage("§cYou have been kicked from channel §e" + chatName + "§c by §e" + player.getName());
                target.sendMessage("§7You will need to be re-invited to join this channel again.");
            });
        });

    }

    public static void forceKickUser(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (!player.hasPermission("chat.admin")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /chat forcekick <player> <channel>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        String chatName = args[2].toLowerCase();

        storageManager.removeUserFromChat(target.getUniqueId(), chatName);
        storageManager.removeChatPermission(target.getUniqueId(), chatName);

        UserChatProfile targetProfile = profileManager.getProfile(target.getUniqueId());
        targetProfile.leaveChat(chatName);

        player.sendMessage("§aSuccessfully force-kicked §e" + target.getName() + "§a from channel §e" + chatName);
        target.sendMessage("§cYou have been force-kicked from channel §e" + chatName + "§c by §e" + player.getName());
    }

    public static void banUser(Player player, String[] args) {
        if (storageManager == null) {
            player.sendMessage("§cChat system not properly initialized! Please contact an administrator.");
            return;
        }

        if (!player.hasPermission("chat.admin")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /chat ban <player> [reason]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        String reason = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "No reason provided";

        storageManager.banUser(target.getUniqueId(), player.getUniqueId(), reason);

        storageManager.getUserChats(target.getUniqueId()).thenAccept(chats -> {
            for (String chatName : chats) {
                storageManager.removeUserFromChat(target.getUniqueId(), chatName);
                storageManager.removeChatPermission(target.getUniqueId(), chatName);

                UserChatProfile targetProfile = profileManager.getProfile(target.getUniqueId());
                targetProfile.leaveChat(chatName);
            }
        });

        player.sendMessage("§aSuccessfully banned §e" + target.getName() + "§a from all chat channels");
        player.sendMessage("§7Reason: §e" + reason);
        if (displayusername) {
            target.sendMessage("§cYou have been banned from all chat channels by §e" + player.getName());
        } else {
            target.sendMessage("§cYou have been banned from all chat channels" );
        }
        target.sendMessage("§7Reason: §e" + reason);
    }

    public static void unbanUser(Player player, String[] args) {
        if (!player.hasPermission("chat.admin")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /chat unban <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
            if (!isBanned) {
                player.sendMessage("§c" + target.getName() + " is not banned!");
                return;
            }

            storageManager.unbanUser(target.getUniqueId());

            player.sendMessage("§aSuccessfully unbanned §e" + target.getName() + "§a from chat channels");
            target.sendMessage("§aYou have been unbanned from chat channels by §e" + player.getName());
        });
    }

    public static void investigationMode(Player player, String[] args) {
        if (!player.hasPermission("chat.admin")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /chat investigation <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }

        UserChatProfile targetProfile = profileManager.getProfile(target.getUniqueId());

        player.sendMessage("§6=== Investigation Mode: " + target.getName() + " ===");

        storageManager.getUserChats(target.getUniqueId()).thenAccept(chats -> {
            player.sendMessage("§eChats: §7" + String.join(", ", chats));
        });

        storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
            player.sendMessage("§eBanned: §7" + (isBanned ? "Yes" : "No"));
        });

        player.sendMessage("§eCurrent Chat: §7" + targetProfile.getCurrentChat());
        player.sendMessage("§7Use /chat history <channel> to view their chat history");
    }

    private void initializeDefaultChannels() {
        UUID systemUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

        ChatChannel publicChannel = new ChatChannel("public", "§7[§aP§7] ", false, systemUUID, "Main public chat channel", null);
        publicChannel.setDescription("Main public chat channel");
        channels.put("public", publicChannel);

        ChatChannel staffChannel = new ChatChannel("staff", "§7[§cS§7] ", true, systemUUID, "Staff only chat channel", "chat.staff");
        staffChannel.setDescription("Staff only chat channel");
        staffChannel.setRequiredPermission("chat.staff");
        channels.put("staff", staffChannel);

        ChatChannel devChannel = new ChatChannel("dev", "§7[§bD§7] ", true, systemUUID, "Developer chat channel", "chat.dev");
        devChannel.setDescription("Developer chat channel");
        devChannel.setRequiredPermission("chat.dev");
        channels.put("dev", devChannel);


        storageManager.loadAllChannels().thenAccept(customChannels -> {
            for (ChatChannel channel : customChannels) {
                channels.put(channel.getName(), channel);
            }
        });

    }

}

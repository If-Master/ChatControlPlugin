package me.kanuunankuulaspluginchat.chatSystem.managers;

import me.kanuunankuulaspluginchat.chatSystem.Language.LanguageManager;
import me.kanuunankuulaspluginchat.chatSystem.compatibility.UniversalCompatibilityManager;
import me.kanuunankuulaspluginchat.chatSystem.Language.Messager;
import me.kanuunankuulaspluginchat.chatSystem.models.ChatChannel;
import me.kanuunankuulaspluginchat.chatSystem.models.UserChatProfile;
import me.kanuunankuulaspluginchat.chatSystem.storage.StorageManager;
import me.kanuunankuulaspluginchat.chatSystem.util.GroupAmount;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
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
    private static Messager messager;
    private static LanguageManager languageManager;
    private static String language;
    private static String defaultLanguageKey;

    private static UniversalCompatibilityManager compatibilityManager;
    private static Boolean displayusername;
    private static boolean globallyFrozen = false;
    static String languagekey;




    public ChatManager(UserProfileManager profilemanager, StorageManager storagemanager, Plugin plugin, UniversalCompatibilityManager compatibilitymanager, Messager Messager, LanguageManager languagemanager) {

        chatControlPlugin = plugin;
        languageManager = languagemanager;
        messager = Messager;
        profileManager = profilemanager;
        storageManager = storagemanager;
        compatibilityManager = compatibilitymanager;

        initializeDefaultChannels();
        displayusername = chatControlPlugin.getConfig().getBoolean("Display_Admin");
        defaultLanguageKey = GetLanguageKey();
        languagekey = GetLanguageKey();
    }

    private static String GetLanguage() {
        FileConfiguration config = chatControlPlugin.getConfig();
        String configLanguage = config.getString("language", "en");

        if (LanguageManager.isLanguageSupported(configLanguage)) {
            return configLanguage;
        } else {
            return "en";
        }

    }

    private static String GetLanguageKey() {
        GetLanguage();
        language = GetLanguage();
        return GetLanguage();
    }

    public static ChatChannel getChannel(String name) {
        return channels.get(name.toLowerCase());
    }
    public static UniversalCompatibilityManager getCompatibilityManager() {
        return compatibilityManager;
    }

    public static void displaycommands(Player player) {

        messager.sendMessage(player, "Help_message_1");
        messager.sendMessage(player, "Help_message_2");
        messager.sendMessage(player, "Help_message_3");
        messager.sendMessage(player, "Help_message_4");
        messager.sendMessage(player, "Help_message_5");
        messager.sendMessage(player, "Help_message_6");
        messager.sendMessage(player, "Help_message_7");
        messager.sendMessage(player, "Help_message_8");
        messager.sendMessage(player, "Help_message_9");

        if (player.hasPermission("chat.admin")) {
            messager.sendMessage(player, "Help_message_10");
            messager.sendMessage(player, "Help_message_11");
            messager.sendMessage(player, "Help_message_12");
            messager.sendMessage(player, "Help_message_13");
            messager.sendMessage(player, "Help_message_14");
            messager.sendMessage(player, "Help_message_15");
            messager.sendMessage(player, "Help_message_16");
            messager.sendMessage(player, "Help_message_17");
            messager.sendMessage(player, "Help_message_18");
        }
        if (player.hasPermission("chat.dev")) {
            messager.sendMessage(player, "Help_message_19");

        }
        messager.sendMessage(player, "Help_message_20");
        messager.sendMessage(player, "Help_message_21");
        messager.sendMessage(player, "Help_message_22");
        messager.sendMessage(player, "Help_message_23");
        messager.sendMessage(player, "Help_message_24");
    }

    public static void createChat(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 2) {
            messager.sendMessage(player, "Help_warnings_2");
            return;
        }

        String chatName = args[1].toLowerCase();

        storageManager.isUserBanned(player.getUniqueId()).thenAccept(isBanned -> {
            if (isBanned) {
                messager.sendMessage(player, "Help_warnings_3");
                return;
            }

            GroupAmount.canPlayerCreateChannel(player).thenAccept(canCreate -> {
                if (canCreate) {
                    if (channels.containsKey(chatName)) {
                        messager.sendMessage(player, "Chat_Warnings_1");
                        return;
                    }
                    if (chatName.length() > 16) {
                        messager.sendMessage(player, "Chat_Warnings_2");
                        return;
                    }

                    String Msg = LanguageManager.get("Chat_Warnings_3", languagekey);

                    String description = Msg + player.getName();
                    ChatChannel channel = new ChatChannel(chatName, "§7[§e" + chatName + "§7] ", true, player.getUniqueId(), description, null);

                    channels.put(chatName, channel);
                    storageManager.saveChannel(channel);

                    storageManager.setChatPermission(player.getUniqueId(), chatName, "owner", null);

                    GroupAmount.recordChannelCreation(player, chatName);

                    UserChatProfile profile = profileManager.getProfile(player.getUniqueId());
                    profile.joinChat(chatName);

                    storageManager.addUserToChat(player.getUniqueId(), chatName);

                    String Msg2 = LanguageManager.get("Chat_Warnings_3", languagekey);
                    player.sendMessage(Msg2 + chatName);
                    messager.sendMessage(player, "Chat_Warnings_6");
                } else {
                    messager.sendMessage(player, "Chat_Warnings_7");
                }
            });
        });
    }

    public static void joinChat(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 2) {
            messager.sendMessage(player, "Chat_Warnings_8");
            return;
        }

        String chatName = args[1].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            messager.sendMessage(player, "Chat_Warnings_9");
            return;
        }

        storageManager.isUserBanned(player.getUniqueId()).thenAccept(isBanned -> {
            if (isBanned) {
                storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
                    if (!"owner".equals(permission)) {
                        messager.sendMessage(player, "Chat_Warnings_10");
                        return;
                    }

                    storageManager.isUserBlockedFromChannel(player.getUniqueId(), chatName).thenAccept(isBlocked -> {
                                if (isBlocked) {
                                    messager.sendMessage(player, "Chat_Warnings_11");
                                    return;
                                }

                                if (!channel.canPlayerJoin(player) && !player.hasPermission("chat.forcejoin")) {
                                    messager.sendMessage(player, "Chat_Warnings_12");

                                    return;
                                }
                        storageManager.hasInvitation(player.getUniqueId(), chatName).thenAccept(hasInvite -> {
                            if (hasInvite || channel.canPlayerJoin(player) || player.hasPermission("chat.forcejoin")) {
                                if (hasInvite) {
                                    storageManager.removeInvitation(player.getUniqueId(), chatName);
                                }
                                joinChatInternal(player, chatName, channel);
                            } else {
                                messager.sendMessage(player, "Chat_Warnings_12");
                            }
                        });

                    });
                });
                return;
            }

            if (!channel.canPlayerJoin(player) && !player.hasPermission("chat.forcejoin")) {
                messager.sendMessage(player, "Chat_Warnings_12");

                return;
            }

            joinChatInternal(player, chatName, channel);
        });
    }

    public static void unblockUser(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 3) {
            messager.sendMessage(player, "Chat_Warnings_13");

            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");
            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            messager.sendMessage(player, "Chat_Warnings_9");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if (!"owner".equals(permission) && !"manager".equals(permission) && !player.hasPermission("chat.admin")) {
                messager.sendMessage(player, "Chat_Warnings_15");
                return;
            }

            storageManager.isUserBlockedFromChannel(target.getUniqueId(), chatName).thenAccept(isBlocked -> {
                if (!isBlocked) {
                    String Msg2 = LanguageManager.get("Chat_Warnings_16", languagekey);
                    player.sendMessage("§c" + target.getName() + Msg2 + chatName + "!");
                    return;
                }

                storageManager.unblockUserFromChannel(target.getUniqueId(), chatName);
                String Msg1 = LanguageManager.get("Chat_Warnings_17", languagekey);
                String Msg2 = LanguageManager.get("Chat_Warnings_18", languagekey);
                String Msg3 = LanguageManager.get("Chat_Warnings_19", languagekey);
                String Msg4 = LanguageManager.get("Chat_Warnings_20", languagekey);


                player.sendMessage(Msg1 + target.getName() + Msg2 + chatName);
                target.sendMessage(Msg3 + chatName + Msg4 + player.getName());
                messager.sendMessage(target, "Chat_Warnings_9");
//                target.sendMessage(Msg5);
            });
        });
    }

    private static void joinChatInternal(Player player, String chatName, ChatChannel channel) {
        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        storageManager.isUserInChat(player.getUniqueId(), chatName).thenAccept(isInChat -> {
            if (isInChat) {
                profile.setCurrentChat(chatName);
                String Msg = LanguageManager.get("Chat_Warnings_22", languagekey);
                player.sendMessage(Msg + chatName);
                return;
            }

            profile.joinChat(chatName);
            storageManager.addUserToChat(player.getUniqueId(), chatName);
            String Msg = LanguageManager.get("Chat_Warnings_23", languagekey);
            player.sendMessage(Msg + chatName);
        });
    }

    public static void leaveChat(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 2) {
            messager.sendMessage(player, "Chat_Warnings_24");
            return;
        }

        String chatName = args[1].toLowerCase();

        if (chatName.equals("public")) {
            messager.sendMessage(player, "Chat_Warnings_25");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if ("owner".equals(permission)) {
                messager.sendMessage(player, "Chat_Warnings_26");
                return;
            }

            UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

            storageManager.isUserInChat(player.getUniqueId(), chatName).thenAccept(isInChat -> {
                if (!isInChat) {
                    messager.sendMessage(player, "Chat_Warnings_27");
                    return;
                }

                profile.leaveChat(chatName);
                storageManager.removeUserFromChat(player.getUniqueId(), chatName);
                storageManager.removeChatPermission(player.getUniqueId(), chatName);
                storageManager.blockUserFromChannel(player.getUniqueId(), chatName, player.getUniqueId(), "Left channel voluntarily");
                String Msg = LanguageManager.get("Chat_Warnings_28", languagekey);

                player.sendMessage(Msg + chatName);
                messager.sendMessage(player, "Chat_Warnings_29");
            });
        });
    }

    public static void selectChat(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 2) {
            messager.sendMessage(player, "Chat_Warnings_30");
            return;
        }

        String chatName = args[1].toLowerCase();
        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        storageManager.isUserBanned(player.getUniqueId()).thenAccept(isBanned -> {
            if (isBanned) {
                storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
                    if (!"owner".equals(permission)) {
                        messager.sendMessage(player, "Chat_Warnings_31");
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
                String Msg1 = LanguageManager.get("Chat_Warnings_32", languagekey);
                String Msg2 = LanguageManager.get("Chat_Warnings_33", languagekey);

                if ("staff".equalsIgnoreCase(chatName) && player.hasPermission("chat.staff")) {
                    player.sendMessage(Msg1 + chatName + " .");
                } else if ("dev".equalsIgnoreCase(chatName) && player.hasPermission("chat.dev")) {
                    player.sendMessage(Msg1 + chatName + " .");
                } else {
                    player.sendMessage(Msg2 + chatName + " .");
                    return;
                }
            }
            String Msg = LanguageManager.get("Chat_Warnings_34", languagekey);

            profile.setCurrentChat(chatName);
            player.sendMessage(Msg + chatName);
        });
    }

    public static void inviteToChat(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 3) {
            messager.sendMessage(player, "Chat_Warnings_35");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            messager.sendMessage(player, "Chat_Warnings_9");
            return;
        }

        storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
            if (isBanned) {
                String Msg = LanguageManager.get("Chat_Warnings_36", languagekey);
                player.sendMessage("§c" + target.getName() + Msg);
                return;
            }

            storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
                if (permission == null || (!permission.equals("owner") && !permission.equals("manager") && !player.hasPermission("chat.admin"))) {
                    messager.sendMessage(player, "Chat_Warnings_37");
                    return;
                }

                storageManager.isUserInChat(target.getUniqueId(), chatName).thenAccept(isInChat -> {
                    if (isInChat) {
                        String Msg = LanguageManager.get("Chat_Warnings_38", languagekey);
                        player.sendMessage("§c" + target.getName() + Msg);
                        return;
                    }

                    storageManager.addChatInvitation(target.getUniqueId(), chatName, player.getUniqueId());

                    String Msg1 = LanguageManager.get("Chat_Warnings_39", languagekey);
                    String Msg2 = LanguageManager.get("Chat_Warnings_40", languagekey);
                    String Msg3 = LanguageManager.get("Chat_Warnings_41", languagekey);
                    String Msg4 = LanguageManager.get("Chat_Warnings_42", languagekey);
                    String Msg5 = LanguageManager.get("Chat_Warnings_43", languagekey);

                    player.sendMessage(Msg1 + target.getName() + Msg2 + chatName);
                    target.sendMessage("§a" + player.getName() + Msg3 + chatName);
                    target.sendMessage(Msg4 + chatName + Msg5);
                });
            });
        });
    }




    public static void joinPublicChat(Player player) {
        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());
        profile.setCurrentChat("public");
        messager.sendMessage(player, "Chat_Warnings_44");
        player.sendMessage("§aSuccessfully switched to public chat.");
    }


    public static Map<String, ChatChannel> getChannels() {
        return channels;
    }


    public static void joinStaffChat(Player player) {
        if (storageManager == null) {
            messager.sendMessage(player, "Chat_Warnings_1");
            return;
        }

        if (!player.hasPermission("chat.staff")) {
            messager.sendMessage(player, "Chat_Warnings_45");
            return;
        }

        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        storageManager.isUserInChat(player.getUniqueId(), "staff").thenAccept(isInChat -> {
            if (!isInChat) {
                profile.joinChat("staff");
                storageManager.addUserToChat(player.getUniqueId(), "staff");
            }

            profile.setCurrentChat("staff");
            messager.sendMessage(player, "Chat_Warnings_46");
        });
    }

    public static void joinDevChat(Player player) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (!player.hasPermission("chat.dev")) {
            messager.sendMessage(player, "Chat_Warnings_47");
            return;
        }

        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        storageManager.isUserInChat(player.getUniqueId(), "dev").thenAccept(isInChat -> {
            if (!isInChat) {
                profile.joinChat("dev");
                storageManager.addUserToChat(player.getUniqueId(), "dev");
            }

            profile.setCurrentChat("dev");
            messager.sendMessage(player, "Chat_Warnings_48");
        });
    }

    public static void hideChat(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 2) {
            messager.sendMessage(player, "Chat_Warnings_49");
            return;
        }

        String chatName = args[1].toLowerCase();
        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        storageManager.isUserInChat(player.getUniqueId(), chatName).thenAccept(isInChat -> {
            if (!isInChat) {

                messager.sendMessage(player, "Chat_Warnings_27");
                return;
            }

            profile.toggleChatVisibility(chatName);

            if (profile.isChatHidden(chatName)) {
                String Msg1 = LanguageManager.get("Chat_Warnings_50", languagekey);
                player.sendMessage(Msg1 + chatName);
            } else {
                String Msg1 = LanguageManager.get("Chat_Warnings_51", languagekey);
                player.sendMessage(Msg1 + chatName);
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
            messager.sendMessage(player, "Chat_Warnings_52");
            return;
        }

        boolean shouldFreeze = !globallyFrozen;
        setGloballyFrozen(shouldFreeze);

        for (ChatChannel channel : channels.values()) {
            channel.setFrozen(shouldFreeze);
        }
        String frozen = LanguageManager.get("frozen", languagekey);
        String unfrozen = LanguageManager.get("unfrozen", languagekey);

        String status = shouldFreeze ? frozen : unfrozen;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            String Msg1 = LanguageManager.get("Chat_Warnings_53", languagekey);
            String Msg2 = LanguageManager.get("Chat_Warnings_54", languagekey);

            onlinePlayer.sendMessage(Msg1 + status + Msg2 + player.getName());
        }
    }

    public static void clearChat(Player player, String[] args) {
        if (!player.hasPermission("chat.admin")) {
            messager.sendMessage(player, "Chat_Warnings_55");
            return;
        }

        String chatName = args.length > 1 ? args[1].toLowerCase() : "public";

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UserChatProfile profile = profileManager.getProfile(onlinePlayer.getUniqueId());
            if (profile.isInChat(chatName)) {
                for (int i = 0; i < 100; i++) {
                    onlinePlayer.sendMessage("");
                }
                String Msg1 = LanguageManager.get("Chat_Warnings_56", languagekey);
                onlinePlayer.sendMessage(Msg1 + player.getName());
            }
        }
    }

    public static void transferOwnership(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 3) {
            messager.sendMessage(player, "Chat_Warnings_57");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            messager.sendMessage(player, "Chat_Warnings_9");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if (!"owner".equals(permission) && !player.hasPermission("chat.admin")) {
                messager.sendMessage(player, "Chat_Warnings_58");
                return;
            }

            storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
                if (isBanned) {
                    String Msg1 = LanguageManager.get("Chat_Warnings_56", languagekey);
                    player.sendMessage("§c" + target.getName() + Msg1);
                    return;
                }

                storageManager.removeChatPermission(player.getUniqueId(), chatName);
                storageManager.setChatPermission(target.getUniqueId(), chatName, "owner", player.getUniqueId());

                channel.setOwner(target.getUniqueId());

                storageManager.addUserToChat(target.getUniqueId(), chatName);
                String Msg1 = LanguageManager.get("Chat_Warnings_60", languagekey);
                String Msg2 = LanguageManager.get("Chat_Warnings_61", languagekey);
                String Msg3 = LanguageManager.get("Chat_Warnings_62", languagekey);
                String Msg4 = LanguageManager.get("Chat_Warnings_63", languagekey);

                player.sendMessage(Msg1 + chatName + Msg2 + target.getName());
                target.sendMessage("§a" + player.getName() + Msg3 + chatName + Msg4);
            });
        });
    }

    public static void trustUser(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /chat trust <player> <channel>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            messager.sendMessage(player, "Chat_Warnings_9");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if (!"owner".equals(permission) && !"manager".equals(permission) && !player.hasPermission("chat.admin")) {
                messager.sendMessage(player, "Chat_Warnings_65");
                return;
            }

            storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
                if (isBanned) {
                    String Msg1 = LanguageManager.get("Chat_Warnings_66", languagekey);
                    player.sendMessage("§c" + target.getName() + Msg1);
                    return;
                }

                storageManager.setChatPermission(target.getUniqueId(), chatName, "trusted", player.getUniqueId());
                storageManager.addUserToChat(target.getUniqueId(), chatName);

                String Msg1 = LanguageManager.get("Chat_Warnings_67", languagekey);
                String Msg2 = LanguageManager.get("Chat_Warnings_68", languagekey);
                String Msg3 = LanguageManager.get("Chat_Warnings_69", languagekey);
                String Msg4 = LanguageManager.get("Chat_Warnings_20", languagekey);


                player.sendMessage(Msg1 + target.getName() + Msg2 + chatName);
                target.sendMessage(Msg3 + chatName + Msg4 + player.getName());
            });
        });
    }

    public static void assignManager(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 3) {
            messager.sendMessage(player, "Chat_Warnings_70");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            messager.sendMessage(player, "Chat_Warnings_9");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if (!"owner".equals(permission) && !player.hasPermission("chat.admin")) {
                messager.sendMessage(player, "Chat_Warnings_71");
                return;
            }

            storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
                if (isBanned) {
                    String Msg1 = LanguageManager.get("Chat_Warnings_72", languagekey);
                    player.sendMessage("§c" + target.getName() + Msg1);
                    return;
                }

                storageManager.setChatPermission(target.getUniqueId(), chatName, "manager", player.getUniqueId());
                storageManager.addUserToChat(target.getUniqueId(), chatName);
                String Msg1 = LanguageManager.get("Chat_Warnings_73", languagekey);
                String Msg2 = LanguageManager.get("Chat_Warnings_74", languagekey);
                String Msg3 = LanguageManager.get("Chat_Warnings_75", languagekey);
                String Msg4 = LanguageManager.get("Chat_Warnings_20", languagekey);

                player.sendMessage(Msg1 + target.getName() + Msg2 + chatName);
                target.sendMessage(Msg3 + chatName + Msg4 + player.getName());
            });
        });
    }

    public static void muteUser(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 3) {

            messager.sendMessage(player, "Chat_Warnings_76");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            messager.sendMessage(player, "Chat_Warnings_9");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
            if (!"owner".equals(permission) && !"manager".equals(permission) && !player.hasPermission("chat.admin")) {
                compatibilityManager.runPlayerTask(player, () ->
                        messager.sendMessage(player, "Chat_Warnings_77"));
                return;
            }

            storageManager.setChatPermission(target.getUniqueId(), chatName, "muted", player.getUniqueId())
                    .thenRun(() -> {
                        compatibilityManager.runPlayerTask(player, () -> {
                            String Msg1 = LanguageManager.get("Chat_Warnings_78", languagekey);
                            String Msg2 = LanguageManager.get("Chat_Warnings_68", languagekey);
                            String Msg3 = LanguageManager.get("Chat_Warnings_80", languagekey);
                            String Msg4 = LanguageManager.get("Chat_Warnings_81", languagekey);

                            player.sendMessage(Msg1 + target.getName() + Msg2 + chatName);
                            if (displayusername) {
                                target.sendMessage(Msg3 + chatName + Msg4 + player.getName());
                            } else {
                                target.sendMessage(Msg3 + chatName);
                            }
                        });
                    });
        });
    }

    public static void unmute(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 3) {
            messager.sendMessage(player, "Chat_Warnings_113");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            messager.sendMessage(player, "Chat_Warnings_9");
            return;
        }

        storageManager.getChatPermission(player.getUniqueId(), chatName)
                .thenCompose(permission -> {
                    if (!"owner".equals(permission) && !"manager".equals(permission) && !player.hasPermission("chat.admin")) {
                        compatibilityManager.runPlayerTask(player, () ->
                                messager.sendMessage(player, "Chat_Warnings_82"));
                        return CompletableFuture.completedFuture(null);
                    }

                    return storageManager.getChatPermission(target.getUniqueId(), chatName);
                })
                .thenCompose(targetPermission -> {
                    if (targetPermission == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    String Msg1 = LanguageManager.get("Chat_Warnings_83", languagekey);

                    if (!"muted".equals(targetPermission)) {
                        compatibilityManager.runPlayerTask(player, () ->
                                player.sendMessage("§c" + target.getName() + Msg1 + chatName + "!"));
                        return CompletableFuture.completedFuture(null);
                    }

                    return storageManager.unmutePlayer(target.getUniqueId(), chatName);
                })
                .thenRun(() -> {
                    String Msg1 = LanguageManager.get("Chat_Warnings_84", languagekey);
                    String Msg2 = LanguageManager.get("Chat_Warnings_68", languagekey);
                    String Msg3 = LanguageManager.get("Chat_Warnings_85", languagekey);
                    String Msg4 = LanguageManager.get("Chat_Warnings_20", languagekey);

                    compatibilityManager.runPlayerTask(player, () -> {
                        player.sendMessage(Msg1 + target.getName() + Msg2 + chatName);
                        if (displayusername) {
                            target.sendMessage(Msg3 + chatName + Msg4 + player.getName());
                        } else {
                            target.sendMessage(Msg3 + chatName);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    if (throwable != null) {
                        compatibilityManager.severe("Error during unmute operation: " + throwable.getMessage());
                        compatibilityManager.runPlayerTask(player, () ->
                                messager.sendMessage(player, "Chat_Warnings_79"));
                    }
                    return null;
                });
    }

    public static void kickUser(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (args.length < 3) {
            messager.sendMessage(player, "Chat_Warnings_86");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

            return;
        }

        String chatName = args[2].toLowerCase();
        ChatChannel channel = channels.get(chatName);

        if (channel == null) {
            messager.sendMessage(player, "Chat_Warnings_9");
            return;
        }

        storageManager.getChatPermission(target.getUniqueId(), chatName).thenAccept(targetPermission -> {
            if ("owner".equals(targetPermission)) {
                messager.sendMessage(player, "Chat_Warnings_87");
                return;
            }

            storageManager.getChatPermission(player.getUniqueId(), chatName).thenAccept(permission -> {
                if (!"owner".equals(permission) && !"manager".equals(permission) && !player.hasPermission("chat.admin")) {
                    messager.sendMessage(player, "Chat_Warnings_88");
                    return;
                }

                storageManager.removeUserFromChat(target.getUniqueId(), chatName);
                storageManager.removeChatPermission(target.getUniqueId(), chatName);

                storageManager.blockUserFromChannel(target.getUniqueId(), chatName, player.getUniqueId(), "Kicked from channel");


                UserChatProfile targetProfile = profileManager.getProfile(target.getUniqueId());
                targetProfile.leaveChat(chatName);


                String Msg1 = LanguageManager.get("Chat_Warnings_89", languagekey);
                String Msg2 = LanguageManager.get("Chat_Warnings_18", languagekey);
                String Msg3 = LanguageManager.get("Chat_Warnings_90", languagekey);
                String Msg4 = LanguageManager.get("Chat_Warnings_81", languagekey);
                String Msg5 = LanguageManager.get("Chat_Warnings_91", languagekey);

                player.sendMessage(Msg1 + target.getName() + Msg2 + chatName);
                target.sendMessage(Msg3 + chatName + Msg4 + player.getName());
                target.sendMessage(Msg5);
            });
        });

    }

    public static void forceKickUser(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (!player.hasPermission("chat.admin")) {
            messager.sendMessage(player, "Chat_Warnings_92");
            return;
        }

        if (args.length < 3) {
            messager.sendMessage(player, "Chat_Warnings_93");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

            return;
        }

        String chatName = args[2].toLowerCase();

        storageManager.removeUserFromChat(target.getUniqueId(), chatName);
        storageManager.removeChatPermission(target.getUniqueId(), chatName);

        UserChatProfile targetProfile = profileManager.getProfile(target.getUniqueId());
        targetProfile.leaveChat(chatName);
        String Msg1 = LanguageManager.get("Chat_Warnings_94", languagekey);
        String Msg2 = LanguageManager.get("Chat_Warnings_18", languagekey);
        String Msg3 = LanguageManager.get("Chat_Warnings_95", languagekey);
        String Msg4 = LanguageManager.get("Chat_Warnings_81", languagekey);

        player.sendMessage(Msg1 + target.getName() + Msg2 + chatName);
        target.sendMessage(Msg3 + chatName + Msg4 + player.getName());
    }

    public static void banUser(Player player, String[] args) {
        if (storageManager == null) {
            messager.sendMessage(player, "Help_warnings_1");
            return;
        }

        if (!player.hasPermission("chat.admin")) {
            messager.sendMessage(player, "Chat_Warnings_92");
            return;
        }

        if (args.length < 2) {
            messager.sendMessage(player, "Chat_Warnings_96");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

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

        String Msg1 = LanguageManager.get("Chat_Warnings_97", languagekey);
        String Msg2 = LanguageManager.get("Chat_Warnings_98", languagekey);
        String Msg3 = LanguageManager.get("Chat_Warnings_99", languagekey);
        String Msg4 = LanguageManager.get("Chat_Warnings_100", languagekey);
        String Msg5 = LanguageManager.get("Chat_Warnings_54", languagekey);

        player.sendMessage(Msg1 + target.getName() + Msg2);
        player.sendMessage(Msg3 + reason);
        if (displayusername) {
            target.sendMessage(Msg4 + Msg5 + "§e" + player.getName());
        } else {
            target.sendMessage(Msg4 );
        }
        target.sendMessage(Msg3 + reason);
    }

    public static void unbanUser(Player player, String[] args) {
        if (!player.hasPermission("chat.admin")) {
            messager.sendMessage(player, "Chat_Warnings_92");
            return;
        }

        if (args.length < 2) {
            messager.sendMessage(player, "Chat_Warnings_101");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

            return;
        }

        storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
            if (!isBanned) {
                String Msg1 = LanguageManager.get("Chat_Warnings_102", languagekey);
                player.sendMessage("§c" + target.getName() + Msg1);
                return;
            }

            storageManager.unbanUser(target.getUniqueId());
            String Msg1 = LanguageManager.get("Chat_Warnings_103", languagekey);
            String Msg2 = LanguageManager.get("Chat_Warnings_104", languagekey);
            String Msg3 = LanguageManager.get("Chat_Warnings_105", languagekey);

            player.sendMessage(Msg1 + target.getName() + Msg2);
            target.sendMessage(Msg3 + player.getName());
        });
    }

    public static void investigationMode(Player player, String[] args) {
        if (!player.hasPermission("chat.admin")) {
            messager.sendMessage(player, "Chat_Warnings_92");
            return;
        }

        if (args.length < 2) {
            messager.sendMessage(player, "Chat_Warnings_106");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messager.sendMessage(player, "Chat_Warnings_14");

            return;
        }

        UserChatProfile targetProfile = profileManager.getProfile(target.getUniqueId());
        String Msg1 = LanguageManager.get("Chat_Warnings_107", languagekey);
        String Msg2 = LanguageManager.get("Chat_Warnings_108", languagekey);
        String Msg3 = LanguageManager.get("Chat_Warnings_109", languagekey);
        String Msg4 = LanguageManager.get("Chat_Warnings_110", languagekey);
        String yes = LanguageManager.get("yes", languagekey);
        String no = LanguageManager.get("no", languagekey);

        player.sendMessage("§6=== "+Msg1 + target.getName() + " ===");

        storageManager.getUserChats(target.getUniqueId()).thenAccept(chats -> {
            player.sendMessage(Msg2 + String.join(", ", chats));
        });

        storageManager.isUserBanned(target.getUniqueId()).thenAccept(isBanned -> {
            player.sendMessage(Msg3 + (isBanned ? yes : no));
        });

        player.sendMessage(Msg4 + targetProfile.getCurrentChat());
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

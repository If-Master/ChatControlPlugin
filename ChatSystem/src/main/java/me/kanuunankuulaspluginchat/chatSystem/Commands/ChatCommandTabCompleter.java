package me.kanuunankuulaspluginchat.chatSystem.Commands;

import me.kanuunankuulaspluginchat.chatSystem.managers.UserProfileManager;
import me.kanuunankuulaspluginchat.chatSystem.models.UserChatProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChatCommandTabCompleter implements TabCompleter {

    private final UserProfileManager profileManager;

    public ChatCommandTabCompleter(UserProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList(
                    "help", "create", "join", "leave", "select", "invite",
                    "public", "staff", "dev", "hide", "freeze", "clear",
                    "kick", "ban", "unban", "trust", "manager", "kick",
                    "transfer", "unblock"
            );

            if (player.hasPermission("chat.admin")) {
                subcommands = new ArrayList<>(subcommands);
                subcommands.addAll(Arrays.asList(
                        "mute","unmute", "forcekick", "investigation"
                ));
            }

            return subcommands.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();

            switch (subcommand) {
                case "join":
                case "select":
                case "leave":
                case "hide":
                case "clear":
                    UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

                    if (subcommand.equals("join")) {
                        completions.addAll(getAvailableChannels(player, false));
                    } else if (subcommand.equals("select") || subcommand.equals("hide")) {
                        completions.addAll(profile.getJoinedChats());
                    } else if (subcommand.equals("leave")) {
                        completions.addAll(profile.getJoinedChats().stream()
                                .filter(chat -> !chat.equals("public"))
                                .collect(Collectors.toList()));
                    } else if (subcommand.equals("clear")) {
                        if (player.hasPermission("chat.admin")) {
                            completions.addAll(getAllChannels());
                        }
                    }
                    break;

                case "invite":
                case "trust":
                case "manager":
                case "mute":
                case "kick":
                case "forcekick":
                case "ban":
                case "investigation":
                case "transfer":
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> !name.equals(player.getName()))
                            .collect(Collectors.toList()));
                    break;
            }
        }

        String currentArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(currentArg))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> getAvailableChannels(Player player, boolean includeJoined) {
        List<String> channels = new ArrayList<>();

        channels.add("public");

        if (player.hasPermission("chat.staff")) {
            channels.add("staff");
        }

        if (player.hasPermission("chat.dev")) {
            channels.add("dev");
        }

        UserChatProfile profile = profileManager.getProfile(player.getUniqueId());

        if (!includeJoined) {
            channels.removeAll(profile.getJoinedChats());
        }

        return channels;
    }

    private List<String> getAllChannels() {
        List<String> channels = new ArrayList<>();

        channels.add("public");
        channels.add("staff");
        channels.add("dev");

        // TODO: Add custom channels from ChatManager

        return channels;
    }
}

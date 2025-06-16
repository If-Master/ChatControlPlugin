package me.kanuunankuulaspluginchat.chatSystem.Commands;

import me.kanuunankuulaspluginchat.chatSystem.managers.ChatManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatCommandExecutor implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /chat <subcommand>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help":
                ChatManager.displaycommands(player);
                break;
            case "unblock":
                ChatManager.unblockUser(player, args);
            case "create":
                ChatManager.createChat(player, args);
                break;
            case "join":
                ChatManager.joinChat(player, args);
                break;
            case "invite":
                ChatManager.inviteToChat(player, args);
                break;
            case "leave":
                ChatManager.leaveChat(player, args);
                break;
            case "select":
                ChatManager.selectChat(player, args);
                break;
            case "trust":
                ChatManager.trustUser(player, args);
                break;
            case "manager":
                ChatManager.assignManager(player, args);
                break;
            case "mute":
                ChatManager.muteUser(player, args);
                break;
            case "kick":
                ChatManager.kickUser(player, args);
                break;
            case "forcekick":
                ChatManager.forceKickUser(player, args);
                break;
            case "ban":
                ChatManager.banUser(player, args);
                break;
            case "unban":
                ChatManager.unbanUser(player, args);
                break;
            case "unmute":
                ChatManager.unmute(player, args);
                break;
            case "staff":
                ChatManager.joinStaffChat(player);
                break;
            case "dev":
                ChatManager.joinDevChat(player);
                break;
            case "investigation":
                ChatManager.investigationMode(player, args);
                break;
            case "hide":
                ChatManager.hideChat(player, args);
                break;
            case "freeze":
                ChatManager.freezeChats(player);
                break;
            case "clear":
                ChatManager.clearChat(player, args);
                break;
            case "public":
                ChatManager.joinPublicChat(player);
                break;
            case "transfer":
                ChatManager.transferOwnership(player, args);
                break;
            default:
                player.sendMessage("§cUnknown subcommand. Use /chat help");
                break;
        }

        return true;
    }
}



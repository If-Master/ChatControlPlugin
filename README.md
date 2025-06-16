# Custom Chat Channels Plugin

## A powerful and Easy to use chat control plugin designed for expanding Minecraft servers. This plugin will allow players to create, manage, and participate in custom chat channels, such as clan chats and etc, supporting both file and MySQL storage backends.

## Whether you're managing a small community or a large-scale server network, this plugin makes communication clean, organized, and clan-based. It will though not leak anyones clan since it will hide the chats that they can't access

# Features
  Create and join custom chat channels

  Role based chats (e.g., Staff, Dev)

  Switch between public and private chats

  Admin moderation tools (freeze, clear, investigate)

  Supports both MySQL and file-based data storage

  Folia compatibility

# Commands:
```
/chat help - Show the help menu  
/chat create <name> - Create a new chat channel  
/chat join <name> - Join a chat channel  
/chat leave <name> - Leave a chat channel  
/chat select <name> - Switch to a chat channel  
/chat invite <player> <channel> - Invite a player to a channel  
/chat public - Switch to public chat  
/chat staff - Switch to staff chat  
/chat dev - Switch to dev chat  
/chat hide <channel> - Hide/unhide a channel  
/chat freeze - Toggle chat freeze mode  
/chat clear <channel> - Clear chat history  

=== Admin Commands ===  
/chat trust <player> <channel> - Trust a player in a channel  
/chat manager <player> <channel> - Make a player a channel manager  
/chat mute <player> <channel> - Mute a player in a channel  
/chat unmute <player> <channel> - Unmute a player in a channel  
/chat kick <player> <channel> - Kick a player from a channel  
/chat ban <player> <channel> - Ban a player from a channel  
/chat unban <player> - Unban a player from all chats  
/chat investigation <player> - Toggle investigation mode  
/chat transfer <player> <channel> - Transfer channel ownership  

```
# Permissions
```
  chat.admin:
    description: Access to all chat admin commands
    default: op
  chat.staff:
    description: Access to staff chat
    default: op
  chat.dev:
    description: Access to dev chat
    default: op
  chat.create:
    description: Create custom chat channels
    default: op
  chat.join:
    description: Join chat channels
    default: op
  chat.invite:
    description: Invite players to chat channels
    default: op
  chat.freeze:
    description: Freeze chat channels
    default: op
  chat.clear:
    description: Clear chat channels
    default: op
  chat.investigation:
    description: Investigation mode for monitoring
    default: op
  chat.channels.limit.<Amount>:
    description: Add here the amount you want the user to have for groups
    default: op
  chat.channels.unlimited:
    description: User will be able to generate Unlimited groups
    default: op
```

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

# Config File;
```
# Supported languages:
# en (English), fi (Suomi), sv (Svenska), es (Español)
language: "en"

update-checker:
  enabled: true
  notify-ops-only: true
  resource-id: "126110"

# Server identification
server-name: "MyServer"

# Storage configuration [Vault required]
storage:
  # Storage type: "file" or "mysql"
  type: "file"
  
  # MySQL configuration (only used if type is "mysql")
  mysql:
    host: "localhost"
    port: 3306
    database: "minecraft"
    username: "root"
    password: ""

# Chat settings
chat:
  # Default chat channel for new players
  default-channel: "public"
  
  # Maximum number of custom channels a player can create
  max-channels-per-player: 0
  
  # Default maximum members per channel
  default-max-members: 9999

  # Chat cooldown in seconds (0 to disable)
  chat-cooldown: 0
  
  # Maximum message length
  max-message-length: 256

# Colors:
#  §0 Black
#  §1 Dark Blue
#  §2 Dark Green
#  §3 Dark Aqua
#  §4 Dark Red
#  §5 Dark Purple
#  §6 Gold
#  §7 Gray
#  §8 Dark Gray
#  §9 Blue
#  §a Green
#  §b Aqua
#  §c Red
#  §d Light Purple
#  §e Yellow
#  §f White


### Channel settings ###
# The following entries are always required: /chat Dev, /chat Staff, and /chat Public.
# Removing or modifying any of these may cause errors.
# If you're not familiar with coding, please do not change anything other than the display-name: "NAME" field.
channels:
  public:
    display-name: "§7[§aP§7]"
    description: "Main public chat channel"
    auto-join: true

  staff:
    display-name: "§7[§cS§7]" # Staff
    description: "Staff only chat channel"
    required-permission: "chat.staff"

  dev:
    display-name: "§7[§bD§7]" #Dev
    description: "Developer chat channel"
    required-permission: "chat.dev"

# Integration settings
integrations:
  # EssentialsChat integration
  essentials-chat:
    # Use EssentialsChat formatting for prefixes/suffixes
    use-formatting: true
    
    # Override EssentialsChat's chat handling
    override-chat: true

# Logging settings
logging:
  # Log all chat messages to console
  console-logging: true
  
  # Log chat messages to files
  file-logging: true
  
  # Include timestamps in logs
  include-timestamps: true
```

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

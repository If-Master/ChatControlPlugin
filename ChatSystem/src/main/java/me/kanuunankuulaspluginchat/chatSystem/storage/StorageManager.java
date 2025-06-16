package me.kanuunankuulaspluginchat.chatSystem.storage;

import me.kanuunankuulaspluginchat.chatSystem.ChatControlPlugin;
import me.kanuunankuulaspluginchat.chatSystem.compatibility.UniversalCompatibilityManager;
import me.kanuunankuulaspluginchat.chatSystem.models.ChatChannel;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class StorageManager {

    private final ChatControlPlugin plugin;
    private final boolean useDatabase;
    private Connection connection;

    private File channelDataFile;
    private FileConfiguration channelDataConfig;
    private final Map<UUID, Integer> channelCountCache = new ConcurrentHashMap<>();

    private String host;
    private int port;
    private String database;
    private String username;
    private String password;

    private File chatDataFile;
    private FileConfiguration chatDataConfig;
    private final Map<UUID, Set<String>> userChatMemberships = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, String>> chatPermissions = new ConcurrentHashMap<>();
    private final Set<UUID> bannedUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, ChatChannel> channelCache = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> channelBlocks = new ConcurrentHashMap<>();
    private final UniversalCompatibilityManager compatibilityManager;


    public StorageManager(ChatControlPlugin plugin) {
        this.plugin = plugin;
        this.useDatabase = plugin.getConfig().getString("storage.type", "file").equalsIgnoreCase("mysql");
        this.compatibilityManager = new UniversalCompatibilityManager(plugin);

        if (useDatabase) {
            loadDatabaseConfig();
            initializeDatabase();
        } else {
            initializeFileStorage();
            logToConsole("No database detected. Using file-based storage.");
        }
    }

    private void initializeFileStorage() {
        channelDataFile = new File(plugin.getDataFolder(), "channel_data.yml");
        if (!channelDataFile.exists()) {
            try {
                channelDataFile.createNewFile();
            } catch (IOException e) {
                logToConsole("Failed to create channel data file: " + e.getMessage());
            }
        }
        channelDataConfig = YamlConfiguration.loadConfiguration(channelDataFile);

        chatDataFile = new File(plugin.getDataFolder(), "chats.yml");
        if (!chatDataFile.exists()) {
            try {
                chatDataFile.createNewFile();
            } catch (IOException e) {
                logToConsole("Failed to create chat data file: " + e.getMessage());
            }
        }
        chatDataConfig = YamlConfiguration.loadConfiguration(chatDataFile);

        loadChannelDataFromFile();
        loadChatDataFromFile();
        loadChannelsFromFile();
    }

    public CompletableFuture<List<ChatChannel>> loadAllChannels() {
        if (useDatabase && connection != null) {
            return compatibilityManager.supplyAsync(() -> {
                List<ChatChannel> channels = new ArrayList<>();
                String selectSQL = """
                SELECT channel_name, channel_prefix, is_private, owner_uuid, description, required_permission 
                FROM custom_channels 
                WHERE is_active = TRUE
                """;
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String name = rs.getString("channel_name");
                            String prefix = rs.getString("channel_prefix");
                            boolean isPrivate = rs.getBoolean("is_private");
                            UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                            String description = rs.getString("description");
                            String requiredPermission = rs.getString("required_permission");

                            ChatChannel channel = new ChatChannel(name, prefix, isPrivate, owner, description, requiredPermission);
                            channels.add(channel);
                        }
                    }
                } catch (SQLException e) {
                    logToConsole("Error loading channels from database: " + e.getMessage());
                }
                return channels;
            });
        } else {
            return CompletableFuture.completedFuture(new ArrayList<>(channelCache.values()));
        }
    }

    private void loadChatDataFromFile() {
        if (chatDataConfig.getConfigurationSection("memberships") != null) {
            for (String uuidString : chatDataConfig.getConfigurationSection("memberships").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    List<String> chats = chatDataConfig.getStringList("memberships." + uuidString);
                    userChatMemberships.put(uuid, new HashSet<>(chats));
                } catch (IllegalArgumentException e) {
                    logToConsole("Invalid UUID in chat memberships: " + uuidString);
                }
            }
        }

        if (chatDataConfig.getConfigurationSection("permissions") != null) {
            for (String chatName : chatDataConfig.getConfigurationSection("permissions").getKeys(false)) {
                Map<UUID, String> chatPerms = new HashMap<>();
                if (chatDataConfig.getConfigurationSection("permissions." + chatName) != null) {
                    for (String uuidString : chatDataConfig.getConfigurationSection("permissions." + chatName).getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidString);
                            String permission = chatDataConfig.getString("permissions." + chatName + "." + uuidString);
                            chatPerms.put(uuid, permission);
                        } catch (IllegalArgumentException e) {
                            logToConsole("Invalid UUID in chat permissions: " + uuidString);
                        }
                    }
                }
                chatPermissions.put(chatName, chatPerms);
            }
        }

        if (chatDataConfig.isList("banned_users")) {
            List<String> bannedList = chatDataConfig.getStringList("banned_users");
            for (String uuidString : bannedList) {
                try {
                    bannedUsers.add(UUID.fromString(uuidString));
                } catch (IllegalArgumentException e) {
                    logToConsole("Invalid UUID in banned users: " + uuidString);
                }
            }
        }
    }


    private void loadChannelsFromFile() {
        if (channelDataConfig.getConfigurationSection("custom_channels") != null) {
            for (String channelName : channelDataConfig.getConfigurationSection("custom_channels").getKeys(false)) {
                try {
                    String prefix = channelDataConfig.getString("custom_channels." + channelName + ".prefix");
                    boolean isPrivate = channelDataConfig.getBoolean("custom_channels." + channelName + ".is_private", false);
                    String ownerString = channelDataConfig.getString("custom_channels." + channelName + ".owner");
                    String description = channelDataConfig.getString("custom_channels." + channelName + ".description");
                    String requiredPermission = channelDataConfig.getString("custom_channels." + channelName + ".required_permission");

                    if (ownerString != null) {
                        UUID owner = UUID.fromString(ownerString);
                        ChatChannel channel = new ChatChannel(channelName, prefix, isPrivate, owner, description, requiredPermission);
                        channelCache.put(channelName, channel);
                    }
                } catch (IllegalArgumentException e) {
                    logToConsole("Invalid data for channel: " + channelName + " - " + e.getMessage());
                }
            }
        }
    }


    private void loadChannelDataFromFile() {
        if (channelDataConfig.getConfigurationSection("players") != null) {
            for (String uuidString : channelDataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    int count = channelDataConfig.getInt("players." + uuidString + ".channel_count", 0);
                    channelCountCache.put(uuid, count);
                } catch (IllegalArgumentException e) {
                    logToConsole("Invalid UUID in channel data file: " + uuidString);
                }
            }
        }
    }


    private void loadDatabaseConfig() {
        this.host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        this.port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        this.database = plugin.getConfig().getString("storage.mysql.database", "minecraft");
        this.username = plugin.getConfig().getString("storage.mysql.username", "root");
        this.password = plugin.getConfig().getString("storage.mysql.password", "");
    }

    private void initializeDatabase() {
//        CompletableFuture.runAsync(() -> {
        compatibilityManager.runTaskAsync(() -> {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");

                String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                        "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

                connection = DriverManager.getConnection(url, username, password);

                createChatTable();
                createChannelTrackingTables();
                createChatMembershipTables();
                createBanTable();
                createChannelBlockTable();

                logToConsole("Successfully connected to MySQL database!");

            } catch (ClassNotFoundException e) {
                logToConsole("MySQL driver not found! Please add mysql-connector-java to your dependencies.");
                logToConsole("Falling back to file-based storage.");
                initializeFileStorage();
            } catch (SQLException e) {
                logToConsole("Failed to connect to MySQL: " + e.getMessage());
                logToConsole("Falling back to file-based storage.");
                initializeFileStorage();
            }
        });
    }

    private void createChannelBlockTable() throws SQLException {
        String createChannelBlockSQL = """
        CREATE TABLE IF NOT EXISTS channel_blocks (
            id INT AUTO_INCREMENT PRIMARY KEY,
            player_uuid VARCHAR(36) NOT NULL,
            channel_name VARCHAR(100) NOT NULL,
            blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            blocked_by VARCHAR(36),
            reason TEXT,
            is_active BOOLEAN DEFAULT TRUE,
            UNIQUE KEY unique_block (player_uuid, channel_name),
            INDEX idx_player_uuid (player_uuid),
            INDEX idx_channel_name (channel_name),
            INDEX idx_is_active (is_active)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

        try (PreparedStatement stmt = connection.prepareStatement(createChannelBlockSQL)) {
            stmt.executeUpdate();
            logToConsole("Channel blocks table created/verified successfully.");
        }
    }

    private void createChatMembershipTables() throws SQLException {
        String createMembershipsSQL = """
            CREATE TABLE IF NOT EXISTS chat_memberships (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                chat_name VARCHAR(100) NOT NULL,
                joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                is_active BOOLEAN DEFAULT TRUE,
                UNIQUE KEY unique_membership (player_uuid, chat_name),
                INDEX idx_player_uuid (player_uuid),
                INDEX idx_chat_name (chat_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        String createPermissionsSQL = """
            CREATE TABLE IF NOT EXISTS chat_permissions (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                chat_name VARCHAR(100) NOT NULL,
                permission_level VARCHAR(50) NOT NULL,
                granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                granted_by VARCHAR(36),
                UNIQUE KEY unique_permission (player_uuid, chat_name),
                INDEX idx_player_uuid (player_uuid),
                INDEX idx_chat_name (chat_name),
                INDEX idx_permission_level (permission_level)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        try (PreparedStatement stmt1 = connection.prepareStatement(createMembershipsSQL);
             PreparedStatement stmt2 = connection.prepareStatement(createPermissionsSQL)) {

            stmt1.executeUpdate();
            stmt2.executeUpdate();
            logToConsole("Chat membership and permission tables created/verified successfully.");
        }
    }

    private void createChannelStorageTable() throws SQLException {
        String createChannelSQL = """
        CREATE TABLE IF NOT EXISTS custom_channels (
            id INT AUTO_INCREMENT PRIMARY KEY,
            channel_name VARCHAR(100) NOT NULL UNIQUE,
            channel_prefix VARCHAR(10),
            is_private BOOLEAN DEFAULT FALSE,
            owner_uuid VARCHAR(36) NOT NULL,
            description TEXT,
            required_permission VARCHAR(100),
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            is_active BOOLEAN DEFAULT TRUE,
            INDEX idx_channel_name (channel_name),
            INDEX idx_owner_uuid (owner_uuid),
            INDEX idx_is_active (is_active)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

        try (PreparedStatement stmt = connection.prepareStatement(createChannelSQL)) {
            stmt.executeUpdate();
            logToConsole("Custom channels table created/verified successfully.");
        }
    }


    private void createBanTable() throws SQLException {
        String createBanSQL = """
            CREATE TABLE IF NOT EXISTS chat_bans (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL UNIQUE,
                banned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                banned_by VARCHAR(36),
                reason TEXT,
                is_active BOOLEAN DEFAULT TRUE,
                INDEX idx_player_uuid (player_uuid),
                INDEX idx_is_active (is_active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        try (PreparedStatement stmt = connection.prepareStatement(createBanSQL)) {
            stmt.executeUpdate();
            logToConsole("Chat ban table created/verified successfully.");
        }
    }


    private void createChatTable() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS chat_logs (
                id INT AUTO_INCREMENT PRIMARY KEY,
                chat_name VARCHAR(100) NOT NULL,
                sender VARCHAR(50) NOT NULL,
                message TEXT NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                server_name VARCHAR(50),
                INDEX idx_chat_name (chat_name),
                INDEX idx_sender (sender),
                INDEX idx_timestamp (timestamp)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        try (PreparedStatement stmt = connection.prepareStatement(createTableSQL)) {
            stmt.executeUpdate();
            logToConsole("Chat logs table created/verified successfully.");
        }
    }

    private void createChannelTrackingTables() throws SQLException {
        String createPlayerChannelsSQL = """
            CREATE TABLE IF NOT EXISTS player_channels (
                player_uuid VARCHAR(36) PRIMARY KEY,
                channel_count INT DEFAULT 0,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        String createChannelOwnershipSQL = """
            CREATE TABLE IF NOT EXISTS channel_ownership (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                channel_name VARCHAR(100) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                is_active BOOLEAN DEFAULT TRUE,
                server_name VARCHAR(50),
                UNIQUE KEY unique_player_channel (player_uuid, channel_name),
                INDEX idx_player_uuid (player_uuid),
                INDEX idx_channel_name (channel_name),
                INDEX idx_is_active (is_active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        try (PreparedStatement stmt1 = connection.prepareStatement(createPlayerChannelsSQL);
             PreparedStatement stmt2 = connection.prepareStatement(createChannelOwnershipSQL)) {

            stmt1.executeUpdate();
            stmt2.executeUpdate();
            logToConsole("Channel tracking tables created/verified successfully.");
        }
    }

    public CompletableFuture<Void> blockUserFromChannel(UUID playerUuid, String channelName, UUID blockedBy, String reason) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String insertSQL = """
                INSERT INTO channel_blocks (player_uuid, channel_name, blocked_by, reason) 
                VALUES (?, ?, ?, ?) 
                ON DUPLICATE KEY UPDATE is_active = TRUE, blocked_at = CURRENT_TIMESTAMP, blocked_by = ?, reason = ?
                """;
                try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, channelName);
                    stmt.setString(3, blockedBy != null ? blockedBy.toString() : null);
                    stmt.setString(4, reason);
                    stmt.setString(5, blockedBy != null ? blockedBy.toString() : null);
                    stmt.setString(6, reason);
                    stmt.executeUpdate();
                    logToConsole("Blocked user " + playerUuid + " from channel " + channelName);
                } catch (SQLException e) {
                    logToConsole("Error blocking user from channel: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                channelBlocks.computeIfAbsent(channelName, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
                saveChannelDataToFile();
                logToConsole("Blocked user " + playerUuid + " from channel " + channelName);
            });
        }
    }

    public CompletableFuture<Void> unblockUserFromChannel(UUID playerUuid, String channelName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String updateSQL = "UPDATE channel_blocks SET is_active = FALSE WHERE player_uuid = ? AND channel_name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(updateSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, channelName);
                    int rowsAffected = stmt.executeUpdate();
                    logToConsole("Unblocked user " + playerUuid + " from channel " + channelName + " (rows affected: " + rowsAffected + ")");
                } catch (SQLException e) {
                    logToConsole("Error unblocking user from channel: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                Set<UUID> blockedUsers = channelBlocks.get(channelName);
                if (blockedUsers != null) {
                    boolean removed = blockedUsers.remove(playerUuid);
                    if (removed) {
                        saveChannelDataToFile();
                        logToConsole("Unblocked user " + playerUuid + " from channel " + channelName);
                    } else {
                        logToConsole("User " + playerUuid + " was not blocked from channel " + channelName);
                    }
                }
            });
        }
    }

    public CompletableFuture<Boolean> isUserBlockedFromChannel(UUID playerUuid, String channelName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.supplyAsync(() -> {
                String selectSQL = "SELECT 1 FROM channel_blocks WHERE player_uuid = ? AND channel_name = ? AND is_active = TRUE";
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, channelName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        boolean isBlocked = rs.next();
                        logToConsole("User " + playerUuid + " blocked from channel " + channelName + ": " + isBlocked);
                        return isBlocked;
                    }
                } catch (SQLException e) {
                    logToConsole("Error checking if user is blocked from channel: " + e.getMessage());
                }
                return false;
            });
        } else {
            boolean isBlocked = channelBlocks.getOrDefault(channelName, new HashSet<>()).contains(playerUuid);
            return CompletableFuture.completedFuture(isBlocked);
        }
    }

    public CompletableFuture<Set<String>> getUserBlockedChannels(UUID playerUuid) {
        if (useDatabase && connection != null) {
            return compatibilityManager.supplyAsync(() -> {
                Set<String> blockedChannels = new HashSet<>();
                String selectSQL = "SELECT channel_name FROM channel_blocks WHERE player_uuid = ? AND is_active = TRUE";
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            blockedChannels.add(rs.getString("channel_name"));
                        }
                    }
                } catch (SQLException e) {
                    logToConsole("Error getting user blocked channels: " + e.getMessage());
                }
                return blockedChannels;
            });
        } else {
            return compatibilityManager.supplyAsync(() -> {
                Set<String> blockedChannels = new HashSet<>();
                for (Map.Entry<String, Set<UUID>> entry : channelBlocks.entrySet()) {
                    if (entry.getValue().contains(playerUuid)) {
                        blockedChannels.add(entry.getKey());
                    }
                }
                return blockedChannels;
            });
        }
    }

    public CompletableFuture<Void> clearAllUserBlocks(UUID playerUuid) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String updateSQL = "UPDATE channel_blocks SET is_active = FALSE WHERE player_uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(updateSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    int rowsAffected = stmt.executeUpdate();
                    logToConsole("Cleared all blocks for user " + playerUuid + " (rows affected: " + rowsAffected + ")");
                } catch (SQLException e) {
                    logToConsole("Error clearing all user blocks: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                boolean changed = false;
                for (Set<UUID> blockedUsers : channelBlocks.values()) {
                    if (blockedUsers.remove(playerUuid)) {
                        changed = true;
                    }
                }
                if (changed) {
                    saveChannelDataToFile();
                    logToConsole("Cleared all blocks for user " + playerUuid);
                }
            });
        }
    }

    private void loadChannelBlocksFromFile() {
        if (channelDataConfig.getConfigurationSection("channel_blocks") != null) {
            for (String channelName : channelDataConfig.getConfigurationSection("channel_blocks").getKeys(false)) {
                List<String> blockedList = channelDataConfig.getStringList("channel_blocks." + channelName);
                Set<UUID> blockedUsers = new HashSet<>();
                for (String uuidString : blockedList) {
                    try {
                        blockedUsers.add(UUID.fromString(uuidString));
                    } catch (IllegalArgumentException e) {
                        logToConsole("Invalid UUID in channel blocks: " + uuidString);
                    }
                }
                if (!blockedUsers.isEmpty()) {
                    channelBlocks.put(channelName, blockedUsers);
                }
            }
        }
    }


    public CompletableFuture<Void> addUserToChat(UUID playerUuid, String chatName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String insertSQL = """
                    INSERT INTO chat_memberships (player_uuid, chat_name) 
                    VALUES (?, ?) 
                    ON DUPLICATE KEY UPDATE is_active = TRUE, joined_at = CURRENT_TIMESTAMP
                    """;
                try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, chatName);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logToConsole("Error adding user to chat: " + e.getMessage());
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                userChatMemberships.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(chatName);
                saveChatDataToFile();
            });
        }
    }

    public CompletableFuture<Void> removeUserFromChat(UUID playerUuid, String chatName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String updateSQL = "UPDATE chat_memberships SET is_active = FALSE WHERE player_uuid = ? AND chat_name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(updateSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, chatName);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logToConsole("Error removing user from chat: " + e.getMessage());
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                Set<String> chats = userChatMemberships.get(playerUuid);
                if (chats != null) {
                    chats.remove(chatName);
                }
                saveChatDataToFile();
            });
        }
    }

    public CompletableFuture<Boolean> isUserInChat(UUID playerUuid, String chatName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.supplyAsync(() -> {
                String selectSQL = "SELECT 1 FROM chat_memberships WHERE player_uuid = ? AND chat_name = ? AND is_active = TRUE";
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, chatName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        boolean inChat = rs.next();
                        logToConsole("User " + playerUuid + " in chat " + chatName + ": " + inChat);
                        return inChat;
                    }
                } catch (SQLException e) {
                    logToConsole("Error checking user chat membership: " + e.getMessage());
                }
                return false;
            });
        } else {
            boolean inChat = userChatMemberships.getOrDefault(playerUuid, new HashSet<>()).contains(chatName);
            return CompletableFuture.completedFuture(inChat);
        }
    }

    public CompletableFuture<Set<String>> getUserChats(UUID playerUuid) {
        if (useDatabase && connection != null) {
            return compatibilityManager.supplyAsync(() -> {
                Set<String> chats = new HashSet<>();
                String selectSQL = "SELECT chat_name FROM chat_memberships WHERE player_uuid = ? AND is_active = TRUE";
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            chats.add(rs.getString("chat_name"));
                        }
                    }
                } catch (SQLException e) {
                    logToConsole("Error getting user chats: " + e.getMessage());
                }
                return chats;
            });
        } else {
            return CompletableFuture.completedFuture(
                    new HashSet<>(userChatMemberships.getOrDefault(playerUuid, new HashSet<>()))
            );
        }
    }

    public CompletableFuture<Void> setChatPermission(UUID playerUuid, String chatName, String permissionLevel, UUID grantedBy) {
        if (permissionLevel != null && !isValidPermissionLevel(permissionLevel)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid permission level: " + permissionLevel));
        }

        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String insertSQL = """
            INSERT INTO chat_permissions (player_uuid, chat_name, permission_level, granted_by) 
            VALUES (?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE permission_level = ?, granted_at = CURRENT_TIMESTAMP, granted_by = ?
            """;
                try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, chatName);
                    stmt.setString(3, permissionLevel);
                    stmt.setString(4, grantedBy != null ? grantedBy.toString() : null);
                    stmt.setString(5, permissionLevel);
                    stmt.setString(6, grantedBy != null ? grantedBy.toString() : null);
                    int rowsAffected = stmt.executeUpdate();
                    logToConsole("Set permission for " + playerUuid + " in " + chatName + " to " + permissionLevel + " (rows affected: " + rowsAffected + ")");
                } catch (SQLException e) {
                    logToConsole("Error setting chat permission: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                chatPermissions.computeIfAbsent(chatName, k -> new HashMap<>()).put(playerUuid, permissionLevel);
                saveChatDataToFile();
                logToConsole("Set permission for " + playerUuid + " in " + chatName + " to " + permissionLevel);
            });
        }
    }

    private boolean isValidPermissionLevel(String permissionLevel) {
        return permissionLevel != null && (
                permissionLevel.equals("owner") ||
                        permissionLevel.equals("manager") ||
                        permissionLevel.equals("trusted") ||
                        permissionLevel.equals("muted") ||
                        permissionLevel.equals("banned")
        );
    }


    public CompletableFuture<Void> unmutePlayer(UUID playerUuid, String chatName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String deleteSQL = "DELETE FROM chat_permissions WHERE player_uuid = ? AND chat_name = ? AND permission_level = 'muted'";
                try (PreparedStatement stmt = connection.prepareStatement(deleteSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, chatName);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logToConsole("Error unmuting player: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                try {
                    Map<UUID, String> chatPerms = chatPermissions.computeIfAbsent(chatName, k -> new HashMap<>());

                    String currentPermission = chatPerms.get(playerUuid);

                    if ("muted".equals(currentPermission)) {
                        String removedPermission = chatPerms.remove(playerUuid);
                        logToConsole("Removed mute permission for player " + playerUuid + " from chat " + chatName +
                                " (removed: " + removedPermission + ")");

                        saveChatDataToFile();

                        verifyUnmuteInFile(playerUuid, chatName);
                    } else {
                        logToConsole("Player " + playerUuid + " was not muted in chat " + chatName +
                                " (current permission: " + currentPermission + ")");
                    }
                } catch (Exception e) {
                    logToConsole("Error in unmute operation: " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void verifyUnmuteInFile(UUID playerUuid, String chatName) {
        try {
            // Mitä Vittua.
            String memoryPermission = chatPermissions.getOrDefault(chatName, new HashMap<>()).get(playerUuid);

            FileConfiguration tempConfig = YamlConfiguration.loadConfiguration(chatDataFile);
            String filePermission = tempConfig.getString("permissions." + chatName + "." + playerUuid.toString());

            if (memoryPermission == null && filePermission == null) {
            } else {
                logToConsole("VERIFICATION FAILED: Player " + playerUuid + " still has permission - Memory: " +
                        memoryPermission + ", File: " + filePermission);
            }
        } catch (Exception e) {
            logToConsole("Error verifying unmute: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveChannelDataToFile() {
        for (Map.Entry<UUID, Integer> entry : channelCountCache.entrySet()) {
            channelDataConfig.set("players." + entry.getKey().toString() + ".channel_count", entry.getValue());
        }
        channelDataConfig.set("channel_blocks", null);
        for (Map.Entry<String, Set<UUID>> entry : channelBlocks.entrySet()) {
            String channelName = entry.getKey();
            List<String> blockedList = new ArrayList<>();
            for (UUID uuid : entry.getValue()) {
                blockedList.add(uuid.toString());
            }
            if (!blockedList.isEmpty()) {
                channelDataConfig.set("channel_blocks." + channelName, blockedList);
            }
        }

        try {
            channelDataConfig.save(channelDataFile);
            logToConsole("Channel data with blocks successfully saved to file");
        } catch (IOException e) {
            logToConsole("Failed to save channel data to file: " + e.getMessage());
        }
    }

    private void saveChannelsToFile() {
        try {
            channelDataConfig.set("custom_channels", null);

            for (ChatChannel channel : channelCache.values()) {
                String path = "custom_channels." + channel.getName();
                channelDataConfig.set(path + ".prefix", channel.getPrefix());
                channelDataConfig.set(path + ".is_private", channel.isPrivate());
                channelDataConfig.set(path + ".owner", channel.getOwner().toString());
                channelDataConfig.set(path + ".description", channel.getDescription());
                channelDataConfig.set(path + ".required_permission", channel.getRequiredPermission());
            }

            channelDataConfig.save(channelDataFile);
            logToConsole("Custom channels successfully saved to file");

        } catch (IOException e) {
            logToConsole("Failed to save custom channels to file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public CompletableFuture<Void> saveChannel(ChatChannel channel) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String insertSQL = """
                INSERT INTO custom_channels (channel_name, channel_prefix, is_private, owner_uuid, description, required_permission) 
                VALUES (?, ?, ?, ?, ?, ?) 
                ON DUPLICATE KEY UPDATE 
                    channel_prefix = ?, 
                    is_private = ?, 
                    description = ?, 
                    required_permission = ?,
                    is_active = TRUE
                """;
                try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
                    stmt.setString(1, channel.getName());
                    stmt.setString(2, channel.getPrefix());
                    stmt.setBoolean(3, channel.isPrivate());
                    stmt.setString(4, channel.getOwner().toString());
                    stmt.setString(5, channel.getDescription());
                    stmt.setString(6, channel.getRequiredPermission());
                    stmt.setString(7, channel.getPrefix());
                    stmt.setBoolean(8, channel.isPrivate());
                    stmt.setString(9, channel.getDescription());
                    stmt.setString(10, channel.getRequiredPermission());

                    stmt.executeUpdate();
                    logToConsole("Saved channel: " + channel.getName());
                } catch (SQLException e) {
                    logToConsole("Error saving channel: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                channelCache.put(channel.getName(), channel);
                saveChannelsToFile();
                logToConsole("Saved channel to file: " + channel.getName());
            });
        }
    }

    private void saveChatDataToFile() {
        try {
            chatDataConfig.set("permissions", null);

            for (Map.Entry<UUID, Set<String>> entry : userChatMemberships.entrySet()) {
                chatDataConfig.set("memberships." + entry.getKey().toString(), new ArrayList<>(entry.getValue()));
            }

            for (Map.Entry<String, Map<UUID, String>> chatEntry : chatPermissions.entrySet()) {
                String chatName = chatEntry.getKey();
                for (Map.Entry<UUID, String> permEntry : chatEntry.getValue().entrySet()) {
                    String permission = permEntry.getValue();
                    if (permission != null && !permission.trim().isEmpty()) {
                        chatDataConfig.set("permissions." + chatName + "." + permEntry.getKey().toString(), permission);
                    }
                }
            }

            List<String> bannedList = new ArrayList<>();
            for (UUID uuid : bannedUsers) {
                bannedList.add(uuid.toString());
            }
            chatDataConfig.set("banned_users", bannedList);

            chatDataConfig.save(chatDataFile);
            logToConsole("Chat data successfully saved to file");

        } catch (IOException e) {
            logToConsole("Failed to save chat data to file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public CompletableFuture<String> getChatPermission(UUID playerUuid, String chatName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.supplyAsync(() -> {
                String selectSQL = "SELECT permission_level FROM chat_permissions WHERE player_uuid = ? AND chat_name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, chatName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String permission = rs.getString("permission_level");
                            logToConsole("Retrieved permission for " + playerUuid + " in " + chatName + ": " + permission);
                            return permission;
                        }
                    }
                } catch (SQLException e) {
                    logToConsole("Error getting chat permission: " + e.getMessage());
                }
                return null;
            });
        } else {
            return CompletableFuture.completedFuture(
                    chatPermissions.getOrDefault(chatName, new HashMap<>()).get(playerUuid)
            );
        }
    }

    public CompletableFuture<Void> banUser(UUID playerUuid, UUID bannedBy, String reason) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String insertSQL = """
                    INSERT INTO chat_bans (player_uuid, banned_by, reason) 
                    VALUES (?, ?, ?) 
                    ON DUPLICATE KEY UPDATE is_active = TRUE, banned_at = CURRENT_TIMESTAMP, banned_by = ?, reason = ?
                    """;
                try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, bannedBy != null ? bannedBy.toString() : null);
                    stmt.setString(3, reason);
                    stmt.setString(4, bannedBy != null ? bannedBy.toString() : null);
                    stmt.setString(5, reason);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logToConsole("Error banning user: " + e.getMessage());
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                bannedUsers.add(playerUuid);
                saveChatDataToFile();
            });
        }
    }

    public CompletableFuture<Void> unbanUser(UUID playerUuid) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String updateSQL = "UPDATE chat_bans SET is_active = FALSE WHERE player_uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(updateSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logToConsole("Error unbanning user: " + e.getMessage());
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                bannedUsers.remove(playerUuid);
                saveChatDataToFile();
            });
        }
    }

    public CompletableFuture<Boolean> isUserBanned(UUID playerUuid) {
        if (useDatabase && connection != null) {
            return compatibilityManager.supplyAsync(() -> {
                String selectSQL = "SELECT 1 FROM chat_bans WHERE player_uuid = ? AND is_active = TRUE";
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next();
                    }
                } catch (SQLException e) {
                    logToConsole("Error checking user ban status: " + e.getMessage());
                }
                return false;
            });
        } else {
            return CompletableFuture.completedFuture(bannedUsers.contains(playerUuid));
        }
    }


    public CompletableFuture<Integer> getPlayerChannelCount(UUID playerUuid) {
        if (useDatabase && connection != null) {
            return compatibilityManager.supplyAsync(() -> {
                String selectSQL = "SELECT channel_count FROM player_channels WHERE player_uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("channel_count");
                        }
                    }
                } catch (SQLException e) {
                    logToConsole("Error retrieving channel count for player " + playerUuid + ": " + e.getMessage());
                }
                return 0;
            });
        } else {
            return CompletableFuture.completedFuture(channelCountCache.getOrDefault(playerUuid, 0));
        }
    }

    public CompletableFuture<Void> incrementPlayerChannelCount(UUID playerUuid) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String upsertSQL = """
                    INSERT INTO player_channels (player_uuid, channel_count) 
                    VALUES (?, 1) 
                    ON DUPLICATE KEY UPDATE channel_count = channel_count + 1
                    """;
                try (PreparedStatement stmt = connection.prepareStatement(upsertSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logToConsole("Error incrementing channel count for player " + playerUuid + ": " + e.getMessage());
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                int currentCount = channelCountCache.getOrDefault(playerUuid, 0);
                channelCountCache.put(playerUuid, currentCount + 1);
                saveChannelDataToFile();
            });
        }
    }

    public CompletableFuture<Void> decrementPlayerChannelCount(UUID playerUuid) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String updateSQL = """
                    UPDATE player_channels 
                    SET channel_count = GREATEST(0, channel_count - 1) 
                    WHERE player_uuid = ?
                    """;
                try (PreparedStatement stmt = connection.prepareStatement(updateSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logToConsole("Error decrementing channel count for player " + playerUuid + ": " + e.getMessage());
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                int currentCount = channelCountCache.getOrDefault(playerUuid, 0);
                channelCountCache.put(playerUuid, Math.max(0, currentCount - 1));
                saveChannelDataToFile();
            });
        }
    }

    public CompletableFuture<Void> recordChannelCreation(UUID playerUuid, String channelName) {
        CompletableFuture<Void> incrementFuture = incrementPlayerChannelCount(playerUuid);

        if (useDatabase && connection != null) {
            return incrementFuture.thenRunAsync(() -> {
                String insertSQL = """
                    INSERT INTO channel_ownership (player_uuid, channel_name, server_name) 
                    VALUES (?, ?, ?) 
                    ON DUPLICATE KEY UPDATE is_active = TRUE, created_at = CURRENT_TIMESTAMP
                    """;
                try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, channelName);
                    stmt.setString(3, getServerName());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logToConsole("Error recording channel creation: " + e.getMessage());
                }
            });
        } else {
            return incrementFuture.thenRunAsync(() -> {
                String playerSection = "players." + playerUuid.toString() + ".channels";
                channelDataConfig.set(playerSection + "." + channelName + ".created_at", System.currentTimeMillis());
                channelDataConfig.set(playerSection + "." + channelName + ".is_active", true);
                saveChannelDataToFile();
            });
        }
    }

    public CompletableFuture<Void> recordChannelDeletion(UUID playerUuid, String channelName) {
        CompletableFuture<Void> decrementFuture = decrementPlayerChannelCount(playerUuid);

        if (useDatabase && connection != null) {
            return decrementFuture.thenRunAsync(() -> {
                String updateSQL = "UPDATE channel_ownership SET is_active = FALSE WHERE player_uuid = ? AND channel_name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(updateSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, channelName);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logToConsole("Error recording channel deletion: " + e.getMessage());
                }
            });
        } else {
            return decrementFuture.thenRunAsync(() -> {
                String playerSection = "players." + playerUuid.toString() + ".channels";
                channelDataConfig.set(playerSection + "." + channelName + ".is_active", false);
                saveChannelDataToFile();
            });
        }
    }

    public void logChatMessage(String chatName, String sender, String message) {
        String fullMsg = "[" + chatName + "] " + sender + ": " + message;

        if (useDatabase && connection != null) {
            compatibilityManager.runAsync(() -> {
                try {
                    insertChatMessage(chatName, sender, message);
                } catch (SQLException e) {
                    logToConsole("Failed to log message to database: " + e.getMessage());
                }
            });
        }
    }

    private void insertChatMessage(String chatName, String sender, String message) throws SQLException {
        String insertSQL = "INSERT INTO chat_logs (chat_name, sender, message, server_name) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
            stmt.setString(1, chatName);
            stmt.setString(2, sender);
            stmt.setString(3, message);
            stmt.setString(4, getServerName());

            stmt.executeUpdate();
        }
    }

    private String getServerName() {
        return plugin.getConfig().getString("server-name", "Unknown");
    }

    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                logToConsole("Database connection closed.");
            } catch (SQLException e) {
                logToConsole("Error closing database connection: " + e.getMessage());
            }
        }

        if (!useDatabase) {
            saveChannelDataToFile();
            saveChatDataToFile();
            saveChannelsToFile();
        }
    }

    public CompletableFuture<java.util.List<ChatMessage>> getChatHistory(String chatName, int limit) {
        return compatibilityManager.supplyAsync(() -> {
            if (!useDatabase || connection == null) {
                return new java.util.ArrayList<>();
            }

            java.util.List<ChatMessage> messages = new java.util.ArrayList<>();
            String selectSQL = "SELECT sender, message, timestamp FROM chat_logs WHERE chat_name = ? ORDER BY timestamp DESC LIMIT ?";

            try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                stmt.setString(1, chatName);
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        messages.add(new ChatMessage(
                                rs.getString("sender"),
                                rs.getString("message"),
                                rs.getTimestamp("timestamp")
                        ));
                    }
                }
            } catch (SQLException e) {
                logToConsole("Error retrieving chat history: " + e.getMessage());
            }

            return messages;
        });
    }

    public CompletableFuture<Void> removeChatPermission(UUID playerUuid, String chatName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String deleteSQL = "DELETE FROM chat_permissions WHERE player_uuid = ? AND chat_name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(deleteSQL)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, chatName);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logToConsole("Error removing chat permission: " + e.getMessage());
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                Map<UUID, String> chatPerms = chatPermissions.get(chatName);
                if (chatPerms != null) {
                    chatPerms.remove(playerUuid);
                }
                saveChatDataToFile();
            });
        }
    }

    public CompletableFuture<Void> deleteChannel(String channelName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.runAsync(() -> {
                String updateSQL = "UPDATE custom_channels SET is_active = FALSE WHERE channel_name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(updateSQL)) {
                    stmt.setString(1, channelName);
                    int rowsAffected = stmt.executeUpdate();
                    if (rowsAffected > 0) {
                        logToConsole("Deleted channel from database: " + channelName);
                    } else {
                        logToConsole("Channel not found in database: " + channelName);
                    }
                } catch (SQLException e) {
                    logToConsole("Error deleting channel from database: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        } else {
            return compatibilityManager.runAsync(() -> {
                ChatChannel removed = channelCache.remove(channelName);
                if (removed != null) {
                    saveChannelsToFile();
                    logToConsole("Deleted channel from file: " + channelName);
                } else {
                    logToConsole("Channel not found in cache: " + channelName);
                }
            });
        }
    }


    public CompletableFuture<Map<UUID, String>> getChatPermissions(String chatName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.supplyAsync(() -> {
                Map<UUID, String> permissions = new HashMap<>();
                String selectSQL = "SELECT player_uuid, permission_level FROM chat_permissions WHERE chat_name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    stmt.setString(1, chatName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                            permissions.put(uuid, rs.getString("permission_level"));
                        }
                    }
                } catch (SQLException e) {
                    logToConsole("Error getting chat permissions: " + e.getMessage());
                }
                return permissions;
            });
        } else {
            return CompletableFuture.completedFuture(
                    new HashMap<>(chatPermissions.getOrDefault(chatName, new HashMap<>()))
            );
        }
    }

    public CompletableFuture<Set<UUID>> getChatMembers(String chatName) {
        if (useDatabase && connection != null) {
            return compatibilityManager.supplyAsync(() -> {
                Set<UUID> members = new HashSet<>();
                String selectSQL = "SELECT player_uuid FROM chat_memberships WHERE chat_name = ? AND is_active = TRUE";
                try (PreparedStatement stmt = connection.prepareStatement(selectSQL)) {
                    stmt.setString(1, chatName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            members.add(UUID.fromString(rs.getString("player_uuid")));
                        }
                    }
                } catch (SQLException e) {
                    logToConsole("Error getting chat members: " + e.getMessage());
                }
                return members;
            });
        } else {
            return compatibilityManager.supplyAsync(() -> {
                Set<UUID> members = new HashSet<>();
                for (Map.Entry<UUID, Set<String>> entry : userChatMemberships.entrySet()) {
                    if (entry.getValue().contains(chatName)) {
                        members.add(entry.getKey());
                    }
                }
                return members;
            });
        }
    }


    private void logToConsole(String msg) {
        Bukkit.getLogger().info("[ChatControl] " + msg);
    }

    public static class ChatMessage {
        public final String sender;
        public final String message;
        public final Timestamp timestamp;

        public ChatMessage(String sender, String message, Timestamp timestamp) {
            this.sender = sender;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}

package me.kanuunankuulaspluginchat.chatSystem.models;

import me.kanuunankuulaspluginchat.chatSystem.storage.StorageManager;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatChannel {
    private final String name;
    private final String displayPrefix;
    private final boolean isPrivate;
    private final String prefix;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Set<UUID> managers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> trusted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> banned = ConcurrentHashMap.newKeySet();
    private final long createdTime;
    private String description;
    private UUID owner;
    private String requiredPermission;
    private boolean frozen = false;
    private StorageManager storageManager;
    private boolean allowInvites = true;
    private boolean autoJoin = false;
    private int maxMembers = 100;

    public ChatChannel(String name, String displayPrefix, boolean isPrivate, UUID owner, String description, String requiredPermission) {
        this.name = name.toLowerCase();
        this.displayPrefix = displayPrefix;
        this.prefix = displayPrefix;

        this.isPrivate = isPrivate;
        this.createdTime = System.currentTimeMillis();
        this.storageManager = storageManager;
        this.requiredPermission = requiredPermission;
        this.description = description;
        this.owner = owner;


    }


    public String getName() { return name; }

    public String getDisplayPrefix() {
        return displayPrefix;
    }

    public String getPrefix() { return prefix; }

    public boolean isPrivate() { return isPrivate; }

    public String getDescription() { return description; }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getOwner() { return owner; }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public String getRequiredPermission() { return requiredPermission; }

    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public boolean isAllowInvites() {
        return allowInvites;
    }

    public void setAllowInvites(boolean allowInvites) {
        this.allowInvites = allowInvites;
    }

    public boolean isAutoJoin() {
        return autoJoin;
    }

    public void setAutoJoin(boolean autoJoin) {
        this.autoJoin = autoJoin;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public boolean canPlayerJoin(Player player) {
        UUID playerId = player.getUniqueId();

        if (banned.contains(playerId)) {
            return false;
        }

        if (members.size() >= maxMembers) {
            return false;
        }

        if (requiredPermission != null && !player.hasPermission(requiredPermission)) {
            return false;
        }

        return !isPrivate || isOwner(playerId) || managers.contains(playerId) || trusted.contains(playerId);
    }

    public boolean canPlayerSpeak(Player player) {
        UUID playerId = player.getUniqueId();

        if (frozen && !player.hasPermission("chat.admin") && !isOwner(playerId) && !managers.contains(playerId)) {
            return false;
        }

        if (muted.contains(playerId)) {
            return false;
        }

        return !banned.contains(playerId);
    }

    public boolean canPlayerReceive(Player player) {
        UUID playerId = player.getUniqueId();

        if (banned.contains(playerId)) {
            return false;
        }

        return !isPrivate || requiredPermission == null || player.hasPermission(requiredPermission);
    }

    public boolean canPlayerInvite(Player player) {
        UUID playerId = player.getUniqueId();

        if (isOwner(playerId) || managers.contains(playerId)) {
            return true;
        }

        if (!allowInvites) {
            return false;
        }

        if (trusted.contains(playerId)) {
            return true;
        }

        return !isPrivate;
    }

    public boolean isOwner(UUID playerId) {
        return owner != null && owner.equals(playerId);
    }

    public boolean isManager(UUID playerId) {
        return managers.contains(playerId);
    }

    public boolean isTrusted(UUID playerId) {
        return trusted.contains(playerId);
    }

    public boolean isPlayerMuted(UUID playerUuid) {
        return storageManager.getChatPermission(playerUuid, this.name)
                .join()
                .equals("muted");
    }

    public boolean isPlayerBanned(UUID playerId) {
        return banned.contains(playerId);
    }

    public void addMember(UUID playerId) {
        members.add(playerId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
        managers.remove(playerId);
        trusted.remove(playerId);
        muted.remove(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public Set<UUID> getMembers() {
        return Set.copyOf(members);
    }

    public int getMemberCount() {
        return members.size();
    }

    public void addManager(UUID playerId) {
        managers.add(playerId);
        trusted.add(playerId);
    }

    public void removeManager(UUID playerId) {
        managers.remove(playerId);
    }

    public void addTrusted(UUID playerId) {
        trusted.add(playerId);
    }

    public void removeTrusted(UUID playerId) {
        trusted.remove(playerId);
    }

    public void mutePlayer(UUID playerId) {
        muted.add(playerId);
    }

    public void unmutePlayer(UUID playerId) {
        muted.remove(playerId);
    }

    public void banPlayer(UUID playerId) {
        banned.add(playerId);
        members.remove(playerId);
        managers.remove(playerId);
        trusted.remove(playerId);
        muted.remove(playerId);
    }

    public void unbanPlayer(UUID playerId) {
        banned.remove(playerId);
    }

    public Set<UUID> getManagers() {
        return Set.copyOf(managers);
    }

    public Set<UUID> getTrusted() {
        return Set.copyOf(trusted);
    }

    public Set<UUID> getMuted() {
        return Set.copyOf(muted);
    }

    public Set<UUID> getBanned() {
        return Set.copyOf(banned);
    }

    public String getChannelInfo() {
        StringBuilder info = new StringBuilder();
        info.append("§6=== Channel Info: ").append(name).append(" ===\n");
        info.append("§eDescription: §f").append(description != null ? description : "No description").append("\n");
        info.append("§eType: §f").append(isPrivate ? "Private" : "Public").append("\n");
        info.append("§eMembers: §f").append(members.size()).append("/").append(maxMembers).append("\n");
        info.append("§eCreated: §f").append(new java.util.Date(createdTime)).append("\n");

        if (requiredPermission != null) {
            info.append("§eRequired Permission: §f").append(requiredPermission).append("\n");
        }

        if (frozen) {
            info.append("§cThis channel is currently frozen!\n");
        }

        return info.toString();
    }

    @Override
    public String toString() {
        return "ChatChannel{" +
                "name='" + name + '\'' +
                ", private=" + isPrivate +
                ", members=" + members.size() +
                ", frozen=" + frozen +
                '}';
    }
}

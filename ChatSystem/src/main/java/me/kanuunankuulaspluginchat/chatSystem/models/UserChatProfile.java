package me.kanuunankuulaspluginchat.chatSystem.models;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserChatProfile {
    private final Set<String> joinedChats = ConcurrentHashMap.newKeySet();
    private final Set<String> hiddenChats = ConcurrentHashMap.newKeySet();
    private String currentChat = "public";
    private final Map<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> messageCount = new ConcurrentHashMap<>();
    private boolean chatNotifications = true;
    private boolean chatSounds = true;
    private final long profileCreated;

    public UserChatProfile() {
        this.profileCreated = System.currentTimeMillis();
        this.joinedChats.add("public");
    }

    public String getCurrentChat() {
        return currentChat;
    }

    public void setCurrentChat(String chatName) {
        this.currentChat = chatName;
    }

    public Set<String> getJoinedChats() {
        return joinedChats;
    }

    public boolean isInChat(String chatName) {
        return joinedChats.contains(chatName);
    }

    public void joinChat(String chatName) {
        joinedChats.add(chatName.toLowerCase());
        if (currentChat == null || currentChat.equals("public")) {
            currentChat = chatName.toLowerCase();
        }
    }

    public void leaveChat(String chatName) {
        String lowerChatName = chatName.toLowerCase();
        joinedChats.remove(lowerChatName);

        if (lowerChatName.equals(currentChat)) {
            if (!joinedChats.isEmpty()) {
                currentChat = joinedChats.iterator().next();
            } else {
                currentChat = "public";
                joinedChats.add("public");
            }
        }
    }

    public Set<String> getHiddenChats() {
        return hiddenChats;
    }

    public boolean isChatHidden(String chatName) {
        return hiddenChats.contains(chatName.toLowerCase());
    }

    public void hideChat(String chatName) {
        hiddenChats.add(chatName.toLowerCase());
    }

    public void unhideChat(String chatName) {
        hiddenChats.remove(chatName.toLowerCase());
    }

    public void toggleChatVisibility(String chatName) {
        String lowerChatName = chatName.toLowerCase();
        if (hiddenChats.contains(lowerChatName)) {
            hiddenChats.remove(lowerChatName);
        } else {
            hiddenChats.add(lowerChatName);
        }
    }

    public void recordMessage(String chatName) {
        String lowerChatName = chatName.toLowerCase();
        lastMessageTime.put(lowerChatName, System.currentTimeMillis());
        messageCount.merge(lowerChatName, 1, Integer::sum);
    }

    public long getLastMessageTime(String chatName) {
        return lastMessageTime.getOrDefault(chatName.toLowerCase(), 0L);
    }

    public int getMessageCount(String chatName) {
        return messageCount.getOrDefault(chatName.toLowerCase(), 0);
    }

    public int getTotalMessageCount() {
        return messageCount.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isChatNotificationsEnabled() {
        return chatNotifications;
    }

    public void setChatNotifications(boolean enabled) {
        this.chatNotifications = enabled;
    }

    public boolean isChatSoundsEnabled() {
        return chatSounds;
    }

    public void setChatSounds(boolean enabled) {
        this.chatSounds = enabled;
    }

    public long getProfileCreated() {
        return profileCreated;
    }

    public List<String> getVisibleChats() {
        List<String> visible = new ArrayList<>();
        for (String chat : joinedChats) {
            if (!hiddenChats.contains(chat)) {
                visible.add(chat);
            }
        }
        return visible;
    }

    public boolean canSwitchToChat(String chatName) {
        return joinedChats.contains(chatName.toLowerCase());
    }

    public void leaveAllChats() {
        joinedChats.clear();
        joinedChats.add("public");
        currentChat = "public";
        hiddenChats.clear();
    }

    public Map<String, Object> getChatActivity() {
        Map<String, Object> activity = new HashMap<>();
        activity.put("joinedChats", new ArrayList<>(joinedChats));
        activity.put("currentChat", currentChat);
        activity.put("totalMessages", getTotalMessageCount());
        activity.put("hiddenChats", new ArrayList<>(hiddenChats));
        return activity;
    }

    @Override
    public String toString() {
        return "UserChatProfile{" +
                "joinedChats=" + joinedChats.size() +
                ", currentChat='" + currentChat + '\'' +
                ", hiddenChats=" + hiddenChats.size() +
                ", totalMessages=" + getTotalMessageCount() +
                ", notifications=" + chatNotifications +
                '}';
    }
}
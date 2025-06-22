package me.kanuunankuulaspluginchat.chatSystem.compatibility;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.kanuunankuulaspluginchat.chatSystem.Language.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public class UpdateChecker {
    private String language;

    private final JavaPlugin plugin;
    private final UniversalCompatibilityManager compatibilityManager;
    private final LanguageManager languageManager;

    private final String currentVersion;
    private final boolean updateCheckEnabled;
    private final boolean notifyOpsOnly;
    private final boolean autoDownloadEnabled;
    private final String userAgent;
    private UpdateChecker updateChecker;

    private String lastNotifiedVersion = null;

    private static final String SPIGET_API_BASE = "https://api.spiget.org/v2/resources/";
    private static final String SPIGET_VERSION_ENDPOINT = "/versions/latest";
    private static final String SPIGET_DOWNLOAD_ENDPOINT = "/download";

    private static final String SPIGOT_DOWNLOAD_BASE = "https://www.spigotmc.org/resources/";

    private static final String LEGACY_ENDPOINT = "aHR0cHM6Ly9hcGkuc3BpZ290bWMub3JnL2xlZ2FjeS91cGRhdGUubGFzdC8=";

    public UpdateChecker(JavaPlugin plugin, UniversalCompatibilityManager compatibilityManager, LanguageManager languageManager) {
        this.plugin = plugin;
        this.compatibilityManager = compatibilityManager;
        this.currentVersion = plugin.getDescription().getVersion();

        this.updateCheckEnabled = plugin.getConfig().getBoolean("update-checker.enabled", true);
        this.notifyOpsOnly = plugin.getConfig().getBoolean("update-checker.notify-ops-only", true);
        this.autoDownloadEnabled = plugin.getConfig().getBoolean("update-checker.auto-download", false);
        this.languageManager = languageManager;

        this.userAgent = generateUserAgent();

        if (updateCheckEnabled) {
            initializeUpdateChecker();
        }
    }
    private String GetLanguage() {
        FileConfiguration config = plugin.getConfig();
        String configLanguage = config.getString("language", "en");

        if (LanguageManager.isLanguageSupported(configLanguage)) {
            return configLanguage;
        } else {
            plugin.getLogger().warning("Language '" + configLanguage + "' is not supported. Falling back to English.");
            return "en";
        }

    }

    private String getLanguageKey() {
        GetLanguage();
        language = GetLanguage();
        return GetLanguage();
    }

    private void logMessage(String translationKey, Object... args) {
        String languageKey = getLanguageKey();
        String message = LanguageManager.get(translationKey, languageKey);
        if (args.length > 0) {
            message = formatMessage(message, args);
        }
        plugin.getLogger().info(message);
    }

    private void logWarning(String translationKey, Object... args) {
        String languageKey = getLanguageKey();
        String message = LanguageManager.get(translationKey, languageKey);
        if (args.length > 0) {
            message = formatMessage(message, args);
        }
        plugin.getLogger().warning(message);
    }

    private void logError(String translationKey, Throwable throwable) {
        String languageKey = getLanguageKey();
        String message = LanguageManager.get(translationKey, languageKey);
        plugin.getLogger().log(Level.WARNING, message, throwable);
    }

    private String formatMessage(String message, Object... args) {
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return message;
    }

    private String generateUserAgent() {
        String serverVersion = Bukkit.getVersion();
        String javaVersion = System.getProperty("java.version");

        StringBuilder ua = new StringBuilder();
        ua.append("Minecraft/").append(extractMinecraftVersion(serverVersion));
        ua.append(" Java/").append(javaVersion.split("\\.")[0]);
        ua.append(" (").append(System.getProperty("os.name")).append(")");
        ua.append(" Plugin/").append(plugin.getName()).append("/").append(currentVersion);

        return ua.toString();
    }

    private String extractMinecraftVersion(String serverVersion) {
        if (serverVersion.contains("MC: ")) {
            return serverVersion.substring(serverVersion.indexOf("MC: ") + 4).split("\\)")[0];
        }
        return "Unknown";
    }

    private void initializeUpdateChecker() {
        compatibilityManager.runTaskLater(() -> {
            checkForUpdates(result -> {
                if (result.hasUpdate()) {
                    handleUpdateAvailable(result);
                }
            });
        }, 200L);

        compatibilityManager.runTaskTimer(() -> {
            checkForUpdates(result -> {
                if (result.hasUpdate()) {
                    handleUpdateAvailable(result);
                }
            });
        }, 432000L, 432000L);
    }

    public void checkForUpdates(Consumer<UpdateResult> callback) {
        if (!updateCheckEnabled) {
            String languageKey = getLanguageKey();
            String errorMsg = LanguageManager.get("update_check_disabled", languageKey);
            callback.accept(new UpdateResult(false, currentVersion, currentVersion, errorMsg));
            return;
        }

        compatibilityManager.runTaskAsync(() -> {
            try {
                String resourceId = getResourceId();
                if (resourceId == null) {
                    String languageKey = getLanguageKey();
                    String errorMsg = LanguageManager.get("update_resource_id_not_configured", languageKey);
                    callback.accept(new UpdateResult(false, currentVersion, currentVersion, errorMsg));
                    return;
                }

                logMessage("update_check_starting", resourceId);

                UpdateResult result = performUpdateCheck(resourceId);
                callback.accept(result);

            } catch (Exception e) {
                logError("update_check_failed", e);
                String languageKey = getLanguageKey();
                String errorMsg = LanguageManager.get("update_check_error", languageKey);
                errorMsg = formatMessage(errorMsg, e.getMessage());
                callback.accept(new UpdateResult(false, currentVersion, currentVersion, errorMsg));
            }
        });
    }

    public void downloadUpdate(Consumer<DownloadResult> callback) {
        if (!updateCheckEnabled) {
            String languageKey = getLanguageKey();
            String errorMsg = LanguageManager.get("update_check_disabled", languageKey);
            callback.accept(new DownloadResult(false, errorMsg, null));
            return;
        }

        compatibilityManager.runTaskAsync(() -> {
            try {
                String resourceId = getResourceId();
                if (resourceId == null) {
                    String languageKey = getLanguageKey();
                    String errorMsg = LanguageManager.get("update_resource_id_not_configured", languageKey);
                    callback.accept(new DownloadResult(false, errorMsg, null));
                    return;
                }

                logMessage("download_starting", resourceId);

                UpdateResult updateResult = performUpdateCheck(resourceId);
                if (!updateResult.hasUpdate()) {
                    String languageKey = getLanguageKey();
                    String errorMsg = LanguageManager.get("download_no_update", languageKey);
                    callback.accept(new DownloadResult(false, errorMsg, null));
                    return;
                }

                DownloadResult downloadResult = performDownload(resourceId, updateResult.getLatestVersion());
                callback.accept(downloadResult);

            } catch (Exception e) {
                logError("download_failed", e);
                String languageKey = getLanguageKey();
                String errorMsg = LanguageManager.get("download_error", languageKey);
                errorMsg = formatMessage(errorMsg, e.getMessage());
                callback.accept(new DownloadResult(false, errorMsg, null));
            }
        });
    }

    private DownloadResult performDownload(String resourceId, String version) throws Exception {
        Path currentPluginPath = getCurrentPluginPath();
        if (currentPluginPath == null) {
            String languageKey = getLanguageKey();
            String errorMsg = LanguageManager.get("download_plugin_path_error", languageKey);
            throw new Exception(errorMsg);
        }

        logMessage("download_current_path", currentPluginPath.toString());
        logMessage("download_replacing");

        boolean downloadSuccess = downloadFileWithRedirects(resourceId, currentPluginPath);

        if (!downloadSuccess) {
            String languageKey = getLanguageKey();
            String errorMsg = LanguageManager.get("download_file_failed", languageKey);
            throw new Exception(errorMsg);
        }

        if (!Files.exists(currentPluginPath) || Files.size(currentPluginPath) < 1024) {
            String languageKey = getLanguageKey();
            String errorMsg = LanguageManager.get("download_invalid_file", languageKey);
            throw new Exception(errorMsg);
        }

        logMessage("download_success", Files.size(currentPluginPath));
        logMessage("download_restart_required");

        String languageKey = getLanguageKey();
        String successMsg = LanguageManager.get("download_success_message", languageKey);

        return new DownloadResult(true, successMsg, currentPluginPath.toString());
    }

    private Path getCurrentPluginPath() {
        try {
            File pluginFile = new File(plugin.getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (pluginFile.exists() && pluginFile.getName().endsWith(".jar")) {
                return pluginFile.toPath();
            }
        } catch (Exception e) {
            logWarning("plugin_path_error", e.getMessage());
        }
        return null;
    }

    private boolean downloadFileWithRedirects(String resourceId, Path outputPath) {
        String[] downloadUrls = {
                SPIGET_API_BASE + resourceId + SPIGET_DOWNLOAD_ENDPOINT,
                SPIGOT_DOWNLOAD_BASE + resourceId + "/download",
                "https://api.spiget.org/v2/resources/" + resourceId + "/download"
        };

        for (String downloadUrl : downloadUrls) {
            logMessage("download_attempting", downloadUrl);

            if (downloadFile(downloadUrl, outputPath)) {
                logMessage("download_success_from", downloadUrl);
                return true;
            }
        }

        logWarning("download_all_failed");
        return false;
    }

    private boolean downloadFile(String downloadUrl, Path outputPath) {
        HttpsURLConnection connection = null;
        try {
            connection = createConnection(downloadUrl);
            connection.setRequestProperty("Accept", "application/java-archive,application/octet-stream,*/*");
            connection.setInstanceFollowRedirects(false);

            int responseCode = connection.getResponseCode();
            logMessage("download_response_code", responseCode);

            if (responseCode == HttpsURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpsURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpsURLConnection.HTTP_SEE_OTHER) {

                String newUrl = connection.getHeaderField("Location");
                logMessage("download_redirected", newUrl);

                if (newUrl != null) {
                    connection.disconnect();
                    return downloadFile(newUrl, outputPath);
                }
            }

            if (responseCode != 200) {
                logWarning("download_http_error", responseCode);
                return false;
            }

            String contentType = connection.getContentType();
            logMessage("download_content_type", contentType);

            if (contentType != null && contentType.contains("text/html")) {
                logWarning("download_html_received");
                return false;
            }

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(outputPath.toFile())) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }

                logMessage("download_bytes_downloaded", totalBytes);

                if (totalBytes > 1024 && isValidJarFile(outputPath)) {
                    return true;
                } else {
                    logWarning("download_invalid_jar");
                    return false;
                }
            }

        } catch (Exception e) {
            logWarning("download_file_error", downloadUrl);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isValidJarFile(Path filePath) {
        try {
            byte[] header = new byte[4];
            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                int bytesRead = fis.read(header);
                if (bytesRead == 4) {
                    return header[0] == 0x50 && header[1] == 0x4B;
                }
            }
        } catch (Exception e) {
            logWarning("jar_validation_error", e.getMessage());
        }
        return false;
    }

    private String getCurrentPluginFileName() {
        try {
            File pluginFile = new File(plugin.getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (pluginFile.exists() && pluginFile.getName().endsWith(".jar")) {
                return pluginFile.getName();
            }
        } catch (Exception e) {
            logWarning("plugin_filename_error", e.getMessage());
        }
        return null;
    }

    private String getResourceId() {
        String configId = plugin.getConfig().getString("update-checker.resource-id");
        if (configId != null && !configId.isEmpty()) {
            logMessage("resource_id_from_config", configId);
            return configId;
        }

        String resourceId = plugin.getDescription().getWebsite();
        if (resourceId != null && resourceId.contains("spigotmc.org/resources/")) {
            String[] parts = resourceId.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("resources".equals(parts[i]) && i + 1 < parts.length) {
                    String idPart = parts[i + 1];
                    if (idPart.contains(".")) {
                        String[] subParts = idPart.split("\\.");
                        if (subParts.length > 1) {
                            for (String subPart : subParts) {
                                if (subPart.matches("\\d+")) {
                                    logMessage("resource_id_extracted", subPart);
                                    return subPart;
                                }
                            }
                        }
                    } else if (idPart.matches("\\d+")) {
                        logMessage("resource_id_extracted", idPart);
                        return idPart;
                    }
                }
            }
        }

        logWarning("resource_id_not_found");
        return null;
    }

    private UpdateResult performUpdateCheck(String resourceId) throws Exception {
        String latestVersion = null;
        Exception lastException = null;

        try {
            latestVersion = fetchVersionFromSpiget(resourceId);
            logMessage("version_fetch_spiget_success", latestVersion);
        } catch (Exception e) {
            logWarning("version_fetch_spiget_failed", e.getMessage());
            lastException = e;
        }

        if (latestVersion == null) {
            try {
                latestVersion = fetchVersionFromLegacy(resourceId);
                logMessage("version_fetch_legacy_success", latestVersion);
            } catch (Exception e) {
                logWarning("version_fetch_legacy_failed", e.getMessage());
                if (lastException != null) {
                    throw lastException;
                }
                throw e;
            }
        }

        if (latestVersion == null) {
            String languageKey = getLanguageKey();
            String errorMsg = LanguageManager.get("version_fetch_all_failed", languageKey);
            throw new Exception(errorMsg);
        }

        boolean hasUpdate = isNewerVersion(latestVersion, currentVersion);
        return new UpdateResult(hasUpdate, currentVersion, latestVersion, null);
    }

    private String fetchVersionFromSpiget(String resourceId) throws Exception {
        String url = SPIGET_API_BASE + resourceId + SPIGET_VERSION_ENDPOINT;
        logMessage("version_trying_spiget", url);

        HttpsURLConnection connection = createConnection(url);

        int responseCode = connection.getResponseCode();
        logMessage("version_spiget_response", responseCode);

        if (responseCode != 200) {
            String languageKey = getLanguageKey();
            String errorMsg = LanguageManager.get("version_spiget_http_error", languageKey);
            errorMsg = formatMessage(errorMsg, responseCode);
            throw new IOException(errorMsg);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
            return json.get("name").getAsString();
        }
    }

    private String fetchVersionFromLegacy(String resourceId) throws Exception {
        String url = decodeBase64(LEGACY_ENDPOINT) + resourceId;
        logMessage("version_trying_legacy", url);

        HttpsURLConnection connection = createConnection(url);

        int responseCode = connection.getResponseCode();
        logMessage("version_legacy_response", responseCode);

        if (responseCode != 200) {
            String languageKey = getLanguageKey();
            String errorMsg = LanguageManager.get("version_legacy_http_error", languageKey);
            errorMsg = formatMessage(errorMsg, responseCode);
            throw new IOException(errorMsg);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            return response.toString().trim();
        }
    }

    private HttpsURLConnection createConnection(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Charset", "UTF-8");

        return connection;
    }

    private boolean isNewerVersion(String latest, String current) {
        try {
            String cleanLatest = latest.replaceAll("^[^0-9]*", "");
            String cleanCurrent = current.replaceAll("^[^0-9]*", "");

            String[] latestParts = cleanLatest.split("\\.");
            String[] currentParts = cleanCurrent.split("\\.");

            int maxLength = Math.max(latestParts.length, currentParts.length);

            for (int i = 0; i < maxLength; i++) {
                int latestPart = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
                int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;

                if (latestPart > currentPart) {
                    return true;
                } else if (latestPart < currentPart) {
                    return false;
                }
            }

            return false;
        } catch (Exception e) {
            logWarning("version_comparison_failed", e.getMessage());
            return !latest.equals(current);
        }
    }

    private int parseVersionPart(String part) {
        try {
            StringBuilder numeric = new StringBuilder();
            for (char c : part.toCharArray()) {
                if (Character.isDigit(c)) {
                    numeric.append(c);
                } else {
                    break;
                }
            }
            return numeric.length() > 0 ? Integer.parseInt(numeric.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void handleUpdateAvailable(UpdateResult result) {
        if (result.getLatestVersion().equals(lastNotifiedVersion)) {
            return;
        }

        String languageKey = getLanguageKey();
        String message = LanguageManager.get("update_available_broadcast", languageKey);
        message = formatMessage(message, result.getLatestVersion(), result.getCurrentVersion());

        String finalMessage = message;
        compatibilityManager.runTask(() -> {
            if (notifyOpsOnly) {
                compatibilityManager.broadcastToPermission("bukkit.command.op", finalMessage);
            } else {
                String logMessage = LanguageManager.get("update_available_log", languageKey);
                logMessage = formatMessage(logMessage, result.getLatestVersion(), result.getCurrentVersion());
                plugin.getLogger().info(logMessage);
            }
        });

        lastNotifiedVersion = result.getLatestVersion();
    }

    public void resetNotificationTracker() {
        lastNotifiedVersion = null;
    }

    private String decodeBase64(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }


    public CompletableFuture<UpdateResult> checkForUpdatesAsync() {
        CompletableFuture<UpdateResult> future = new CompletableFuture<>();
        checkForUpdates(future::complete);
        return future;
    }

    public CompletableFuture<DownloadResult> downloadUpdateAsync() {
        CompletableFuture<DownloadResult> future = new CompletableFuture<>();
        downloadUpdate(future::complete);
        return future;
    }

    public boolean isUpdateCheckEnabled() {
        return updateCheckEnabled;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public static class UpdateResult {
        private final boolean hasUpdate;
        private final String currentVersion;
        private final String latestVersion;
        private final String error;

        public UpdateResult(boolean hasUpdate, String currentVersion, String latestVersion, String error) {
            this.hasUpdate = hasUpdate;
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.error = error;
        }

        public boolean hasUpdate() {
            return hasUpdate;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        public String getError() {
            return error;
        }

        public boolean hasError() {
            return error != null;
        }
    }

    public static class DownloadResult {
        private final boolean success;
        private final String message;
        private final String filePath;

        public DownloadResult(boolean success, String message, String filePath) {
            this.success = success;
            this.message = message;
            this.filePath = filePath;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getFilePath() {
            return filePath;
        }
    }

    public static void setupConfig(JavaPlugin plugin) {
        plugin.getConfig().addDefault("update-checker.enabled", true);
        plugin.getConfig().addDefault("update-checker.notify-ops-only", true);
        plugin.getConfig().addDefault("update-checker.auto-download", false);
        plugin.getConfig().addDefault("update-checker.resource-id", "126110");
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }
}
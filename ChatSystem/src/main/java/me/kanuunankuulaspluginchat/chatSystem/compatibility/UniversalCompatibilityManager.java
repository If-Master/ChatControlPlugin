package me.kanuunankuulaspluginchat.chatSystem.compatibility;
import me.kanuunankuulaspluginchat.chatSystem.Language.LanguageManager;
import me.kanuunankuulaspluginchat.chatSystem.compatibility.UpdateChecker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class UniversalCompatibilityManager {

    private final JavaPlugin plugin;
    private final ServerType serverType;
    private final Logger logger;
    private UpdateChecker updateChecker;
    private String language;


    public enum ServerType {
        FOLIA,
        PAPER,
        SPIGOT,
        BUKKIT
    }

    public interface TaskWrapper {
        void cancel();
        boolean isCancelled();
    }

    private static class BukkitTaskWrapper implements TaskWrapper {
        private final BukkitTask task;

        public BukkitTaskWrapper(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }

    private static class FoliaTaskWrapper implements TaskWrapper {
        private final Object task;

        public FoliaTaskWrapper(Object task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            try {
                java.lang.reflect.Method cancelMethod = task.getClass().getMethod("cancel");
                cancelMethod.invoke(task);
            } catch (Exception e) {
            }
        }

        @Override
        public boolean isCancelled() {
            try {
                java.lang.reflect.Method isCancelledMethod = task.getClass().getMethod("isCancelled");
                return (Boolean) isCancelledMethod.invoke(task);
            } catch (Exception e) {
                return false;
            }
        }
    }

    public UniversalCompatibilityManager(JavaPlugin plugin, LanguageManager languageManager) {
        this.plugin = plugin;
        this.serverType = detectServerType();
        this.logger = plugin.getLogger();

        logger.info("Detected server type: " + serverType.name());
        this.updateChecker = new UpdateChecker(plugin, this, languageManager);

    }
    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
    public void checkForUpdates(java.util.function.Consumer<UpdateChecker.UpdateResult> callback) {
        if (updateChecker != null) {
            updateChecker.checkForUpdates(callback);
        }
    }
    public CompletableFuture<UpdateChecker.UpdateResult> checkForUpdatesAsync() {
        if (updateChecker != null) {
            return updateChecker.checkForUpdatesAsync();
        }

        CompletableFuture<UpdateChecker.UpdateResult> future = new CompletableFuture<>();
        future.complete(new UpdateChecker.UpdateResult(false, "Unknown", "Unknown", "Update checker not initialized"));
        return future;
    }

    private ServerType detectServerType() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return ServerType.FOLIA;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return ServerType.PAPER;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("org.spigotmc.SpigotConfig");
            return ServerType.SPIGOT;
        } catch (ClassNotFoundException ignored) {}

        return ServerType.BUKKIT;
    }

    // === BASIC SCHEDULING METHODS ===

    public TaskWrapper runTask(Runnable task) {
        switch (serverType) {
            case FOLIA:
                return runFoliaGlobalTask(task);
            default:
                return new BukkitTaskWrapper(Bukkit.getScheduler().runTask(plugin, task));
        }
    }

    public TaskWrapper runTaskAsync(Runnable task) {
        switch (serverType) {
            case FOLIA:
                return runFoliaAsyncTask(task);
            default:
                return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
        }
    }

    public TaskWrapper runTaskLater(Runnable task, long delay) {
        switch (serverType) {
            case FOLIA:
                return runFoliaDelayedTask(task, delay);
            default:
                return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
        }
    }

    public TaskWrapper runTaskTimer(Runnable task, long delay, long period) {
        switch (serverType) {
            case FOLIA:
                return runFoliaTimerTask(task, delay, period);
            default:
                return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
        }
    }

    // === PLAYER-SPECIFIC SCHEDULING ===

    public void runPlayerTask(Player player, Runnable task) {
        switch (serverType) {
            case FOLIA:
                runFoliaPlayerTask(player, task);
                break;
            default:
                runTask(task);
                break;
        }
    }

    public void runPlayerTaskLater(Player player, Runnable task, long delay) {
        switch (serverType) {
            case FOLIA:
                runFoliaPlayerTaskLater(player, task, delay);
                break;
            default:
                runTaskLater(task, delay);
                break;
        }
    }

    public void runPlayerTaskTimer(Player player, Runnable task, long delay, long period) {
        switch (serverType) {
            case FOLIA:
                runFoliaPlayerTaskTimer(player, task, delay, period);
                break;
            default:
                runTaskTimer(task, delay, period);
                break;
        }
    }

    // === ENTITY-SPECIFIC SCHEDULING ===

    public void runEntityTask(Entity entity, Runnable task) {
        switch (serverType) {
            case FOLIA:
                runFoliaEntityTask(entity, task);
                break;
            default:
                runTask(task);
                break;
        }
    }

    public void runEntityTaskLater(Entity entity, Runnable task, long delay) {
        switch (serverType) {
            case FOLIA:
                runFoliaEntityTaskLater(entity, task, delay);
                break;
            default:
                runTaskLater(task, delay);
                break;
        }
    }

    // === LOCATION-SPECIFIC SCHEDULING ===

    public void runLocationTask(Location location, Runnable task) {
        switch (serverType) {
            case FOLIA:
                runFoliaLocationTask(location, task);
                break;
            default:
                runTask(task);
                break;
        }
    }

    public void runLocationTaskLater(Location location, Runnable task, long delay) {
        switch (serverType) {
            case FOLIA:
                runFoliaLocationTaskLater(location, task, delay);
                break;
            default:
                runTaskLater(task, delay);
                break;
        }
    }

    // === ASYNC UTILITIES ===

    public <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();

        runTaskAsync(() -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        runTaskAsync(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    // === BROADCAST UTILITIES ===

    public void broadcastToAllPlayers(String message) {
        runTask(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        });
    }

    public void broadcastToPermission(String permission, String message) {
        runTask(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(permission)) {
                    player.sendMessage(message);
                }
            }
        });
    }

    // === LOGGING UTILITIES ===

    public void log(String message) {
        logger.info(message);
    }

    public void warning(String message) {
        logger.warning(message);
    }

    public void severe(String message) {
        logger.severe(message);
    }

    // === FOLIA-SPECIFIC IMPLEMENTATIONS ===

    private TaskWrapper runFoliaGlobalTask(Runnable task) {
        try {
            Object globalScheduler = Bukkit.getGlobalRegionScheduler();
            java.lang.reflect.Method runMethod = globalScheduler.getClass()
                    .getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);

            Object scheduledTask = runMethod.invoke(globalScheduler, plugin, (Consumer<Object>) scheduledTaskObj -> task.run());
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            warning("Failed to run Folia global task, falling back to Bukkit scheduler: " + e.getMessage());
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTask(plugin, task));
        }
    }

    private TaskWrapper runFoliaAsyncTask(Runnable task) {
        try {
            Object asyncScheduler = Bukkit.getAsyncScheduler();
            java.lang.reflect.Method runNowMethod = asyncScheduler.getClass()
                    .getMethod("runNow", org.bukkit.plugin.Plugin.class, Consumer.class);

            Object scheduledTask = runNowMethod.invoke(asyncScheduler, plugin, (Consumer<Object>) scheduledTaskObj -> task.run());
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            warning("Failed to run Folia async task, falling back to Bukkit scheduler: " + e.getMessage());
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
        }
    }

    private TaskWrapper runFoliaDelayedTask(Runnable task, long delay) {
        try {
            Object globalScheduler = Bukkit.getGlobalRegionScheduler();
            java.lang.reflect.Method runDelayedMethod = globalScheduler.getClass()
                    .getMethod("runDelayed", org.bukkit.plugin.Plugin.class, Consumer.class, long.class);

            Object scheduledTask = runDelayedMethod.invoke(globalScheduler, plugin,
                    (Consumer<Object>) scheduledTaskObj -> task.run(), delay);
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            warning("Failed to run Folia delayed task, falling back to Bukkit scheduler: " + e.getMessage());
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
        }
    }

    private TaskWrapper runFoliaTimerTask(Runnable task, long delay, long period) {
        try {
            Object globalScheduler = Bukkit.getGlobalRegionScheduler();
            java.lang.reflect.Method runAtFixedRateMethod = globalScheduler.getClass()
                    .getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, Consumer.class, long.class, long.class);

            Object scheduledTask = runAtFixedRateMethod.invoke(globalScheduler, plugin,
                    (Consumer<Object>) scheduledTaskObj -> task.run(), delay, period);
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            warning("Failed to run Folia timer task, falling back to Bukkit scheduler: " + e.getMessage());
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
        }
    }

    private void runFoliaPlayerTask(Player player, Runnable task) {
        try {
            Object entityScheduler = player.getScheduler();
            java.lang.reflect.Method runMethod = entityScheduler.getClass()
                    .getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class);

            runMethod.invoke(entityScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), null);
        } catch (Exception e) {
            warning("Failed to run Folia player task, falling back to global task: " + e.getMessage());
            runTask(task);
        }
    }

    private void runFoliaPlayerTaskLater(Player player, Runnable task, long delay) {
        try {
            Object entityScheduler = player.getScheduler();
            java.lang.reflect.Method runDelayedMethod = entityScheduler.getClass()
                    .getMethod("runDelayed", org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class, long.class);

            runDelayedMethod.invoke(entityScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), null, delay);
        } catch (Exception e) {
            warning("Failed to run Folia player delayed task, falling back to global delayed task: " + e.getMessage());
            runTaskLater(task, delay);
        }
    }

    private void runFoliaPlayerTaskTimer(Player player, Runnable task, long delay, long period) {
        try {
            Object entityScheduler = player.getScheduler();
            java.lang.reflect.Method runAtFixedRateMethod = entityScheduler.getClass()
                    .getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class, long.class, long.class);

            runAtFixedRateMethod.invoke(entityScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), null, delay, period);
        } catch (Exception e) {
            warning("Failed to run Folia player timer task, falling back to global timer task: " + e.getMessage());
            runTaskTimer(task, delay, period);
        }
    }

    private void runFoliaEntityTask(Entity entity, Runnable task) {
        try {
            Object entityScheduler = entity.getScheduler();
            java.lang.reflect.Method runMethod = entityScheduler.getClass()
                    .getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class);

            runMethod.invoke(entityScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), null);
        } catch (Exception e) {
            warning("Failed to run Folia entity task, falling back to global task: " + e.getMessage());
            runTask(task);
        }
    }

    private void runFoliaEntityTaskLater(Entity entity, Runnable task, long delay) {
        try {
            Object entityScheduler = entity.getScheduler();
            java.lang.reflect.Method runDelayedMethod = entityScheduler.getClass()
                    .getMethod("runDelayed", org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class, long.class);

            runDelayedMethod.invoke(entityScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), null, delay);
        } catch (Exception e) {
            warning("Failed to run Folia entity delayed task, falling back to global delayed task: " + e.getMessage());
            runTaskLater(task, delay);
        }
    }

    private void runFoliaLocationTask(Location location, Runnable task) {
        try {
            Object regionScheduler = Bukkit.getRegionScheduler();
            java.lang.reflect.Method runMethod = regionScheduler.getClass()
                    .getMethod("run", org.bukkit.plugin.Plugin.class, Location.class, Consumer.class);

            runMethod.invoke(regionScheduler, plugin, location, (Consumer<Object>) scheduledTask -> task.run());
        } catch (Exception e) {
            warning("Failed to run Folia location task, falling back to global task: " + e.getMessage());
            runTask(task);
        }
    }

    private void runFoliaLocationTaskLater(Location location, Runnable task, long delay) {
        try {
            Object regionScheduler = Bukkit.getRegionScheduler();
            java.lang.reflect.Method runDelayedMethod = regionScheduler.getClass()
                    .getMethod("runDelayed", org.bukkit.plugin.Plugin.class, Location.class, Consumer.class, long.class);

            runDelayedMethod.invoke(regionScheduler, plugin, location, (Consumer<Object>) scheduledTask -> task.run(), delay);
        } catch (Exception e) {
            warning("Failed to run Folia location delayed task, falling back to global delayed task: " + e.getMessage());
            runTaskLater(task, delay);
        }
    }

    // === GETTERS AND UTILITY METHODS ===

    public ServerType getServerType() {
        return serverType;
    }

    public boolean isFolia() {
        return serverType == ServerType.FOLIA;
    }

    public boolean isPaper() {
        return serverType == ServerType.PAPER || serverType == ServerType.FOLIA;
    }

    public boolean isSpigot() {
        return serverType == ServerType.SPIGOT || isPaper();
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public static class SchedulerWrapper {
        private final UniversalCompatibilityManager manager;

        public SchedulerWrapper(UniversalCompatibilityManager manager) {
            this.manager = manager;
        }

        public TaskWrapper runTask(JavaPlugin plugin, Runnable task) {
            return manager.runTask(task);
        }

        public TaskWrapper runTaskAsynchronously(JavaPlugin plugin, Runnable task) {
            return manager.runTaskAsync(task);
        }

        public TaskWrapper runTaskLater(JavaPlugin plugin, Runnable task, long delay) {
            return manager.runTaskLater(task, delay);
        }

        public TaskWrapper runTaskTimer(JavaPlugin plugin, Runnable task, long delay, long period) {
            return manager.runTaskTimer(task, delay, period);
        }
    }

    public SchedulerWrapper getSchedulerWrapper() {
        return new SchedulerWrapper(this);
    }
}

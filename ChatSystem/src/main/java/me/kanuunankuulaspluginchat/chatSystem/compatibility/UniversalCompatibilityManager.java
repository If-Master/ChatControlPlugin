package me.kanuunankuulaspluginchat.chatSystem.compatibility;

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

    public enum ServerType {
        FOLIA,
        PAPER,
        SPIGOT,
        BUKKIT
    }



    public UniversalCompatibilityManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.serverType = detectServerType();
        this.logger = plugin.getLogger();

        logger.info("Detected server type: " + serverType.name());
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

    public BukkitTask runTask(Runnable task) {
        switch (serverType) {
            case FOLIA:
                return runFoliaGlobalTask(task);
            default:
                return Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public BukkitTask runTaskAsync(Runnable task) {
        switch (serverType) {
            case FOLIA:
                return runFoliaAsyncTask(task);
            default:
                return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public BukkitTask runTaskLater(Runnable task, long delay) {
        switch (serverType) {
            case FOLIA:
                return runFoliaDelayedTask(task, delay);
            default:
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    public BukkitTask runTaskTimer(Runnable task, long delay, long period) {
        switch (serverType) {
            case FOLIA:
                return runFoliaTimerTask(task, delay, period);
            default:
                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
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

    private BukkitTask runFoliaGlobalTask(Runnable task) {
        try {
            Object globalScheduler = Bukkit.getGlobalRegionScheduler();
            java.lang.reflect.Method runMethod = globalScheduler.getClass()
                    .getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);

            return (BukkitTask) runMethod.invoke(globalScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run());
        } catch (Exception e) {
            warning("Failed to run Folia global task, falling back to Bukkit scheduler: " + e.getMessage());
            return Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private BukkitTask runFoliaAsyncTask(Runnable task) {
        try {
            Object asyncScheduler = Bukkit.getAsyncScheduler();
            java.lang.reflect.Method runNowMethod = asyncScheduler.getClass()
                    .getMethod("runNow", org.bukkit.plugin.Plugin.class, Consumer.class);

            return (BukkitTask) runNowMethod.invoke(asyncScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run());
        } catch (Exception e) {
            warning("Failed to run Folia async task, falling back to Bukkit scheduler: " + e.getMessage());
            return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    private BukkitTask runFoliaDelayedTask(Runnable task, long delay) {
        try {
            Object globalScheduler = Bukkit.getGlobalRegionScheduler();
            java.lang.reflect.Method runDelayedMethod = globalScheduler.getClass()
                    .getMethod("runDelayed", org.bukkit.plugin.Plugin.class, Consumer.class, long.class);

            return (BukkitTask) runDelayedMethod.invoke(globalScheduler, plugin,
                    (Consumer<Object>) scheduledTask -> task.run(), delay);
        } catch (Exception e) {
            warning("Failed to run Folia delayed task, falling back to Bukkit scheduler: " + e.getMessage());
            return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    private BukkitTask runFoliaTimerTask(Runnable task, long delay, long period) {
        try {
            Object globalScheduler = Bukkit.getGlobalRegionScheduler();
            java.lang.reflect.Method runAtFixedRateMethod = globalScheduler.getClass()
                    .getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, Consumer.class, long.class, long.class);

            return (BukkitTask) runAtFixedRateMethod.invoke(globalScheduler, plugin,
                    (Consumer<Object>) scheduledTask -> task.run(), delay, period);
        } catch (Exception e) {
            warning("Failed to run Folia timer task, falling back to Bukkit scheduler: " + e.getMessage());
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
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

    /**
     * Creates a wrapper around existing methods that use Bukkit.getScheduler()
     * Use this to easily convert existing code
     */
    public static class SchedulerWrapper {
        private final UniversalCompatibilityManager manager;

        public SchedulerWrapper(UniversalCompatibilityManager manager) {
            this.manager = manager;
        }

        public BukkitTask runTask(JavaPlugin plugin, Runnable task) {
            return manager.runTask(task);
        }

        public BukkitTask runTaskAsynchronously(JavaPlugin plugin, Runnable task) {
            return manager.runTaskAsync(task);
        }

        public BukkitTask runTaskLater(JavaPlugin plugin, Runnable task, long delay) {
            return manager.runTaskLater(task, delay);
        }

        public BukkitTask runTaskTimer(JavaPlugin plugin, Runnable task, long delay, long period) {
            return manager.runTaskTimer(task, delay, period);
        }
    }

    public SchedulerWrapper getSchedulerWrapper() {
        return new SchedulerWrapper(this);
    }
}
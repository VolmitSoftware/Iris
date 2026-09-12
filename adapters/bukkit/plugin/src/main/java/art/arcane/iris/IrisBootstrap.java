package art.arcane.iris;

import art.arcane.iris.world.lifecycle.BukkitStartupPaths;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration.IrisWorldStorageEntry;
import art.arcane.iris.world.lifecycle.HuskWorldQuarantine;
import art.arcane.iris.world.lifecycle.MissingWorldStorageLog;
import art.arcane.iris.world.lifecycle.WorldReplacementBootstrap;
import art.arcane.iris.world.lifecycle.WorldReplacementBootstrapMarker;
import art.arcane.iris.pack.DefaultPackBootstrapProvisioner;
import art.arcane.iris.pack.DefaultPackBootstrapProvisioner.ProvisionResult;
import art.arcane.iris.platform.bootstrap.SlimJar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public final class IrisBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        WorldReplacementBootstrapMarker.markBootstrapped();
        try {
            loadRuntimeLibraries(context);
            BukkitStartupPaths startupPaths = BukkitStartupPaths.resolveCurrent();
            reconcilePendingWorldReplacements(context, startupPaths);
            quarantineWorthlessHusks(startupPaths, message -> context.getLogger().warn(message));
            requireUsableWorldStorage(startupPaths);
            ProvisionResult provisioned = provision(context, startupPaths);
            Path datapackRoot = provisioned.datapackRoot();
            context.getLogger().info("Iris startup datapack is {} at {}", provisioned.status(), datapackRoot);
        } catch (Throwable failure) {
            armStartupFailure(context, failure);
        }
    }

    private static void loadRuntimeLibraries(BootstrapContext context) {
        SlimJar.loadBootstrap(
                context.getDataDirectory().resolve("cache").resolve("libraries"),
                new SlimJar.BootstrapLogger() {
                    @Override
                    public void info(String message) {
                        context.getLogger().info(message);
                    }

                    @Override
                    public void error(String message) {
                        context.getLogger().error(message);
                    }

                    @Override
                    public void debug(String message) {
                        context.getLogger().debug(message);
                    }
                });
    }

    private static void reconcilePendingWorldReplacements(
            BootstrapContext context,
            BukkitStartupPaths startupPaths
    ) {
        try {
            WorldReplacementBootstrap.ReconcileResult result = WorldReplacementBootstrap.reconcile(
                    context.getDataDirectory(),
                    startupPaths.levelRoot(),
                    startupPaths.bukkitConfiguration(),
                    message -> context.getLogger().info(message)
            );
            if (result.transactions() > 0) {
                context.getLogger().info(
                        "Reconciled {} pending Iris world replacement(s): {} published, {} rolled back, {} retained",
                        result.transactions(),
                        result.published(),
                        result.rolledBack(),
                        result.retained()
                );
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Unable to reconcile pending Iris world replacements before registry bootstrap",
                    failure
            );
        }
    }

    /**
     * Moves a hot-deleted world's husk out of {@code <levelRoot>/dimensions/iris} before anything enumerates
     * it.
     * <p>
     * Deleting a loaded world's folder and letting the server save the level back leaves a directory holding
     * only the server's own {@code data/} skeleton. Left there it stops the boot whichever way it is
     * classified: as unusable storage it trips the guard below, and as an empty folder it is excluded from
     * the dimension registry, which trips Paper's interactive world-migration gate - a prompt that consumes
     * no console input and hangs startup forever. The folder is moved rather than deleted, so it stays
     * recoverable; {@link HuskWorldQuarantine} refuses anything that is not provably worthless.
     */
    static void quarantineWorthlessHusks(BukkitStartupPaths startupPaths, Consumer<String> warn) {
        HuskWorldQuarantine.quarantineWorthlessHusks(startupPaths.levelRoot(), warn);
    }

    /**
     * Stops startup when a world the server is about to load carries Iris storage Iris cannot generate from.
     * <p>
     * The server enumerates levels from {@code <levelRoot>/dimensions/<namespace>/<key>} on disk, so a world
     * whose folder is present is going to be loaded whatever Iris does; and CraftBukkit swallows whatever
     * {@code getDefaultWorldGenerator} throws and falls back to the vanilla generator, which writes vanilla
     * terrain straight into that world's region files. A pack snapshot that has gone missing therefore has
     * to be found here, before any level is created, exactly as a corrupt {@code dimensions/<id>.json} is.
     * <p>
     * A world with no folder at all is not a startup failure: nothing enumerates it, nothing loads it, and
     * its bukkit.yml and Multiverse entries are what make restoring the folder a complete recovery. Neither
     * is a folder that holds no pack snapshot and no world data - {@link #quarantineWorthlessHusks} has
     * already moved those out of the dimensions tree, and one that survives it (a Spigot-layout world folder,
     * or a move that failed) owns nothing a vanilla generator could destroy. Both are only reported.
     */
    static void requireUsableWorldStorage(BukkitStartupPaths startupPaths) throws IOException {
        Path levelRoot = startupPaths.levelRoot();
        Path levelName = levelRoot.getFileName();
        if (levelName == null || levelName.toString().isBlank()) {
            throw new IOException("Configured level root has no startup level id: " + levelRoot);
        }
        List<IrisWorldStorageEntry> entries = BukkitWorldConfiguration.auditIrisWorldStorage(
                startupPaths.bukkitConfiguration().toFile(),
                levelName.toString(),
                levelRoot
        );
        StringBuilder unusable = new StringBuilder();
        for (IrisWorldStorageEntry entry : entries) {
            switch (entry.state()) {
                case PRESENT -> {
                }
                case MISSING -> MissingWorldStorageLog.warnOnce(
                        entry.configuredWorldName(),
                        entry.storagePath());
                case EMPTY -> MissingWorldStorageLog.warnEmptyOnce(
                        entry.configuredWorldName(),
                        entry.storagePath());
                case UNUSABLE -> unusable.append(unusable.isEmpty() ? "" : "; ")
                        .append(entry.configuredWorldName())
                        .append(" (")
                        .append(entry.storagePath())
                        .append(": ")
                        .append(entry.detail() == null ? "storage is unusable" : entry.detail())
                        .append(')');
            }
        }
        if (unusable.isEmpty()) {
            return;
        }
        throw new IllegalStateException("Iris world storage is unusable and the server would generate vanilla"
                + " terrain over it: " + unusable
                + ". Restore each world folder from a backup, or move it aside; a world with no folder is"
                + " reported and skipped, and the server starts.");
    }

    static void armStartupFailure(BootstrapContext context, Throwable failure) {
        armStartupFailure(context, failure, LifecycleEvents.DATAPACK_DISCOVERY);
    }

    static <E extends LifecycleEvent> void armStartupFailure(
            BootstrapContext context,
            Throwable failure,
            LifecycleEventType<? super BootstrapContext, ? extends E, ?> eventType
    ) {
        context.getLifecycleManager().registerEventHandler(eventType, event -> {
            throw new IllegalStateException("Iris bootstrap did not establish safe world-generation state.", failure);
        });
        try {
            context.getLogger().error(
                    "Iris bootstrap failed; registry and world startup will be stopped at datapack discovery.",
                    failure
            );
        } catch (Throwable loggingFailure) {
            failure.addSuppressed(loggingFailure);
            failure.printStackTrace(System.err);
        }
    }

    private static ProvisionResult provision(BootstrapContext context, BukkitStartupPaths startupPaths) {
        try {
            return DefaultPackBootstrapProvisioner.provision(
                    context.getDataDirectory(),
                    message -> context.getLogger().info(message),
                    startupPaths
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to provision the Iris startup datapack", e);
        }
    }
}

package art.arcane.iris;

import art.arcane.iris.world.lifecycle.BukkitStartupPaths;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration.IrisWorldStorageEntry;
import art.arcane.iris.world.lifecycle.HuskWorldQuarantine;
import art.arcane.iris.world.lifecycle.MissingWorldStorageLog;
import art.arcane.iris.world.lifecycle.WorldReplacementBootstrap;
import art.arcane.iris.world.lifecycle.WorldReplacementBootstrapMarker;
import art.arcane.iris.pack.DefaultPackBootstrapProvisioner;
import art.arcane.iris.pack.DefaultPackBootstrapProvisioner.BootstrapRequest;
import art.arcane.iris.pack.datapack.DataVersion;
import io.papermc.paper.ServerBuildInfo;
import art.arcane.iris.pack.DefaultPackBootstrapProvisioner.ProvisionResult;
import art.arcane.iris.platform.bootstrap.SlimJar;
import art.arcane.iris.world.safeguard.GenerationRefusalNotice;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
                ServerBuildInfo.buildInfo().minecraftVersionId(),
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
     * whose folder is present is going to be loaded whatever Iris does. The generator resolver answers a world
     * it cannot generate with a fail-closed generator, which stops startup at that world's own level creation,
     * after the levels before it have loaded. A pack snapshot that has gone missing is found here instead,
     * before any level is created, exactly as a corrupt {@code dimensions/<id>.json} is. An Iris world in a
     * vanilla slot whose bukkit.yml entry no longer names Iris stops startup too: CraftServer would build it
     * with the vanilla generator without ever asking Iris.
     * <p>
     * A world with no folder at all is not a startup failure: nothing enumerates it, nothing loads it, and
     * its bukkit.yml and Multiverse entries are what make restoring the folder a complete recovery. Neither
     * is a folder that holds no pack snapshot and no world data - {@link #quarantineWorthlessHusks} has
     * already moved those out of the dimensions tree, and one that survives it (a Spigot-layout world folder,
     * or a move that failed) owns nothing to lose. Both are only reported here.
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
        StringBuilder unbound = new StringBuilder();
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
                case UNBOUND -> unbound.append(unbound.isEmpty() ? "" : "; ")
                        .append(entry.configuredWorldName())
                        .append(" (")
                        .append(entry.storagePath())
                        .append("): set worlds.")
                        .append(entry.configuredWorldName())
                        .append(".generator to Iris:<pack> in bukkit.yml, or move the folder aside to start a new"
                                + " vanilla world there");
            }
        }
        List<String> refusals = new ArrayList<>(2);
        if (!unusable.isEmpty()) {
            refusals.add("Iris world storage is unusable and the server would generate vanilla terrain over it: "
                    + unusable + ". Restore each world folder from a backup, or move it aside; a world with no"
                    + " folder is reported and skipped, and the server starts.");
        }
        if (!unbound.isEmpty()) {
            refusals.add("Iris worlds in vanilla slots are not bound to Iris, so the server would generate vanilla"
                    + " terrain into them: " + unbound + ".");
        }
        if (!refusals.isEmpty()) {
            throw new IllegalStateException(String.join(" ", refusals));
        }
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
        List<String> notice = startupRefusalNotice(failure);
        try {
            for (String line : notice) {
                context.getLogger().error(line);
            }
            context.getLogger().warn("Iris bootstrap failure", failure);
        } catch (Throwable loggingFailure) {
            notice.forEach(System.err::println);
            failure.addSuppressed(loggingFailure);
            failure.printStackTrace(System.err);
        }
    }

    static List<String> startupRefusalNotice(Throwable failure) {
        return GenerationRefusalNotice.compose(
                "Iris stopped server startup before any world loads",
                GenerationRefusalNotice.causes(failure),
                List.of(
                        "Startup stops at datapack discovery: no world loads and no chunks are written.",
                        "Fix the cause, then start the server again. Paper's --safeMode hint does not apply:"
                                + " Iris worlds must not load without Iris."
                ));
    }

    private static ProvisionResult provision(BootstrapContext context, BukkitStartupPaths startupPaths) {
        try {
            return DefaultPackBootstrapProvisioner.provision(new BootstrapRequest(
                    context.getDataDirectory(),
                    message -> context.getLogger().info(message),
                    startupPaths,
                    DataVersion.forMinecraftVersion(ServerBuildInfo.buildInfo().minecraftVersionId())
            ));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to provision the Iris startup datapack", e);
        }
    }
}

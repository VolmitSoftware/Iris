package art.arcane.iris;

import art.arcane.iris.platform.bootstrap.ServerProperties;
import art.arcane.iris.world.IrisStartupValidation;
import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration;
import art.arcane.iris.world.safeguard.GenerationRefusalNotice;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Keeps a failed enable from handing Iris worlds to the vanilla generator.
 * <p>
 * Paper disables a plugin whose onEnable fails, and CraftServer builds every world bukkit.yml points at a disabled
 * generator plugin with the vanilla generator. On a server with Iris worlds, Iris therefore stays enabled with its
 * runtime locked: the generator resolver answers every Iris world with a fail-closed generator, which stops the
 * level before it exists, and the admission listener refuses logins. It only needs IrisStartupValidation and
 * logging, never a service the failed enable may not have created.
 */
final class FailedEnableLock {
    private FailedEnableLock() {
    }

    /**
     * @return true when Iris must stay enabled and locked, false when the failure may disable Iris as before
     */
    static boolean engage(Throwable failure, boolean irisWorldsPresent) {
        if (!irisWorldsPresent) {
            return false;
        }
        IrisStartupValidation.markRuntimeInvalid("Iris failed to enable: " + GenerationRefusalNotice.summary(failure));
        List<String> notice = notice(failure);
        try {
            for (String line : notice) {
                Iris.error(line);
            }
            Iris.reportError(failure);
        } catch (Throwable loggingFailure) {
            notice.forEach(System.err::println);
            failure.addSuppressed(loggingFailure);
            failure.printStackTrace(System.err);
        }
        return true;
    }

    static List<String> notice(Throwable failure) {
        return GenerationRefusalNotice.compose(
                "Iris failed to enable",
                GenerationRefusalNotice.causes(failure),
                List.of(
                        "Iris stays enabled with a locked runtime so no Iris world falls back to vanilla generation.",
                        "Every Iris world refuses to generate: server startup stops before the first one loads,"
                                + " and no chunks are written.",
                        "Player logins are refused until this is fixed and the server restarts."
                ));
    }

    static boolean irisWorldsPresent() {
        try {
            return irisWorldsPresent(IrisWorldStorage.levelRoot(), ServerProperties.BUKKIT_YML);
        } catch (Throwable unreadable) {
            Iris.reportError("Could not inspect this server for Iris worlds; treating it as having them.", unreadable);
            return true;
        }
    }

    static boolean irisWorldsPresent(File levelRoot, File bukkitConfiguration) throws IOException {
        return IrisWorldStorage.hasManagedWorldStorage(levelRoot)
                || BukkitWorldConfiguration.configuresIrisGenerator(bukkitConfiguration);
    }
}

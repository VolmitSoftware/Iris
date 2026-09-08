package art.arcane.iris.core.lifecycle;

import art.arcane.iris.spi.IrisLogging;

import art.arcane.iris.util.common.io.Durability;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Directory-level durability barrier shared by every atomic publication in the
 * world-replacement protocol (journal writes, world-directory moves, bukkit.yml
 * saves). A rename is only durable once its parent directory has been fsynced;
 * skipped on Windows, where directory handles cannot be forced.
 */
final class DirectoryDurability {
    private DirectoryDurability() {
    }

    static void forceDirectoryRequired(Path directory) throws IOException {
        if (!Durability.enabled()) {
            return;
        }

        if (File.separatorChar == '\\') {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            Durability.force(channel);
        } catch (UnsupportedOperationException failure) {
            throw new IOException("Directory durability sync is unavailable for " + directory + ".", failure);
        }
    }

    static void forceDirectoryAfterCommit(Path directory, String context) {
        try {
            forceDirectoryRequired(directory);
        } catch (IOException failure) {
            IrisLogging.reportError(
                    context + " completed, but its parent directory could not be durability-synced.",
                    failure
            );
        }
    }
}

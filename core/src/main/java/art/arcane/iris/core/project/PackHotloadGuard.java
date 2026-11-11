package art.arcane.iris.core.project;

import art.arcane.iris.core.protocol.IrisProtocolServer;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides whether a pack hotload may run. A hotload is skipped, logged and announced to studio clients while any
 * touched pack JSON fails to parse; it never throws, so a watcher callback can return normally and stay quiet
 * until the next save.
 */
public final class PackHotloadGuard {
    private static final int NO_CHANGED_FILES = 0;

    private final File packFolder;
    private final UnreadablePackFiles unreadable = new UnreadablePackFiles();

    public PackHotloadGuard(File packFolder) {
        this.packFolder = packFolder;
    }

    public static List<File> touched(Collection<File> created, Collection<File> changed) {
        List<File> touched = new ArrayList<>(created);
        touched.addAll(changed);
        return touched;
    }

    public boolean allowsHotload(Collection<File> touched) {
        return skipReason(touched).isEmpty();
    }

    /** The first unreadable file and what is wrong with it, empty when the hotload may run. Every skip is logged and announced. */
    public Optional<String> skipReason(Collection<File> touched) {
        Map<File, String> blocking = unreadable.recheck(touched);
        List<String> unreadableFiles = blocking.entrySet().stream()
                .map(unreadableFile -> describeUnreadable(unreadableFile.getKey(), unreadableFile.getValue()))
                .toList();
        unreadableFiles.forEach(this::reportSkipped);
        return unreadableFiles.stream().findFirst();
    }

    private String describeUnreadable(File file, String reason) {
        return packFolder.toPath().relativize(file.toPath()) + ": " + reason;
    }

    private void reportSkipped(String unreadableFile) {
        String message = "Hotload skipped, " + unreadableFile;
        IrisLogging.warn("%s", message);
        announce(message);
    }

    private void announce(String message) {
        try {
            IrisProtocolServer protocolServer = IrisServices.getOrNull(IrisProtocolServer.class);
            if (protocolServer == null) {
                return;
            }
            protocolServer.broadcastStudioHotload(packFolder.getName(), NO_CHANGED_FILES, true, message);
        } catch (Throwable announceFailure) {
            IrisLogging.error("Iris studio hotload skip broadcast failed: %s: %s",
                    announceFailure.getClass().getSimpleName(), announceFailure.getMessage());
        }
    }
}

package art.arcane.iris.core.project;

import art.arcane.iris.core.protocol.IrisProtocolServer;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
        Map<File, String> blocking = unreadable.recheck(touched);
        if (blocking.isEmpty()) {
            return true;
        }
        blocking.forEach(this::reportSkipped);
        return false;
    }

    private void reportSkipped(File file, String reason) {
        String message = "Hotload skipped, " + packFolder.toPath().relativize(file.toPath()) + ": " + reason;
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

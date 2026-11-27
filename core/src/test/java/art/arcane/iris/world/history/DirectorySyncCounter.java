package art.arcane.iris.world.history;

import org.mockito.MockedStatic;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.Mockito.mockStatic;

/**
 * Records directory channels opened on the calling thread, which is how directory fsyncs are issued.
 */
final class DirectorySyncCounter {
    private DirectorySyncCounter() {
    }

    static List<Path> openedDirectories(IoAction action) throws Exception {
        List<Path> opened = new CopyOnWriteArrayList<>();
        try (MockedStatic<FileChannel> ignored = mockStatic(FileChannel.class, invocation -> {
            if (invocation.getMethod().getName().equals("open")
                    && invocation.getArgument(0) instanceof Path path
                    && Files.isDirectory(path)) {
                opened.add(path.toAbsolutePath().normalize());
            }
            return invocation.callRealMethod();
        })) {
            action.run();
        }
        return opened;
    }

    static long countUnder(Path root, List<Path> opened) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        return opened.stream().filter(path -> path.startsWith(normalizedRoot)).count();
    }

    @FunctionalInterface
    interface IoAction {
        void run() throws Exception;
    }
}

package art.arcane.iris.util.common.io;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;

public final class Durability {
    public static final String MODE_PROPERTY = "iris.durability";
    private static final String RELAXED = "relaxed";

    private Durability() {
    }

    public static boolean enabled() {
        return !RELAXED.equalsIgnoreCase(System.getProperty(MODE_PROPERTY));
    }

    public static void force(FileChannel channel) throws IOException {
        if (!enabled()) {
            return;
        }

        channel.force(true);
    }

    public static void force(FileDescriptor descriptor) throws IOException {
        if (!enabled()) {
            return;
        }

        descriptor.sync();
    }
}

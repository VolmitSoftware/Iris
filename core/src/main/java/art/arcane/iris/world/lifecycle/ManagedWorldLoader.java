package art.arcane.iris.world.lifecycle;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Loads a persistent Iris world back into a running server.
 * <p>
 * Iris creates its worlds from a keyed WorldCreator so the level lands in
 * {@code <levelRoot>/dimensions/iris/<key>} and carries the environment the pack's dimension declares.
 * Nothing else can: a plain name-keyed creator builds a fresh vanilla world beside the level root, and a
 * custom-dimension world reports {@code CUSTOM} after its first restart, which CraftServer refuses outright.
 * Any integration that wants an Iris world loaded has to come through here.
 * <p>
 * The implementation is installed by the platform adapter; core resolves it through
 * {@link art.arcane.iris.spi.IrisServices} and degrades when nothing is registered.
 */
public interface ManagedWorldLoader {
    /**
     * Loads the world registered under {@code configuredWorldName} - the Bukkit startup name, not an alias.
     * Never throws: a refusal is reported through the returned outcome.
     */
    CompletableFuture<ManagedWorldLoad> load(String configuredWorldName);

    record ManagedWorldLoad(boolean loaded, String message) {
        public ManagedWorldLoad {
            Objects.requireNonNull(message, "message");
        }

        public static ManagedWorldLoad loaded(String message) {
            return new ManagedWorldLoad(true, message);
        }

        public static ManagedWorldLoad failed(String message) {
            return new ManagedWorldLoad(false, message);
        }
    }
}

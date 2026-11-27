package art.arcane.iris.world.history;

import java.io.IOException;
import java.util.List;

/**
 * A Studio update references custom biomes the running server has no definition for, so only a restart can
 * apply it.
 */
public final class RegistryRestartRequiredException extends IOException {
    private final List<String> blockingBiomes;
    private final List<String> deferredBiomes;

    public RegistryRestartRequiredException(List<String> blockingBiomes, List<String> deferredBiomes) {
        super("Restart needed to load biomes: " + String.join(", ", blockingBiomes));
        this.blockingBiomes = List.copyOf(blockingBiomes);
        this.deferredBiomes = List.copyOf(deferredBiomes);
    }

    public static boolean inCauseChain(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RegistryRestartRequiredException) {
                return true;
            }
        }
        return false;
    }

    public List<String> blockingBiomes() {
        return blockingBiomes;
    }

    public List<String> deferredBiomes() {
        return deferredBiomes;
    }
}

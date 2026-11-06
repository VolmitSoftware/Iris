package com.volmit.generation;

import com.volmit.ModuleLog;
import com.volmit.world.PackWorld;
import java.util.Optional;

/** Host-owned invocation inputs. Views must not be retained or used by detached tasks.
 *
 * <p>There is no shared random stream. A stage draws from {@link com.volmit.stage.StageContext#random()},
 * a stream of its own.
 */
public interface InvocationContext {
    /** The world this invocation generates: its seed, the pack snapshot's resources, its settings and the state modules
     * keep for it ({@link PackWorld#state}). Every invocation has one. A placement a tool makes outside a world the host
     * generates, such as a studio paste, gets a world of its own that lives for that placement.
     */
    PackWorld world();

    /** The log of the module this invocation runs for, labelled with the module's id. The same as {@code world().log()}. */
    default ModuleLog log() {
        return world().log();
    }

    /**
     * What the host offers for this one invocation beyond the API, as an object of {@code type}: empty when the host
     * offers no such thing. The per invocation counterpart of {@link PackWorld#extension}: a host hands its own modules
     * inputs that belong to one placement or one chunk this way, such as what Iris knows about the placement that is
     * running. The host fills it for each invocation; like every input it must not be kept after the invocation.
     */
    default <T> Optional<T> extension(Class<T> type) {
        return Optional.empty();
    }
}

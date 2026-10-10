package com.volmit.generation;

import com.volmit.ModuleLog;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;

/** Host-owned invocation inputs. Views must not be retained or used by detached tasks.
 *
 * <p>There is no shared random stream. A stage draws from {@link com.volmit.stage.StageContext#random()},
 * a stream of its own, and an object generator from {@link com.volmit.object.ObjectGenerationContext#random()}.
 */
public interface InvocationContext {
    long worldSeed();
    PackResources resources();

    /** The world this invocation generates, where modules keep their per-world state ({@link PackWorld#state}). An
     * invocation outside any world, such as an object a studio tool pastes, throws IllegalStateException.
     */
    PackWorld world();

    /** The log of the module this invocation runs for, labelled with the module's id. The same as
     * {@code world().log()}, which a host overrides where an invocation has no world.
     */
    default ModuleLog log() {
        return world().log();
    }
}

package com.volmit.tooling;

import com.volmit.resource.PlatformRegistries;
import com.volmit.world.PackWorld;
import java.util.Optional;

/**
 * One run of a command, or one completion: the arguments parsed so far, who sent it and what it runs against. Valid only
 * while the action or the suggestions run.
 */
public interface CommandContext {
    /**
     * The value of {@code argument}, or its fallback when the sender left it out.
     *
     * @throws IllegalStateException when the argument is required and has no value yet, as in the suggestions of an
     *     argument that comes before it
     */
    <T> T get(Argument<T> argument);

    /** Who sent the command. */
    Sender sender();

    /** The pack the host resolved for this command: the one named by {@code pack=<name>}, else the pack of the sender's
     * world, else the pack of the sender's open studio.
     */
    ToolPack pack();

    /** The world the sender stands in, when it generates with {@link #pack()}; empty for the console or another pack's world. */
    Optional<PackWorld> world();

    /** The platform's registries; empty when the host runs without a platform. */
    Optional<PlatformRegistries> platform();
}

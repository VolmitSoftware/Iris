package com.volmit.world;

import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import java.util.function.Function;

/** One world the host generates with a pack, for as long as the pack stays loaded: the world's seed, the pack's
 * resources and settings, and the state modules keep for this world. Stage bodies reach it through
 * {@link com.volmit.generation.InvocationContext#world()}, services as their first argument and lifecycle hooks as
 * theirs ({@link com.volmit.ModuleLifecycle}).
 *
 * <p>A hotload ends the world: the host stops it ({@link com.volmit.ModuleLifecycle#worldStopped}) and starts a new
 * one with the reloaded pack and an empty state store. So does a reload of the pack's resources while the world runs,
 * such as after a tool rewrote some of its files. State therefore never outlives the resources it was built from.
 * Invocations that began before the restart finish with the world they began with.
 *
 * <p>Safe to use from any thread.
 */
public interface PackWorld {
    long seed();

    /** The pack snapshot this world generates with. */
    PackResources resources();

    /** The settings of the world's dimension and of every region and biome it can reach, as mantle reaches see them. */
    PackSettings settings();

    /**
     * The world's one {@code type} instance, created by {@code create} the first time anyone asks for it. Modules keep
     * their caches and per-world workers here instead of in fields, since stage bodies and services are shared by every
     * world and every invocation. Use a class of the module as {@code type}, so two modules never share an entry.
     *
     * <p>{@code create} runs once per world, even when several threads ask at once; the others wait for it and get the
     * same instance. It may ask for other state of this world on its own thread, but must not wait for other threads
     * that ask for state, and a type that is asked for again while it is being created fails with
     * IllegalStateException. If {@code create} throws, nothing is kept and the next call tries again. A state that is
     * {@link AutoCloseable} is closed after the world stops, once nothing generates with it any more. After the world
     * stopped, asking for state throws IllegalStateException.
     */
    <T> T state(Class<T> type, Function<? super PackWorld, ? extends T> create);
}

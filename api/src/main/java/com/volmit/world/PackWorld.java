package com.volmit.world;

import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.placement.Placements;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import java.util.Optional;
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
 * <p>A placement a tool makes outside the worlds the host generates, such as a studio paste, runs in a world of its own
 * that the host builds for that placement: the pack's resources and settings, a seed the host picks, and a state store
 * that lives as long as the placement. No lifecycle hook runs for it.
 *
 * <p>Safe to use from any thread.
 */
public interface PackWorld {
    long seed();

    /**
     * The seed this world gives {@code purpose}, such as {@code example:moss/patches}: the same for the same world and
     * purpose, independent across purposes. Seed a module's own randoms and noise with it instead of mixing
     * {@link #seed()} by hand. A host answers {@link com.volmit.random.WorldRandom#purposeSeed} of the world's seed,
     * except for purposes of its own modules whose worlds already depend on other seeds: Iris answers the ids of
     * {@code iris:} purposes its built-in modules use with the seeds its worlds always had.
     */
    long seed(ResourceId purpose);

    /** The pack snapshot this world generates with. */
    PackResources resources();

    /** The settings of the world's dimension and of every region and biome it can reach, as mantle reaches see them. */
    PackSettings settings();

    /** The Y in the world of the dimension's bottom block, such as {@code -64} in the overworld. Every height the API
     * carries is above that bottom; settings that name an absolute world Y, as packs write them for the platform, are
     * converted with it: {@code heightAboveBottom = worldY - minWorldY()}. The same number as
     * {@link TerrainSampler#minWorldY()}.
     */
    int minWorldY();

    /** The world as it runs now, for services and lifecycle hooks; empty for a tool's world, which nothing runs. */
    Optional<RunningWorld> running();

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

    /** Runs the pack's placement stages for the objects a module places in this world. */
    Placements placements();

    /** The log of the module this world was handed to: every service call, lifecycle hook and stage body gets a view of
     * the world whose log is that module's own, so a line is labelled with the module that wrote it.
     */
    ModuleLog log();

    /**
     * What the host offers for this world beyond the API, as an object of {@code type}: empty when the host offers no
     * such thing. A host extends the API this way for its own modules, such as Iris's built-in modules, which reach Iris's
     * object placing or its derived seeds through interfaces of their own exposed package. The host fills it when it
     * builds the world; a module that asks for a type the host does not know gets empty and decides itself whether that
     * is an error. Pack modules written for any host use the API only. Per invocation inputs are
     * {@link com.volmit.generation.InvocationContext#extension}.
     */
    default <T> Optional<T> extension(Class<T> type) {
        return Optional.empty();
    }
}

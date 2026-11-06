package com.volmit.config;

import com.volmit.IrisFeature;
import com.volmit.resource.PackResources;
import java.util.Optional;

/** Every settings declaration of the pack the host is loading, for what depends on the whole pack rather
 * than one column, such as how far a mantle stage reaches ({@link com.volmit.stage.PackReach}).
 *
 * <p>The levels are those chunk and mantle stages see: the dimension being generated, and every region
 * and biome it can reach. Placements are not a level here. Values must be treated as read-only and not
 * retained.
 */
public interface PackSettings {
    /** Every entry of {@code featureType} the pack declares, dimension first, then regions, then biomes;
     * empty when nothing declares it.
     */
    <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType);

    /** The pack snapshot the settings were read from, such as for a reach that depends on the size of the pack's objects. */
    PackResources resources();

    /**
     * What the host offers for the pack it is loading beyond the API, as an object of {@code type}: empty when it offers
     * no such thing. The same door as {@link com.volmit.world.PackWorld#extension}, for what is worked out from the
     * settings before any world generates, such as a {@link com.volmit.stage.PackReach} that needs the size of the pack's
     * objects from the host.
     */
    default <T> Optional<T> extension(Class<T> type) {
        return Optional.empty();
    }
}

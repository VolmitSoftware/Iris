package com.volmit.config;

import com.volmit.IrisFeature;

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
}

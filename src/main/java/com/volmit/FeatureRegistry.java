package com.volmit;

import com.volmit.generation.ModifierFactory;
import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.object.ObjectGeneratorFactory;
import com.volmit.placement.PlacementModifierFactory;

/** Host implementation collects registrations before loading pack configuration.
 * Feature IDs must be unique. JSON fields must be unique per attachment target.
 * Modifiers own their IDs and dependency declarations, independently of feature IDs.
 * All configured instances of a modifier follow the same ordering.
 */
public interface FeatureRegistry {
    /** Registers a layer before pack loading. Null arguments throw NullPointerException;
     * duplicate IDs and negative codec versions throw IllegalArgumentException.
     * Only the owner registers a shared layer; consumers refer to its key. The host must
     * preserve unknown persisted layers or fail explicitly, never silently discard them.
     * GENERATION layers also require codecs.
     */
    <T> void registerMantle(MantleKey<T> key, MantleLifetime lifetime, MantleCodec<T> codec);

    <F extends IrisFeature, T> void register(Class<F> featureType,
                         Class<T> outputType, ModifierFactory<F, T> factory);

    <F extends IrisFeature> void registerObject(Class<F> featureType, ObjectGeneratorFactory<F> factory);

    <F extends IrisFeature> void registerPlacement(Class<F> featureType, PlacementModifierFactory<F> factory);
}

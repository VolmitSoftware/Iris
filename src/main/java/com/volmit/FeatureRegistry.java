package com.volmit;

import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.object.ObjectGeneratorFactory;

/** Host implementation collects non-stage registrations before loading pack configuration.
 * Feature IDs must be unique. JSON fields must be unique per attachment target, including the
 * settings types stages name in {@code when(...)}. Feature IDs and mantle keys a module registers
 * are its module id or start with it and {@code /}, such as {@code iris:snow} or {@code iris:snow/depth}.
 */
public interface FeatureRegistry {
    /** Registers a layer before pack loading. Null arguments throw NullPointerException;
     * duplicate IDs and negative codec versions throw IllegalArgumentException.
     * Only the owner registers a shared layer; consumers refer to its key. The host must
     * preserve unknown persisted layers or fail explicitly, never silently discard them.
     * GENERATION layers also require codecs.
     */
    <T> void registerMantle(MantleKey<T> key, MantleLifetime lifetime, MantleCodec<T> codec);

    <F extends IrisFeature> void registerObject(Class<F> featureType, ObjectGeneratorFactory<F> factory);
}

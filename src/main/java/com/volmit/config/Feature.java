package com.volmit.config;

import java.lang.annotation.*;

/** Identity and JSON attachment for an IrisFeature configuration class. The {@code id}, such as
 * {@code "example:moss"}, is the feature's identity in the stage graph, logs and lookups by class. Packs write
 * the settings under the short {@code key}, such as {@code "moss": {...}}, at each of the targets.
 *
 * <p>The key is a plain JSON field name: not blank, no ':'. It is unique per target across all loaded modules
 * and the JSON fields the host itself uses at that target, so it must not be a field of a biome, region or
 * dimension; a clash refuses the pack load, naming both claimants.
 *
 * <p>The JSON under the key is an object of the class's options, unless {@code root} names an option. Then the
 * key holds exactly that option's value, such as a list, and an object is not accepted: with
 * {@code @Feature(id = "iris:deposit", key = "deposits", root = "generators", ...)} a pack writes
 * {@code "deposits": [...]}. The class has no other option then, since nothing could set it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Feature {
    String id();
    String name();
    String key();
    String root() default "";
    com.volmit.FeatureAttachment.Target[] targets();
}

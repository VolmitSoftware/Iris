package com.volmit.config;

import java.lang.annotation.*;

/** Identity and JSON attachment for an IrisFeature configuration class. Packs write the settings under
 * their feature id, such as {@code "example:moss": {...}}, at each of the targets.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Feature {
    String id();
    String name();
    com.volmit.FeatureAttachment.Target[] targets();
}

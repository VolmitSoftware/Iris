package com.volmit.config;

import java.lang.annotation.*;

/** Identity and JSON attachment for an IrisFeature configuration class. Packs write the settings under
 * their feature id, such as {@code "example:moss": {...}}, at each of the targets.
 *
 * <p>{@code alias} is an optional short JSON key that only modules shipping with the host may set, so
 * existing pack JSON such as {@code "stilt": {...}} keeps loading. A pack module that sets one fails
 * the load. An alias contains no ':', so it never equals a feature id.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Feature {
    String id();
    String name();
    String alias() default "";
    com.volmit.FeatureAttachment.Target[] targets();
}

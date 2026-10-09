package com.volmit.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an interface as a service: a capability modules provide and the host looks up, such as finding a module's
 * structures, instead of knowing the feature behind it. Modules register providers with
 * {@link com.volmit.FeatureRegistry#registerService}; the host gets them from the pack's catalog.
 *
 * <p>Providers hold no state of their own: a provider is shared by every world, and the host may call it from any
 * thread, also from several at once. A provider keeps what it needs in the world's state store
 * ({@link com.volmit.world.PackWorld#state}), which is why service methods take the world. Services compute answers;
 * they never act on the platform themselves: what should happen in the world comes back as a value the host carries out
 * on the right thread.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {
    /** How the host treats several modules providing this service. */
    Providers value();
}

package com.volmit.validation;

import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.resource.PlatformRegistries;

import java.util.List;
import java.util.Optional;

/** The pack one {@link Validator} checks, as the host would load it, and where it reports what it finds. Valid
 * only while {@link Validator#validate} runs.
 */
public interface ValidationContext {
    /** The pack's resources: the ones the host serves (objects, biomes, regions) and every module resource kind.
     * {@link PackResources#ids} lists a kind's resources.
     */
    PackResources resources();

    /** The module settings the pack declares: every entry of every dimension, region and biome of the pack,
     * whether or not a dimension reaches it.
     */
    PackSettings settings();

    /** The platform's registries as the running server has them; empty when the host validates without a platform,
     * such as a build or a data pack compile before the server starts. A check against them is skipped when empty.
     */
    Optional<PlatformRegistries> platform();

    /**
     * The providers of {@code service} that the pack's modules registered, in module id order and each module's in the
     * order it registered them; empty when no module provides it. This is how a validator asks another module, such as
     * whether a module knows a structure the pack names.
     *
     * @param service an interface marked {@link com.volmit.service.Service}
     * @throws IllegalArgumentException when {@code service} is not such an interface
     */
    <S> List<S> services(Class<S> service);

    /** Records {@code problem}. An {@link Severity#ERROR} stops the pack from loading once every validator has run. */
    void report(Problem problem);
}

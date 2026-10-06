package com.volmit.object;

import com.volmit.generation.InvocationContext;
import com.volmit.random.WorldRandom;

/** Geometry-only invocation. The host chooses the variant seed and enforces size limits.
 * Placement and terrain adaptation happen later; generators return reusable local geometry.
 */
public interface ObjectGenerationContext extends InvocationContext {
    /** This generation's stream, seeded from the variant seed the host chose. Do not retain it. */
    WorldRandom random();
}

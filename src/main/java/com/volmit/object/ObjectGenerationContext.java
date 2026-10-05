package com.volmit.object;

import com.volmit.generation.InvocationContext;

/** Geometry-only invocation. The host chooses the variant seed and enforces size limits.
 * Placement and terrain adaptation happen later; generators return reusable local geometry.
 */
public interface ObjectGenerationContext extends InvocationContext {}

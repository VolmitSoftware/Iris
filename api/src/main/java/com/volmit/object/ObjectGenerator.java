package com.volmit.object;

/** Configured geometry generator. Snapshot settings; concurrent calls use only invocation-local
 * state. Return a non-null object. The host owns variant selection and snapshot-scoped caching.
 */
@FunctionalInterface
public interface ObjectGenerator {
    GeneratedObject generate(ObjectGenerationContext context);
}

package com.volmit.object;

/** Host adapter returning immutable transformed geometry with updated bounds, block properties,
 * block entities and markers. Cache keys include pack snapshot, source geometry and transform.
 */
@FunctionalInterface
public interface ObjectTransformer {
    GeneratedObject transform(GeneratedObject object, ObjectTransform transform);
}

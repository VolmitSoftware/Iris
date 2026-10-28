package com.volmit.object;

import com.volmit.geometry.BlockBox;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Optional;

/**
 * The pack's objects, ready to place, and the objects made from them: scaled variants and objects a module builds block
 * by block. The host loads, scales and keeps them for the pack snapshot, so asking again is cheap. Reached through
 * {@link com.volmit.resource.PackResources#objects()}; safe for concurrent calls.
 */
public interface PackObjects {
    /** The pack object {@code id} names, as {@link com.volmit.resource.PackResources#idOf} gives it for the key; empty when the pack has none. */
    Optional<PlaceableObject> object(ResourceId id);

    /** The pack object's size, read without loading its blocks: from the origin to its width, height and depth; empty when the pack has none. */
    Optional<BlockBox> sizeOf(ResourceId id);

    /**
     * {@code object} resized by {@code factor}, filling between its blocks with {@code method}. Built once per object,
     * factor and method, and kept within a memory budget.
     */
    PlaceableObject scaled(PlaceableObject object, double factor, ScaleMethod method);

    /**
     * {@code object} with air in the rooms and pockets a placement should clear, found by casting rays through it. Done
     * once per object; the object itself keeps the air, so later placements of it see it too.
     */
    PlaceableObject smartBored(PlaceableObject object);

    /** An object of {@code blocks}, at their offsets from its center, declared {@code width} by {@code height} by {@code depth} with its center in the middle; placeable like the pack's. */
    PlaceableObject build(String name, int width, int height, int depth, List<ObjectBlock> blocks);
}

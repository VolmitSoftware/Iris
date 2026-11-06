/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modules.placement;

import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PlaceableObject;
import com.volmit.world.PackWorld;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The columns of each object's lowest solid layer, worked out once per object of a world and kept while the object is: an
 * object stands on these when its placement needs surface support. Foliage does not count as solid.
 */
final class SurfaceSupportOffsets {
    private final ReferenceQueue<PlaceableObject> collected = new ReferenceQueue<>();
    private final Map<ObjectKey, List<IrisBlockVector>> offsets = new ConcurrentHashMap<>();

    static SurfaceSupportOffsets of(PackWorld world) {
        return world.state(SurfaceSupportOffsets.class, ignored -> new SurfaceSupportOffsets());
    }

    /** {@code blocks} tells which of the object's blocks are solid. */
    List<IrisBlockVector> of(PlaceableObject object, BlockRegistry blocks) {
        forgetCollected();
        return offsets.computeIfAbsent(new ObjectKey(object, collected), ignored -> lowestSolidLayer(object, blocks));
    }

    private static List<IrisBlockVector> lowestSolidLayer(PlaceableObject object, BlockRegistry blocks) {
        Map<BlockState, Boolean> supporting = new HashMap<>();
        int lowestY = Integer.MAX_VALUE;
        List<IrisBlockVector> layer = new ArrayList<>();
        for (ObjectBlock block : object.geometry().blocks()) {
            if (!supporting.computeIfAbsent(block.state(), state -> supports(state, blocks))) {
                continue;
            }
            BlockPosition position = block.position();
            if (position.y() < lowestY) {
                lowestY = position.y();
                layer.clear();
            }
            if (position.y() == lowestY) {
                layer.add(new IrisBlockVector(position.x(), position.y(), position.z()));
            }
        }
        return List.copyOf(layer);
    }

    private static boolean supports(BlockState state, BlockRegistry blocks) {
        return blocks.hasTrait(state, BlockTrait.SOLID) && !blocks.hasTrait(state, BlockTrait.FOLIAGE);
    }

    private void forgetCollected() {
        Object gone;
        while ((gone = collected.poll()) != null) {
            offsets.remove((ObjectKey) gone);
        }
    }

    /** An object by identity, held weakly so its entry goes once the object is collected. */
    private static final class ObjectKey extends WeakReference<PlaceableObject> {
        private final int hash;

        private ObjectKey(PlaceableObject object, ReferenceQueue<PlaceableObject> queue) {
            super(object, queue);
            this.hash = System.identityHashCode(object);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ObjectKey key)) {
                return false;
            }
            PlaceableObject object = get();
            return object != null && object == key.get();
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}

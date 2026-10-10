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

package art.arcane.iris.core.modules;

import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.mantle.BlockBox;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.object.ObjectMarker;
import com.volmit.object.ObjectTransform;
import com.volmit.object.ObjectTransformer;
import com.volmit.world.BlockPosition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforms geometry with the rotation Iris places objects with ({@link IrisObjectRotation}, its axis order
 * included) about the anchor, turning block states with the object so stairs and logs face the way it does.
 * Mirrors come first, as {@link ObjectTransform} says: an X or Z mirror flips positions about the anchor and
 * mirrors block states through the platform's own mirror, so stairs and doors face the mirrored way. A Y mirror
 * flips positions the way Iris hangs an object from a ceiling, without changing block states, since blocks have
 * no mirror along Y. Iris has no way to scale geometry, so that request fails with
 * {@link UnsupportedOperationException} rather than changing the request. Blocks that a turn by an angle that is
 * not a quarter turn sends to one position keep the last one, as Iris overwrites them when it places.
 */
final class IrisObjectTransformer implements ObjectTransformer {
    private static final double NO_SCALE = 1D;
    private static final int NO_SPIN = 0;

    @Override
    public GeneratedObject transform(GeneratedObject object, ObjectTransform transform) {
        requireSupported(transform);
        if (transform.equals(ObjectTransform.identity())) {
            return object;
        }
        Turn turn = new Turn(object.anchor(), transform);
        Map<BlockPosition, ObjectBlock> blocks = new LinkedHashMap<>();
        for (ObjectBlock block : object.blocks()) {
            BlockPosition moved = turn.position(block.position());
            blocks.put(moved, new ObjectBlock(moved, turn.state(block), block.blockEntity()));
        }
        List<ObjectMarker> markers = new ArrayList<>();
        for (ObjectMarker marker : object.markers()) {
            markers.add(new ObjectMarker(turn.position(marker.position()), marker.type(), marker.attributes()));
        }
        return new GeneratedObject(boundsOf(blocks.keySet(), markers, object.bounds()), object.anchor(), List.copyOf(blocks.values()), markers);
    }

    private static void requireSupported(ObjectTransform transform) {
        if (transform.scaleX() != NO_SCALE || transform.scaleY() != NO_SCALE || transform.scaleZ() != NO_SCALE) {
            throw new UnsupportedOperationException("Iris cannot scale an object's geometry: " + transform);
        }
    }

    private static BlockBox boundsOf(Iterable<BlockPosition> blocks, List<ObjectMarker> markers, BlockBox whenEmpty) {
        List<BlockPosition> positions = new ArrayList<>();
        blocks.forEach(positions::add);
        markers.forEach(marker -> positions.add(marker.position()));
        if (positions.isEmpty()) {
            return whenEmpty;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPosition position : positions) {
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            minZ = Math.min(minZ, position.z());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
            maxZ = Math.max(maxZ, position.z());
        }
        return new BlockBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /** One requested turn about an anchor. */
    private static final class Turn {
        private final BlockPosition anchor;
        private final boolean mirrorX;
        private final boolean mirrorY;
        private final boolean mirrorZ;
        private final IrisObjectRotation rotation;
        private final PlatformBlockStates states = new PlatformBlockStates(key -> IrisPlatforms.get().registries().block(key));

        Turn(BlockPosition anchor, ObjectTransform transform) {
            this.anchor = anchor;
            this.mirrorX = transform.mirrorX();
            this.mirrorY = transform.mirrorY();
            this.mirrorZ = transform.mirrorZ();
            this.rotation = IrisObjectRotation.of(transform.rotationX(), transform.rotationY(), transform.rotationZ());
        }

        BlockPosition position(BlockPosition local) {
            IrisBlockVector offset = new IrisBlockVector(local.x() - anchor.x(), local.y() - anchor.y(), local.z() - anchor.z());
            if (mirrorX) {
                offset.setX(-offset.getX());
            }
            if (mirrorY) {
                offset.setY(-offset.getY());
            }
            if (mirrorZ) {
                offset.setZ(-offset.getZ());
            }
            IrisBlockVector turned = rotation.rotate(offset, NO_SPIN, NO_SPIN, NO_SPIN);
            return new BlockPosition(anchor.x() + (int) Math.round(turned.getX()), anchor.y() + (int) Math.round(turned.getY()),
                    anchor.z() + (int) Math.round(turned.getZ()));
        }

        com.volmit.block.BlockState state(ObjectBlock block) {
            return states.toApi(rotation.rotate(mirrored(states.toPlatform(block.state())), NO_SPIN, NO_SPIN, NO_SPIN));
        }

        private PlatformBlockState mirrored(PlatformBlockState state) {
            PlatformBlockState result = mirrorX ? state.mirrorX() : state;
            return mirrorZ ? result.mirrorZ() : result;
        }
    }
}

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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.matter.MatterStructurePOI;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementRecord;
import com.volmit.placement.PlacementTerrain;
import com.volmit.placement.StructureVolumes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An Iris object placer as a placement's terrain: the mantle a module stage writes, or the world a command or a structure
 * pastes into. States convert through one {@link PlatformBlockStates}, so a block read and written back keeps its
 * platform identity. Markers are kept in Iris's mantle form: a pack marker as a {@link MatterMarker}, tree material as a
 * {@link TreeBlockMaterial} of the block written there, buried treasure as a structure point of interest. Meant for one
 * invocation; not thread-safe.
 */
final class PlacerTerrain implements PlacementTerrain {
    private static final StructureVolumes NO_VOLUMES = new StructureVolumes() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean intersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return false;
        }

        @Override
        public boolean containsWithin(int x, int y, int z, int margin) {
            return false;
        }
    };

    private final IObjectPlacer placer;
    private final IrisData data;
    private final PlatformBlockStates states = new PlatformBlockStates(PlacerTerrain::platformState);
    private final Map<BlockPosition, PlatformBlockState> written;

    private PlacerTerrain(IObjectPlacer placer, IrisData data, Map<BlockPosition, PlatformBlockState> written) {
        this.placer = placer;
        this.data = data;
        this.written = written;
    }

    /** {@code data} is the pack the placer reads heights with. */
    static PlacerTerrain of(IObjectPlacer placer, IrisData data) {
        return new PlacerTerrain(placer, data, null);
    }

    /** As {@link #of}, remembering the platform state of each block written ({@link #writtenState}). */
    static PlacerTerrain rememberingWrites(IObjectPlacer placer, IrisData data) {
        return new PlacerTerrain(placer, data, new HashMap<>());
    }

    /** The platform state last written at the block; null when none was, or when this terrain does not remember writes. */
    PlatformBlockState writtenState(BlockPosition position) {
        return written == null ? null : written.get(position);
    }

    @Override
    public int highest(int x, int z, boolean ignoreFluid) {
        return placer.getHighest(x, z, data, ignoreFluid);
    }

    @Override
    public BlockState get(int x, int y, int z) {
        return states.toApi(placer.get(x, y, z));
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        write(x, y, z, states.toPlatform(state));
    }

    @Override
    public void set(int x, int y, int z, BlockState state, BlockEntityData blockEntity) {
        write(x, y, z, states.toPlatform(state));
        placer.setTile(x, y, z, BlockEntities.tileOf(blockEntity));
    }

    private void write(int x, int y, int z, PlatformBlockState state) {
        placer.set(x, y, z, state);
        if (written != null) {
            written.put(new BlockPosition(x, y, z), state);
        }
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return placer.isCarved(x, y, z);
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return placer.isSurfaceSolid(x, y, z);
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return placer.isUnderwater(x, z);
    }

    @Override
    public int fluidHeight() {
        return placer.getFluidHeight();
    }

    @Override
    public boolean preventsDecay() {
        return placer.isPreventingDecay();
    }

    @Override
    public void recordPlacement(int x, int y, int z, PlacementRecord placement) {
        placer.setData(x, y, z, PlacementMarkers.encode(placement, data.getPackResources()));
    }

    /**
     * The object Iris finds placed at the block, as its object lookup finds it ({@link Engine#getObjectPlacement}): a
     * structure piece, or an object a placement of the column's region or biome places. Other records, such as a
     * procedural object's, read as no placement, so placements collide with what they collided with before.
     */
    @Override
    public Optional<PlacementRecord> placementAt(int x, int y, int z) {
        Engine engine = data.getEngine();
        if (engine == null) {
            throw new IllegalStateException("Object placement requires active Iris data for collision settings.");
        }
        PlacedObject placed = engine.getObjectPlacement(x, y, z);
        if (placed == null || placed.getObject() == null) {
            return Optional.empty();
        }
        return Optional.of(PlacementRecord.of(data.getPackResources().idOf(placed.getObject().getLoadKey()), placed.getId()));
    }

    @Override
    public StructureVolumes structureVolumes(int minX, int minZ, int maxX, int maxZ) {
        Engine engine = placer.getEngine();
        if (engine == null) {
            throw new IllegalStateException("This placement runs without an Iris engine, so it has no world");
        }
        KList<NativeStructureVolume> volumes = engine.getNativeStructureVolumes(minX, minZ, maxX, maxZ);
        if (volumes == null || volumes.isEmpty()) {
            return NO_VOLUMES;
        }
        return new NativeVolumes(volumes, engine.getMinHeight());
    }

    @Override
    public void mark(PlacedMarker marker) {
        BlockPosition position = marker.position();
        int x = position.x();
        int y = position.y();
        int z = position.z();
        if (marker.type().equals(MantleMarkerTypes.TREE_MATERIAL)) {
            placer.setData(x, y, z, TreeBlockMaterial.of(placer.get(x, y, z)));
            return;
        }
        if (marker.type().equals(MantleMarkerTypes.BURIED_TREASURE)) {
            placer.setData(x, y, z, MatterStructurePOI.BURIED_TREASURE);
            return;
        }
        placer.setData(x, y, z, new MatterMarker(MantleMarkerTypes.packMarkerName(marker)));
    }

    /** Resolved by the platform bound now, so a rebound platform never answers with another's states. */
    private static PlatformBlockState platformState(String key) {
        return IrisPlatforms.get().registries().block(key);
    }

    /** The engine's volumes, which are in world heights, asked in heights above the dimension's bottom at {@code bottomY}. */
    private record NativeVolumes(KList<NativeStructureVolume> volumes, int bottomY) implements StructureVolumes {
        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean intersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            int worldMinY = worldY(minY);
            int worldMaxY = worldY(maxY);
            return volumes.stream().anyMatch(volume -> volume.intersects(minX, worldMinY, minZ, maxX, worldMaxY, maxZ));
        }

        @Override
        public boolean containsWithin(int x, int y, int z, int margin) {
            int worldY = worldY(y);
            for (NativeStructureVolume volume : volumes) {
                if (volume.containsWithin(x, worldY, z, margin)) {
                    return true;
                }
            }
            return false;
        }

        /** An unbounded height stays unbounded. */
        private int worldY(int height) {
            if (height == Integer.MIN_VALUE || height == Integer.MAX_VALUE) {
                return height;
            }
            return height + bottomY;
        }
    }
}

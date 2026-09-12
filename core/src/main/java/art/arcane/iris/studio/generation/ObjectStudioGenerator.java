/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.generation;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.object.ObjectStudioActivation;
import art.arcane.iris.studio.object.ObjectStudioLayout;
import art.arcane.iris.studio.object.ObjectStudioLayout.GridCell;
import art.arcane.iris.studio.object.ObjectStudioSaveService;
import art.arcane.iris.generation.chunk.TerrainChunk;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.WrongEngineBroException;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.platform.bukkit.BukkitBiome;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.VectorMap;
import art.arcane.volmlib.util.math.Vector3i;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.context.IrisContext;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ObjectStudioGenerator extends EnginedStudioGenerator {
    public static final int DEFAULT_PADDING = 2;
    private static final PlatformBlockState FLOOR = BukkitBlockState.of(Material.POLISHED_DEEPSLATE.createBlockData());
    private static final PlatformBlockState FRAME = BukkitBlockState.of(Material.SMOOTH_QUARTZ.createBlockData());
    private static final PlatformBlockState MARKER = BukkitBlockState.of(Material.END_ROD.createBlockData());
    private static final PlatformBiome DEFAULT_BIOME = BukkitBiome.of(Biome.PLAINS);

    private final int padding;
    private final PlatformBlockState floor;
    private final PlatformBlockState frame;
    private final PlatformBlockState marker;
    private final AtomicBoolean layoutBuilt = new AtomicBoolean(false);
    private final Object layoutLock = new Object();
    private final Map<String, DisplayObject> objectCache = new ConcurrentHashMap<>();
    private final Map<String, IrisData> packData = new ConcurrentHashMap<>();
    private volatile ObjectStudioLayout layout;

    public ObjectStudioGenerator(Engine engine) {
        this(engine, DEFAULT_PADDING, FLOOR, FRAME, MARKER);
    }

    public ObjectStudioGenerator(Engine engine, int padding, PlatformBlockState floor, PlatformBlockState frame, PlatformBlockState marker) {
        super(engine);
        this.padding = padding;
        this.floor = floor;
        this.frame = frame;
        this.marker = marker;
    }

    public ObjectStudioLayout getLayout() {
        return layout;
    }

    public int getPadding() {
        return padding;
    }

    @Override
    public void generateChunk(Engine engine, TerrainChunk tc, int x, int z) throws WrongEngineBroException {
        try (GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_object_studio_stage");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            generateChunkWithinSession(engine, tc, x, z);
        }
    }

    private void generateChunkWithinSession(Engine engine, TerrainChunk tc, int x, int z) {
        int floorY = Math.max(engine.getMinHeight(), ObjectStudioLayout.FLOOR_Y);
        if (floorY >= tc.getMaxHeight()) {
            return;
        }
        ensureLayout(engine);
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                tc.setBiome(bx, floorY, bz, DEFAULT_BIOME);
                tc.setBlock(bx, floorY, bz, floor);
            }
        }

        ObjectStudioLayout currentLayout = layout;
        if (currentLayout == null) {
            return;
        }

        int chunkWorldX = x << 4;
        int chunkWorldZ = z << 4;
        int chunkMaxX = chunkWorldX + 15;
        int chunkMaxZ = chunkWorldZ + 15;
        int minHeight = engine.getMinHeight();
        int maxHeight = engine.getMaxHeight();

        int plinthY = floorY + 1;
        List<PlacedTile> tiles = new ArrayList<>();

        for (GridCell cell : currentLayout.cells()) {
            int frameMinX = cell.originX() - 1;
            int frameMaxX = cell.originX() + cell.w();
            int frameMinZ = cell.originZ() - 1;
            int frameMaxZ = cell.originZ() + cell.d();

            if (frameMaxX < chunkWorldX || frameMinX > chunkMaxX) continue;
            if (frameMaxZ < chunkWorldZ || frameMinZ > chunkMaxZ) continue;

            paintFrame(cell, tc, plinthY, chunkWorldX, chunkWorldZ, minHeight, maxHeight);

            DisplayObject object = loadObject(cell);
            if (object != null) {
                placeSlice(object, cell, tc, chunkWorldX, chunkWorldZ, minHeight, maxHeight, tiles);
            }
        }
        if (!tiles.isEmpty()) {
            ObjectStudioSaveService.get().queueTiles(engine, new ChunkTiles(x, z, List.copyOf(tiles)));
        }
    }

    private void paintFrame(GridCell cell, TerrainChunk tc, int plinthY, int chunkWorldX, int chunkWorldZ, int minHeight, int maxHeight) {
        int x0 = cell.originX() - 1;
        int x1 = cell.originX() + cell.w();
        int z0 = cell.originZ() - 1;
        int z1 = cell.originZ() + cell.d();
        int topY = Math.min(maxHeight - 1, plinthY + cell.h() + 1);

        if (plinthY >= minHeight && plinthY < maxHeight) {
            paintFrameRowX(tc, x0, x1, plinthY, z0, chunkWorldX, chunkWorldZ);
            paintFrameRowX(tc, x0, x1, plinthY, z1, chunkWorldX, chunkWorldZ);
            paintFrameRowZ(tc, x0, plinthY, z0, z1, chunkWorldX, chunkWorldZ);
            paintFrameRowZ(tc, x1, plinthY, z0, z1, chunkWorldX, chunkWorldZ);
        }

        if (topY > plinthY && topY < maxHeight) {
            paintFrameRowX(tc, x0, x1, topY, z0, chunkWorldX, chunkWorldZ);
            paintFrameRowX(tc, x0, x1, topY, z1, chunkWorldX, chunkWorldZ);
            paintFrameRowZ(tc, x0, topY, z0, z1, chunkWorldX, chunkWorldZ);
            paintFrameRowZ(tc, x1, topY, z0, z1, chunkWorldX, chunkWorldZ);
        }

        int edgeLo = plinthY + 1;
        int edgeHi = Math.min(topY - 1, maxHeight - 1);
        if (edgeHi >= edgeLo) {
            paintEdgePillar(tc, x0, edgeLo, edgeHi, z0, chunkWorldX, chunkWorldZ);
            paintEdgePillar(tc, x1, edgeLo, edgeHi, z0, chunkWorldX, chunkWorldZ);
            paintEdgePillar(tc, x0, edgeLo, edgeHi, z1, chunkWorldX, chunkWorldZ);
            paintEdgePillar(tc, x1, edgeLo, edgeHi, z1, chunkWorldX, chunkWorldZ);
        }
    }

    private void paintEdgePillar(TerrainChunk tc, int x, int yMin, int yMax, int z, int chunkWorldX, int chunkWorldZ) {
        if (x < chunkWorldX || x > chunkWorldX + 15) return;
        if (z < chunkWorldZ || z > chunkWorldZ + 15) return;
        int localX = x - chunkWorldX;
        int localZ = z - chunkWorldZ;
        for (int y = yMin; y <= yMax; y++) {
            tc.setBlock(localX, y, localZ, marker);
        }
    }

    private void paintFrameRowX(TerrainChunk tc, int xMin, int xMax, int y, int z, int chunkWorldX, int chunkWorldZ) {
        if (z < chunkWorldZ || z > chunkWorldZ + 15) return;
        int lo = Math.max(xMin, chunkWorldX);
        int hi = Math.min(xMax, chunkWorldX + 15);
        for (int x = lo; x <= hi; x++) {
            tc.setBlock(x - chunkWorldX, y, z - chunkWorldZ, frame);
        }
    }

    private void paintFrameRowZ(TerrainChunk tc, int x, int y, int zMin, int zMax, int chunkWorldX, int chunkWorldZ) {
        if (x < chunkWorldX || x > chunkWorldX + 15) return;
        int lo = Math.max(zMin, chunkWorldZ);
        int hi = Math.min(zMax, chunkWorldZ + 15);
        for (int z = lo; z <= hi; z++) {
            tc.setBlock(x - chunkWorldX, y, z - chunkWorldZ, frame);
        }
    }

    private void placeSlice(DisplayObject object, GridCell cell, TerrainChunk tc, int chunkWorldX, int chunkWorldZ, int minHeight, int maxHeight, List<PlacedTile> tiles) {
        VectorMap<PlatformBlockState> blocks = object.source().getBlocks();
        if (blocks == null || blocks.isEmpty()) return;

        int originX = cell.originX();
        int originY = cell.originY();
        int originZ = cell.originZ();

        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : blocks) {
            IrisBlockVector signed = entry.getKey();
            int worldX = originX + signed.getBlockX() - object.minimumX();
            int worldY = originY + signed.getBlockY() - object.minimumY();
            int worldZ = originZ + signed.getBlockZ() - object.minimumZ();

            if (worldX < chunkWorldX || worldX > chunkWorldX + 15) continue;
            if (worldZ < chunkWorldZ || worldZ > chunkWorldZ + 15) continue;
            if (worldY < minHeight || worldY >= maxHeight) continue;

            PlatformBlockState data = entry.getValue();
            if (data == null) continue;

            tc.setBlock(worldX - chunkWorldX, worldY, worldZ - chunkWorldZ, data);
            TileData tile = object.source().getStates().get(signed);
            if (tile != null) {
                tiles.add(new PlacedTile(worldX - chunkWorldX, worldY, worldZ - chunkWorldZ, tile.clone()));
            }
        }
    }

    public IrisObject createCapture(GridCell cell) {
        DisplayObject displayed = loadObject(cell);
        if (displayed == null) {
            throw new IllegalStateException("Object Studio source is unavailable: " + cell.pack() + "/" + cell.key());
        }
        IrisObject capture = new IrisObject(cell.w(), cell.h(), cell.d());
        capture.setCenter(new Vector3i(-displayed.minimumX(), -displayed.minimumY(), -displayed.minimumZ()));
        return capture;
    }

    private DisplayObject loadObject(GridCell cell) {
        String cacheKey = cell.pack() + "/" + cell.key();
        DisplayObject cached = objectCache.get(cacheKey);
        if (cached != null) return cached;
        IrisData data = packData.get(cell.pack());
        if (data == null) return null;
        IrisObject loaded = data.getObjectLoader().load(cell.key());
        if (loaded != null) {
            DisplayObject displayed = DisplayObject.of(loaded);
            DisplayObject existing = objectCache.putIfAbsent(cacheKey, displayed);
            return existing == null ? displayed : existing;
        }
        return null;
    }

    public Map<String, IrisData> getPackData() {
        return packData;
    }

    private void ensureLayout(Engine engine) {
        if (layoutBuilt.get()) return;
        synchronized (layoutLock) {
            if (layoutBuilt.get()) return;

            Map<String, IrisData> sources = resolveSources(engine);
            packData.putAll(sources);

            File layoutFile = layoutFile(engine);
            ObjectStudioLayout resumed = ObjectStudioLayout.load(layoutFile, sources, padding);
            if (resumed != null) {
                layout = resumed;
            } else {
                layout = ObjectStudioLayout.build(sources, padding);
            }
            layout = layout.atFloor(Math.max(engine.getMinHeight(), ObjectStudioLayout.FLOOR_Y));
            if (layout != resumed) {
                layout.save(layoutFile);
            }
            layoutBuilt.set(true);

            try {
                ObjectStudioSaveService.get().register(engine, this);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }

            int cellCount = layout.cells().size();
            IrisBlockVector worldExtent = computeExtent(layout);
            IrisLogging.debug("Object Studio layout built: %d cells from %d pack(s), extent %d x %d blocks",
                    cellCount, sources.size(), worldExtent.getBlockX(), worldExtent.getBlockZ());
        }
    }

    private Map<String, IrisData> resolveSources(Engine engine) {
        String packKey = engine.getDimension() == null ? null : engine.getDimension().getLoadKey();
        Map<String, IrisData> registered = packKey == null ? null : ObjectStudioActivation.getSources(packKey);
        if (registered != null && !registered.isEmpty()) {
            return registered;
        }
        Map<String, IrisData> fallback = new LinkedHashMap<>();
        IrisData data = engine.getData();
        fallback.put(data.getDataFolder().getName(), data);
        return fallback;
    }

    private File layoutFile(Engine engine) {
        File worldFolder = engine.getTarget().getWorld().worldFolder();
        return new File(new File(worldFolder, ".iris"), "object-studio-layout.json");
    }

    private static IrisBlockVector computeExtent(ObjectStudioLayout layout) {
        int maxX = 0;
        int maxZ = 0;
        for (GridCell cell : layout.cells()) {
            maxX = Math.max(maxX, cell.originX() + cell.w());
            maxZ = Math.max(maxZ, cell.originZ() + cell.d());
        }
        return new IrisBlockVector(maxX, 0, maxZ);
    }

    public record PlacedTile(int x, int y, int z, TileData data) {
    }

    public record ChunkTiles(int chunkX, int chunkZ, List<PlacedTile> tiles) {
    }

    private record DisplayObject(IrisObject source, int minimumX, int minimumY, int minimumZ) {
        private static DisplayObject of(IrisObject object) {
            int minimumX = Integer.MAX_VALUE;
            int minimumY = Integer.MAX_VALUE;
            int minimumZ = Integer.MAX_VALUE;
            for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : object.getBlocks()) {
                if (entry.getValue() == null) {
                    continue;
                }
                IrisBlockVector position = entry.getKey();
                minimumX = Math.min(minimumX, position.getBlockX());
                minimumY = Math.min(minimumY, position.getBlockY());
                minimumZ = Math.min(minimumZ, position.getBlockZ());
            }
            if (minimumX == Integer.MAX_VALUE) {
                Vector3i center = object.getCenter();
                return new DisplayObject(object, -center.getBlockX(), -center.getBlockY(), -center.getBlockZ());
            }
            return new DisplayObject(object, minimumX, minimumY, minimumZ);
        }
    }
}

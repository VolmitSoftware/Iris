package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.nativegen.WorldgenTerrainHeightmaps;
import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMapper;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.IglooPieces;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldgenTerrainHeightmapsTest {
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int LAND_TERRAIN_TOP = 70;
    private static final int LAND_CANOPY_TOP = 80;
    private static final int OCEAN_FLOOR_TOP = 60;
    private static final int OCEAN_FLUID_TOP = 64;
    private static final int LAND_X = 3;
    private static final int LAND_Z = 5;
    private static final int OCEAN_X = 8;
    private static final int OCEAN_Z = 8;
    private static final int TERRAIN_SLAB_DEPTH = 4;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void authoringHeightmapsReadPlacedObjectsAndFluidsWithoutTerrainResolvers() {
        ProtoChunk chunk = terrainChunk(new ChunkPos(0, 0));

        IrisChunkGenerator.primeAuthoringHeightmaps(chunk);

        assertEquals(LAND_CANOPY_TOP, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, LAND_X, LAND_Z));
        assertEquals(LAND_CANOPY_TOP, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, LAND_X, LAND_Z));
        assertEquals(LAND_CANOPY_TOP, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, LAND_X, LAND_Z));
        assertEquals(LAND_TERRAIN_TOP + 6,
                chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, LAND_X, LAND_Z));
        assertEquals(OCEAN_FLUID_TOP, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, OCEAN_X, OCEAN_Z));
        assertEquals(OCEAN_FLOOR_TOP, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, OCEAN_X, OCEAN_Z));
    }

    @Test
    public void primeTerrainWritesEngineSurfaceAndOceanFloorHeights() {
        ProtoChunk chunk = terrainChunk(new ChunkPos(0, 0));

        assertFalse(chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG));
        assertFalse(chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG));

        WorldgenTerrainHeightmaps.primeTerrain(chunk, surfaceFirstFreeY(), floorFirstFreeY());

        assertTrue(chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG));
        assertTrue(chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG));
        assertEquals(LAND_TERRAIN_TOP, chunk.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG, LAND_X, LAND_Z));
        assertEquals(LAND_TERRAIN_TOP, chunk.getHeight(
                Heightmap.Types.OCEAN_FLOOR_WG, LAND_X, LAND_Z));
        assertEquals(OCEAN_FLUID_TOP, chunk.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG, OCEAN_X, OCEAN_Z));
        assertEquals(OCEAN_FLOOR_TOP, chunk.getHeight(
                Heightmap.Types.OCEAN_FLOOR_WG, OCEAN_X, OCEAN_Z));
    }

    @Test
    public void finalHeightmapPrimingDoesNotClobberTheWorldgenHeightmaps() {
        ProtoChunk chunk = terrainChunk(new ChunkPos(0, 0));

        WorldgenTerrainHeightmaps.primeTerrain(chunk, surfaceFirstFreeY(), floorFirstFreeY());
        Heightmap.primeHeightmaps(chunk, ChunkStatus.FINAL_HEIGHTMAPS);

        assertEquals(LAND_TERRAIN_TOP, chunk.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG, LAND_X, LAND_Z));
        assertEquals(LAND_TERRAIN_TOP, chunk.getHeight(
                Heightmap.Types.OCEAN_FLOOR_WG, LAND_X, LAND_Z));
        assertEquals(LAND_CANOPY_TOP, chunk.getHeight(
                Heightmap.Types.WORLD_SURFACE, LAND_X, LAND_Z));
    }

    @Test
    public void unprimedWorldgenHeightmapResolvesTheIrisCanopyInstead() {
        ProtoChunk chunk = terrainChunk(new ChunkPos(0, 0));

        assertEquals(LAND_CANOPY_TOP, chunk.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG, LAND_X, LAND_Z));
    }

    @Test
    public void primeTerrainOverwritesAnAlreadyLazyPrimedCanopyHeight() {
        ProtoChunk chunk = terrainChunk(new ChunkPos(0, 0));

        assertEquals(LAND_CANOPY_TOP, chunk.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG, LAND_X, LAND_Z));
        WorldgenTerrainHeightmaps.primeTerrain(chunk, surfaceFirstFreeY(), floorFirstFreeY());

        assertEquals(LAND_TERRAIN_TOP, chunk.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG, LAND_X, LAND_Z));
    }

    @Test
    public void structurePlacementPrimesTheWestNeighbourAnIglooEntranceReads() {
        Map<Long, ChunkAccess> chunks = new HashMap<>();
        chunks.put(ChunkPos.pack(0, 0), terrainChunk(new ChunkPos(0, 0)));
        chunks.put(ChunkPos.pack(-1, 0), terrainChunk(new ChunkPos(-1, 0)));
        WorldGenLevel world = world(chunks);
        StructureStart start = startInOrigin();

        int canopySnap = IglooPieces.GENERATION_HEIGHT + iglooSnapOffset(world);
        WorldgenTerrainHeightmaps.primeStructurePlacement(
                world, new ChunkPos(0, 0), List.of(start), surfaceFirstFreeY(), floorFirstFreeY());
        int terrainSnap = IglooPieces.GENERATION_HEIGHT + iglooSnapOffset(world);

        assertEquals(LAND_TERRAIN_TOP + 1, world.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG, -2, 5));
        assertEquals(LAND_TERRAIN_TOP, terrainSnap);
        assertEquals(LAND_CANOPY_TOP, canopySnap);
        assertEquals(LAND_CANOPY_TOP - LAND_TERRAIN_TOP, canopySnap - terrainSnap);
    }

    @Test
    public void structurePlacementPrimesEveryChunkTheStartFootprintTouches() {
        Map<Long, ChunkAccess> chunks = new HashMap<>();
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                chunks.put(ChunkPos.pack(chunkX, chunkZ), terrainChunk(new ChunkPos(chunkX, chunkZ)));
            }
        }
        WorldGenLevel world = world(chunks);

        WorldgenTerrainHeightmaps.primeStructurePlacement(
                world, new ChunkPos(0, 0), List.of(startInOrigin()),
                surfaceFirstFreeY(), floorFirstFreeY());

        for (ChunkAccess chunk : chunks.values()) {
            assertTrue(chunk.getPos().toString(),
                    chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG));
            assertTrue(chunk.getPos().toString(),
                    chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG));
            assertEquals(LAND_TERRAIN_TOP, chunk.getHeight(
                    Heightmap.Types.WORLD_SURFACE_WG, LAND_X, LAND_Z));
        }
    }

    @Test
    public void structurePlacementDoesNotPrimeALoadedDistanceTwoChunk() {
        Map<Long, ChunkAccess> chunks = new HashMap<>();
        ProtoChunk origin = terrainChunk(new ChunkPos(0, 0));
        ProtoChunk neighbour = terrainChunk(new ChunkPos(1, 0));
        ProtoChunk distanceTwo = terrainChunk(new ChunkPos(2, 0));
        chunks.put(ChunkPos.pack(0, 0), origin);
        chunks.put(ChunkPos.pack(1, 0), neighbour);
        chunks.put(ChunkPos.pack(2, 0), distanceTwo);
        WorldGenLevel world = world(chunks);
        StructureStart shifted = startInOrigin();
        shifted.getPieces().forEach(piece -> piece.move(16, 0, 0));

        WorldgenTerrainHeightmaps.primeStructurePlacement(
                world, new ChunkPos(0, 0), List.of(shifted),
                surfaceFirstFreeY(), floorFirstFreeY());

        assertTrue(origin.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG));
        assertTrue(neighbour.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG));
        assertFalse(distanceTwo.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG));
    }

    @Test
    public void structurePlacementSkipsChunksOutsideTheGenerationRegion() {
        Map<Long, ChunkAccess> chunks = new HashMap<>();
        ProtoChunk origin = terrainChunk(new ChunkPos(0, 0));
        chunks.put(ChunkPos.pack(0, 0), origin);
        WorldGenLevel world = world(chunks);

        WorldgenTerrainHeightmaps.primeStructurePlacement(
                world, new ChunkPos(0, 0), List.of(startInOrigin()),
                surfaceFirstFreeY(), floorFirstFreeY());

        assertTrue(origin.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG));
        assertEquals(LAND_TERRAIN_TOP, origin.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG, LAND_X, LAND_Z));
    }

    private static int iglooSnapOffset(WorldGenLevel world) {
        return world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, -2, 5)
                - IglooPieces.GENERATION_HEIGHT - 1;
    }

    private static StructureStart startInOrigin() {
        Structure structure = new SwampHutStructure(new Structure.StructureSettings(HolderSet.empty()));
        SwampHutPiece piece = new SwampHutPiece(RandomSource.create(13L), 0, 0);
        return new StructureStart(
                structure, new ChunkPos(0, 0), 0, new PiecesContainer(List.of(piece)));
    }

    private static IntBinaryOperator surfaceFirstFreeY() {
        return (x, z) -> isOcean(x, z) ? OCEAN_FLUID_TOP + 1 : LAND_TERRAIN_TOP + 1;
    }

    private static IntBinaryOperator floorFirstFreeY() {
        return (x, z) -> isOcean(x, z) ? OCEAN_FLOOR_TOP + 1 : LAND_TERRAIN_TOP + 1;
    }

    private static boolean isOcean(int x, int z) {
        return Math.floorMod(x, 16) == OCEAN_X && Math.floorMod(z, 16) == OCEAN_Z;
    }

    private static ProtoChunk terrainChunk(ChunkPos pos) {
        ProtoChunk chunk = new ProtoChunk(
                pos, UpgradeData.EMPTY, LevelHeightAccessor.create(MIN_Y, HEIGHT),
                containerFactory(), null);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                boolean ocean = isOcean(pos.getMinBlockX() + x, pos.getMinBlockZ() + z);
                int stoneTop = ocean ? OCEAN_FLOOR_TOP : LAND_TERRAIN_TOP;
                for (int y = stoneTop - TERRAIN_SLAB_DEPTH; y <= stoneTop; y++) {
                    write(chunk, x, y, z, Blocks.STONE.defaultBlockState());
                }
                if (ocean) {
                    for (int y = OCEAN_FLOOR_TOP + 1; y <= OCEAN_FLUID_TOP; y++) {
                        write(chunk, x, y, z, Blocks.WATER.defaultBlockState());
                    }
                    continue;
                }
                for (int y = LAND_TERRAIN_TOP + 1; y <= LAND_TERRAIN_TOP + 6; y++) {
                    write(chunk, x, y, z, Blocks.SPRUCE_LOG.defaultBlockState());
                }
                for (int y = LAND_TERRAIN_TOP + 7; y <= LAND_CANOPY_TOP; y++) {
                    write(chunk, x, y, z, Blocks.SPRUCE_LEAVES.defaultBlockState());
                }
            }
        }
        return chunk;
    }

    private static void write(ProtoChunk chunk, int x, int y, int z, BlockState state) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(x, y & 15, z, state, false);
    }

    private static PalettedContainerFactory containerFactory() {
        Strategy<BlockState> blockStates = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        BlockState air = Blocks.AIR.defaultBlockState();
        IdMapper<Holder<Biome>> biomeIds = new IdMapper<>();
        Holder<Biome> defaultBiome = Holder.direct((Biome) null);
        biomeIds.add(defaultBiome);
        Strategy<Holder<Biome>> biomes = Strategy.createForBiomes(biomeIds);
        Codec<Holder<Biome>> biomeCodec = Codec.STRING.xmap(
                name -> defaultBiome, holder -> "iris-test-biome");
        return new PalettedContainerFactory(
                blockStates,
                air,
                PalettedContainer.codecRW(BlockState.CODEC, blockStates, air),
                biomes,
                defaultBiome,
                PalettedContainer.codecRO(biomeCodec, biomes, defaultBiome),
                PalettedContainer.codecRW(biomeCodec, biomes, defaultBiome));
    }

    private static WorldGenLevel world(Map<Long, ChunkAccess> chunks) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            String methodName = method.getName();
            if (methodName.equals("hasChunk")) {
                return chunks.containsKey(ChunkPos.pack((int) arguments[0], (int) arguments[1]));
            }
            if (methodName.equals("getChunk") && arguments.length == 2) {
                ChunkAccess chunk = chunks.get(ChunkPos.pack((int) arguments[0], (int) arguments[1]));
                if (chunk == null) {
                    throw new AssertionError("Heightmap priming requested an unavailable chunk "
                            + arguments[0] + "," + arguments[1]);
                }
                return chunk;
            }
            if (methodName.equals("getHeight") && arguments.length == 3) {
                Heightmap.Types type = (Heightmap.Types) arguments[0];
                int x = (int) arguments[1];
                int z = (int) arguments[2];
                ChunkAccess chunk = chunks.get(ChunkPos.pack(x >> 4, z >> 4));
                if (chunk == null) {
                    throw new AssertionError("Heightmap read requested an unavailable chunk at "
                            + x + "," + z);
                }
                return chunk.getHeight(type, x & 15, z & 15) + 1;
            }
            if (methodName.equals("getBlockState")) {
                BlockPos position = (BlockPos) arguments[0];
                ChunkAccess chunk = chunks.get(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4));
                if (chunk == null) {
                    throw new AssertionError("Block read requested an unavailable chunk at " + position);
                }
                return chunk.getBlockState(position);
            }
            if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (methodName.equals("equals")) {
                return proxy == arguments[0];
            }
            if (methodName.equals("toString")) {
                return "worldgen-heightmap-test-world";
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
    }
}

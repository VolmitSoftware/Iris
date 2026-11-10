package art.arcane.iris.engine.modifier;

import art.arcane.iris.core.modules.CarveTestTerrain;
import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.core.modules.InMemoryMantle;
import art.arcane.iris.engine.mantle.components.CarveWorld;
import art.arcane.iris.engine.mantle.components.CaveCarving;
import art.arcane.iris.engine.mantle.components.CaveFloorMarking;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.modules.carve.CaveProfile;
import art.arcane.volmlib.util.matter.MatterCavern;
import com.volmit.block.BlockState;
import com.volmit.config.Range;
import com.volmit.geometry.BlockPosition;
import com.volmit.host.ArrayHunk;
import com.volmit.hunk.Hunk;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.MantleLayer;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementTerrain;
import com.volmit.world.BiomeSample;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Carves and applies a fixed block of chunks of a fixed seed, every chunk carved before any is applied so that no chunk's
 * neighbours depend on load order, and pins a digest of the planned voids and of the final blocks. With
 * {@code IRIS_CARVE_BENCH} set it also times both passes.
 */
public class CarvePipelineHarnessTest {
    private static final int HEIGHT = 128;
    private static final int FLUID_HEIGHT = 62;
    private static final int LAVA_HEIGHT = 10;
    private static final long SEED = 424242L;
    private static final int APPLIED_CHUNKS_PER_SIDE = 6;
    private static final int CARVE_RING = 1;
    private static final String VOIDS_DIGEST = "df7ae961c3025eb5";
    private static final String BLOCKS_DIGEST = "bdb40db5625eb942";

    private static final BlockState STONE = new BlockState("minecraft:stone");
    private static final BlockState AIR = new BlockState("minecraft:air");
    private static final BlockState WATER = new BlockState("minecraft:water");
    private static final BlockState WALL = new BlockState("minecraft:mossy_cobblestone");
    private static final BlockState FLOOR = new BlockState("minecraft:moss_block");
    private static final BlockState CEILING = new BlockState("minecraft:dripstone_block");

    private record Result(long voidsDigest, long blocksDigest, int carvedCells, long carveNanos, long markNanos, long applyNanos) {
    }

    @Test
    public void thePlannedVoidsAndTheFinalBlocksAreAsPinned() {
        Result result = run();

        System.out.println("CARVE-HARNESS voids=" + Long.toHexString(result.voidsDigest) + " blocks=" + Long.toHexString(result.blocksDigest)
                + " cells=" + result.carvedCells);
        assertEquals(VOIDS_DIGEST, Long.toHexString(result.voidsDigest));
        assertEquals(BLOCKS_DIGEST, Long.toHexString(result.blocksDigest));
    }

    @Test
    public void timing() {
        Assume.assumeTrue(System.getenv("IRIS_CARVE_BENCH") != null);
        int warmups = 6;
        int rounds = 20;
        long[] carve = new long[rounds];
        long[] mark = new long[rounds];
        long[] apply = new long[rounds];
        long[] total = new long[rounds];
        for (int round = 0; round < warmups + rounds; round++) {
            Result result = run();
            if (round >= warmups) {
                carve[round - warmups] = result.carveNanos;
                mark[round - warmups] = result.markNanos;
                apply[round - warmups] = result.applyNanos;
                total[round - warmups] = result.carveNanos + result.markNanos + result.applyNanos;
            }
        }
        Arrays.sort(carve);
        Arrays.sort(mark);
        Arrays.sort(apply);
        Arrays.sort(total);
        int chunks = (APPLIED_CHUNKS_PER_SIDE + 2 * CARVE_RING) * (APPLIED_CHUNKS_PER_SIDE + 2 * CARVE_RING);
        System.out.printf("CARVE-BENCH carve median %.1f ms (%d chunks, min %.1f), mark median %.1f (min %.1f), apply median %.1f ms (%d chunks, min %.1f), total median %.1f (min %.1f)%n",
                carve[rounds / 2] / 1e6, chunks, carve[0] / 1e6, mark[rounds / 2] / 1e6, mark[0] / 1e6,
                apply[rounds / 2] / 1e6, APPLIED_CHUNKS_PER_SIDE * APPLIED_CHUNKS_PER_SIDE, apply[0] / 1e6,
                total[rounds / 2] / 1e6, total[0] / 1e6);
    }

    private Result run() {
        CarveTestWorld world = new CarveTestWorld().biome("cave/lush").biome("plains").carveSeed(SEED).dimension(profile());
        CarveWorld carveWorld = new CarveWorld(world.resources().noise(), SEED, HEIGHT, FLUID_HEIGHT, LAVA_HEIGHT, CarveTestWorld.MIN_HEIGHT);
        RecordingCaveDressing dressing = new RecordingCaveDressing();
        dressing.fluid = WATER;
        dressing.walls.put(CarveTestWorld.id("cave/lush"), WALL);
        dressing.floors.put(CarveTestWorld.id("cave/lush"), List.of(FLOOR));
        dressing.ceilings.put(CarveTestWorld.id("cave/lush"), List.of(CEILING));
        InMemoryMantle mantle = new InMemoryMantle(HEIGHT / 16);
        MantleLayer<CarvedVoid> voids = mantle.carved();
        CaveCarving carving = new CaveCarving(world, carveWorld, false, (x, z) -> surface(x, z) < FLUID_HEIGHT);
        CaveFloorMarking floorMarking = new CaveFloorMarking(carveWorld, false);
        CaveApplying applying = new CaveApplying(world, carveWorld, false);
        List<String> floors = new ArrayList<>();
        PlacementTerrain placement = mock(PlacementTerrain.class);
        doAnswer(call -> {
            BlockPosition position = ((PlacedMarker) call.getArgument(0)).position();
            floors.add(((PlacedMarker.PackMarker) call.getArgument(0)).key() + " " + position.x() + " " + position.y() + " " + position.z());
            return null;
        }).when(placement).mark(any());

        long carveStart = System.nanoTime();
        for (int chunkX = -CARVE_RING; chunkX < APPLIED_CHUNKS_PER_SIDE + CARVE_RING; chunkX++) {
            for (int chunkZ = -CARVE_RING; chunkZ < APPLIED_CHUNKS_PER_SIDE + CARVE_RING; chunkZ++) {
                carving.carve(voids, terrain(chunkX, chunkZ), chunkX, chunkZ);
            }
        }
        long carveNanos = System.nanoTime() - carveStart;

        long markNanos = 0;
        for (int chunkX = 0; chunkX < APPLIED_CHUNKS_PER_SIDE; chunkX++) {
            for (int chunkZ = 0; chunkZ < APPLIED_CHUNKS_PER_SIDE; chunkZ++) {
                long markStart = System.nanoTime();
                floorMarking.mark(voids, terrain(chunkX, chunkZ), placement, chunkX, chunkZ);
                markNanos += System.nanoTime() - markStart;
            }
        }

        long voidsDigest = 0xcbf29ce484222325L;
        int cells = 0;
        for (int chunkX = -CARVE_RING; chunkX < APPLIED_CHUNKS_PER_SIDE + CARVE_RING; chunkX++) {
            for (int chunkZ = -CARVE_RING; chunkZ < APPLIED_CHUNKS_PER_SIDE + CARVE_RING; chunkZ++) {
                List<long[]> planned = new ArrayList<>();
                mantle.chunk(chunkX, chunkZ).iterate(MatterCavern.class, (x, y, z, cavern) ->
                        planned.add(new long[]{(((long) y << 8) | ((long) x << 4) | z), cavern.getLiquid() * 31L + cavern.getCustomBiome().hashCode()}));
                planned.sort((a, b) -> Long.compare(a[0], b[0]));
                voidsDigest = mix(voidsDigest, chunkX * 1000003L + chunkZ);
                for (long[] cell : planned) {
                    voidsDigest = mix(mix(voidsDigest, cell[0]), cell[1]);
                }
                cells += planned.size();
            }
        }

        long applyNanos = 0;
        long blocksDigest = 0xcbf29ce484222325L;
        Map<BlockState, Long> stateHashes = new IdentityHashMap<>();
        for (int chunkX = 0; chunkX < APPLIED_CHUNKS_PER_SIDE; chunkX++) {
            for (int chunkZ = 0; chunkZ < APPLIED_CHUNKS_PER_SIDE; chunkZ++) {
                Hunk<BlockState> blocks = terrainBlocks(chunkX, chunkZ);
                long applyStart = System.nanoTime();
                applying.apply(voids, terrain(chunkX, chunkZ), chunkX, chunkZ, blocks, dressing);
                applyNanos += System.nanoTime() - applyStart;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < HEIGHT; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = blocks.get(x, y, z);
                            long stateHash = stateHashes.computeIfAbsent(state, known -> (long) known.toString().hashCode());
                            blocksDigest = mix(blocksDigest, stateHash);
                        }
                    }
                }
            }
        }
        if (System.getenv("IRIS_CARVE_DUMP") != null) {
            dumpMarkers(floors, System.getenv("IRIS_CARVE_DUMP"));
        }
        return new Result(voidsDigest, blocksDigest, cells, carveNanos, markNanos, applyNanos);
    }

    private static void dumpMarkers(List<String> floors, String file) {
        List<String> lines = new ArrayList<>(floors);
        java.util.Collections.sort(lines);
        try {
            java.nio.file.Files.write(java.nio.file.Path.of(file), lines);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static long mix(long digest, long value) {
        long mixed = (digest ^ value) * 0x100000001b3L;
        return mixed ^ (mixed >>> 29);
    }

    private static int surface(int x, int z) {
        return (int) (72 + 14 * Math.sin(x * 0.045) * Math.cos(z * 0.06) + 7 * Math.sin(x * 0.19 + z * 0.11));
    }

    private static CarveTestTerrain terrain(int chunkX, int chunkZ) {
        return new CarveTestTerrain(chunkX << 4, chunkZ << 4, HEIGHT, FLUID_HEIGHT, CarvePipelineHarnessTest::surface)
                .biomes((x, y, z) -> new BiomeSample(CarveTestWorld.id("region"), CarveTestWorld.id("plains"), Optional.of(CarveTestWorld.id("cave/lush"))));
    }

    private static Hunk<BlockState> terrainBlocks(int chunkX, int chunkZ) {
        Hunk<BlockState> blocks = new ArrayHunk<>(BlockState.class, 16, HEIGHT, 16, STONE);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int surface = surface((chunkX << 4) + x, (chunkZ << 4) + z);
                for (int y = surface; y < HEIGHT; y++) {
                    blocks.set(x, y, z, y < FLUID_HEIGHT ? WATER : AIR);
                }
            }
        }
        return blocks;
    }

    private static CaveProfile profile() {
        return TestSettings.with(new CaveProfile(), "enabled", true, "verticalRange", Range.of(4D, HEIGHT - 8D));
    }
}

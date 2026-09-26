package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.concurrent.BurstExecutor;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.decoration.IrisSpeleothems;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.hunk.storage.ArrayHunk;
import org.bukkit.block.data.BlockData;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class IrisPerfectionModifierColumnTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void randomColumnsMatchOriginalGlobalPasses() {
        Fixture fixture = new Fixture();
        Random random = new Random(82714L);
        NativeBlockState[] palette = {null, fixture.air, fixture.water, fixture.stone,
                fixture.decorant, fixture.half, fixture.spike(true, false), fixture.spike(false, true)};
        for (int trial = 0; trial < 8; trial++) {
            Hunk<NativeBlockState> source = Hunk.newArrayHunk(16, 32, 16);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 32; y++) {
                        source.setRaw(x, y, z, y == 0 ? fixture.stone : palette[random.nextInt(palette.length)]);
                    }
                }
            }
            assertParity(fixture, source);
        }
    }

    @Test
    public void unrelatedTallColumnsAreScannedOnceDuringRepeatedCleanup() {
        Fixture fixture = new Fixture();
        CountingHunk output = new CountingHunk(768);
        for (int y = 0; y < 768; y++) {
            output.setRaw(1, y, 0, fixture.stone);
        }
        for (int y = 1; y <= 24; y++) {
            output.setRaw(0, y, 0, fixture.decorant);
        }
        fixture.modifier.onModify(0, 0, output, false, null);
        assertTrue("clean column must not be scanned for each removed decorant", output.cleanColumnReads < 780);
        for (int y = 1; y <= 24; y++) {
            assertTrue(output.getRaw(0, y, 0).isAir());
        }
    }

    @Test
    public void pairedHalvesMergedSpikesAndWaterloggedSupportsMatchOriginal() {
        Fixture fixture = new Fixture();
        Hunk<NativeBlockState> source = Hunk.newArrayHunk(16, 16, 16);
        for (int x = 0; x < 16; x++) {
            source.setRaw(x, 1, 0, x % 2 == 0 ? fixture.stone : fixture.water);
            source.setRaw(x, 8, 0, x % 3 == 0 ? fixture.stone : fixture.decorant);
            for (int y = 2; y <= 4; y++) {
                source.setRaw(x, y, 0, fixture.spike(true, x % 2 == 0));
            }
            for (int y = 5; y <= 7; y++) {
                source.setRaw(x, y, 0, fixture.spike(false, x % 2 != 0));
            }
            source.setRaw(x, 10, 0, fixture.half);
            source.setRaw(x, 11, 0, fixture.decorant);
        }
        assertParity(fixture, source);
    }

    private void assertParity(Fixture fixture, Hunk<NativeBlockState> source) {
        Hunk<NativeBlockState> expected = Hunk.newArrayHunk(16, source.getHeight(), 16);
        Hunk<NativeBlockState> actual = Hunk.newArrayHunk(16, source.getHeight(), 16);
        source.iterateSync((x, y, z, state) -> {
            expected.setRaw(x, y, z, state);
            actual.setRaw(x, y, z, state);
        });
        original(expected, fixture.air, fixture.water);
        fixture.modifier.onModify(0, 0, actual, false, null);
        expected.iterateSync((x, y, z, state) -> assertEquals("voxel " + x + "," + y + "," + z,
                state == null ? null : state.key(), actual.getRaw(x, y, z) == null ? null : actual.getRaw(x, y, z).key()));
    }

    private static void original(Hunk<NativeBlockState> output, NativeBlockState air, NativeBlockState water) {
        boolean changed;
        do {
            changed = false;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    List<Integer> surfaces = new ArrayList<>();
                    int top = 0;
                    for (int y = output.getHeight() - 1; y >= 0; y--) {
                        NativeBlockState block = output.get(x, y, z);
                        if (block != null && !B.isAir(block) && !B.isFluid(block)) {
                            top = y;
                            break;
                        }
                    }
                    boolean inside = true;
                    surfaces.add(top);
                    for (int y = top; y >= 0; y--) {
                        NativeBlockState block = output.get(x, y, z);
                        if (IrisSpeleothems.isSpike(block)) {
                            block = IrisPerfectionModifier.normalizeSpike(block, output, x, z, y, air, water);
                        }
                        boolean now = block != null && !(B.isAir(block) || B.isFluid(block));
                        if (now != inside) {
                            inside = now;
                            if (inside) {
                                surfaces.add(y);
                            }
                        }
                    }
                    for (int y : surfaces) {
                        NativeBlockState tip = output.get(x, y, z);
                        if (tip == null) {
                            continue;
                        }
                        boolean remove = false;
                        boolean removeBelow = false;
                        if (B.isDecorant(tip)) {
                            NativeBlockState below = output.get(x, y - 1, z);
                            if (below == null || !B.canPlaceOnto(tip, below)) {
                                remove = true;
                            } else if (IrisProceduralBlocks.hasProperty(below, "half")) {
                                NativeBlockState support = output.get(x, y - 2, z);
                                if (support == null || !B.canPlaceOnto(below, support)) {
                                    remove = true;
                                    removeBelow = true;
                                }
                            }
                            if (remove) {
                                changed = true;
                                output.set(x, y, z, air);
                                if (removeBelow) {
                                    output.set(x, y - 1, z, air);
                                }
                            }
                        }
                    }
                }
            }
        } while (changed);
    }

    private final class Fixture {
        private final Map<String, NativeBlockState> states = new HashMap<>();
        private final NativeBlockState air = state("minecraft:air");
        private final NativeBlockState water = state("minecraft:water");
        private final NativeBlockState stone = state("minecraft:stone");
        private final NativeBlockState decorant = state("minecraft:poppy");
        private final NativeBlockState half = state("minecraft:tall_grass[half=lower]");
        private final IrisPerfectionModifier modifier;

        private Fixture() {
            when(platform.registries().block("AIR")).thenReturn(air);
            when(platform.registries().block("WATER")).thenReturn(water);
            Engine engine = mock(Engine.class);
            when(engine.getDimension()).thenReturn(new IrisDimension());
            when(engine.getMetrics()).thenReturn(mock(EngineMetrics.class, RETURNS_DEEP_STUBS));
            MultiBurst pool = mock(MultiBurst.class);
            when(engine.burst()).thenReturn(pool);
            when(pool.burst(false)).thenAnswer(call -> {
                BurstExecutor burst = new BurstExecutor(mock(ExecutorService.class), 16);
                burst.setMulticore(false);
                return burst;
            });
            modifier = new IrisPerfectionModifier(engine);
        }

        private NativeBlockState spike(boolean upward, boolean waterlogged) {
            return state("minecraft:pointed_dripstone[thickness=tip,vertical_direction="
                    + (upward ? "up" : "down") + ",waterlogged=" + waterlogged + "]");
        }

        private NativeBlockState state(String key) {
            NativeBlockState existing = states.get(key);
            if (existing != null) {
                return existing;
            }
            NativeBlockState block = mock(NativeBlockState.class, withSettings().stubOnly());
            states.put(key, block);
            when(block.key()).thenReturn(key);
            when(block.isAir()).thenReturn(key.equals("minecraft:air"));
            when(block.isFluid()).thenReturn(key.equals("minecraft:water"));
            when(block.isWater()).thenReturn(key.equals("minecraft:water"));
            when(block.isWaterLogged()).thenReturn(key.contains("waterlogged=true"));
            when(block.isDecorant()).thenReturn(key.equals("minecraft:poppy") || key.startsWith("minecraft:tall_grass"));
            when(block.canPlaceOnto(any())).thenAnswer(call -> "minecraft:tall_grass[half=lower]".equals(call.<NativeBlockState>getArgument(0).key()));
            BlockData handle = mock(BlockData.class, withSettings().stubOnly());
            when(block.nativeHandle()).thenReturn(handle);
            when(handle.isFaceSturdy(any(), any())).thenReturn(key.equals("minecraft:stone"));
            when(block.withProperty(anyString(), anyString())).thenAnswer(call -> {
                String property = call.getArgument(0);
                String value = call.getArgument(1);
                return state(key.replaceAll(property + "=[^,\\]]+", property + "=" + value));
            });
            return block;
        }
    }

    private static final class CountingHunk extends ArrayHunk<NativeBlockState> {
        private int cleanColumnReads;

        private CountingHunk(int height) {
            super(16, height, 16);
        }

        @Override
        public NativeBlockState getRaw(int x, int y, int z) {
            if (x == 1 && z == 0) {
                cleanColumnReads++;
            }
            return super.getRaw(x, y, z);
        }
    }
}

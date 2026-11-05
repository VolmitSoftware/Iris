package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.modifier.ApiChunk;
import art.arcane.iris.engine.object.material.IrisBlockData;
import com.google.gson.Gson;
import com.volmit.block.BlockState;
import com.volmit.host.ModuleLogs;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResolvedDecoratorTest {
    private static final long SEED = 1337L;
    /** Taken from {@code IrisDecorator} (now deleted) picking the same blocks with the same seed, before decorators moved into the module. */
    private static final int PICKS_LENGTH = 246533;
    private static final int PICKS_HASH = 1275397037;
    private static final String[] DECORATORS = {
            "{\"chance\":0.5,\"palette\":[{\"block\":\"grass\"},{\"block\":\"fern\",\"weight\":2}],\"style\":{\"style\":\"SIMPLEX\",\"zoom\":2},"
                    + "\"variance\":{\"style\":\"PERLIN\",\"zoom\":3},\"stackMin\":1,\"stackMax\":4,\"heightVariance\":{\"style\":\"SIMPLEX\",\"zoom\":5},"
                    + "\"topPalette\":[{\"block\":\"poppy\"},{\"block\":\"dandelion\"}],\"topThreshold\":0.6}",
            "{}",
            "{\"chance\":1,\"palette\":[{\"block\":\"cactus\"}],\"topPalette\":[{\"block\":\"cactus_flower\"},{\"block\":\"stone\"}],\"stackMin\":2,\"stackMax\":2}",
            "{\"chance\":0.8,\"palette\":[{\"block\":\"kelp\"},{\"block\":\"seagrass\"},{\"block\":\"tall_seagrass\"}],\"stackMin\":2,\"stackMax\":9}",
    };

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void everyDecoratorPicksAsIrisDecoratorDid() {
        UsableBlockPlatform.bind();
        IrisData data = data();
        Gson gson = new Gson();
        StringBuilder transcript = new StringBuilder();
        for (String json : DECORATORS) {
            ResolvedDecorator resolved = ResolvedDecorator.of(gson.fromJson(json, Decorator.class), data.getPackResources(), SEED, ModuleLogs.discarding(), "test");
            for (int x = -40; x < 40; x += 3) {
                for (int z = -40; z < 40; z += 5) {
                    transcript.append(resolved.passesChanceGate(x, z)).append(resolved.stackHeight(x, z)).append(key(resolved.pickBlock(x, z)))
                            .append(key(resolved.pickTopBlock(x, z)));
                    for (int y = 60; y < 66; y += 2) {
                        transcript.append(key(resolved.blockAt(x, y, z))).append(key(resolved.topBlockAt(x, y, z)));
                    }
                }
            }
        }
        assertTrue("both outcomes of the chance gate occur", transcript.indexOf("true") >= 0 && transcript.indexOf("false") >= 0);
        assertTrue("the palettes' blocks are picked", transcript.indexOf("minecraft:fern") >= 0 && transcript.indexOf("minecraft:tall_seagrass") >= 0
                && transcript.indexOf("minecraft:cactus_flower") >= 0 && transcript.indexOf("minecraft:dandelion") >= 0);
        assertEquals(PICKS_LENGTH, transcript.length());
        assertEquals(PICKS_HASH, transcript.toString().hashCode());
    }

    @Test
    public void everyNoiseIsSeededFromTheDecoratorsSeedPlusTheOffsetsIrisAlwaysUsed() {
        List<String> built = new ArrayList<>();
        NoiseFactory recording = (style, seed) -> {
            built.add(style.style() + "@" + seed);
            return constant(0.5);
        };
        Decorator decorator = new Gson().fromJson("{\"style\": {\"style\": \"SIMPLEX\"}, \"variance\": {\"style\": \"PERLIN\"}, \"heightVariance\": {\"style\": \"CLOVER\"},"
                + "\"stackMin\": 2, \"stackMax\": 7}", Decorator.class);
        ResolvedDecorator resolved = new ResolvedDecorator(decorator, SEED, recording, state("a", "b", "b"), List.of(), null, List.of(), List.of(), ModuleLogs.discarding(), "test");

        resolved.warm();

        assertEquals(List.of("CLOVER@" + (SEED + 3 + 7 + 2), "SIMPLEX@" + (SEED + 3), "PERLIN@" + (SEED + 3)), built);
    }

    @Test
    public void theChanceGateReadsASeededStyleInPlainBlocks() {
        List<String> reads = new ArrayList<>();
        NoiseFactory recording = (style, seed) -> new NoiseSampler() {
            @Override
            public double sample(double worldX, double worldZ) {
                reads.add(worldX + "," + worldZ);
                return 0;
            }

            @Override
            public double sample(double worldX, double y, double worldZ) {
                return 0;
            }
        };
        Decorator zoomed = new Gson().fromJson("{\"style\": {\"style\": \"SIMPLEX\", \"zoom\": 4}}", Decorator.class);
        Decorator seeded = new Gson().fromJson("{\"style\": {\"style\": \"SIMPLEX\", \"zoom\": 4, \"seed\": 3}}", Decorator.class);

        new ResolvedDecorator(zoomed, SEED, recording, state("a"), List.of(), null, List.of(), List.of(), ModuleLogs.discarding(), "test").passesChanceGate(40, -8);
        new ResolvedDecorator(seeded, SEED, recording, state("a"), List.of(), null, List.of(), List.of(), ModuleLogs.discarding(), "test").passesChanceGate(40, -8);

        assertEquals(List.of("10.0,-2.0", "40.0,-8.0"), reads);
    }

    @Test
    public void noiseIsOnlyBuiltForWhatIsAskedFor() {
        NoiseFactory refusing = (style, seed) -> {
            throw new IllegalStateException("no noise");
        };
        ResolvedDecorator single = new ResolvedDecorator(new Decorator(), SEED, refusing, state("a"), List.of(), null, List.of(), List.of(), ModuleLogs.discarding(), "test");

        assertEquals("a one block palette never needs its variance noise", "minecraft:a", single.pickBlock(1, 2).key());
        assertEquals("equal limits never need the height noise", 1, single.stackHeight(1, 2));
        assertThrows(IllegalStateException.class, () -> single.passesChanceGate(1, 2));
    }

    @Test
    public void stackHeightFollowsTheHeightNoiseBetweenTheLimits() {
        Decorator decorator = new Gson().fromJson("{\"stackMin\": 1, \"stackMax\": 2}", Decorator.class);
        ResolvedDecorator top = new ResolvedDecorator(decorator, SEED, (style, seed) -> constant(1.0), state("a"), List.of(), null, List.of(), List.of(), ModuleLogs.discarding(), "test");
        ResolvedDecorator bottom = new ResolvedDecorator(decorator, SEED, (style, seed) -> constant(0.0), state("a"), List.of(), null, List.of(), List.of(), ModuleLogs.discarding(), "test");

        assertEquals(2, top.stackHeight(0, 0));
        assertEquals(1, bottom.stackHeight(0, 0));
    }

    @Test
    public void whiteAndBlackListsKeepTheDecoratorOffSurfaces() {
        BlockState sand = state("sand").get(0);
        BlockState stone = state("stone").get(0);
        Decorator listed = new Gson().fromJson("{\"whitelist\": [{\"block\": \"sand\"}], \"blacklist\": [{\"block\": \"stone\"}]}", Decorator.class);
        ResolvedDecorator resolved = new ResolvedDecorator(listed, SEED, (style, seed) -> constant(0), state("a"), List.of(),
                null, List.of(sand), List.of(stone), ModuleLogs.discarding(), "test");
        ResolvedDecorator unlisted = new ResolvedDecorator(new Decorator(), SEED, (style, seed) -> constant(0), state("a"), List.of(), null, List.of(), List.of(), ModuleLogs.discarding(), "test");

        assertFalse(resolved.rejectsSurface(sand));
        assertTrue(resolved.rejectsSurface(stone));
        assertTrue(resolved.rejectsSurface(state("dirt").get(0)));
        assertFalse(unlisted.rejectsSurface(stone));
    }

    @Test
    public void onlyASlopeOutsideTheLimitsBlocksAndOnlyWhenTheDecoratorLimitsIt() {
        Decorator limited = new Gson().fromJson("{\"slopeCondition\": {\"maximumSlope\": 4}}", Decorator.class);
        Decorator forced = new Gson().fromJson("{\"forcePlace\": true, \"slopeCondition\": {\"maximumSlope\": 4}}", Decorator.class);
        ResolvedDecorator resolvedLimited = new ResolvedDecorator(limited, SEED, (style, seed) -> constant(0), state("a"), List.of(), null, List.of(), List.of(), ModuleLogs.discarding(), "test");
        ResolvedDecorator resolvedForced = new ResolvedDecorator(forced, SEED, (style, seed) -> constant(0), state("a"), List.of(), null, List.of(), List.of(), ModuleLogs.discarding(), "test");
        ResolvedDecorator resolvedDefault = new ResolvedDecorator(new Decorator(), SEED, (style, seed) -> constant(0), state("a"), List.of(), null, List.of(), List.of(), ModuleLogs.discarding(), "test");

        assertTrue(resolvedLimited.slopeBlocks(() -> 5));
        assertFalse(resolvedLimited.slopeBlocks(() -> 3));
        assertFalse(resolvedForced.slopeBlocks(() -> 5));
        assertFalse(resolvedDefault.slopeBlocks(() -> {
            throw new AssertionError("a decorator without limits never asks for the slope");
        }));
    }

    private static List<BlockState> state(String... keys) {
        List<BlockState> states = new ArrayList<>();
        for (String key : keys) {
            states.add(ApiChunk.blockRegistry().find(key).orElseThrow());
        }
        return states;
    }

    private static NoiseSampler constant(double value) {
        return new NoiseSampler() {
            @Override
            public double sample(double worldX, double worldZ) {
                return value;
            }

            @Override
            public double sample(double worldX, double y, double worldZ) {
                return value;
            }
        };
    }

    private static String key(BlockState state) {
        return state == null ? null : state.key();
    }

    private static IrisData data() {
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBlockData> blocks = mock(ResourceLoader.class);
        when(data.getBlockLoader()).thenReturn(blocks);
        when(data.getDataFolder()).thenReturn(new File("pack"));
        IrisPackResources resources = new IrisPackResources(data);
        when(data.getPackResources()).thenReturn(resources);
        return data;
    }
}

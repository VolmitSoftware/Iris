package art.arcane.iris.engine.actuator;

import art.arcane.iris.modules.terrain.OreGenerator;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.material.IrisBlockData;
import com.google.gson.Gson;
import com.volmit.block.BlockState;
import com.volmit.noise.NoiseSampler;
import com.volmit.resource.PackResources;
import org.junit.After;
import org.junit.Test;

import java.io.File;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResolvedOreTest {
    private static final long TERRAIN_SEED = 1337L;
    private static final long WARM_SEED = 99L;
    /** Taken from {@code IrisOreGenerator} (now deleted) generating the same ores with the same seeds, before ores moved into the terrain module. */
    private static final int TRANSCRIPT_LENGTH = 128237;
    private static final int TRANSCRIPT_HASH = -1665706847;
    private static final String[] ORES = {
            "{\"palette\":{\"palette\":[{\"block\":\"coal_ore\"}]},\"chanceStyle\":{\"style\":\"STATIC\"},\"threshold\":0.2,\"range\":{\"min\":0,\"max\":64}}",
            "{\"palette\":{\"palette\":[{\"block\":\"iron_ore\"},{\"block\":\"deepslate_iron_ore\"}]},\"chanceStyle\":{\"style\":\"SIMPLEX\",\"zoom\":4},"
                    + "\"threshold\":0.5,\"range\":{\"min\":-16,\"max\":40}}",
            "{\"palette\":{\"palette\":[{\"block\":\"gold_ore\"},{\"block\":\"gold_block\"},{\"block\":\"raw_gold_block\"}]},\"chanceStyle\":{\"style\":\"PERLIN\",\"zoom\":2},"
                    + "\"threshold\":0.35,\"range\":{\"min\":10,\"max\":20}}",
            "{\"palette\":{\"palette\":[]},\"chanceStyle\":{\"style\":\"STATIC\"},\"threshold\":1,\"range\":{\"min\":0,\"max\":100}}",
            "{\"palette\":{\"palette\":[{\"block\":\"bedrock\"}]},\"chanceStyle\":{\"style\":\"FLAT\"},\"threshold\":1,\"range\":{\"min\":0,\"max\":3}}",
    };

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void everyOreGeneratesAsIrisOreGeneratorDid() {
        UsableBlockPlatform.bind();
        PackResources resources = resources();
        StringBuilder transcript = new StringBuilder();
        for (String json : ORES) {
            ResolvedOre ore = ResolvedOre.of(new Gson().fromJson(json, OreGenerator.class), resources, TERRAIN_SEED);
            ore.warm(WARM_SEED);
            for (int x = -30; x < 30; x += 3) {
                for (int z = -30; z < 30; z += 4) {
                    for (int y = -20; y < 70; y += 5) {
                        BlockState state = ore.generate(x, y, z);
                        transcript.append(state == null ? "-" : state.key()).append(';');
                    }
                }
            }
        }
        assertTrue("the palettes' blocks are placed", transcript.indexOf("minecraft:deepslate_iron_ore") >= 0
                && transcript.indexOf("minecraft:iron_ore") >= 0 && transcript.indexOf("minecraft:coal_ore") >= 0 && transcript.indexOf("minecraft:bedrock") >= 0);
        assertTrue("positions without ore occur", transcript.indexOf("-") >= 0);
        assertEquals(TRANSCRIPT_LENGTH, transcript.length());
        assertEquals(TRANSCRIPT_HASH, transcript.toString().hashCode());
    }

    @Test
    public void theNoiseKeepsTheFirstSeedItIsBuiltFrom() {
        OreGenerator settings = new Gson().fromJson(ORES[1], OreGenerator.class);
        List<Long> seeds = new ArrayList<>();
        ResolvedOre ore = new ResolvedOre(settings, (style, seed) -> {
            seeds.add(seed);
            return constant(0);
        }, null, TERRAIN_SEED, List.of(new BlockState("minecraft:iron_ore")));

        ore.warm(WARM_SEED);
        ore.warm(WARM_SEED + 1);
        ore.generate(0, 0, 0);

        assertEquals(List.of(WARM_SEED), seeds);
    }

    @Test
    public void anOreNothingWarmedBuildsItsNoiseFromTheTerrainSeed() {
        OreGenerator settings = new Gson().fromJson(ORES[1], OreGenerator.class);
        List<Long> seeds = new ArrayList<>();
        ResolvedOre ore = new ResolvedOre(settings, (style, seed) -> {
            seeds.add(seed);
            return constant(0);
        }, null, TERRAIN_SEED, List.of(new BlockState("minecraft:iron_ore")));

        ore.generate(0, 0, 0);
        ore.generate(1, 1, 1);

        assertEquals(List.of(TERRAIN_SEED), seeds);
    }

    @Test
    public void nothingGeneratesOutsideTheBandOrWithAnEmptyPaletteOrAboveTheThreshold() {
        BlockState iron = new BlockState("minecraft:iron_ore");
        OreGenerator settings = new Gson().fromJson(ORES[1], OreGenerator.class);
        ResolvedOre atThreshold = new ResolvedOre(settings, (style, seed) -> constant(0.5), null, TERRAIN_SEED, List.of(iron));
        ResolvedOre aboveThreshold = new ResolvedOre(settings, (style, seed) -> constant(0.51), null, TERRAIN_SEED, List.of(iron));
        ResolvedOre noBlocks = new ResolvedOre(settings, (style, seed) -> constant(0), null, TERRAIN_SEED, List.of());
        OreGenerator emptyPalette = new Gson().fromJson(ORES[3], OreGenerator.class);
        ResolvedOre empty = new ResolvedOre(emptyPalette, (style, seed) -> constant(0), null, TERRAIN_SEED, List.of());

        assertEquals("noise at the threshold becomes ore", iron, atThreshold.generate(0, 0, 0));
        assertNull(aboveThreshold.generate(0, 0, 0));
        assertNull("below the band", atThreshold.generate(0, -17, 0));
        assertNull("above the band", atThreshold.generate(0, 41, 0));
        assertEquals("the band's ends are in it", iron, atThreshold.generate(0, 40, 0));
        assertNull(noBlocks.generate(0, 0, 0));
        assertNull(empty.generate(0, 50, 0));
    }

    @Test
    public void oresDefaultToNoBlocksSurfaceOffAndTheDocumentedBand() {
        OreGenerator ore = new Gson().fromJson("{}", OreGenerator.class);

        assertEquals(List.of(), ore.palette().palette());
        assertEquals("STATIC", ore.chanceStyle().style());
        assertEquals(false, ore.generateSurface());
        assertEquals(0.5, ore.threshold(), 0);
        assertEquals(30, ore.range().min(), 0);
        assertEquals(80, ore.range().max(), 0);
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

    private static PackResources resources() {
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBlockData> blocks = mock(ResourceLoader.class);
        when(data.getBlockLoader()).thenReturn(blocks);
        when(data.getDataFolder()).thenReturn(new File("pack"));
        IrisPackResources resources = new IrisPackResources(data);
        when(data.getPackResources()).thenReturn(resources);
        return resources;
    }
}

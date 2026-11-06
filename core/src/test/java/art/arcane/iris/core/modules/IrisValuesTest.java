package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.material.IrisMaterialPalette;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.noise.expression.IrisExpression;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.block.PaletteSampler;
import com.volmit.block.PaletteSpec;
import com.volmit.host.OptionsMetadata;
import com.volmit.noise.NoiseSampler;
import com.volmit.noise.NoiseStyle;
import com.volmit.object.BlockEntityData;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** IrisApi's value types read the JSON of Iris's own classes and resolve, and build noise, exactly as those do. */
public class IrisValuesTest {
    private static final double[][] COLUMNS = {{0, 0}, {17.5, -3}, {-640, 1280}, {12345.25, 678.5}};
    private static final Map<Class<?>, Set<String>> IRIS_ONLY_FIELDS = Map.of(IrisBlockData.class, Set.of("debug"),
            IrisGeneratorStyle.class, Set.of("cacheSize"));

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void everyValueTypeHasTheJsonFieldsOfItsIrisClass() {
        IrisValues.irisClasses().forEach((apiType, irisClass) -> {
            Set<String> api = new TreeSet<>(OptionsMetadata.inspect(apiClass(apiType)).options().stream().map(OptionsMetadata.Option::name).toList());
            Set<String> iris = new TreeSet<>(jsonFields(irisClass));
            iris.removeAll(IRIS_ONLY_FIELDS.getOrDefault(irisClass, Set.of()));
            assertEquals(apiType + " and " + irisClass.getName(), iris, api);
        });
    }

    @Test
    public void blockSpecsResolveThroughThePlatformWithTheirProperties() {
        UsableBlockPlatform.bind();
        IrisBlockRegistry blocks = blocksOf(packWithoutCustomBlocks());

        assertEquals(BlockState.parse("minecraft:iron_ore"), blocks.resolve(BlockSpec.of("iron_ore")));
        assertEquals(BlockState.parse("minecraft:oak_log[axis=y]"), blocks.resolve(BlockSpec.of("minecraft:oak_log").withProperty("axis", "y")));
    }

    @Test
    public void aBlockSpecNamesTheCustomBlocksOfThePackAndFallsBackToItsBackup() {
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key.contains("missing") ? null : UsableBlockPlatform.block(key);
        });
        when(registries.blockOrNull(anyString(), anyBoolean())).thenAnswer(invocation -> registries.block(invocation.getArgument(0)));
        bind(registries);
        IrisData data = packWithoutCustomBlocks();
        when(data.getBlockLoader().load("glowing_stone", false)).thenReturn(new IrisBlockData("minecraft:stone").setData(new KMap<String, Object>().qput("lit", true)));
        IrisBlockRegistry blocks = blocksOf(data);

        assertEquals(BlockState.parse("minecraft:stone[lit=true]"), blocks.resolve(BlockSpec.of("glowing_stone")));
        assertEquals(BlockState.parse("minecraft:cobblestone"), blocks.resolve(BlockSpec.of("mod:missing").withBackup(BlockSpec.of("cobblestone"))));
    }

    @Test
    public void tileDataBecomesIrisBlockEntities() {
        UsableBlockPlatform.bind();
        TileData.TileFactory previous = TileData.bindPlatformFactory((state, properties) -> new TileData(state.key(), properties));
        try {
            IrisBlockRegistry blocks = blocksOf(packWithoutCustomBlocks());
            BlockSpec spawner = BlockSpec.of("spawner").withTileData(Map.of("Delay", 20.0));

            assertEquals(Optional.empty(), blocks.blockEntity(BlockSpec.of("stone")));
            assertEquals("the platform's stone-like spawner has no block entity", Optional.empty(), blocks.blockEntity(spawner));
            PlatformBlockState withEntity = UsableBlockPlatform.block("minecraft:chest");
            when(withEntity.hasTileEntity()).thenReturn(true);
            PlatformRegistries registries = mock(PlatformRegistries.class);
            when(registries.block(anyString())).thenReturn(withEntity);
            bind(registries);
            BlockEntityData entity = blocksOf(packWithoutCustomBlocks()).blockEntity(BlockSpec.of("chest").withTileData(Map.of("Lock", "key")))
                    .orElseThrow();
            assertEquals(BlockEntities.TILE_FORMAT, entity.format());
        } finally {
            TileData.restorePlatformFactory(previous);
        }
    }

    @Test
    public void noiseStylesBuildTheNoiseOfTheirIrisGeneratorStyle() {
        String json = "{\"style\": \"SIMPLEX\", \"zoom\": 4, \"exponent\": 1.5, \"cellularFrequency\": 0.3,"
                + " \"fracture\": {\"style\": \"PERLIN\", \"zoom\": 2, \"multiplier\": 3}}";
        IrisData data = packWithoutCustomBlocks();
        NoiseStyle style = new Gson().fromJson(json, NoiseStyle.class);
        CNG iris = new Gson().fromJson(json, IrisGeneratorStyle.class).create(new RNG(1337), data);
        NoiseSampler sampler = noiseOf(data).sampler(style, 1337);

        for (double[] column : COLUMNS) {
            assertEquals(iris.noise(column[0], column[1]), sampler.sample(column[0], column[1]), 0);
            assertEquals(iris.noise(column[0], 64, column[1]), sampler.sample(column[0], 64, column[1]), 0);
        }
    }

    @Test
    public void aSeededStyleIgnoresTheSeedItIsAskedForAndFollowsTheWorldSeed() {
        NoiseStyle seeded = NoiseStyle.of("SIMPLEX").withZoom(40).withSeed(7);
        IrisData world = packOfWorldSeed(1337L);
        IrisData otherWorld = packOfWorldSeed(4242L);

        NoiseSampler asked = noiseOf(world).sampler(seeded, 1);
        NoiseSampler askedOtherwise = noiseOf(world).sampler(seeded, 987_654_321L);
        NoiseSampler inOtherWorld = noiseOf(otherWorld).sampler(seeded, 1);
        NoiseSampler otherStyleSeed = noiseOf(world).sampler(seeded.withSeed(8), 1);

        int differsByWorld = 0;
        int differsByStyleSeed = 0;
        for (double[] column : COLUMNS) {
            assertEquals(asked.sample(column[0], column[1]), askedOtherwise.sample(column[0], column[1]), 0);
            differsByWorld += asked.sample(column[0], column[1]) != inOtherWorld.sample(column[0], column[1]) ? 1 : 0;
            differsByStyleSeed += asked.sample(column[0], column[1]) != otherStyleSeed.sample(column[0], column[1]) ? 1 : 0;
        }
        assertEquals(COLUMNS.length, differsByWorld);
        assertEquals(COLUMNS.length, differsByStyleSeed);
    }

    @Test
    public void signedNoiseIsTheSignedFastReadOfTheIrisGeneratorsNoise() {
        String json = "{\"style\": \"SIMPLEX\", \"zoom\": 4, \"exponent\": 1.5, \"fracture\": {\"style\": \"PERLIN\", \"zoom\": 2, \"multiplier\": 3}}";
        IrisData data = packWithoutCustomBlocks();
        CNG iris = new Gson().fromJson(json, IrisGeneratorStyle.class).create(new RNG(1337), data);
        NoiseSampler sampler = noiseOf(data).sampler(new Gson().fromJson(json, NoiseStyle.class), 1337);

        for (double[] column : COLUMNS) {
            assertEquals(iris.noiseFastSigned2D(column[0], column[1]), sampler.signed(column[0], column[1]), 0);
            assertEquals(iris.noiseFastSigned3D(column[0], 64, column[1]), sampler.signed(column[0], 64, column[1]), 0);
            assertEquals(iris.noiseFastSigned3D(column[0], 64, column[1]), sampler.signed(column[0], 64, column[1]), 0);
        }
    }

    @Test
    public void scaledNoiseIsTheGeneratorWithTheScaleBakedIn() {
        String json = "{\"style\": \"CELLULAR_IRIS_DOUBLE\", \"zoom\": 1}";
        IrisData data = packWithoutCustomBlocks();
        double scale = 1.5D;
        CNG baked = new Gson().fromJson(json, IrisGeneratorStyle.class).createNoCache(new RNG(4242), data).bake().scale(scale).bake();
        NoiseSampler plain = noiseOf(data).sampler(new Gson().fromJson(json, NoiseStyle.class), 4242);
        NoiseSampler scaled = plain.scaled(scale);

        for (int x = -30; x <= 30; x += 3) {
            assertEquals(baked.noiseFast2D(x, 7), scaled.sample(x, 7), 0);
        }
        assertEquals("the sampler it was made from is left as it was", new Gson().fromJson(json, IrisGeneratorStyle.class)
                .createNoCache(new RNG(4242), data).noise(9, 4), plain.sample(9, 4), 0);
    }

    @Test
    public void theIrisStyleIsTheSignatureNoiseOfTheSeedThatSetsTheThicknessOfBiomeLayers() {
        IrisNoiseFactory factory = noiseOf(packWithoutCustomBlocks());
        long mismatches = 0;
        for (long seed : new long[]{1, 7235, -3999281L, 1337L * 31, 987654321987L}) {
            CNG signature = CNG.signature(new RNG(seed));
            NoiseSampler sampler = factory.sampler(NoiseStyle.of("IRIS"), seed);
            for (int x = -2000; x < 2000; x += 7) {
                for (int z = -1000; z < 1000; z += 11) {
                    double zoom = 5;
                    mismatches += signature.noiseFast2D(x / zoom, z / zoom) == sampler.sample(x / zoom, z / zoom) ? 0 : 1;
                }
            }
        }
        assertEquals(0, mismatches);
    }

    @Test
    public void aStyleNameIrisDoesNotKnowFails() {
        IrisNoiseFactory noise = noiseOf(packWithoutCustomBlocks());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> noise.sampler(NoiseStyle.of("NOT_A_STYLE"), 7));

        assertEquals("Iris has no noise style \"NOT_A_STYLE\"", failure.getMessage());
    }

    @Test
    public void anExpressionThePackDoesNotHaveFails() {
        IrisData data = packWithoutCustomBlocks();
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisExpression> noExpressions = mock(ResourceLoader.class);
        when(noExpressions.getPossibleKeys()).thenReturn(new String[0]);
        when(data.getExpressionLoader()).thenReturn(noExpressions);
        IrisNoiseFactory noise = noiseOf(data);
        String json = "{\"style\": \"SIMPLEX\", \"expression\": {\"namespace\": \"test\", \"path\": \"missing\"}}";

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> noise.sampler(new Gson().fromJson(json, NoiseStyle.class), 7));

        assertEquals("The pack has no expression test:missing", failure.getMessage());
    }

    @Test
    public void palettesPickTheBlocksOfTheirIrisMaterialPalette() {
        UsableBlockPlatform.bind();
        String json = "{\"style\": {\"style\": \"SIMPLEX\", \"zoom\": 0.7, \"exponent\": 1.5}, \"zoom\": 3, \"palette\": ["
                + "{\"block\": \"stone\"}, {\"block\": \"andesite\", \"weight\": 2}, {\"block\": \"granite\", \"weight\": 3}]}";
        IrisData data = packWithoutCustomBlocks();
        IrisMaterialPalette iris = new Gson().fromJson(json, IrisMaterialPalette.class);
        PaletteSampler sampler = blocksOf(data).palette(new Gson().fromJson(json, PaletteSpec.class), 1337);

        assertTrue("the positions cover several blocks", picksLike(iris, sampler, data, 1337).size() > 1);
    }

    @Test
    public void aPaletteWithoutOptionsIsOneStoneEverywhereLikeIris() {
        UsableBlockPlatform.bind();
        IrisData data = packWithoutCustomBlocks();
        PaletteSampler sampler = blocksOf(data).palette(new Gson().fromJson("{}", PaletteSpec.class), 7);

        picksLike(new Gson().fromJson("{}", IrisMaterialPalette.class), sampler, data, 7);
        assertEquals(BlockState.parse("minecraft:stone"), sampler.at(3, 4, 5));
    }

    @Test
    public void palettesAreBuiltOncePerSpecAndSeed() {
        UsableBlockPlatform.bind();
        IrisBlockRegistry blocks = blocksOf(packWithoutCustomBlocks());
        PaletteSpec spec = PaletteSpec.of(List.of(BlockSpec.of("stone"), BlockSpec.of("dirt")));

        assertSame(blocks.palette(spec, 1), blocks.palette(PaletteSpec.of(spec.palette()), 1));
        assertTrue(blocks.palette(spec, 1) != blocks.palette(spec, 2));
    }

    @Test
    public void aPaletteWithNoBlockToPickIsRefused() {
        UsableBlockPlatform.bind();
        IrisBlockRegistry blocks = blocksOf(packWithoutCustomBlocks());

        assertThrows(IllegalArgumentException.class, () -> blocks.palette(PaletteSpec.of(List.of()), 1));
        assertThrows(IllegalArgumentException.class, () -> blocks.palette(PaletteSpec.of(List.of(BlockSpec.of("stone").withWeight(0))), 1));
    }

    /** Asserts both pick the same block at several positions; returns the blocks picked. */
    private static Set<BlockState> picksLike(IrisMaterialPalette iris, PaletteSampler sampler, IrisData data, long seed) {
        Set<BlockState> picked = new TreeSet<>(java.util.Comparator.comparing(BlockState::key));
        for (double[] position : new double[][]{{0, 0, 0}, {17.5, 64, -3}, {-640, 12, 1280}, {12345.25, -30.5, 678.5}, {5, 200, 5}, {-3, 7, 9}}) {
            String expected = iris.get(new RNG(seed), position[0], position[1], position[2], data).key();
            BlockState actual = sampler.at(position[0], position[1], position[2]);
            assertEquals(BlockState.parse(expected), actual);
            picked.add(actual);
        }
        return picked;
    }

    private static IrisData packWithoutCustomBlocks() {
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBlockData> blocks = mock(ResourceLoader.class);
        when(data.getBlockLoader()).thenReturn(blocks);
        when(data.getDataFolder()).thenReturn(new java.io.File("test-pack"));
        return data;
    }

    private static IrisData packOfWorldSeed(long worldSeed) {
        IrisData data = packWithoutCustomBlocks();
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        when(engine.getSeedManager().getSeed()).thenReturn(worldSeed);
        when(data.getEngine()).thenReturn(engine);
        return data;
    }

    private static IrisBlockRegistry blocksOf(IrisData data) {
        return new IrisBlockRegistry(data, new IrisPackResources(data, List::of));
    }

    private static IrisNoiseFactory noiseOf(IrisData data) {
        return new IrisNoiseFactory(data, new IrisPackResources(data, List::of));
    }

    private static void bind(PlatformRegistries registries) {
        UsableBlockPlatform.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    private static Class<?> apiClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(missing);
        }
    }

    /** The fields Iris's Gson reads: instance fields that are not transient, a superclass's included. */
    private static List<String> jsonFields(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !field.isSynthetic()) {
                    names.add(field.getName());
                }
            }
        }
        return names;
    }
}

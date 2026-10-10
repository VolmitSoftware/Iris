package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import com.volmit.host.ModuleLogs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The decorators of a world's biomes, sorted into the places they decorate and seeded from the world. */
public class BiomeDecoratorsTest {
    private static final long DECORATOR_SEED = 5000L;
    private static final String MEADOW_DECORATORS = """
            [{"chance": 0.5, "palette": [{"block": "poppy"}]},
             {"partOf": "SHORE_LINE", "chance": 1, "stackMax": 3, "palette": [{"block": "sugar_cane"}]},
             {"chance": 0.25, "palette": [{"block": "fern"}, {"block": "grass"}]},
             {"partOf": "CEILING", "chance": 1, "palette": [{"block": "glow_lichen"}]}]
            """;

    private Engine engine;
    private IrisBiome meadow;
    private IrisBiome bare;
    private BiomeDecorators decorators;

    @Before
    public void createWorld() {
        UsableBlockPlatform.bind();
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBlockData> blocks = mock(ResourceLoader.class);
        when(data.getBlockLoader()).thenReturn(blocks);
        when(data.getDataFolder()).thenReturn(new File("pack"));
        IrisPackResources resources = new IrisPackResources(data);
        when(data.getPackResources()).thenReturn(resources);
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getDecorator()).thenReturn(DECORATOR_SEED);
        engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getSeedManager()).thenReturn(seeds);
        meadow = biome("meadow", MEADOW_DECORATORS);
        bare = mock(IrisBiome.class);
        when(bare.getLoadKey()).thenReturn("bare");
        when(bare.getModuleSettings()).thenReturn(ModuleSettings.NONE);
        decorators = new BiomeDecorators(engine, ModuleLogs.discarding());
    }

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void aBiomesDecoratorsAreSortedIntoTheirPlacesInTheOrderTheBiomeListsThem() {
        assertEquals(List.of(0.5, 0.25), chances(decorators.decorators(meadow, DecorationPart.NONE)));
        assertEquals(List.of(1.0), chances(decorators.decorators(meadow, DecorationPart.SHORE_LINE)));
        assertEquals(List.of(1.0), chances(decorators.decorators(meadow, DecorationPart.CEILING)));
        assertEquals(0, decorators.decorators(meadow, DecorationPart.SEA_FLOOR).length);
        assertTrue(decorators.hasDecorators(meadow, DecorationPart.CEILING));
        assertFalse(decorators.hasDecorators(meadow, DecorationPart.SEA_SURFACE));
    }

    @Test
    public void aBiomeWithoutTheSettingsHasNoDecorators() {
        assertFalse(decorators.hasDecorators(bare));
        assertEquals(0, decorators.decorators(bare, DecorationPart.NONE).length);
        assertEquals(0D, decorators.load(bare), 0);
    }

    @Test
    public void decoratorsAreResolvedOnceAndKeptForTheWorld() {
        assertSame(decorators.decorators(meadow, DecorationPart.NONE)[0], decorators.decorators(meadow, DecorationPart.NONE)[0]);
    }

    @Test
    public void everyDecoratorNoiseSeedIsAFunctionOfTheWorldsDecoratorSeedItsBiomeAndItsPlace() {
        long first = seedOf(decorators.decorators(meadow, DecorationPart.NONE)[0]);
        long second = seedOf(decorators.decorators(meadow, DecorationPart.NONE)[1]);

        assertEquals("biome meadow, index 0", DECORATOR_SEED + "meadow".hashCode() * 0x9E3779B97F4A7C15L, first);
        assertEquals("biome meadow, index 2", DECORATOR_SEED + "meadow".hashCode() * 0x9E3779B97F4A7C15L + 2 * 0xC2B2AE3D27D4EB4FL, second);
        assertNotEquals(first, second);
        assertEquals("another build of the same world draws the same seeds", first,
                seedOf(new BiomeDecorators(engine, ModuleLogs.discarding()).decorators(meadow, DecorationPart.NONE)[0]));
    }

    @Test
    public void warmingResolvesEveryDecoratorOfEveryReachableBiome() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>(List.of(meadow, bare)));
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getAllBiomes()).thenCallRealMethod();

        decorators.warm();

        assertEquals(2, decorators.decorators(meadow, DecorationPart.NONE).length);
    }

    @Test
    public void theBusiestBiomeSetsTheScaleOfTheLoadViews() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>(List.of(meadow, bare)));
        when(engine.getDimension()).thenReturn(dimension);

        assertEquals(0.5 + 3 * 1.0 + 0.25 + 1.0, decorators.load(meadow), 1e-9);
        assertEquals(decorators.load(meadow), decorators.busiestBiomeLoad(), 0);
        assertEquals((0.5 + 1.0 + 0.25 + 1.0) * 256, decorators.colourLoad(meadow), 1e-9);
    }

    private IrisBiome biome(String key, String decoratorsJson) {
        List<Decorator> list = List.of(new Gson().fromJson(decoratorsJson, Decorator[].class));
        ModuleSettings settings = mock(ModuleSettings.class);
        when(settings.find(DecorantSettings.class)).thenReturn(Optional.of(new DecorantSettings(list)));
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getLoadKey()).thenReturn(key);
        when(biome.getModuleSettings()).thenReturn(settings);
        return biome;
    }

    private static List<Double> chances(ResolvedDecorator[] bucket) {
        return java.util.Arrays.stream(bucket).map(decorator -> decorator.settings().chance()).toList();
    }

    private static long seedOf(ResolvedDecorator decorator) {
        return decorator.seed();
    }
}

package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.iris.engine.modifier.ApiChunk;
import com.google.gson.Gson;
import com.volmit.resource.ResourceId;
import com.volmit.world.BiomeKind;
import com.volmit.world.PackWorld;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The decorators of a world's biomes, sorted into the places they decorate and seeded from the world. */
public class BiomeDecoratorsTest {
    private static final String MEADOW_DECORATORS = """
            [{"chance": 0.5, "palette": [{"block": "poppy"}]},
             {"partOf": "SHORE_LINE", "chance": 1, "stackMax": 3, "palette": [{"block": "sugar_cane"}]},
             {"chance": 0.25, "palette": [{"block": "fern"}, {"block": "grass"}]},
             {"partOf": "CEILING", "chance": 1, "palette": [{"block": "glow_lichen"}]}]
            """;

    private ApiChunk chunk;
    private PackWorld world;
    private DecoratedBiome meadow;
    private DecoratedBiome bare;
    private BiomeDecorators decorators;

    @Before
    public void createWorld() {
        List<Decorator> list = List.of(new Gson().fromJson(MEADOW_DECORATORS, Decorator[].class));
        DecorantSettings settings = new DecorantSettings(list);
        chunk = new ApiChunk(0, 0, 16).biomeSettings(settings);
        world = chunk.world();
        meadow = new DecoratedBiome("meadow", settings, BiomeKind.LAND);
        bare = new DecoratedBiome("bare", null, BiomeKind.LAND);
        decorators = BiomeDecorators.of(world);
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
        assertFalse(decorators.hasDecorators(null));
        assertEquals(0, decorators.decorators(bare, DecorationPart.NONE).length);
    }

    @Test
    public void decoratorsAreResolvedOnceAndKeptForTheWorld() {
        assertSame(decorators.decorators(meadow, DecorationPart.NONE)[0], decorators.decorators(meadow, DecorationPart.NONE)[0]);
        assertSame(decorators, BiomeDecorators.of(world));
    }

    @Test
    public void everyDecoratorNoiseSeedIsAFunctionOfTheWorldsDecoratorSeedItsBiomeAndItsPlace() {
        long decoratorSeed = IrisSeed.DECORATOR.of(world);
        long first = decorators.decorators(meadow, DecorationPart.NONE)[0].seed();
        long second = decorators.decorators(meadow, DecorationPart.NONE)[1].seed();

        assertEquals("biome meadow, index 0", decoratorSeed + "meadow".hashCode() * 0x9E3779B97F4A7C15L, first);
        assertEquals("biome meadow, index 2", decoratorSeed + "meadow".hashCode() * 0x9E3779B97F4A7C15L + 2 * 0xC2B2AE3D27D4EB4FL, second);
        assertNotEquals(first, second);
        assertEquals("another build of the same world draws the same seeds", first,
                new BiomeDecorators(world).decorators(meadow, DecorationPart.NONE)[0].seed());
    }

    @Test
    public void aBiomesKeyIsTheRestOfTheIdOfTheSettingsItDeclared() {
        assertEquals("desert/dunes", BiomeDecorators.biomeKey(ResourceId.parse("overworld:biome/desert/dunes")));
    }

    @Test
    public void warmingResolvesEveryDecoratorOfEveryBiomeThePackDeclares() {
        decorators.warm();

        assertEquals(2, decorators.decorators(new DecoratedBiome("plains", chunk.world().settings().declared(DecorantSettings.class).getFirst().value(),
                BiomeKind.LAND), DecorationPart.NONE).length);
    }

    private static List<Double> chances(ResolvedDecorator[] bucket) {
        return java.util.Arrays.stream(bucket).map(decorator -> decorator.settings().chance()).toList();
    }
}

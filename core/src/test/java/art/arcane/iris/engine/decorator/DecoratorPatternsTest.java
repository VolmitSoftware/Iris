package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.iris.engine.modifier.ApiChunk;
import com.google.gson.Gson;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.host.ModuleLogs;
import com.volmit.noise.NoiseSampler;
import com.volmit.resource.PackResources;
import com.volmit.resource.PackSnippet;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.BlockPattern;
import com.volmit.tooling.PatternArea;
import com.volmit.tooling.PatternEntry;
import com.volmit.world.PackBiome;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DecoratorPatternsTest {
    private static final ResourceId MEADOW = ResourceId.parse("pack:meadow");
    private static final double NOISE_EVERYWHERE = 0.3;
    private static final String POPPIES = "{\"chance\": 0.5, \"palette\": [{\"block\": \"minecraft:poppy\"}]}";
    private static final String RARE_FERNS = "{\"chance\": 0.1, \"palette\": [{\"block\": \"minecraft:fern\"}]}";
    private static final String CANE = "{\"chance\": 1, \"stackMin\": 3, \"stackMax\": 3, \"palette\": [{\"block\": \"minecraft:sugar_cane\"}],"
            + " \"topPalette\": [{\"block\": \"minecraft:bamboo\"}]}";
    private static final String UNUSED_TULIPS = "{\"chance\": 1, \"palette\": [{\"block\": \"minecraft:red_tulip\"}]}";

    private final DecoratorPatterns view = new DecoratorPatterns();
    private final PatternArea area = new PatternArea(0, 0, 4, 1);
    private PackWorld world;

    @Before
    public void createWorld() {
        Gson gson = new Gson();
        Decorator poppies = gson.fromJson(POPPIES, Decorator.class);
        Decorator tulips = gson.fromJson(UNUSED_TULIPS, Decorator.class);
        DecorantSettings meadow = new DecorantSettings(List.of(poppies, gson.fromJson(RARE_FERNS, Decorator.class), gson.fromJson(CANE, Decorator.class)));

        PackResources resources = mock(PackResources.class);
        when(resources.ids(PackResources.BIOMES)).thenReturn(List.of(MEADOW));
        when(resources.find(PackResources.BIOMES, MEADOW))
                .thenReturn(Optional.of(new PackBiome(MEADOW, "Meadow", ResourceId.parse("minecraft:plains"), List.of())));
        when(resources.blocks()).thenReturn(ApiChunk.blockRegistry());
        when(resources.noise()).thenReturn((style, seed) -> constant(NOISE_EVERYWHERE));
        when(resources.snippetOf(any())).thenReturn(Optional.empty());
        when(resources.snippetOf(poppies)).thenReturn(Optional.of(new PackSnippet.Source("flowers/poppies", 0)));
        when(resources.snippets(Decorator.class)).thenReturn(List.of(
                new PackSnippet<>("flowers/broken", List.of()),
                new PackSnippet<>("flowers/poppies", List.of(poppies)),
                new PackSnippet<>("flowers/tulips", List.of(gson.fromJson(RARE_FERNS, Decorator.class), tulips))));

        FeatureSettings settings = mock(FeatureSettings.class);
        when(settings.declaredBy(MEADOW, DecorantSettings.class)).thenReturn(Optional.of(meadow));
        RunningWorld running = mock(RunningWorld.class);
        when(running.settings()).thenReturn(settings);

        world = mock(PackWorld.class);
        when(world.running()).thenReturn(Optional.of(running));
        when(world.resources()).thenReturn(resources);
        when(world.log()).thenReturn(ModuleLogs.discarding());
        when(world.state(eq(BiomeDecorators.class), any())).thenAnswer(asked -> asked.<Function<PackWorld, BiomeDecorators>>getArgument(1).apply(world));
    }

    @Test
    public void listsEachDecoratorUnderItsBiomeAndEachSnippetFileAfterThem() {
        List<PatternEntry> entries = view.entries(world);

        assertEquals(List.of("pack:meadow#0", "pack:meadow#1", "pack:meadow#2", "snippet:flowers/broken#0", "snippet:flowers/poppies#0",
                "snippet:flowers/tulips#0", "snippet:flowers/tulips#1"), entries.stream().map(PatternEntry::key).toList());
        assertEquals(Optional.of("Meadow"), entries.getFirst().group());
        assertTrue(entries.getFirst().facts().contains("Snippet flowers/poppies"));
        assertFalse(entries.get(1).facts().stream().anyMatch(fact -> fact.startsWith("Snippet")));
        assertEquals("a stack is stepped through block by block", 3, entries.get(2).depths());
        assertEquals(List.of("Could not be read; the server log says why"), entries.get(3).facts());
        assertEquals("flowers/poppies", entries.get(4).name());
        assertTrue(entries.get(4).facts().contains("Meadow · Decorator 1"));
        assertEquals("a file holding several is listed entry by entry", "flowers/tulips · 2", entries.get(6).name());
        assertTrue(entries.get(6).facts().contains("Not used by any biome"));
    }

    @Test
    public void placesOnlyWhereTheDecoratorsNoiseStaysUnderItsChance() {
        assertEquals(List.of("minecraft:poppy"), blocksOf("pack:meadow#0", 0));
        assertTrue(placesEverywhere("pack:meadow#0", 0));
        assertEquals(List.of(), blocksOf("pack:meadow#1", 0));
    }

    @Test
    public void aStackHasItsTopPaletteOnTopAndEndsAtItsHeight() {
        assertEquals(List.of("minecraft:bamboo"), blocksOf("pack:meadow#2", 0));
        assertEquals(List.of("minecraft:sugar_cane"), blocksOf("pack:meadow#2", 2));
        assertEquals(List.of(), blocksOf("pack:meadow#2", 3));
    }

    @Test
    public void aSnippetFileShowsWhatItsFirstUsePlacesOrItsOwnBlocksWhenUnused() {
        assertEquals(List.of("minecraft:poppy"), blocksOf("snippet:flowers/poppies#0", 0));
        assertEquals(List.of("minecraft:red_tulip"), blocksOf("snippet:flowers/tulips#1", 0));
        assertEquals("a file that can't be read places nothing", List.of(), blocksOf("snippet:flowers/broken#0", 0));
        assertTrue(view.pattern(world, "snippet:flowers/none#0", 0, area).isEmpty());
        assertTrue(view.pattern(world, "snippet:flowers/tulips#2", 0, area).isEmpty());
        assertTrue(view.pattern(world, "pack:meadow#7", 0, area).isEmpty());
    }

    private List<String> blocksOf(String key, int depth) {
        return view.pattern(world, key, depth, area).orElseThrow().blocks().stream().map(BlockState::key).toList();
    }

    private boolean placesEverywhere(String key, int depth) {
        BlockPattern pattern = view.pattern(world, key, depth, area).orElseThrow();
        for (int row = 0; row < area.columns(); row++) {
            for (int column = 0; column < area.columns(); column++) {
                if (pattern.pickAt(column, row).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
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
}

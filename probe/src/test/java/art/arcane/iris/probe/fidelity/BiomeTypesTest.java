package art.arcane.iris.probe.fidelity;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class BiomeTypesTest {
    @Test
    public void childrenInheritTheListTypeOfTheirParent() {
        IrisRegion region = region(List.of("plains"), List.of("ocean"), List.of("beach"));
        Map<String, IrisBiome> biomes = Map.of(
                "plains", biome("meadow"),
                "meadow", biome("plains"),
                "ocean", biome("reef"),
                "reef", biome(),
                "beach", biome());

        Map<String, String> types = BiomeTypes.of(List.of(region), biomes::get);

        assertEquals(Map.of("plains", "land", "meadow", "land", "ocean", "sea", "reef", "sea", "beach", "shore"), types);
    }

    @Test
    public void biomesListedUnderSeveralTypesJoinThem() {
        IrisRegion first = region(List.of("plains"), List.of(), List.of());
        IrisRegion second = region(List.of(), List.of("plains"), List.of("missing"));

        Map<String, String> types = BiomeTypes.of(List.of(first, second), key -> key.equals("plains") ? biome() : null);

        assertEquals(Map.of("plains", "land+sea", "missing", "shore"), types);
    }

    private static IrisRegion region(List<String> land, List<String> sea, List<String> shore) {
        return new IrisRegion().setLandBiomes(new KList<>(land)).setSeaBiomes(new KList<>(sea)).setShoreBiomes(new KList<>(shore));
    }

    private static IrisBiome biome(String... children) {
        return new IrisBiome().setChildren(new KList<>(List.of(children)));
    }
}

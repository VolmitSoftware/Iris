package art.arcane.iris.pack.datapack.export.compile.decoration;

import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BiomeFeatureListsTest {
    private static final String NAMESPACE = "iris_export";

    @Test
    public void globalOrderIsSharedAcrossBiomes() {
        BiomeFeatureLists lists = new BiomeFeatureLists();
        lists.add("iris_export:plains", GenerationStep.VEGETAL_DECORATION, "iris_export:grass");
        lists.add("iris_export:plains", GenerationStep.VEGETAL_DECORATION, "iris_export:flowers");
        lists.add("iris_export:forest", GenerationStep.VEGETAL_DECORATION, "iris_export:flowers");
        lists.add("iris_export:forest", GenerationStep.VEGETAL_DECORATION, "iris_export:grass");

        List<BiomeDef> biomes = lists.apply(List.of(biome("plains"), biome("forest")), NAMESPACE);

        List<String> expected = List.of("iris_export:grass", "iris_export:flowers");
        assertEquals(expected, step(biomes.get(0), GenerationStep.VEGETAL_DECORATION));
        assertEquals(expected, step(biomes.get(1), GenerationStep.VEGETAL_DECORATION));
    }

    @Test
    public void biomeListIsFilteredGlobalOrder() {
        BiomeFeatureLists lists = new BiomeFeatureLists();
        lists.add("iris_export:plains", GenerationStep.UNDERGROUND_ORES, "iris_export:coal");
        lists.add("iris_export:desert", GenerationStep.UNDERGROUND_ORES, "iris_export:gold");
        lists.add("iris_export:plains", GenerationStep.UNDERGROUND_ORES, "minecraft:ore_iron");
        lists.add("iris_export:desert", GenerationStep.UNDERGROUND_ORES, "minecraft:ore_iron");
        lists.add("iris_export:desert", GenerationStep.UNDERGROUND_ORES, "iris_export:coal");

        List<BiomeDef> biomes = lists.apply(List.of(biome("plains"), biome("desert")), NAMESPACE);

        assertEquals(List.of("iris_export:coal", "minecraft:ore_iron"), step(biomes.get(0), GenerationStep.UNDERGROUND_ORES));
        assertEquals(List.of("iris_export:coal", "iris_export:gold", "minecraft:ore_iron"), step(biomes.get(1), GenerationStep.UNDERGROUND_ORES));
        assertEquals(List.of(), step(biomes.get(0), GenerationStep.VEGETAL_DECORATION));
    }

    @Test
    public void duplicateAddIsNoOp() {
        BiomeFeatureLists lists = new BiomeFeatureLists();
        lists.add("iris_export:plains", GenerationStep.LAKES, "iris_export:pond");
        lists.add("iris_export:plains", GenerationStep.LAKES, "iris_export:pond");
        lists.add("iris_export:plains", GenerationStep.FLUID_SPRINGS, "iris_export:pond");

        BiomeDef biome = lists.apply(List.of(biome("plains")), NAMESPACE).getFirst();

        assertEquals(List.of("iris_export:pond"), step(biome, GenerationStep.LAKES));
        assertEquals(List.of("iris_export:pond"), step(biome, GenerationStep.FLUID_SPRINGS));
    }

    @Test
    public void elevenStepArraysAlwaysWritten() {
        BiomeFeatureLists lists = new BiomeFeatureLists();
        lists.add("iris_export:plains", GenerationStep.TOP_LAYER_MODIFICATION, "minecraft:freeze_top_layer");
        BiomeDef source = biome("desert");

        List<BiomeDef> biomes = lists.apply(List.of(biome("plains"), source), NAMESPACE);

        assertEquals(11, GenerationStep.values().length);
        assertEquals(GenerationStep.TOP_LAYER_MODIFICATION, GenerationStep.values()[10]);
        JsonArray empty = new JsonArray();
        for (int index = 0; index < 11; index++) {
            empty.add(new JsonArray());
        }
        assertEquals(empty, biomes.get(1).json().get("features"));
        assertEquals(11, biomes.get(0).json().getAsJsonArray("features").size());
        assertEquals(List.of("minecraft:freeze_top_layer"), step(biomes.get(0), GenerationStep.TOP_LAYER_MODIFICATION));
        assertEquals(0.8, biomes.get(1).json().get("temperature").getAsDouble(), 0.0);
        assertEquals(0, source.json().getAsJsonArray("features").size());
    }

    @Test
    public void featureForUndefinedBiomeIsRejected() {
        BiomeFeatureLists lists = new BiomeFeatureLists();
        lists.add("iris_export:swamp", GenerationStep.LAKES, "iris_export:pond");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> lists.apply(List.of(biome("plains")), NAMESPACE));

        assertEquals("Feature lists reference undefined biome 'iris_export:swamp'", error.getMessage());
    }

    private static BiomeDef biome(String id) {
        JsonObject json = JsonParser.parseString("{\"temperature\":0.8,\"features\":[]}").getAsJsonObject();
        return new BiomeDef(id, json);
    }

    private static List<String> step(BiomeDef biome, GenerationStep step) {
        JsonArray features = biome.json().getAsJsonArray("features").get(step.ordinal()).getAsJsonArray();
        List<String> ids = new ArrayList<>(features.size());
        for (int index = 0; index < features.size(); index++) {
            ids.add(features.get(index).getAsString());
        }
        return ids;
    }
}

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.terrain.IrisDimensionTypeOptions;

import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisBiomeCustomCompatTest {
    private static final IDataFixer PASSTHROUGH = new IDataFixer() {
        @Override
        public JSONObject resolve(IDataFixer.Dimension dimension, art.arcane.iris.generation.terrain.IrisDimensionTypeOptions options) {
            return new JSONObject();
        }

        @Override
        public void fixDimension(IDataFixer.Dimension dimension, JSONObject json) {
        }
    };

    private static ContentGate gateWithEntities(String... present) {
        List<String> entities = new ArrayList<>();
        entities.add("minecraft:pig");
        entities.addAll(List.of(present));
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.blockKeys()).thenReturn(List.of("minecraft:stone"));
        when(registries.entityKeys()).thenReturn(entities);
        return new ContentGate(registries, Map.of(), new PackCompatReport());
    }

    private static IrisBiomeCustom customBiome() {
        IrisBiomeCustom biome = new IrisBiomeCustom();
        biome.setId("sulfur_grotto");
        biome.setSpawnRarity(1);
        biome.setSpawns(new KList<>(
                new IrisBiomeCustomSpawn().setType("minecraft:zombie"),
                new IrisBiomeCustomSpawn().setType("minecraft:camel")));
        return biome;
    }

    @Test
    public void generateJsonOmitsSpawnWhoseEntityTypeIsMissing() {
        JSONObject json = new JSONObject(customBiome().generateJson(PASSTHROUGH, gateWithEntities("minecraft:zombie")));
        JSONArray monsters = json.getJSONObject("spawners").getJSONArray("misc");

        assertEquals(1, monsters.length());
        assertEquals("minecraft:zombie", monsters.getJSONObject(0).getString("type"));
        assertFalse(json.getJSONObject("spawners").toString().contains("minecraft:camel"));
    }

    @Test
    public void generateJsonKeepsEverySpawnWhenAllEntitiesArePresent() {
        JSONObject json = new JSONObject(customBiome()
                .generateJson(PASSTHROUGH, gateWithEntities("minecraft:zombie", "minecraft:camel")));

        assertEquals(2, json.getJSONObject("spawners").getJSONArray("misc").length());
    }

    @Test
    public void generateJsonKeepsEverySpawnWhenRegistryCannotBeConsulted() {
        // Registry not ready (modded early boot): UNKNOWN must never behave like MISSING.
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.blockKeys()).thenReturn(List.of());
        when(registries.entityKeys()).thenReturn(List.of());
        ContentGate gate = new ContentGate(registries, Map.of(), new PackCompatReport());

        JSONObject json = new JSONObject(customBiome().generateJson(PASSTHROUGH, gate));

        assertEquals(2, json.getJSONObject("spawners").getJSONArray("misc").length());
    }

    @Test
    public void generateJsonWritesNoSpawnersWhenEveryEntityIsMissing() {
        JSONObject json = new JSONObject(customBiome().generateJson(PASSTHROUGH, gateWithEntities()));

        assertEquals(0, json.getJSONObject("spawners").length());
    }
}

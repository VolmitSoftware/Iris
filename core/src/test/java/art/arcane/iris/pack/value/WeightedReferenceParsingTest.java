package art.arcane.iris.pack.value;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeEntry;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.IrisRegionEntry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WeightedReferenceParsingTest {
    private final Gson gson = new Gson();

    @Test
    public void aPlainStringParsesAsAnEntryThatWasNotWrittenAsAnObject() {
        IrisRegion region = gson.fromJson("{\"landBiomes\":[\"plains\"]}", IrisRegion.class);

        IrisBiomeEntry entry = region.getLandBiomes().getFirst();
        assertEquals("plains", entry.getBiome());
        assertEquals(1, entry.getWeight());
        assertFalse(entry.isWrittenAsObject());
    }

    @Test
    public void anObjectParsesAsAnEntryWithItsWeight() {
        IrisRegion region = gson.fromJson("{\"seaBiomes\":[{\"biome\":\"reef\",\"weight\":4}]}", IrisRegion.class);

        IrisBiomeEntry entry = region.getSeaBiomes().getFirst();
        assertEquals("reef", entry.getBiome());
        assertEquals(4, entry.getWeight());
        assertTrue(entry.isWrittenAsObject());
    }

    @Test
    public void anObjectWithoutAWeightGetsWeightOne() {
        IrisRegion region = gson.fromJson("{\"shoreBiomes\":[{\"biome\":\"beach\"}]}", IrisRegion.class);

        IrisBiomeEntry entry = region.getShoreBiomes().getFirst();
        assertEquals(1, entry.getWeight());
        assertTrue(entry.isWrittenAsObject());
    }

    @Test
    public void aMixedListKeepsOrderAndMakesTheListWeighted() {
        IrisRegion region = gson.fromJson("{\"landBiomes\":[\"plains\",{\"biome\":\"forest\",\"weight\":3},\"hills\"]}",
                IrisRegion.class);

        assertEquals(List.of("plains", "forest", "hills"), region.getLandBiomeKeys());
        assertEquals(List.of(1, 3, 1), region.getLandBiomes().stream().map(IrisBiomeEntry::getWeight).toList());
        assertTrue(region.hasWeightedBiomes(InferredType.LAND));
    }

    @Test
    public void aListOfOnlyStringsIsNotWeighted() {
        IrisRegion region = gson.fromJson("{\"landBiomes\":[\"plains\",\"hills\"]}", IrisRegion.class);

        assertFalse(region.hasWeightedBiomes(InferredType.LAND));
        assertFalse(region.hasWeightedBiomes(InferredType.CAVE));
    }

    @Test
    public void dimensionRegionsAndBiomeChildrenAcceptBothForms() {
        IrisDimension dimension = gson.fromJson("{\"regions\":[\"a\",{\"region\":\"b\",\"weight\":2}]}", IrisDimension.class);
        IrisBiome biome = gson.fromJson("{\"children\":[{\"biome\":\"c\",\"weight\":5},\"d\"]}", IrisBiome.class);

        assertEquals(List.of("a", "b"), dimension.getRegionKeys());
        assertEquals(2, dimension.getRegions().get(1).getWeight());
        assertTrue(dimension.hasWeightedRegions());
        assertEquals(List.of("c", "d"), biome.getChildKeys());
        assertEquals(5, biome.getChildren().getFirst().getWeight());
        assertTrue(biome.hasWeightedChildren());
    }

    @Test
    public void stringOnlyListsWriteBackAsPlainStrings() {
        String source = "{\"regions\":[\"a\",\"b\"]}";
        IrisDimension dimension = gson.fromJson(source, IrisDimension.class);

        JsonArray written = JsonParser.parseString(gson.toJson(dimension)).getAsJsonObject().getAsJsonArray("regions");

        assertEquals(JsonParser.parseString(source).getAsJsonObject().getAsJsonArray("regions"), written);
        assertTrue(written.get(0).isJsonPrimitive());
    }

    @Test
    public void objectFormEntriesWriteBackAsObjectsAndStringsStayStrings() {
        IrisRegion region = gson.fromJson("{\"landBiomes\":[\"plains\",{\"biome\":\"forest\",\"weight\":3}]}", IrisRegion.class);

        JsonArray written = JsonParser.parseString(gson.toJson(region)).getAsJsonObject().getAsJsonArray("landBiomes");

        assertEquals("plains", written.get(0).getAsString());
        JsonObject object = written.get(1).getAsJsonObject();
        assertEquals("forest", object.get("biome").getAsString());
        assertEquals(3, object.get("weight").getAsInt());
    }

    @Test
    public void theObjectFormFlagSurvivesARoundTrip() {
        IrisRegion region = gson.fromJson("{\"landBiomes\":[\"plains\",{\"biome\":\"forest\"}]}", IrisRegion.class);

        IrisRegion restored = gson.fromJson(gson.toJson(region), IrisRegion.class);

        assertFalse(restored.getLandBiomes().get(0).isWrittenAsObject());
        assertTrue(restored.getLandBiomes().get(1).isWrittenAsObject());
        assertEquals(region, restored);
    }

    @Test
    public void theFlagIsNotWrittenAsAField() {
        IrisRegionEntry entry = new IrisRegionEntry("a", 2, true);

        JsonElement written = JsonParser.parseString(new Gson().toJson(entry));

        assertFalse(written.getAsJsonObject().has("writtenAsObject"));
    }

    @Test
    public void aWeightOutsideTheAllowedRangeIsRejectedWhereItIsWritten() {
        assertThrows(JsonParseException.class,
                () -> gson.fromJson("{\"landBiomes\":[{\"biome\":\"a\",\"weight\":0}]}", IrisRegion.class));
        assertThrows(JsonParseException.class,
                () -> gson.fromJson("{\"landBiomes\":[{\"biome\":\"a\",\"weight\":1001}]}", IrisRegion.class));
    }

    @Test
    public void anObjectWithoutItsKeyFieldIsRejectedNamingTheFieldAndPath() {
        JsonParseException biomeError = assertThrows(JsonParseException.class,
                () -> gson.fromJson("{\"landBiomes\":[{\"weight\":2}]}", IrisRegion.class));
        JsonParseException regionError = assertThrows(JsonParseException.class,
                () -> gson.fromJson("{\"regions\":[{\"weight\":2}]}", IrisDimension.class));

        assertTrue(biomeError.getMessage(), biomeError.getMessage().contains("\"biome\"") && biomeError.getMessage().contains("$.landBiomes"));
        assertTrue(regionError.getMessage(), regionError.getMessage().contains("\"region\"") && regionError.getMessage().contains("$.regions"));
    }

    @Test
    public void anEntryThatIsNeitherAStringNorAnObjectIsRejected() {
        assertThrows(JsonParseException.class,
                () -> gson.fromJson("{\"landBiomes\":[12]}", IrisRegion.class));
    }
}

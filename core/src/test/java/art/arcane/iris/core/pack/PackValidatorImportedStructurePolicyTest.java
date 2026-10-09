package art.arcane.iris.core.pack;

import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackValidatorImportedStructurePolicyTest {
    @Test
    public void denyOnlyPolicyAcceptsDisabledKeysAndAdjustments() {
        JSONObject policy = new JSONObject()
                .put("disabled", new JSONArray().put("minecraft:stronghold"))
                .put("disabledExact", new JSONArray().put("minecraft:ruined_portal"))
                .put("frequencyOverrides", new JSONArray().put(new JSONObject()
                        .put("structureSet", "minecraft:nether_complexes")
                        .put("multiplier", 1.1D)))
                .put("adjustments", new JSONArray().put(new JSONObject()
                        .put("match", new JSONArray().put("minecraft:village"))));
        List<String> errors = validate(policy);

        assertTrue(errors.isEmpty());
    }

    @Test
    public void malformedFrequencyOverridesAreRejected() {
        JSONObject policy = new JSONObject()
                .put("frequencyOverrides", new JSONArray()
                        .put("minecraft:nether_complexes")
                        .put(new JSONObject().put("structureSet", "nether_complexes"))
                        .put(new JSONObject()
                                .put("structureSet", "minecraft:ruined_portals")
                                .put("multiplier", 0D))
                        .put(new JSONObject()
                                .put("structureSet", "minecraft:nether_fossils")
                                .put("multiplier", "often")));
        List<String> errors = validate(policy);

        assertEquals(4, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.contains("frequencyOverrides[0] must be an object")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("frequencyOverrides[1].structureSet")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("frequencyOverrides[2].multiplier must be at least")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("frequencyOverrides[3].multiplier must be a number")));
    }

    @Test
    public void encaseTerrainAndYBandAdjustmentsAreAccepted() {
        JSONObject policy = new JSONObject()
                .put("adjustments", new JSONArray().put(new JSONObject()
                        .put("match", new JSONArray().put("minecraft:stronghold"))
                        .put("yBand", new JSONObject().put("min", -120).put("max", -20))
                        .put("terrain", new JSONObject()
                                .put("mode", "ENCASE")
                                .put("horizontalPadding", 4)
                                .put("ceilingPadding", 4)
                                .put("floorPadding", 4)
                                .put("encasePalette", new JSONObject()
                                        .put("palette", new JSONArray()
                                                .put(new JSONObject()
                                                        .put("block", "minecraft:stone_bricks")
                                                        .put("weight", 6))
                                                .put(new JSONObject()
                                                        .put("block", "minecraft:cobblestone")
                                                        .put("weight", 1)))))));
        List<String> errors = validate(policy);

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void nonObjectEncasePaletteIsRejected() {
        JSONObject policy = new JSONObject()
                .put("adjustments", new JSONArray().put(new JSONObject()
                        .put("match", new JSONArray().put("minecraft:stronghold"))
                        .put("terrain", new JSONObject()
                                .put("mode", "ENCASE")
                                .put("encasePalette", "minecraft:stone_bricks"))));
        List<String> errors = validate(policy);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0)
                .contains("adjustments[0].terrain.encasePalette must be an object"));
    }

    @Test
    public void malformedYBandAndTerrainAdjustmentsAreRejected() {
        JSONObject policy = new JSONObject()
                .put("adjustments", new JSONArray()
                        .put(new JSONObject()
                                .put("match", new JSONArray().put("minecraft:stronghold"))
                                .put("yBand", -120))
                        .put(new JSONObject()
                                .put("match", new JSONArray().put("minecraft:trial_chambers"))
                                .put("yBand", new JSONObject().put("min", -9000))
                                .put("terrain", new JSONObject().put("mode", "SOLIDIFY"))));
        List<String> errors = validate(policy);

        assertEquals(3, errors.size());
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("adjustments[0].yBand must be an object")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("adjustments[1].yBand.min")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("adjustments[1].terrain.mode")));
    }

    @Test
    public void legacyModeAndEnabledWhitelistAreRejected() {
        JSONObject policy = new JSONObject()
                .put("mode", "ALL_OFF")
                .put("enabled", new JSONArray().put("minecraft:monument"));
        List<String> errors = validate(policy);

        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("importedStructures.mode is not supported"));
        assertTrue(errors.get(1).contains("importedStructures.enabled is not supported"));
    }

    @Test
    public void explicitNullsAndWrongShapesAreRejected() {
        JSONObject policy = new JSONObject()
                .put("disabled", JSONObject.NULL)
                .put("disabledExact", new JSONObject())
                .put("frequencyOverrides", JSONObject.NULL)
                .put("adjustments", new JSONObject());
        List<String> errors = validate(policy);

        assertEquals(4, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.contains("'disabled' must be an array")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("'disabledExact' must be an array")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("frequencyOverrides must be an array")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("adjustments must be an array")));
    }

    @Test
    public void blankOrNonStringExactKeysAreRejected() {
        JSONObject policy = new JSONObject()
                .put("disabledExact", new JSONArray().put(" ").put(4));
        List<String> errors = validate(policy);

        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("'disabledExact' has a blank or non-string entry at index 0"));
        assertTrue(errors.get(1).contains("'disabledExact' has a blank or non-string entry at index 1"));
    }

    @Test
    public void explicitNullPolicyIsRejectedWhileOmissionUsesDefaults() {
        List<String> missingErrors = new ArrayList<>();
        PackDimensionValidator.validateImportedStructurePolicy("overworld", new JSONObject(),
                missingErrors, new ArrayList<>());
        assertTrue(missingErrors.isEmpty());

        List<String> nullErrors = new ArrayList<>();
        PackDimensionValidator.validateImportedStructurePolicy("overworld",
                new JSONObject().put("importedStructures", JSONObject.NULL), nullErrors, new ArrayList<>());
        assertEquals(List.of("Dimension 'overworld' importedStructures must be an object."), nullErrors);
    }

    @Test
    public void removedClearVegetationAdjustmentWarnsWithoutBlockingThePack() {
        JSONObject policy = new JSONObject()
                .put("adjustments", new JSONArray().put(new JSONObject()
                        .put("match", new JSONArray().put("minecraft:village"))
                        .put("clearVegetation", true)));
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        PackDimensionValidator.validateImportedStructurePolicy("overworld",
                new JSONObject().put("importedStructures", policy), errors, warnings);

        assertTrue(errors.toString(), errors.isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0)
                .contains("adjustments[0].clearVegetation was removed and is ignored"));
    }

    private List<String> validate(JSONObject policy) {
        List<String> errors = new ArrayList<>();
        PackDimensionValidator.validateImportedStructurePolicy("overworld",
                new JSONObject().put("importedStructures", policy), errors, new ArrayList<>());
        return errors;
    }
}

package art.arcane.iris.engine.loot;

import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.loot.LootSettings;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.object.TestSettings;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.MaxNumber;
import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.validation.Problem;
import com.volmit.validation.Severity;
import com.volmit.validation.ValidationContext;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LootValidatorTest {
    private ModuleResourceFixture tables;
    private PackResources resources;
    private final List<Entry<LootSettings>> references = new ArrayList<>();

    @Before
    public void servePack() {
        IrisData data = mock(IrisData.class);
        tables = ModuleResourceFixture.serve(data);
        resources = data.getPackResources();
    }

    @Test
    public void acceptsValidLootTableAndReference() {
        table("global/clutter", "{\"name\":\"Clutter\",\"minPicked\":1,\"maxPicked\":2,"
                + "\"loot\":[{\"type\":\"stick\",\"rarity\":2,\"minAmount\":1,\"maxAmount\":3}]}");
        reference(Target.DIMENSION, "main", "{\"mode\":\"FALLBACK\",\"multiplier\":0.5,\"tables\":[\"global/clutter\"]}");

        assertTrue(errors().isEmpty());
    }

    @Test
    public void rejectsMissingLootReference() {
        reference(Target.DIMENSION, "main", "{\"tables\":[\"missing\"]}");

        assertEquals(List.of("Dimension 'main'.loot.tables[0] references missing loot table 'missing'."), errors());
    }

    @Test
    public void rejectsInvalidLootBoundsAndEmptyEntries() {
        table("broken", "{\"rarity\":0,\"minPicked\":4,\"maxPicked\":2,\"maxTries\":0,"
                + "\"loot\":[{\"type\":\"\",\"rarity\":0,\"minAmount\":3,\"maxAmount\":1,"
                + "\"enchantments\":[{\"enchantment\":\"\",\"minLevel\":4,\"maxLevel\":2,\"chance\":2}]}]}");

        List<String> errors = errors();

        assertTrue(errors.stream().anyMatch(error -> error.contains(".rarity must be at least 1")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".minPicked must not exceed")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".maxTries must be at least 1")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".type must not be blank")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".minAmount must not exceed")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".minLevel must not exceed")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".chance must be a finite number from 0 to 1")));
    }

    @Test
    public void aTableWithoutEntriesIsRejected() {
        table("empty", "{\"name\":\"Empty\"}");

        assertEquals(List.of("Loot table 'empty'.loot must be a non-empty array."), errors());
    }

    @Test
    public void rejectsInvalidReferenceMultiplier() {
        reference(Target.DIMENSION, "main", "{\"multiplier\":-1,\"tables\":[]}");

        assertEquals(List.of("Dimension 'main'.loot.multiplier must be a finite number from 0 to 16."), errors());
    }

    @Test
    public void enforcesPublishedLootMultiplierCap() {
        reference(Target.DIMENSION, "accepted", "{\"multiplier\":16,\"tables\":[]}");
        assertTrue(errors().isEmpty());

        reference(Target.DIMENSION, "rejected", "{\"multiplier\":16.01,\"tables\":[]}");
        assertEquals(List.of("Dimension 'rejected'.loot.multiplier must be a finite number from 0 to 16."), errors());
    }

    @Test
    public void acceptsPublishedLootWorkBounds() {
        table("boundary", "{\"minPicked\":64,\"maxPicked\":64,\"maxTries\":256,"
                + "\"loot\":[{\"type\":\"stone\",\"minAmount\":64,\"maxAmount\":64}]}");

        assertTrue(errors().isEmpty());
    }

    @Test
    public void rejectsLootWorkBoundsAbovePublishedCaps() {
        table("excessive", "{\"minPicked\":65,\"maxPicked\":65,\"maxTries\":257,"
                + "\"loot\":[{\"type\":\"stone\",\"minAmount\":65,\"maxAmount\":65}]}");

        List<String> errors = errors();

        assertTrue(errors.contains("Loot table 'excessive'.minPicked must be at most 64."));
        assertTrue(errors.contains("Loot table 'excessive'.maxPicked must be at most 64."));
        assertTrue(errors.contains("Loot table 'excessive'.maxTries must be at most 256."));
        assertTrue(errors.contains("Loot table 'excessive'.loot[0].minAmount must be at most 64."));
        assertTrue(errors.contains("Loot table 'excessive'.loot[0].maxAmount must be at most 64."));
    }

    @Test
    public void lootWorkCapsArePublishedInSchemaMetadata() throws Exception {
        assertSchemaMaximum(LootTable.class, "minPicked", LootTable.MAX_PICKED);
        assertSchemaMaximum(LootTable.class, "maxPicked", LootTable.MAX_PICKED);
        assertSchemaMaximum(LootTable.class, "maxTries", LootTable.MAX_TRIES);
        assertSchemaMaximum(LootEntry.class, "minAmount", LootEntry.MAX_AMOUNT);
        assertSchemaMaximum(LootEntry.class, "maxAmount", LootEntry.MAX_AMOUNT);
    }

    private void assertSchemaMaximum(Class<?> type, String fieldName, int expected) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        MaxNumber maximum = field.getAnnotation(MaxNumber.class);
        assertEquals(expected, maximum.value(), 0D);
    }

    @Test
    public void clearModeWithTablesWarnsWithoutBlocking() {
        table("t", "{\"loot\":[{\"type\":\"minecraft:stone\"}]}");
        reference(Target.BIOME, "x", "{\"mode\":\"CLEAR\",\"tables\":[\"t\"]}");

        assertTrue(errors().isEmpty());
        assertEquals(List.of("Biome 'x'.loot uses mode CLEAR and lists 1 table(s); CLEAR clears parent tables"
                + " and contributes no tables of its own, so these entries are dead. Use REPLACE to substitute them."),
                warnings());
    }

    @Test
    public void clearModeWithMissingTableStillBlocksOnTheDanglingKey() {
        reference(Target.BIOME, "x", "{\"mode\":\"CLEAR\",\"tables\":[\"missing\"]}");

        assertTrue(errors().contains("Biome 'x'.loot.tables[0] references missing loot table 'missing'."));
    }

    @Test
    public void aTableThatFailsToLoadIsReportedWithoutStoppingTheCheck() {
        table("fine", "{\"loot\":[{\"type\":\"stone\"}]}");
        table("broken", "{\"loot\":[{\"type\":\"stone\"}]}");
        when(resources.find(LootResources.TABLES.key(new ResourceId("test", "broken")))).thenThrow(new IllegalStateException("Expected an int but was STRING at line 1 column 12 path $.rarity"));

        assertEquals(List.of("Loot table 'broken' cannot be read: Expected an int but was STRING at line 1 column 12 path $.rarity"), errors());
    }

    @Test
    public void referencesAreFoundByTheIdThePackGivesTheirKey() {
        table("Global/Clutter", "{\"loot\":[{\"type\":\"stick\"}]}");
        reference(Target.REGION, "plains", "{\"tables\":[\"Global/Clutter\"]}");

        assertTrue(errors().isEmpty());
    }

    private void table(String key, String json) {
        tables.resource(LootResources.TABLES, key, TestSettings.of(LootTable.class, json));
    }

    private void reference(Target level, String key, String json) {
        String folder = switch (level) {
            case DIMENSION -> "dimension";
            case REGION -> "region";
            default -> "biome";
        };
        references.add(new Entry<>(level, new ResourceId("test", folder + "/" + key), TestSettings.of(LootSettings.class, json)));
    }

    private List<String> errors() {
        return messages(Severity.ERROR);
    }

    private List<String> warnings() {
        return messages(Severity.WARNING);
    }

    private List<String> messages(Severity severity) {
        List<Problem> problems = new ArrayList<>();
        new LootValidator().validate(context(problems));
        return problems.stream().filter(problem -> problem.severity() == severity).map(Problem::message).toList();
    }

    private ValidationContext context(List<Problem> problems) {
        return new ValidationContext() {
            @Override
            public PackResources resources() {
                return resources;
            }

            @Override
            public PackSettings settings() {
                return new PackSettings() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <F extends IrisFeature> List<Entry<F>> declared(Class<F> featureType) {
                        return featureType == LootSettings.class ? (List<Entry<F>>) (List<?>) List.copyOf(references) : List.of();
                    }
                };
            }

            @Override
            public void report(Problem problem) {
                problems.add(problem);
            }
        };
    }
}

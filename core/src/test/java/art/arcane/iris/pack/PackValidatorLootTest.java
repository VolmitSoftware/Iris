package art.arcane.iris.pack;

import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackValidatorLootTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsValidLootTableAndReference() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "loot/global/clutter.json", "{\"name\":\"Clutter\",\"minPicked\":1,\"maxPicked\":2,"
                + "\"loot\":[{\"type\":\"stick\",\"rarity\":2,\"minAmount\":1,\"maxAmount\":3}]}");
        write(pack, "dimensions/main.json", "{\"loot\":{\"mode\":\"FALLBACK\",\"multiplier\":0.5,"
                + "\"tables\":[\"global/clutter\"]}}");

        assertTrue(PackLootValidator.validateLootGraph(pack).errors().isEmpty());
    }

    @Test
    public void rejectsMissingLootReference() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"loot\":{\"tables\":[\"missing\"]}}");

        assertEquals(List.of("Dimension 'main'.loot.tables[0] references missing loot table 'missing'."),
                PackLootValidator.validateLootGraph(pack).errors());
    }

    @Test
    public void rejectsInvalidLootBoundsAndEmptyEntries() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "loot/broken.json", "{\"rarity\":0,\"minPicked\":4,\"maxPicked\":2,\"maxTries\":0,"
                + "\"loot\":[{\"type\":\"\",\"rarity\":0,\"minAmount\":3,\"maxAmount\":1,"
                + "\"enchantments\":[{\"enchantment\":\"\",\"minLevel\":4,\"maxLevel\":2,\"chance\":2}]}]}");

        List<String> errors = PackLootValidator.validateLootGraph(pack).errors();

        assertTrue(errors.stream().anyMatch(error -> error.contains(".rarity must be at least 1")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".minPicked must not exceed")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".maxTries must be at least 1")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".type must not be blank")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".minAmount must not exceed")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".minLevel must not exceed")));
        assertTrue(errors.stream().anyMatch(error -> error.contains(".chance must be a finite number from 0 to 1")));
    }

    @Test
    public void rejectsInvalidReferenceModeAndMultiplier() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"loot\":{\"mode\":\"MERGE\",\"multiplier\":-1,\"tables\":[]}}");

        List<String> errors = PackLootValidator.validateLootGraph(pack).errors();

        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains(".mode must be"));
        assertTrue(errors.get(1).contains(".multiplier must be"));
    }

    @Test
    public void enforcesPublishedLootMultiplierCap() throws Exception {
        File acceptedPack = temporaryFolder.newFolder("accepted-pack");
        write(acceptedPack, "dimensions/main.json", "{\"loot\":{\"multiplier\":16,\"tables\":[]}}");
        assertTrue(PackLootValidator.validateLootGraph(acceptedPack).errors().isEmpty());

        File rejectedPack = temporaryFolder.newFolder("rejected-pack");
        write(rejectedPack, "dimensions/main.json", "{\"loot\":{\"multiplier\":16.01,\"tables\":[]}}");
        assertEquals(List.of("Dimension 'main'.loot.multiplier must be a finite number from 0 to 16."),
                PackLootValidator.validateLootGraph(rejectedPack).errors());
    }

    @Test
    public void acceptsPublishedLootWorkBounds() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "loot/boundary.json", "{\"minPicked\":64,\"maxPicked\":64,\"maxTries\":256,"
                + "\"loot\":[{\"type\":\"stone\",\"minAmount\":64,\"maxAmount\":64}]}");

        assertTrue(PackLootValidator.validateLootGraph(pack).errors().isEmpty());
    }

    @Test
    public void rejectsLootWorkBoundsAbovePublishedCaps() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "loot/excessive.json", "{\"minPicked\":65,\"maxPicked\":65,\"maxTries\":257,"
                + "\"loot\":[{\"type\":\"stone\",\"minAmount\":65,\"maxAmount\":65}]}");

        List<String> errors = PackLootValidator.validateLootGraph(pack).errors();

        assertTrue(errors.contains("Loot table 'excessive'.minPicked must be at most 64."));
        assertTrue(errors.contains("Loot table 'excessive'.maxPicked must be at most 64."));
        assertTrue(errors.contains("Loot table 'excessive'.maxTries must be at most 256."));
        assertTrue(errors.contains("Loot table 'excessive'.loot[0].minAmount must be at most 64."));
        assertTrue(errors.contains("Loot table 'excessive'.loot[0].maxAmount must be at most 64."));
    }

    @Test
    public void lootWorkCapsArePublishedInSchemaMetadata() throws Exception {
        assertSchemaMaximum(IrisLootTable.class, "minPicked", IrisLootTable.MAX_PICKED);
        assertSchemaMaximum(IrisLootTable.class, "maxPicked", IrisLootTable.MAX_PICKED);
        assertSchemaMaximum(IrisLootTable.class, "maxTries", IrisLootTable.MAX_TRIES);
        assertSchemaMaximum(IrisLoot.class, "minAmount", IrisLoot.MAX_AMOUNT);
        assertSchemaMaximum(IrisLoot.class, "maxAmount", IrisLoot.MAX_AMOUNT);
    }

    private void assertSchemaMaximum(Class<?> type, String fieldName, int expected) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        MaxNumber maximum = field.getAnnotation(MaxNumber.class);
        assertEquals(expected, maximum.value(), 0D);
    }

    @Test
    public void clearModeWithTablesWarnsWithoutBlocking() throws Exception {
        File pack = temporaryFolder.newFolder("clear-warn");
        write(pack, "loot/t.json", "{\"loot\":[{\"type\":\"minecraft:stone\"}]}");
        write(pack, "biomes/x.json", "{\"loot\":{\"mode\":\"CLEAR\",\"tables\":[\"t\"]}}");

        PackLootValidator.LootGraphIssues issues = PackLootValidator.validateLootGraph(pack);

        assertTrue(issues.errors().isEmpty());
        assertEquals(List.of("Biome 'x'.loot uses mode CLEAR and lists 1 table(s); CLEAR clears parent tables"
                + " and contributes no tables of its own, so these entries are dead. Use REPLACE to substitute them."),
                issues.warnings());
    }

    @Test
    public void clearModeWithMissingTableStillBlocksOnTheDanglingKey() throws Exception {
        File pack = temporaryFolder.newFolder("clear-dangling");
        write(pack, "biomes/x.json", "{\"loot\":{\"mode\":\"CLEAR\",\"tables\":[\"missing\"]}}");

        PackLootValidator.LootGraphIssues issues = PackLootValidator.validateLootGraph(pack);

        assertTrue(issues.errors().contains(
                "Biome 'x'.loot.tables[0] references missing loot table 'missing'."));
    }

    private void write(File root, String relative, String content) throws Exception {
        File file = new File(root, relative);
        Files.createDirectories(file.toPath().getParent());
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }
}

package art.arcane.iris.pack;

import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackValidatorCompatTest {
    private static CompatFinding block(CompatAction action, String subjectKey) {
        return new CompatFinding(
                CompatRegistry.BLOCK, "minecraft:sulfur", action, "Biome", subjectKey, "layers[0].palette[1]");
    }

    private static CompatFinding entity(CompatAction action, String subjectKey) {
        return new CompatFinding(
                CompatRegistry.ENTITY, "minecraft:camel", action, "Spawner", subjectKey, "spawns[0]");
    }

    @Test
    public void excludedDimensionBecomesABlockingError() {
        List<String> blockingErrors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        PackValidator.applyCompatFindings(
                "26.1.2",
                true,
                List.of(block(CompatAction.EXCLUDED, "cave/sulfur"), entity(CompatAction.DROPPED, "desert")),
                blockingErrors,
                warnings);

        assertEquals(1, blockingErrors.size());
        assertEquals(
                "Pack cannot generate on Minecraft 26.1.2: minecraft:sulfur (block), minecraft:camel (entity)",
                blockingErrors.getFirst());
    }

    @Test
    public void unexcludedDimensionAddsNoBlockingError() {
        List<String> blockingErrors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        PackValidator.applyCompatFindings(
                "26.1.2", false, List.of(block(CompatAction.DROPPED, "cave/sulfur")), blockingErrors, warnings);

        assertTrue(blockingErrors.toString(), blockingErrors.isEmpty());
    }

    @Test
    public void excludedDimensionWithoutFindingsStillNamesTheVersion() {
        List<String> blockingErrors = new ArrayList<>();

        PackValidator.applyCompatFindings("26.1.2", true, List.of(), blockingErrors, new ArrayList<>());

        assertEquals(
                "Pack cannot generate on Minecraft 26.1.2: the dimension composes content this server does not have",
                blockingErrors.getFirst());
        assertTrue(PackValidator.excludedDimensionError(null, List.of()).contains("Minecraft unknown"));
    }

    @Test
    public void contentKeyWarningsForGatedKeysAreSuppressed() {
        List<String> warnings = new ArrayList<>(List.of(
                "Unknown block key 'minecraft:sulfur' (missing from the block registry)",
                "Unknown entity key 'minecraft:camel' (missing from the entity registry)",
                "Unknown block key 'minecraft:cheese' (missing from the block registry)",
                "Duplicate generator 'overworld'"));

        PackValidator.applyCompatFindings(
                "26.1.2",
                false,
                List.of(block(CompatAction.EXCLUDED, "cave/sulfur"), entity(CompatAction.DROPPED, "desert")),
                new ArrayList<>(),
                warnings);

        assertEquals(List.of(
                "Unknown block key 'minecraft:cheese' (missing from the block registry)",
                "Duplicate generator 'overworld'"), warnings);
    }

    @Test
    public void bootLinesCapSubjectsPerKeyAndSummaryCountsActions() {
        PackValidationResult result = new PackValidationResult(
                "overworld",
                List.of(),
                List.of(),
                1L,
                List.of(
                        block(CompatAction.EXCLUDED, "cave/sulfur-grotto"),
                        block(CompatAction.EXCLUDED, "desert/sulfur-flats"),
                        block(CompatAction.DROPPED, "cave/sulfur-grotto"),
                        block(CompatAction.SUBSTITUTED, "desert/dunes"),
                        entity(CompatAction.EXCLUDED, "camel")),
                "26.1.2");

        List<String> lines = PackValidator.compatBootLines(result, null, 3);

        assertEquals(4, lines.size());
        assertEquals("Pack 'overworld': content unavailable on Minecraft 26.1.2", lines.get(0));
        assertTrue(lines.get(1), lines.get(1).startsWith("  minecraft:sulfur (block): "));
        assertTrue(lines.get(1), lines.get(1).endsWith("; +1 more"));
        assertEquals(3, lines.get(1).split("; ").length - 1);
        assertTrue(lines.get(2), lines.get(2).startsWith("  minecraft:camel (entity): "));
        assertFalse(lines.get(2), lines.get(2).contains("more"));
        assertTrue(lines.get(3), lines.get(3).contains("/iris pack compat overworld"));

        assertEquals(
                "2 content keys unavailable on Minecraft 26.1.2: 3 excluded, 1 dropped, 1 substituted.",
                PackValidator.compatSummary(result, null));
        assertEquals(
                "2 content keys unavailable on Minecraft 26.2: 3 excluded, 1 dropped, 1 substituted.",
                PackValidator.compatSummary(result, "26.2"));
    }

    @Test
    public void bootLinesAndSummaryAreEmptyWithoutFindings() {
        PackValidationResult result = new PackValidationResult("overworld", List.of(), List.of(), 1L);

        assertTrue(PackValidator.compatBootLines(result, "26.1.2", 3).isEmpty());
        assertEquals("", PackValidator.compatSummary(result, "26.1.2"));
        assertTrue(PackValidator.compatBootLines(null, "26.1.2", 3).isEmpty());
        assertEquals("", PackValidator.compatSummary(null, "26.1.2"));
    }
}

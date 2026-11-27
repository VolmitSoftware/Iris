package art.arcane.iris.pack;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackValidatorMissingContentFailureTest {
    private static final String PACK = "overworld";
    private static final List<String> FAILING_DIMENSION = List.of("overworld");

    private static CompatFinding block(CompatAction action, String subjectKey) {
        return new CompatFinding(
                CompatRegistry.BLOCK, "minecraft:sulfur", action, "Biome", subjectKey, "layers[0].palette[1]");
    }

    private static CompatFinding entity(CompatAction action, String subjectKey) {
        return new CompatFinding(
                CompatRegistry.ENTITY, "minecraft:camel", action, "Spawner", subjectKey, "spawns[0]");
    }

    private static List<String> blockingErrors(List<String> failingDimensions, List<CompatFinding> findings) {
        List<String> blockingErrors = new ArrayList<>();
        PackValidator.applyFailOnMissingContent(PACK, failingDimensions, "26.1.2", findings, blockingErrors);
        return blockingErrors;
    }

    @Test
    public void failingIsOffByDefaultAndMissingContentStaysNonBlocking() {
        assertFalse(new IrisDimension().isFailOnMissingContent());

        List<String> errors = blockingErrors(List.of(),
                List.of(block(CompatAction.EXCLUDED, "cave/sulfur"), entity(CompatAction.DROPPED, "desert")));

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void droppedEntryFailsTheDimension() {
        List<String> errors = blockingErrors(FAILING_DIMENSION, List.of(entity(CompatAction.DROPPED, "desert")));

        assertEquals(List.of("Dimension 'overworld' in pack 'overworld' uses failOnMissingContent and content is unavailable"
                + " on Minecraft 26.1.2: minecraft:camel (entity): dropped Spawner desert at spawns[0]"), errors);
    }

    @Test
    public void excludedRegistrantFailsTheDimension() {
        List<String> errors = blockingErrors(FAILING_DIMENSION, List.of(
                block(CompatAction.EXCLUDED, "cave/sulfur"), entity(CompatAction.DROPPED, "desert")));

        assertEquals(1, errors.size());
        assertTrue(errors.getFirst(), errors.getFirst().endsWith(
                ": minecraft:sulfur (block): excluded Biome cave/sulfur at layers[0].palette[1]"
                        + " | minecraft:camel (entity): dropped Spawner desert at spawns[0]"));
    }

    @Test
    public void substitutionsAloneKeepTheDimensionLoadable() {
        List<String> errors = blockingErrors(FAILING_DIMENSION, List.of(block(CompatAction.SUBSTITUTED, "desert/dunes")));

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void dimensionWithNothingMissingIsLoadable() {
        assertTrue(blockingErrors(FAILING_DIMENSION, List.of()).isEmpty());
    }

    @Test
    public void substitutionsAreLeftOutAndSubjectsAreCappedPerKey() {
        List<String> errors = blockingErrors(FAILING_DIMENSION, List.of(
                block(CompatAction.EXCLUDED, "a"),
                block(CompatAction.EXCLUDED, "b"),
                block(CompatAction.DROPPED, "c"),
                block(CompatAction.DROPPED, "d"),
                block(CompatAction.SUBSTITUTED, "e")));

        assertEquals(1, errors.size());
        assertTrue(errors.getFirst(), errors.getFirst().endsWith("; +1 more"));
        assertFalse(errors.getFirst(), errors.getFirst().contains("substituted"));
    }
}

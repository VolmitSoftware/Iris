package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationHistory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class StudioPackAuditTest {
    private static final String ACTIVE_EPOCH = "a".repeat(64);
    private static final String SUPERSEDED_EPOCH = "b".repeat(64);

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void blockingErrorsOfTheActiveEpochAreReported() throws Exception {
        List<String> reports = new ArrayList<>();

        audit(ACTIVE_EPOCH, false, reports).report(invalidAnchorValidation());

        assertEquals(1, reports.size());
        assertTrue(reports.getFirst(), reports.getFirst().startsWith("Pack validation: "));
        assertTrue(reports.getFirst(), reports.getFirst().contains(
                "Dimension 'test' structures[0].anchor must be omitted, null, or LEGACY for nativeStructures."));
    }

    @Test
    public void supersededEpochsAndClosedEnginesStayQuiet() throws Exception {
        List<String> reports = new ArrayList<>();
        PackValidationResult invalid = invalidAnchorValidation();

        audit(SUPERSEDED_EPOCH, false, reports).report(invalid);
        audit(ACTIVE_EPOCH, true, reports).report(invalid);

        assertTrue(reports.isEmpty());
    }

    private StudioPackAudit audit(String epochId, boolean engineClosed, List<String> reports) {
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.isClosed()).thenReturn(engineClosed);
        when(engine.getPackSource()).thenReturn(temporary.getRoot().toPath().resolve("test-pack"));
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationEpoch active = mock(GenerationEpoch.class);
        when(history.activeEpoch()).thenReturn(active);
        when(active.epochId()).thenReturn(ACTIVE_EPOCH);
        return new StudioPackAudit(engine, history, epochId, reports::add);
    }

    private PackValidationResult invalidAnchorValidation() throws Exception {
        Path pack = temporary.newFolder().toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.writeString(pack.resolve("dimensions/test.json"), """
                {"structures":[{"placementId":"village","anchor":"SURFACE",
                  "nativeStructures":[{"structure":"minecraft:village_plains"}]}]}
                """);
        return PackValidator.validate(pack.toFile());
    }
}

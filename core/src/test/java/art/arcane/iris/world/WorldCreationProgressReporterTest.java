package art.arcane.iris.world;

import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.iris.localization.C;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WorldCreationProgressReporterTest {
    @Test
    public void playerBarAlwaysContainsFortyFourCellsAndClampsProgress() {
        String half = WorldCreationProgressReporter.buildPlayerBar(0.5D);
        String below = WorldCreationProgressReporter.buildPlayerBar(-1.0D);
        String above = WorldCreationProgressReporter.buildPlayerBar(2.0D);

        assertEquals("[" + "|".repeat(44) + "]", C.stripColor(half));
        assertEquals(22, occurrences(half, C.GREEN.toString()));
        assertEquals(0, occurrences(below, C.GREEN.toString()));
        assertEquals(44, occurrences(above, C.GREEN.toString()));
    }

    @Test
    public void consoleBarIsReadableWithoutColorAndClampsProgress() {
        assertEquals("[##########----------]", WorldCreationProgressReporter.buildConsoleBar(0.5D));
        assertEquals("[--------------------]", WorldCreationProgressReporter.buildConsoleBar(-1.0D));
        assertEquals("[####################]", WorldCreationProgressReporter.buildConsoleBar(2.0D));
    }

    @Test
    public void everyCreationPhaseHasAStableLocalizedStage() {
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_RESOLVE_DIMENSION,
                WorldCreationProgressReporter.stageKey("resolve_dimension"));
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_VALIDATE_PACK,
                WorldCreationProgressReporter.stageKey("validate_pack"));
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_PREPARE_WORLD_PACK,
                WorldCreationProgressReporter.stageKey("prepare_world_pack"));
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_INSTALL_DATAPACKS,
                WorldCreationProgressReporter.stageKey("install_datapacks"));
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_PREPARE_GENERATOR,
                WorldCreationProgressReporter.stageKey("prepare_generator"));
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_CREATE_WORLD,
                WorldCreationProgressReporter.stageKey("create_world"));
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_REGISTER_WORLD,
                WorldCreationProgressReporter.stageKey("register_world"));
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_TELEPORT_PLAYER,
                WorldCreationProgressReporter.stageKey("teleport_player"));
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_PREGENERATE,
                WorldCreationProgressReporter.stageKey("pregenerate"));
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_FINALIZE,
                WorldCreationProgressReporter.stageKey("finalize"));
        assertSame(RuntimeProgressMessages.WORLD_CREATE_STAGE_COMPLETE,
                WorldCreationProgressReporter.stageKey("complete"));
    }

    @Test
    public void terminalPlayerMessagesHonorStrictPlaceholderContracts() {
        String ready = C.stripColor(WorldCreationProgressReporter.terminalPlayerMessage(
                false,
                1.0D,
                "World ready",
                "",
                1_000L
        ));
        String failed = C.stripColor(WorldCreationProgressReporter.terminalPlayerMessage(
                true,
                0.98D,
                "Entering world",
                "",
                1_000L
        ));

        assertTrue(ready.contains("100% | World ready"));
        assertTrue(failed.contains("FAILED | Entering world"));
    }

    private static int occurrences(String value, String match) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(match, offset)) >= 0) {
            count++;
            offset += match.length();
        }
        return count;
    }
}

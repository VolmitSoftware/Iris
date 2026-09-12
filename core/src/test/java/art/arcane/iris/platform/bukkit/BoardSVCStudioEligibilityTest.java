package art.arcane.iris.platform.bukkit;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class BoardSVCStudioEligibilityTest {
    @Test
    public void productionIrisGeneratorIsNotEligible() {
        PlatformChunkGenerator generator = generator(false, false, mock(Engine.class));

        assertFalse(BoardSVC.isStudioGeneratorEligible(generator));
    }

    @Test
    public void activeStudioGeneratorIsEligible() {
        PlatformChunkGenerator generator = generator(true, false, mock(Engine.class));

        assertTrue(BoardSVC.isStudioGeneratorEligible(generator));
    }

    @Test
    public void studioGeneratorWithoutEngineIsNotEligible() {
        PlatformChunkGenerator generator = generator(true, false, null);

        assertFalse(BoardSVC.isStudioGeneratorEligible(generator));
    }

    @Test
    public void closingStudioGeneratorIsNotEligible() {
        PlatformChunkGenerator generator = generator(true, true, mock(Engine.class));

        assertFalse(BoardSVC.isStudioGeneratorEligible(generator));
    }

    @Test
    public void playerToggleIsSessionScopedAndDefaultsVisible() {
        BoardSVC service = new BoardSVC();
        UUID playerId = UUID.randomUUID();

        assertTrue(service.isPlayerBoardEnabled(playerId));
        assertFalse(service.togglePlayerBoard(playerId));
        assertFalse(service.isPlayerBoardEnabled(playerId));
        assertTrue(service.togglePlayerBoard(playerId));
        assertTrue(service.isPlayerBoardEnabled(playerId));

        service.togglePlayerBoard(playerId);
        service.clearPlayerPreference(playerId);
        assertTrue(service.isPlayerBoardEnabled(playerId));
    }

    private PlatformChunkGenerator generator(boolean studio, boolean closing, Engine engine) {
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        doReturn(studio).when(generator).isStudio();
        doReturn(closing).when(generator).isClosing();
        doReturn(engine).when(generator).getEngine();
        return generator;
    }
}

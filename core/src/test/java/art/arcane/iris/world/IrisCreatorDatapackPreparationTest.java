package art.arcane.iris.world;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisCreatorDatapackPreparationTest {
    @Test
    public void nonStudioCreationAlwaysInstallsDatapacks() {
        assertTrue(IrisCreator.DatapackPreparation.INSTALL_IF_CHANGED.requiresInstall(false));
        assertTrue(IrisCreator.DatapackPreparation.INSTALL_IF_CHANGED.requiresInstall(true));
    }

    @Test
    public void studioCreationReusesOnlyAReadyLoadedRuntimeWithMatchingInputs() {
        assertTrue(IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY.requiresInstall(false));
        assertFalse(IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY.requiresInstall(true));
    }

    @Test
    public void forcedStudioCreationNeverInstallsDatapacks() {
        assertFalse(IrisCreator.DatapackPreparation.FORCE_REUSE_LOADED_RUNTIME.requiresInstall(false));
        assertFalse(IrisCreator.DatapackPreparation.FORCE_REUSE_LOADED_RUNTIME.requiresInstall(true));
        assertTrue(IrisCreator.DatapackPreparation.FORCE_REUSE_LOADED_RUNTIME.forcesLoadedRuntime());
    }
}

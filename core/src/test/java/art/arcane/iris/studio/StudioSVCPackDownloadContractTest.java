package art.arcane.iris.studio;

import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.PackDownloadMessages;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StudioSVCPackDownloadContractTest {
    @Test
    public void stackedDownloadUsesLocalizedBusyMessage() {
        LifecycleOperationCoordinator.ActiveOperation download = new LifecycleOperationCoordinator.ActiveOperation(
                1L,
                LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                LifecycleOperationCoordinator.OperationKind.PACK_DOWNLOAD,
                "overworld"
        );
        LifecycleOperationCoordinator.ActiveOperation worldCreation = new LifecycleOperationCoordinator.ActiveOperation(
                2L,
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                "iris_world"
        );

        assertEquals(
                IrisLanguage.plain(PackDownloadMessages.IN_PROGRESS),
                StudioSVC.packMutationBusyMessage(download)
        );
        assertEquals(
                "Iris pack changes are busy with world_create for 'iris_world'. Try again when it completes.",
                StudioSVC.packMutationBusyMessage(worldCreation)
        );
    }
}

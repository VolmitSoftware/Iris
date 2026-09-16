package art.arcane.iris.world;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.DatapackInstallResult;
import art.arcane.iris.pack.datapack.ServerConfigurator;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

public class IrisCreatorDatapackPreparationTest {
    @Test
    public void explicitInstallModeAlwaysChecksDatapacks() {
        assertTrue(IrisCreator.DatapackPreparation.INSTALL_IF_CHANGED.requiresInstall(false));
        assertTrue(IrisCreator.DatapackPreparation.INSTALL_IF_CHANGED.requiresInstall(true));
    }

    @Test
    public void normalAndStudioCreationReuseOnlyAReadyLoadedRuntime() {
        assertTrue(IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY.requiresInstall(false));
        assertFalse(IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY.requiresInstall(true));
    }

    @Test
    public void forcedStudioCreationNeverInstallsDatapacks() {
        assertFalse(IrisCreator.DatapackPreparation.FORCE_REUSE_LOADED_RUNTIME.requiresInstall(false));
        assertFalse(IrisCreator.DatapackPreparation.FORCE_REUSE_LOADED_RUNTIME.requiresInstall(true));
        assertTrue(IrisCreator.DatapackPreparation.FORCE_REUSE_LOADED_RUNTIME.forcesLoadedRuntime());
    }

    @Test
    public void defaultCreationSkipsInstallationWhenRequiredRegistriesAreLoaded() throws Exception {
        IrisCreator creator = creator();
        IrisDimension dimension = mock(IrisDimension.class);
        try (MockedStatic<ServerConfigurator> configuration = mockStatic(ServerConfigurator.class)) {
            configuration.when(() -> ServerConfigurator.isLoadedDatapackRuntimeReady(dimension)).thenReturn(true);

            assertEquals(DatapackInstallResult.unchangedResult(), prepare(creator, dimension));

            configuration.verify(() -> ServerConfigurator.isLoadedDatapackRuntimeReady(dimension));
            configuration.verify(() -> ServerConfigurator.installDataPacksIfChanged(true, null), never());
        }
    }

    @Test
    public void unavailableRequiredRegistriesRunInstallationAndPreserveRestartRequirement() throws Exception {
        IrisCreator creator = creator();
        IrisDimension dimension = mock(IrisDimension.class);
        DatapackInstallResult result = DatapackInstallResult.restartRequiredResult();
        try (MockedStatic<ServerConfigurator> configuration = mockStatic(ServerConfigurator.class)) {
            configuration.when(() -> ServerConfigurator.isLoadedDatapackRuntimeReady(dimension)).thenReturn(false);
            configuration.when(() -> ServerConfigurator.installDataPacksIfChanged(true, null)).thenReturn(result);

            assertSame(result, prepare(creator, dimension));

            configuration.verify(() -> ServerConfigurator.installDataPacksIfChanged(true, null));
        }
    }

    @Test
    public void failedInstallationRemainsFailed() throws Exception {
        IrisCreator creator = creator();
        IrisDimension dimension = mock(IrisDimension.class);
        DatapackInstallResult result = DatapackInstallResult.failedResult();
        try (MockedStatic<ServerConfigurator> configuration = mockStatic(ServerConfigurator.class)) {
            configuration.when(() -> ServerConfigurator.installDataPacksIfChanged(true, null)).thenReturn(result);

            assertSame(result, prepare(creator, dimension));
        }
    }

    @Test
    public void explicitInstallRequestDoesNotReuseLoadedRegistries() throws Exception {
        IrisCreator creator = creator().datapackPreparation(IrisCreator.DatapackPreparation.INSTALL_IF_CHANGED);
        IrisDimension dimension = mock(IrisDimension.class);
        DatapackInstallResult result = DatapackInstallResult.readyResult();
        try (MockedStatic<ServerConfigurator> configuration = mockStatic(ServerConfigurator.class)) {
            configuration.when(() -> ServerConfigurator.installDataPacksIfChanged(true, null)).thenReturn(result);

            assertSame(result, prepare(creator, dimension));

            configuration.verify(() -> ServerConfigurator.isLoadedDatapackRuntimeReady(dimension), never());
        }
    }

    private static IrisCreator creator() {
        try (MockedStatic<IrisSettings> settings = mockStatic(IrisSettings.class)) {
            settings.when(IrisSettings::get).thenReturn(new IrisSettings());
            return new IrisCreator();
        }
    }

    private static DatapackInstallResult prepare(IrisCreator creator, IrisDimension dimension) throws Exception {
        Method method = IrisCreator.class.getDeclaredMethod("prepareDatapacks", IrisDimension.class);
        method.setAccessible(true);
        return (DatapackInstallResult) method.invoke(creator, dimension);
    }
}

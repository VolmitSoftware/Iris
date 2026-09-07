package art.arcane.iris.core.loader;

import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.engine.history.GenerationRegistryContract;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

public class IrisDataRetainedPackTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisData retained;

    @Before
    public void openRetainedData() throws Exception {
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        retained = spy(IrisData.openRuntime(temporaryFolder.newFolder("retained-pack")));
        retained.bindGenerationRegistryContract(GenerationRegistryContract.empty());
    }

    @After
    public void closeData() {
        if (retained != null) {
            retained.close();
        }
        IrisServices.remove(PreservationRegistry.class);
    }

    @Test
    public void missingRetainedResourceNeverSearchesMutableGlobalPacks() {
        doReturn(null).when(retained).load(IrisDimension.class, "missing", false);

        try (MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class)) {
            assertNull(IrisData.loadAnyDimension("missing", retained));
            platforms.verifyNoInteractions();
        }
    }

    @Test
    public void existingRetainedResourceComesFromItsBoundPack() {
        IrisDimension dimension = new IrisDimension();
        doReturn(dimension).when(retained).load(IrisDimension.class, "overworld", false);

        try (MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class)) {
            assertSame(dimension, IrisData.loadAnyDimension("overworld", retained));
            platforms.verifyNoInteractions();
        }
    }
}

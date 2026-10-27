package art.arcane.iris.generation.runtime;

import art.arcane.iris.spi.IrisLogging;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class IrisEngineFailureReportingTest {
    @Test
    public void failedGenerationReportsOnceAndRetainsItsCause() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IllegalStateException failure = new IllegalStateException("Hydrology raster failed");
        Field platformHooks = IrisEngine.class.getDeclaredField("platformHooks");
        platformHooks.setAccessible(true);
        platformHooks.set(engine, mock(EnginePlatformHooks.class));
        doThrow(failure).when(engine).openGenerationHistoryCoordinateScope(-352, -144);
        doCallRealMethod().when(engine).generate(-352, -144, null, null, false);
        doCallRealMethod().when(engine).fail("Failed to generate -352, -144", failure);
        doCallRealMethod().when(engine).hasFailed();

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            WrongEngineBroException reported = assertThrows(WrongEngineBroException.class,
                    () -> engine.generate(-352, -144, null, null, false));

            assertSame(failure, reported.getCause());
            assertTrue(engine.hasFailed());
            logging.verify(() -> IrisLogging.error("Failed to generate -352, -144"));
            logging.verify(() -> IrisLogging.reportError(failure));
            logging.verifyNoMoreInteractions();
        }
    }
}

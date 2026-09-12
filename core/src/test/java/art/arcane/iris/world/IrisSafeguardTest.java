package art.arcane.iris.world;

import art.arcane.iris.world.safeguard.IrisSafeguard;
import art.arcane.iris.world.safeguard.Mode;
import art.arcane.iris.world.safeguard.Task;
import art.arcane.iris.world.safeguard.Tasks;
import art.arcane.iris.spi.IrisLogging;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

public class IrisSafeguardTest {
    @After
    public void resetValidationAndSafeguard() {
        IrisStartupValidation.disable();
        try (MockedStatic<Tasks> tasks = mockStatic(Tasks.class)) {
            tasks.when(Tasks::getTasks).thenReturn(List.of());
            IrisSafeguard.execute();
        }
    }

    @Test
    public void injectionExceptionRevokesReadinessAndReportsUnstableRuntime() {
        IllegalStateException injectionFailure = new IllegalStateException("Java agent unavailable");
        Task injection = Task.critical("injection",
                "Iris runtime injection failed. Resolve the startup errors and restart the server.",
                () -> {
                    throw injectionFailure;
                });
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        IrisStartupValidation.beginRuntimeValidation();
        IrisStartupValidation.markRuntimeReady();
        assertTrue(IrisStartupValidation.isReady());

        try (MockedStatic<Tasks> tasks = mockStatic(Tasks.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            tasks.when(Tasks::getTasks).thenReturn(List.of(injection));

            IrisSafeguard.execute();

            assertFalse(IrisStartupValidation.isReady());
            assertEquals(IrisStartupValidation.ValidationState.INVALID, IrisStartupValidation.snapshot().runtime());
            assertEquals(Mode.UNSTABLE, IrisSafeguard.mode());
            assertEquals("unstable", IrisSafeguard.asContext().get("injection"));
            assertEquals("Iris runtime injection failed. Resolve the startup errors and restart the server.",
                    IrisStartupValidation.denialReason().orElseThrow());
            assertThrows(IllegalStateException.class, IrisStartupValidation::requireWorldCreationReady);
            logging.verify(() -> IrisLogging.reportError(anyString(), org.mockito.ArgumentMatchers.same(injectionFailure)));
        }
    }
}

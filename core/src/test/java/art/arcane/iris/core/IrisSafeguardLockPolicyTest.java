package art.arcane.iris.core;

import art.arcane.iris.core.safeguard.IrisSafeguard;
import art.arcane.iris.core.safeguard.Mode;
import art.arcane.iris.core.safeguard.task.CheckResult;
import art.arcane.iris.core.safeguard.task.Diagnostic;
import art.arcane.iris.core.safeguard.task.Task;
import art.arcane.iris.core.safeguard.task.Tasks;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Danger Mode used to be a label: only the check whose id happened to be {@code injection} locked the
 * runtime, and it did so from inside a catch that string-matched that id. Every other critical failure -
 * an NMS binding that could not be bound, {@code general.disableNMS}, missing dimension types - printed
 * Danger and then let the boot carry on with logins open. The severity a check declares is now the whole
 * decision: any Danger result locks the runtime, and every check states the lock reason an operator reads.
 */
public class IrisSafeguardLockPolicyTest {
    @After
    public void resetValidationAndSafeguard() {
        IrisStartupValidation.disable();
        try (MockedStatic<Tasks> tasks = mockStatic(Tasks.class)) {
            tasks.when(Tasks::getTasks).thenReturn(List.of());
            IrisSafeguard.execute();
        }
    }

    @Test
    public void aDangerResultFromAnyCheckLocksTheRuntime() {
        Task dimensionTypes = Task.critical("dimensionTypes", "unused throw reason",
                () -> CheckResult.danger("Iris dimension types were not registered. Restart the server so the registries reload.",
                        Diagnostic.Logger.ERROR.create("Dimension Types")));

        execute(dimensionTypes);

        assertEquals(Mode.UNSTABLE, IrisSafeguard.mode());
        assertFalse(IrisStartupValidation.isReady());
        assertEquals("Iris dimension types were not registered. Restart the server so the registries reload.",
                IrisStartupValidation.denialReason().orElseThrow());
        assertThrows(IllegalStateException.class, IrisStartupValidation::requireWorldCreationReady);
    }

    @Test
    public void anAdvisoryCheckThatThrowsStaysWarningAndLeavesTheRuntimeReady() {
        Task memory = Task.advisory("memory", () -> {
            throw new IllegalStateException("no hardware probe");
        });

        execute(memory);

        assertEquals(Mode.WARNING, IrisSafeguard.mode());
        assertTrue(IrisStartupValidation.isReady());
        assertEquals("warning", IrisSafeguard.asContext().get("memory"));
    }

    @Test
    public void aCriticalCheckThatThrowsLocksWithTheReasonItDeclared() {
        Task injection = Task.critical("injection",
                "Iris runtime injection failed. Resolve the startup errors and restart the server.",
                () -> {
                    throw new IllegalStateException("Java agent unavailable");
                });

        execute(injection);

        assertEquals(Mode.UNSTABLE, IrisSafeguard.mode());
        assertEquals("Iris runtime injection failed. Resolve the startup errors and restart the server.",
                IrisStartupValidation.denialReason().orElseThrow());
    }

    @Test
    public void theFirstDangerCheckOwnsTheLockReason() {
        Task version = Task.critical("version", "unused throw reason",
                () -> CheckResult.danger("first reason", Diagnostic.Logger.ERROR.create("Server Version")));
        Task injection = Task.critical("injection", "unused throw reason",
                () -> CheckResult.danger("second reason", Diagnostic.Logger.ERROR.create("Java Agent")));

        execute(version, injection);

        assertEquals("first reason", IrisStartupValidation.denialReason().orElseThrow());
    }

    @Test
    public void everyCheckStableLeavesTheRuntimeReady() {
        execute(Task.advisory("memory", CheckResult::stable), Task.critical("injection", "unused", CheckResult::stable));

        assertEquals(Mode.STABLE, IrisSafeguard.mode());
        assertTrue(IrisStartupValidation.isReady());
    }

    /**
     * A Danger result has to name what the operator must fix, so a check cannot raise one without a reason.
     */
    @Test
    public void aDangerResultWithoutALockReasonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CheckResult.danger("   ", Diagnostic.Logger.ERROR.create("Broken")));
    }

    /**
     * The per-check modes and diagnostic lines were collected on every boot and read by nothing.
     * {@code /iris debugdump} carries them now, so a support report says which check failed.
     */
    @Test
    public void theDebugReportNamesEveryCheckWithItsModeAndDiagnosticLines() {
        Task memory = Task.advisory("memory", () -> CheckResult.warning(
                Diagnostic.Logger.WARN.create("Low Memory"),
                Diagnostic.Logger.WARN.create("- JVM maximum heap: 1024 MB")));
        Task injection = Task.critical("injection", "unused", CheckResult::stable);

        execute(memory, injection);
        String report = IrisSafeguard.debugReport();

        assertTrue(report, report.contains("Startup safeguard: warning"));
        assertTrue(report, report.contains("memory: warning"));
        assertTrue(report, report.contains("Low Memory"));
        assertTrue(report, report.contains("- JVM maximum heap: 1024 MB"));
        assertTrue(report, report.contains("injection: stable"));
    }

    private void execute(Task... checks) {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        try (MockedStatic<Tasks> tasks = mockStatic(Tasks.class)) {
            tasks.when(Tasks::getTasks).thenReturn(List.of(checks));
            IrisSafeguard.execute();
        }
    }
}

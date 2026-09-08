package art.arcane.iris.core.structure.conversion;

import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisStructureAdoptionValueTest {
    @Test
    public void theDefaultLimitsAreTheOnesTheServiceShipsWith() {
        IrisStructureAdoptionLimits limits = IrisStructureAdoptionLimits.defaults();

        assertEquals(10_000, limits.maxResources());
        assertEquals(8 * 1024 * 1024, limits.maxJsonBytes());
        assertEquals(64 * 1024 * 1024, limits.maxBinaryBytes());
        assertEquals(1024L * 1024L * 1024L, limits.maxTotalBytes());
        assertEquals(10_000, limits.maxStructuresScanned());
        assertEquals(1_000, limits.maxDiagnostics());
        assertEquals(1_024, limits.maxActivePlans());
        assertEquals(Duration.ofMinutes(15L), limits.planTtl());
    }

    @Test
    public void everyLimitRejectsBothEndsOfItsRange() {
        assertThrows(IllegalArgumentException.class, () -> limits(0, 1, 1, 1L, 1, 1, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(100_001, 1, 1, 1L, 1, 1, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 0, 1, 1L, 1, 1, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 67_108_865, 1, 1L, 1, 1, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 0, 1L, 1, 1, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 268_435_457, 1L, 1, 1, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 0L, 1, 1, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1_073_741_825L, 1, 1, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1L, 0, 1, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1L, 1, 0, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1L, 1, 10_001, 1, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1L, 1, 1, 0, Duration.ofMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1L, 1, 1, 10_001, Duration.ofMinutes(1L)));
    }

    @Test
    public void thePlanTtlMustBePositiveAndWithinADay() {
        assertThrows(NullPointerException.class, () -> limits(1, 1, 1, 1L, 1, 1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1L, 1, 1, 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1L, 1, 1, 1, Duration.ofSeconds(-1L)));
        assertThrows(IllegalArgumentException.class,
                () -> limits(1, 1, 1, 1L, 1, 1, 1, Duration.ofHours(24L).plusNanos(1L)));

        assertEquals(Duration.ofHours(24L), limits(1, 1, 1, 1L, 1, 1, 1, Duration.ofHours(24L)).planTtl());
        assertEquals(Duration.ofNanos(1L), limits(1, 1, 1, 1L, 1, 1, 1, Duration.ofNanos(1L)).planTtl());
    }

    @Test
    public void aDiagnosticRendersItsSeverityCodeResourceAndAdvice() {
        IrisStructureAdoptionDiagnostic diagnostic = new IrisStructureAdoptionDiagnostic(
                IrisStructureAdoptionDiagnostic.Severity.ERROR,
                IrisStructureAdoptionDiagnostic.Code.TARGET_RESOURCE_EXISTS,
                "  structures/village.json  ",
                "the target already exists",
                "  pick another target  ");

        assertEquals("structures/village.json", diagnostic.resource());
        assertEquals("ERROR TARGET_RESOURCE_EXISTS [structures/village.json]: "
                + "the target already exists pick another target", diagnostic.summary());
        assertTrue(diagnostic.blocking());
    }

    @Test
    public void aDiagnosticWithoutAResourceOrAdviceStillReadsCleanly() {
        IrisStructureAdoptionDiagnostic diagnostic = new IrisStructureAdoptionDiagnostic(
                IrisStructureAdoptionDiagnostic.Severity.INFO,
                IrisStructureAdoptionDiagnostic.Code.IN_PLACE_AVAILABLE,
                null,
                "the source can be claimed in place",
                null);

        assertEquals("INFO IN_PLACE_AVAILABLE: the source can be claimed in place", diagnostic.summary());
        assertFalse(diagnostic.blocking());
    }

    @Test
    public void aDiagnosticWithoutADetailIsRejected() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new IrisStructureAdoptionDiagnostic(
                        IrisStructureAdoptionDiagnostic.Severity.WARNING,
                        IrisStructureAdoptionDiagnostic.Code.SHARED_DEPENDENCY,
                        "structures/village.json",
                        "   ",
                        null));

        assertTrue(failure.getMessage(), failure.getMessage().contains("detail cannot be blank"));
        assertThrows(NullPointerException.class, () -> new IrisStructureAdoptionDiagnostic(
                null,
                IrisStructureAdoptionDiagnostic.Code.SHARED_DEPENDENCY,
                null,
                "detail",
                null));
    }

    @Test
    public void diagnosticsSortErrorsAheadOfWarningsAndInfo() {
        IrisStructureAdoptionDiagnostic error = diagnostic(
                IrisStructureAdoptionDiagnostic.Severity.ERROR,
                IrisStructureAdoptionDiagnostic.Code.PLAN_BLOCKED, "b", "second");
        IrisStructureAdoptionDiagnostic warning = diagnostic(
                IrisStructureAdoptionDiagnostic.Severity.WARNING,
                IrisStructureAdoptionDiagnostic.Code.SHARED_DEPENDENCY, "a", "first");
        IrisStructureAdoptionDiagnostic sameSeverityEarlierCode = diagnostic(
                IrisStructureAdoptionDiagnostic.Severity.ERROR,
                IrisStructureAdoptionDiagnostic.Code.SOURCE_GRAPH_INVALID, "z", "third");

        assertTrue(error.compareTo(warning) < 0);
        assertTrue(sameSeverityEarlierCode.compareTo(error) < 0);
        assertTrue(diagnostic(IrisStructureAdoptionDiagnostic.Severity.ERROR,
                IrisStructureAdoptionDiagnostic.Code.PLAN_BLOCKED, "a", "x")
                .compareTo(error) < 0);
        assertEquals(0, error.compareTo(diagnostic(
                IrisStructureAdoptionDiagnostic.Severity.ERROR,
                IrisStructureAdoptionDiagnostic.Code.PLAN_BLOCKED, "b", "second")));
    }

    @Test
    public void anUnappliedResultCarriesItsDiagnosticsAndNoReceipt() {
        UUID planId = UUID.randomUUID();
        IrisStructureAdoptionResult result = new IrisStructureAdoptionResult(
                IrisStructureAdoptionResult.Status.BLOCKED,
                planId,
                List.of(diagnostic(IrisStructureAdoptionDiagnostic.Severity.ERROR,
                        IrisStructureAdoptionDiagnostic.Code.PLAN_BLOCKED, "structures/village.json", "blocked")),
                Optional.empty(),
                Optional.empty());

        assertFalse(result.successful());
        assertEquals(List.of(
                "Adoption plan " + planId + ": BLOCKED",
                "ERROR PLAN_BLOCKED [structures/village.json]: blocked"), result.summaryLines());
    }

    @Test
    public void anAppliedResultWithoutAReceiptIsRejected() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new IrisStructureAdoptionResult(
                        IrisStructureAdoptionResult.Status.APPLIED,
                        UUID.randomUUID(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty()));

        assertTrue(failure.getMessage(), failure.getMessage().contains("requires a receipt"));
    }

    private static IrisStructureAdoptionDiagnostic diagnostic(
            IrisStructureAdoptionDiagnostic.Severity severity,
            IrisStructureAdoptionDiagnostic.Code code,
            String resource,
            String detail
    ) {
        return new IrisStructureAdoptionDiagnostic(severity, code, resource, detail, null);
    }

    private static IrisStructureAdoptionLimits limits(
            int maxResources,
            int maxJsonBytes,
            int maxBinaryBytes,
            long maxTotalBytes,
            int maxStructuresScanned,
            int maxDiagnostics,
            int maxActivePlans,
            Duration planTtl
    ) {
        return new IrisStructureAdoptionLimits(
                maxResources,
                maxJsonBytes,
                maxBinaryBytes,
                maxTotalBytes,
                maxStructuresScanned,
                maxDiagnostics,
                maxActivePlans,
                planTtl);
    }
}

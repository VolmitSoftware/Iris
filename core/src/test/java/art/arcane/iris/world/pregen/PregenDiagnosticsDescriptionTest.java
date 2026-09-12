package art.arcane.iris.world.pregen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PregenDiagnosticsDescriptionTest {
    @Test
    public void anUnnamedProbeStillReadsAsAProbe() {
        assertEquals("unnamed", PregenDiagnostics.describeProbe(null));
        assertEquals("unnamed", PregenDiagnostics.describeProbe(""));
        assertEquals("unnamed", PregenDiagnostics.describeProbe("   "));
    }

    @Test
    public void aNamedProbeIsReportedVerbatim() {
        assertEquals("mantle handle for backpressure",
                PregenDiagnostics.describeProbe("mantle handle for backpressure"));
        assertEquals("%s throughput", PregenDiagnostics.describeProbe("%s throughput"));
    }

    @Test
    public void anAbsentThrowableIsCalledOutInsteadOfPrintingNull() {
        assertEquals("no throwable reported", PregenDiagnostics.describeFailure(null));
    }

    @Test
    public void aThrowableWithoutAMessageIsIdentifiedByItsType() {
        assertEquals("java.lang.IllegalStateException",
                PregenDiagnostics.describeFailure(new IllegalStateException()));
    }

    @Test
    public void aThrowableWithAMessageCarriesBothTypeAndMessage() {
        assertEquals("java.lang.IllegalStateException: mantle is gone",
                PregenDiagnostics.describeFailure(new IllegalStateException("mantle is gone")));
        assertEquals("java.lang.RuntimeException: ",
                PregenDiagnostics.describeFailure(new RuntimeException("")));
    }
}

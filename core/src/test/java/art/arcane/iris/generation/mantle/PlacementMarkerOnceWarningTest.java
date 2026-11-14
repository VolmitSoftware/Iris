package art.arcane.iris.generation.mantle;

import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.testsupport.CapturedLog;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlacementMarkerOnceWarningTest {
    private static final String SKIPPED = "Skipping placement marker write";

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private CapturedLog log;

    @Before
    public void captureLog() {
        log = CapturedLog.bind();
    }

    @After
    public void releaseLog() {
        log.close();
    }

    @Test
    public void anObjectWithoutALoadKeyWarnsOncePerContextNoMatterTheId() {
        IrisObject object = new IrisObject(0, 0, 0);

        assertNull(MantleObjectComponent.placementMarker(object, 1, "surface"));
        assertNull(MantleObjectComponent.placementMarker(object, 2, "surface"));
        assertNull(MantleObjectComponent.placementMarker(new IrisObject(0, 0, 0), 3, "surface"));

        assertEquals(1, log.warnings(SKIPPED).size());
    }

    @Test
    public void aDifferentContextWarnsSeparately() {
        IrisObject object = new IrisObject(0, 0, 0);

        MantleObjectComponent.placementMarker(object, 1, "surface");
        MantleObjectComponent.placementMarker(object, 1, "procedural");

        assertEquals(2, log.warnings(SKIPPED).size());
    }

    @Test
    public void aLoadKeyedObjectNeverWarns() {
        IrisObject object = new IrisObject(0, 0, 0);
        object.setLoadKey("trees/oak");

        assertEquals("trees/oak@7", MantleObjectComponent.placementMarker(object, 7, "surface"));

        assertEquals(0, log.warnings(SKIPPED).size());
    }
}

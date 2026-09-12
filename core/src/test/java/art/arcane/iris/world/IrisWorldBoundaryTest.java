package art.arcane.iris.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;

public class IrisWorldBoundaryTest {
    @Test
    public void absentConfigurationHasNoSyntheticBoundarySnapshot() {
        assertThrows(NullPointerException.class, () -> IrisWorldBoundary.snapshot(null));
    }

    @Test
    public void configuredBoundarySnapshotsAllValues() {
        IrisWorldBoundary configured = new IrisWorldBoundary()
                .setCenter(new IrisWorldBoundaryCenter(128.5D, -64.25D))
                .setSize(16_384D)
                .setWarningDistance(16)
                .setDamageBuffer(7.5D)
                .setDamageAmount(0.75D);

        IrisWorldBoundary snapshot = IrisWorldBoundary.snapshot(configured);
        configured.getCenter().setX(512D);

        assertNotSame(configured, snapshot);
        assertNotSame(configured.getCenter(), snapshot.getCenter());
        assertEquals(128.5D, snapshot.getCenter().getX(), 0D);
        assertEquals(-64.25D, snapshot.getCenter().getZ(), 0D);
        assertEquals(16_384D, snapshot.getSize(), 0D);
        assertEquals(16, snapshot.getWarningDistance());
        assertEquals(7.5D, snapshot.getDamageBuffer(), 0D);
        assertEquals(0.75D, snapshot.getDamageAmount(), 0D);
    }

    @Test
    public void rejectsValuesOutsideNativeLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> new IrisWorldBoundary().setSize(IrisWorldBoundary.MAXIMUM_SIZE + 1D).validate());
        assertThrows(IllegalArgumentException.class,
                () -> new IrisWorldBoundary().setWarningDistance(-1).validate());
        assertThrows(IllegalArgumentException.class,
                () -> new IrisWorldBoundary().setCenter(new IrisWorldBoundaryCenter(
                        IrisWorldBoundary.MAXIMUM_CENTER + 1D, 0D)).validate());
        assertThrows(IllegalArgumentException.class,
                () -> new IrisWorldBoundary().setDamageAmount(Double.NaN).validate());
    }
}

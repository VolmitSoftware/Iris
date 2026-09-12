package art.arcane.iris.pack.datapack;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DatapackInstallResultTest {
    @Test
    public void failedResultIsNotSuccessfulOrChanged() {
        DatapackInstallResult result = DatapackInstallResult.failedResult();

        assertFalse(result.succeeded());
        assertFalse(result.changed());
        assertFalse(result.restartRequired());
    }

    @Test
    public void unchangedResultIsSuccessfulWithoutChange() {
        DatapackInstallResult result = DatapackInstallResult.unchangedResult();

        assertTrue(result.succeeded());
        assertFalse(result.changed());
        assertFalse(result.restartRequired());
    }

    @Test
    public void readyResultIsSuccessfulAndChanged() {
        DatapackInstallResult result = DatapackInstallResult.readyResult();

        assertTrue(result.succeeded());
        assertTrue(result.changed());
        assertFalse(result.restartRequired());
    }

    @Test
    public void restartResultIsSuccessfulChangedAndPendingRestart() {
        DatapackInstallResult result = DatapackInstallResult.restartRequiredResult();

        assertTrue(result.succeeded());
        assertTrue(result.changed());
        assertTrue(result.restartRequired());
    }
}

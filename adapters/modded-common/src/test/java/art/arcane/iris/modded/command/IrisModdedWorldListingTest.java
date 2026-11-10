package art.arcane.iris.modded.command;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisModdedWorldListingTest {
    private static final String ALPHA = "iris:alpha";
    private static final String BETA = "iris:beta";
    private static final long ALPHA_SEED = 424242L;
    private static final long BETA_SEED = -77L;
    private static final int ALPHA_CHUNKS = 1089;

    @Test
    public void rowShowsTheIrisWorldSeedAndPersistedChunkCount() {
        String alpha = row(ALPHA, ALPHA_SEED, ALPHA_CHUNKS).format(true, null);
        String beta = row(BETA, BETA_SEED, 0).format(true, null);

        assertTrue(alpha, alpha.startsWith(ALPHA + ": pack=overworld "));
        assertTrue(alpha, alpha.contains(" seed=424242 "));
        assertTrue(alpha, alpha.contains(" generated=1089 "));
        assertTrue(beta, beta.startsWith(BETA + ": pack=overworld "));
        assertTrue(beta, beta.contains(" seed=-77 "));
        assertTrue(beta, beta.contains(" generated=0 "));
        assertFalse(alpha, alpha.contains(" world="));
    }

    @Test
    public void rowOmitsTheSeedWhenItIsNotShown() {
        String row = row(ALPHA, ALPHA_SEED, ALPHA_CHUNKS).format(false, null);

        assertFalse(row, row.contains("seed="));
        assertTrue(row, row.contains(" generated=1089 "));
    }

    @Test
    public void rowAppendsImportedFeatureStatus() {
        String row = row(ALPHA, ALPHA_SEED, ALPHA_CHUNKS).format(true, "ready");

        assertTrue(row, row.contains(" importedFeatures=ready "));
    }

    @Test
    public void rowForAnUnstartedEngineIsLabeledByItsDimension() {
        assertEquals("iris:gamma: pack=overworld:overworld (engine not started yet)",
                IrisModdedCommands.worldRow("iris:gamma", "overworld:overworld", null, true, null));
    }

    private static IrisModdedCommands.WorldRow row(String dimensionId, long seed, int generated) {
        return new IrisModdedCommands.WorldRow(dimensionId, "overworld", seed, -64, 320, generated, "overworld");
    }
}

package art.arcane.iris.pack;

import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackValidatorImportedFeatureHeightTest {
    @Rule
    public TemporaryFolder pack = new TemporaryFolder();

    @Test
    public void deeperWorldWithFeaturesEnabledWarns() {
        List<String> warnings = validate(featuresEnabled(true).put("dimensionHeight", height(-256, 512)));

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("Dimension 'overworld' runs importedFeatures in a -256..512 world"));
    }

    @Test
    public void vanillaOverworldHeightStaysSilent() {
        assertTrue(validate(featuresEnabled(true).put("dimensionHeight", height(-64, 320))).isEmpty());
        assertTrue(validate(featuresEnabled(true)).isEmpty());
    }

    @Test
    public void shorterWorldInsideVanillaBoundsStaysSilent() {
        assertTrue(validate(featuresEnabled(true).put("dimensionHeight", height(0, 256))).isEmpty());
    }

    @Test
    public void disabledOrAbsentFeaturesStaySilent() {
        assertTrue(validate(featuresEnabled(false).put("dimensionHeight", height(-256, 512))).isEmpty());
        assertTrue(validate(new JSONObject().put("dimensionHeight", height(-256, 512))).isEmpty());
    }

    private List<String> validate(JSONObject dimension) {
        List<String> warnings = new ArrayList<>();
        PackDimensionValidator.validateImportedFeatureHeight(pack.getRoot(), "overworld", dimension, warnings);
        return warnings;
    }

    private static JSONObject featuresEnabled(boolean enabled) {
        return new JSONObject().put("importedFeatures", new JSONObject().put("enabled", enabled));
    }

    private static JSONObject height(int min, int max) {
        return new JSONObject().put("min", min).put("max", max);
    }
}

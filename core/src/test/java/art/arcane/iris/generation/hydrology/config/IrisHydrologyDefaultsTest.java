package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IrisHydrologyDefaultsTest {
    private static final String PLAN_JSON = """
            {
              "enabled": false,
              "drainage": { "fillEpsilon": 0.001, "breachDepth": 24, "wetnessThinning": true, "minimumHeadKeep": 0.25, "saturationCatchment": 4194304 },
              "tiers": [
                { "name": "stream", "minCatchment": 1500,   "halfWidth": [0.5, 2],  "widthFactor": 1.0, "depthRatio": 0.4,  "inset": 1, "cascades": true, "waterfallMinimumDrop": 6, "bankBand": 0,  "bankStyle": "SMOOTH", "deltas": false, "materials": {}, "fluid": "water" },
                { "name": "river",  "minCatchment": 20000,  "halfWidth": [2, 8],    "widthFactor": 1.0, "depthRatio": 0.45, "inset": 2, "cascades": true, "waterfallMinimumDrop": 6, "bankBand": 32, "bankStyle": "SMOOTH", "deltas": false, "materials": {}, "fluid": "water" },
                { "name": "major",  "minCatchment": 320000, "halfWidth": [8, 16],   "widthFactor": 1.0, "depthRatio": 0.5,  "inset": 3, "cascades": true, "waterfallMinimumDrop": 6, "bankBand": 48, "bankStyle": "SMOOTH", "deltas": true,  "materials": {}, "fluid": "water" }
              ],
              "ponds": {
                "natural": { "enabled": true, "keep": 1.0, "minArea": 256, "maxArea": 65536, "minDepth": 2 },
                "seeded":  { "enabled": true, "density": 4.0, "radius": [6, 24], "depth": [3, 8], "maxSlope": 0.6, "alongChannels": true, "channelSpacing": [96, 256] },
                "dams":    { "enabled": false, "maxRaise": 4 },
                "freeboard": [1, 3], "climateDrawdown": 6,
                "shore":   { "roundness": 0.5, "shelf": 6, "taper": 14, "maxDepth": 8 },
                "materials": {}, "fluid": "water"
              },
              "lakes": { "enabled": true, "minArea": 65536, "maxArea": 4194304 },
              "deformation": { "maxFill": 6, "bankSlope": 0.2, "bankSteepFactor": 3.5, "bankNoise": 0.2, "caveMask": true },
              "underground": {
                "seaInlets": { "enabled": true, "spacing": 192, "minimumCliffHeight": 8, "length": [64, 256], "halfWidth": [2, 5], "depth": [2, 5], "headroom": [4, 10], "roofClearance": 6, "chamberRadius": [6, 12] },
                "cavePools": { "enabled": true, "density": 0.15, "minArea": 6, "maxArea": 400, "maxDepth": 4, "radius": 15, "freeboard": [0, 1], "fluid": "water" }
              },
              "fluids": [ { "name": "water", "palette": { "palette": [ { "block": "minecraft:water" } ] } } ],
              "prefetchRings": 1,
              "persist": true
            }
            """;

    @Test
    public void defaultsMatchPlan() {
        IrisHydrology hydrology = new IrisHydrology();
        KList<IrisHydrologyTier> tiers = hydrology.getTiers();
        assertFalse(hydrology.isEnabled());
        assertEquals(3, tiers.size());
        assertEquals(1500D, tiers.get(0).getMinCatchment(), 0D);
        assertEquals(20000D, tiers.get(1).getMinCatchment(), 0D);
        assertEquals(320000D, tiers.get(2).getMinCatchment(), 0D);
        assertTrue(hydrology.getPonds().getNatural().isEnabled());
        assertTrue(hydrology.getPonds().getSeeded().isEnabled());
        assertFalse(hydrology.getPonds().getDams().isEnabled());
        assertTrue(hydrology.getDeformation().isCaveMask());
        assertTrue(hydrology.getPonds().getMaterials().isAllowGravityBlocks());
        assertTrue(hydrology.getPonds().getDecorators().isEmpty());
        for (IrisHydrologyTier tier : tiers) {
            assertTrue(tier.getName(), tier.getMaterials().isAllowGravityBlocks());
            assertTrue(tier.getName(), tier.getDecorators().isEmpty());
        }
    }

    @Test
    public void bankStylesOfferEveryValleyShape() {
        assertEquals(List.of(IrisBankStyle.SMOOTH, IrisBankStyle.LINEAR, IrisBankStyle.CONCAVE, IrisBankStyle.TERRACED, IrisBankStyle.CLIFF),
                List.of(IrisBankStyle.values()));
        for (IrisHydrologyTier tier : new IrisHydrology().getTiers()) {
            assertEquals(tier.getName(), IrisBankStyle.SMOOTH, tier.getBankStyle());
        }
    }

    @Test
    public void policyDefaultsInheritEverything() {
        IrisHydrologyPolicy policy = new IrisHydrologyPolicy();
        assertNull(policy.getRainfall());
        assertNull(policy.getPondDensity());
        assertNull(policy.getStreams());
        assertNull(policy.getPonds());
        assertNull(policy.getFluid());
        assertNull(policy.getDecorators());
        assertNull(policy.getShoreBiomes());
        assertNull(policy.getShoreBiomeWidth());
    }

    @Test
    public void planJsonParsesToDefaults() {
        IrisHydrology parsed = new Gson().fromJson(PLAN_JSON, IrisHydrology.class);
        long defaults = HydrologySettings.compile(new IrisHydrology(), 1337L, 62, -64, 320).fingerprint();
        assertEquals(defaults, HydrologySettings.compile(parsed, 1337L, 62, -64, 320).fingerprint());
    }
}

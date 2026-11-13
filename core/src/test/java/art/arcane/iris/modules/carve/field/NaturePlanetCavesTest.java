package art.arcane.iris.modules.carve.field;

import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.modules.carve.Cave;
import com.volmit.noise.NoiseFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * The four caves of piexel's natureplanet pack, as their files are written, carve block for block what they carved
 * when every cave key was one number for the whole world. Carved in a world from Y -64 to 320 under a flat surface.
 */
public class NaturePlanetCavesTest {
    private static final int CHUNKS = 8;
    private static final int SIZE = CHUNKS * 16;
    private static final int WORLD_BOTTOM_Y = -64;
    private static final int WORLD_HEIGHT = 384;
    private static final int SURFACE_WORLD_Y = 100;
    private static final int SURFACE_Y = SURFACE_WORLD_Y - WORLD_BOTTOM_Y;
    private static final long SEED = 91_277L;

    private record Pinned(String name, String cave, int carved, int hash) {
    }

    private static final List<Pinned> NATUREPLANET = List.of(
            new Pinned("caverns", """
                    {"cavern": {"size": 56, "height": 22, "coverage": 0.07},
                     "depth": {"min": -20, "max": 200}, "fade": 16, "surfaceClearance": 12}
                    """, 96_055, -382_152_706),
            new Pinned("crawlways", """
                    {"tunnel": {
                      "width": {"min": 2, "max": 3, "style": {"style": "SIMPLEX", "zoom": 1.2}},
                      "spacing": {"horizontal": 64, "vertical": 53},
                      "warp": {"style": "SIMPLEX", "zoom": 0.25, "multiplier": 8},
                      "coverage": {"share": 0.4, "style": {"style": "SIMPLEX", "zoom": 2.4}}},
                     "surfaceClearance": 6}
                    """, 8_329, 1_344_334_684),
            new Pinned("deep-halls", """
                    {"cavern": {"size": 110, "height": 40, "coverage": 0.18, "pillars": {"spacing": 26, "thickness": 5}},
                     "depth": {"min": -64, "max": 10}, "fade": 14, "surfaceClearance": 16}
                    """, 273_203, 1_962_467_619),
            new Pinned("tunnels", """
                    {"tunnel": {
                      "width": {"min": 4, "max": 7, "style": {"style": "SIMPLEX", "zoom": 1.8}},
                      "spacing": {"horizontal": 96, "vertical": 69},
                      "warp": {"style": "SIMPLEX", "zoom": 0.4, "multiplier": 11.5},
                      "coverage": {"share": 0.8, "style": {"style": "SIMPLEX", "zoom": 3.6}}},
                     "opensToSurface": true}
                    """, 76_608, -1_422_091_570));

    private final NoiseFactory noise = new CarveTestWorld().resources().noise();

    @Test
    public void natureplanetCavesCarveWhatTheyCarvedWithOneNumberPerKey() {
        for (Pinned pinned : NATUREPLANET) {
            BitSet carved = carve(pinned.cave(), 0, 0);

            assertEquals(pinned.name(), pinned.carved(), carved.cardinality());
            assertEquals(pinned.name(), pinned.hash(), carved.hashCode());
        }
    }

    private BitSet carve(String caveJson, int offsetX, int offsetZ) {
        CaveCarver carver = new CaveCarver(TestSettings.of(Cave.class, caveJson), noise, SEED, WORLD_BOTTOM_Y, WORLD_HEIGHT);
        int[] surfaceY = new int[256];
        Arrays.fill(surfaceY, SURFACE_Y);
        double[] weight = new double[256];
        Arrays.fill(weight, 1D);
        boolean[] sealed = new boolean[256];
        BitSet carved = new BitSet(SIZE * SIZE * (SURFACE_Y + 1));
        for (int chunkX = 0; chunkX < CHUNKS; chunkX++) {
            for (int chunkZ = 0; chunkZ < CHUNKS; chunkZ++) {
                int originX = chunkX * 16;
                int originZ = chunkZ * 16;
                carver.carve(offsetX + originX, offsetZ + originZ, surfaceY, weight, sealed,
                        (localX, y, localZ) -> carved.set((y * SIZE * SIZE) + ((originZ + localZ) * SIZE) + originX + localX));
            }
        }
        return carved;
    }
}

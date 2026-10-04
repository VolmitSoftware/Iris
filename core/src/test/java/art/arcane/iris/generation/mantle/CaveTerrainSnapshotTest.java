package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class CaveTerrainSnapshotTest {
    private static final int SECTIONS = 5;

    @BeforeClass
    public static void registerMatter() {
        IrisMatterSupport.ensureRegistered();
    }

    @Test
    public void snapshotMatchesTerrainViewForEveryCellFaceAndResolverInput() {
        for (long seed = 1; seed <= 2; seed++) {
            MantleChunk<Matter> chunk = randomChunk(new Random(seed), seed % 2 == 0);
            CaveTerrainSnapshot snapshot = CaveTerrainSnapshot.capture(chunk, 3, -4);
            assertTrue(snapshot.covers(3, -4));
            assertFalse(snapshot.covers(3, 4));
            boolean anyHydrology = false;
            for (int x = 0; x < 16; x++) {
                for (int y = -2; y < SECTIONS * 16 + 3; y++) {
                    for (int z = 0; z < 16; z++) {
                        String cell = "seed=" + seed + " " + x + "," + y + "," + z;
                        assertSame(cell, TerrainMatterView.get(chunk, x, y, z, MatterCavern.class), snapshot.cavern(x, y, z));
                        HydrologyCaveCell hydrology = TerrainMatterView.get(chunk, x, y, z, HydrologyCaveCell.class);
                        anyHydrology |= hydrology != null;
                        assertSame(cell, hydrology, snapshot.hydrology(x, y, z));
                        assertSame(cell, TerrainMatterView.getComposedCavern(chunk, x, y, z), snapshot.composedCavern(x, y, z));
                    }
                }
            }
            assertEquals(anyHydrology, snapshot.hasHydrology());
            for (TerrainMatterView.Face face : TerrainMatterView.Face.values()) {
                for (int height : new int[]{0, 1, 37, SECTIONS * 16, SECTIONS * 16 + 9}) {
                    MatterCavern[] expected = TerrainMatterView.getComposedFace(chunk, face, height);
                    MatterCavern[] actual = snapshot.composedFace(face, height);
                    assertEquals(expected.length, actual.length);
                    for (int index = 0; index < expected.length; index++) {
                        assertSame(face + " " + index, expected[index], actual[index]);
                    }
                }
            }
            assertEquals(legacyResolverInputs(chunk), snapshotResolverInputs(snapshot));
        }
    }

    @Test
    public void chunksWithoutHydrologyAnswerNullWithoutCells() {
        Matter[] sections = new Matter[SECTIONS];
        sections[1] = new IrisMatter(16, 16, 16);
        MatterCavern cavern = new MatterCavern(true, "", (byte) 0);
        sections[1].slice(MatterCavern.class).set(4, 5, 6, cavern);
        sections[1].slice(PreObjectMatterCell.class).set(4, 6, 6, PreObjectMatterCell.hydrology(null));
        CaveTerrainSnapshot snapshot = CaveTerrainSnapshot.capture(chunk(sections), 0, 0);
        assertFalse(snapshot.hasHydrology());
        assertNull(snapshot.hydrology(4, 22, 6));
        assertSame(cavern, snapshot.composedCavern(4, 21, 6));
        assertNull(snapshot.cavern(4, 22, 6));
    }

    @Test
    public void retainedEstimateCountsOnlyAllocatedSectionsAndTheirCells() {
        Matter[] sections = new Matter[SECTIONS];
        long emptyBytes = CaveTerrainSnapshot.capture(chunk(sections), 0, 0).estimatedRetainedBytes();
        assertTrue(emptyBytes < 1024);
        sections[1] = new IrisMatter(16, 16, 16);
        sections[1].slice(MatterCavern.class).set(0, 0, 0, new MatterCavern(true, "cave", (byte) 0));
        CaveTerrainSnapshot sparse = CaveTerrainSnapshot.capture(chunk(sections), 0, 0);
        long sparseBytes = sparse.estimatedRetainedBytes();
        assertTrue(sparseBytes >= emptyBytes + 4096L * 8L);
        assertTrue(sparseBytes < emptyBytes + 4096L * 16L);
        sections[1].slice(MatterCavern.class).set(1, 0, 0, new MatterCavern(true, "other", (byte) 0));
        long moreCells = CaveTerrainSnapshot.capture(chunk(sections), 0, 0).estimatedRetainedBytes();
        assertTrue(moreCells > sparseBytes);
        assertEquals(sparseBytes, sparse.estimatedRetainedBytes());
    }

    private static Map<String, String> legacyResolverInputs(MantleChunk<Matter> chunk) {
        Map<String, String> inputs = new HashMap<>();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < SECTIONS * 16; y++) {
                for (int z = 0; z < 16; z++) {
                    MatterCavern cavern = TerrainMatterView.get(chunk, x, y, z, MatterCavern.class);
                    HydrologyCaveCell hydrology = TerrainMatterView.get(chunk, x, y, z, HydrologyCaveCell.class);
                    if (cavern != null || hydrology != null) {
                        inputs.put(x + ":" + y + ":" + z, identity(cavern) + "/" + identity(hydrology));
                    }
                }
            }
        }
        return inputs;
    }

    private static Map<String, String> snapshotResolverInputs(CaveTerrainSnapshot snapshot) {
        Map<String, String> inputs = new HashMap<>();
        snapshot.forEachCell((x, y, z, cavern, hydrology) -> assertNull(inputs.put(x + ":" + y + ":" + z,
                identity(cavern) + "/" + identity(hydrology))));
        return inputs;
    }

    private static String identity(Object value) {
        return value == null ? "null" : Integer.toHexString(System.identityHashCode(value));
    }

    private static MantleChunk<Matter> randomChunk(Random random, boolean manyCaverns) {
        MatterCavern[] caverns = new MatterCavern[manyCaverns ? 40 : 4];
        for (int index = 0; index < caverns.length; index++) {
            caverns[index] = new MatterCavern(index % 3 != 0, "biome-" + index, (byte) (index % 4));
        }
        HydrologyCaveCell[] hydrology = new HydrologyCaveCell[HydrologyCaveAction.values().length];
        for (int index = 0; index < hydrology.length; index++) {
            hydrology[index] = new HydrologyCaveCell(HydrologyCaveAction.values()[index], "river", "flooded-" + index);
        }
        Matter[] sections = new Matter[SECTIONS];
        for (int section = 0; section < SECTIONS; section++) {
            if (section == 2) {
                continue;
            }
            Matter matter = new IrisMatter(16, 16, 16);
            sections[section] = matter;
            int mode = section % 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        boolean aligned = ((z << 8 | y << 4 | x) & 0x333) == 0;
                        int roll = random.nextInt(100);
                        if (mode == 1 ? aligned && roll < 50 : roll < 40) {
                            matter.slice(MatterCavern.class).set(x, y, z, caverns[random.nextInt(caverns.length)]);
                        }
                        if (mode != 3 && random.nextInt(100) < 6) {
                            matter.slice(HydrologyCaveCell.class).set(x, y, z, hydrology[random.nextInt(hydrology.length)]);
                        }
                        int journal = random.nextInt(100);
                        if (journal < 3) {
                            matter.slice(PreObjectMatterCell.class).set(x, y, z, PreObjectMatterCell.cavern(null));
                        } else if (journal < 6) {
                            matter.slice(PreObjectMatterCell.class).set(x, y, z,
                                    PreObjectMatterCell.cavern(caverns[random.nextInt(caverns.length)]));
                        } else if (journal < 8) {
                            matter.slice(PreObjectMatterCell.class).set(x, y, z, PreObjectMatterCell.hydrology(null));
                        } else if (journal < 10) {
                            matter.slice(PreObjectMatterCell.class).set(x, y, z,
                                    PreObjectMatterCell.block(null).captureHydrology(hydrology[random.nextInt(hydrology.length)]));
                        } else if (journal < 13) {
                            matter.slice(PreObjectMatterCell.class).set(x, y, z, PreObjectMatterCell.string("marker"));
                        }
                    }
                }
            }
        }
        return chunk(sections);
    }

    @SuppressWarnings("unchecked")
    private static MantleChunk<Matter> chunk(Matter[] sections) {
        MantleChunk<Matter> chunk = mock(MantleChunk.class, withSettings().stubOnly());
        when(chunk.sectionCount()).thenReturn(sections.length);
        when(chunk.exists(anyInt())).thenAnswer(call -> {
            int section = call.getArgument(0);
            return section >= 0 && section < sections.length && sections[section] != null;
        });
        when(chunk.get(anyInt())).thenAnswer(call -> {
            assertTrue(Thread.holdsLock(chunk));
            int section = call.getArgument(0);
            return section >= 0 && section < sections.length ? sections[section] : null;
        });
        return chunk;
    }
}

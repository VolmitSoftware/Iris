package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainRoom;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CarveOwnershipTest {
    private static final int X = 1;
    private static final int Y = 2;
    private static final int Z = 3;
    private static final int HEIGHT = 64;
    private static final SubterrainCell OPEN_ROOM = new SubterrainCell(SubterrainCell.Kind.AIR, "", room(false, false));
    private static final SubterrainCell RESERVED_SOLID = new SubterrainCell(SubterrainCell.Kind.SOLID, "", room(false, true));
    private static final SubterrainCell RESERVED_PASSAGE = new SubterrainCell(SubterrainCell.Kind.AIR, "", room(true, false));
    private static final SubterrainCell SHELL = new SubterrainCell(SubterrainCell.Kind.SOLID, "", room(false, false));
    private static final HydrologyCaveCell[] HYDROLOGY = {
            null, new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, "water"), new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, "water")};
    private static final SubterrainCell[] SUBTERRAIN = {SubterrainCell.OUTSIDE, OPEN_ROOM, RESERVED_SOLID, RESERVED_PASSAGE, SHELL};

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void resolveRanksHydrologyThenSubterrainThenNaturalOpening() {
        assertEquals(CarveOwnership.Owner.CAVERN, CarveOwnership.resolve(false, false, false));
        assertEquals(CarveOwnership.Owner.NATURAL_OPENING, CarveOwnership.resolve(false, false, true));
        assertEquals(CarveOwnership.Owner.SUBTERRAIN, CarveOwnership.resolve(false, true, false));
        assertEquals(CarveOwnership.Owner.SUBTERRAIN, CarveOwnership.resolve(false, true, true));
        assertEquals(CarveOwnership.Owner.HYDROLOGY, CarveOwnership.resolve(true, false, false));
        assertEquals(CarveOwnership.Owner.HYDROLOGY, CarveOwnership.resolve(true, false, true));
        assertEquals(CarveOwnership.Owner.HYDROLOGY, CarveOwnership.resolve(true, true, false));
        assertEquals(CarveOwnership.Owner.HYDROLOGY, CarveOwnership.resolve(true, true, true));
    }

    @Test
    public void subterrainClaimsOpenRoomsAndReservedSolidsButNotShells() {
        assertFalse(CarveOwnership.subterrainClaims(null));
        assertFalse(CarveOwnership.subterrainClaims(SubterrainCell.OUTSIDE));
        assertTrue(CarveOwnership.subterrainClaims(OPEN_ROOM));
        assertTrue(CarveOwnership.subterrainClaims(new SubterrainCell(SubterrainCell.Kind.WATER, "", room(false, false))));
        assertTrue(CarveOwnership.subterrainClaims(RESERVED_SOLID));
        assertTrue(CarveOwnership.subterrainClaims(RESERVED_PASSAGE));
        assertFalse(CarveOwnership.subterrainClaims(SHELL));
    }

    @Test
    public void allReadersAgreeOnTheNaturalLayer() {
        List<String> disagreements = new ArrayList<>();
        for (HydrologyCaveCell hydrology : HYDROLOGY) {
            for (SubterrainCell subterrain : SUBTERRAIN) {
                for (boolean naturalOpening : new boolean[]{false, true}) {
                    for (boolean cavern : new boolean[]{false, true}) {
                        boolean claims = subterrain == OPEN_ROOM || subterrain == RESERVED_SOLID
                                || subterrain == RESERVED_PASSAGE;
                        boolean expected = switch (CarveOwnership.resolve(hydrology != null, claims, naturalOpening)) {
                            case HYDROLOGY -> hydrology.carves();
                            case SUBTERRAIN -> subterrain.carve();
                            case NATURAL_OPENING -> true;
                            case CAVERN -> cavern;
                        };
                        Sources sources = Sources.mocked(hydrology, subterrain, naturalOpening, cavern, null);
                        String state = "hydrology=" + (hydrology == null ? "none" : hydrology.action())
                                + " subterrain=" + describe(subterrain) + " naturalOpening=" + naturalOpening
                                + " cavern=" + cavern;
                        compare(disagreements, state, expected, readers(sources));
                    }
                }
            }
        }
        assertTrue(String.join("\n", disagreements), disagreements.isEmpty());
    }

    @Test
    public void transformedColumnsYieldOnlyToReservedSubterrainOutsideHydrology() {
        List<String> disagreements = new ArrayList<>();
        for (boolean transformedOpen : new boolean[]{false, true}) {
            for (HydrologyCaveCell hydrology : HYDROLOGY) {
                for (SubterrainCell subterrain : SUBTERRAIN) {
                    for (boolean cavern : new boolean[]{false, true}) {
                        boolean reserved = subterrain == RESERVED_SOLID || subterrain == RESERVED_PASSAGE;
                        boolean expected = hydrology == null && reserved ? subterrain.carve() : transformedOpen;
                        Sources sources = Sources.mocked(hydrology, subterrain, false, cavern, column(transformedOpen));
                        String state = "transformedOpen=" + transformedOpen
                                + " hydrology=" + (hydrology == null ? "none" : hydrology.action())
                                + " subterrain=" + describe(subterrain) + " cavern=" + cavern;
                        compare(disagreements, state, expected, readers(sources));
                    }
                }
            }
        }
        assertTrue(String.join("\n", disagreements), disagreements.isEmpty());
    }

    private static void compare(List<String> disagreements, String state, boolean expected, Map<String, Boolean> readers) {
        for (Map.Entry<String, Boolean> reader : readers.entrySet()) {
            if (reader.getValue() != expected) {
                disagreements.add(state + ": " + reader.getKey() + " gives " + reader.getValue() + ", the rule gives " + expected);
            }
        }
        agree(disagreements, state, readers, "MantleWriter.isCarved", "MantleWriter.getCarvedColumn");
        agree(disagreements, state, readers, "MantleWriter.isPrerequisiteCarved", "MantleWriter.getPrerequisiteCarvedColumn");
    }

    private static void agree(List<String> disagreements, String state, Map<String, Boolean> readers, String point, String column) {
        if (!readers.get(point).equals(readers.get(column))) {
            disagreements.add(state + ": " + point + " gives " + readers.get(point) + " but " + column + " gives "
                    + readers.get(column));
        }
    }

    private static Map<String, Boolean> readers(Sources sources) {
        Map<String, Boolean> readers = new LinkedHashMap<>();
        readers.put("MantleWriter.isCarved", sources.writer(writer -> writer.isCarved(X, Y, Z)));
        readers.put("MantleWriter.isPrerequisiteCarved", sources.writer(writer -> writer.isPrerequisiteCarved(X, Y, Z)));
        readers.put("MantleWriter.getCarvedColumn", sources.writer(writer -> writer.getCarvedColumn(X, Z, HEIGHT)[Y] != 0));
        readers.put("MantleWriter.getPrerequisiteCarvedColumn",
                sources.writer(writer -> writer.getPrerequisiteCarvedColumn(X, Z, HEIGHT)[Y] != 0));
        readers.put("EngineMantle.isCarved", sources.engineMantle().isCarved(X, Y, Z));
        return readers;
    }

    private static String describe(SubterrainCell subterrain) {
        if (subterrain == OPEN_ROOM) {
            return "open-room";
        }
        if (subterrain == RESERVED_SOLID) {
            return "reserved-solid";
        }
        if (subterrain == RESERVED_PASSAGE) {
            return "reserved-passage";
        }
        return subterrain == SHELL ? "shell" : "outside";
    }

    private static SubterrainRoom room(boolean reservedPassage, boolean reservedSolid) {
        return new SubterrainRoom("room", "", IrisSubterrainFamily.CENOTE,
                0, 0, 0, 0, 0, 0, -10, 10, 1D, 0, reservedPassage, reservedSolid, SubterrainCell.Kind.AIR);
    }

    private static Terrain3DColumn column(boolean openAtY) {
        return Terrain3DColumn.fromOccupancy(HEIGHT - 1, HEIGHT, y -> !openAtY || y != Y);
    }

    private record Sources(EngineMantle engineMantle, Mantle<Matter> mantle) {
        private boolean writer(Predicate<MantleWriter> query) {
            try (MantleWriter writer = new MantleWriter(engineMantle, mantle, 0, 0, 0, false)) {
                return query.test(writer);
            }
        }

        @SuppressWarnings("unchecked")
        private static Sources mocked(HydrologyCaveCell hydrology, SubterrainCell subterrain, boolean naturalOpening,
                                      boolean cavern, Terrain3DColumn transformed) {
            MatterCavern carved = cavern ? new MatterCavern(true, "", (byte) 0) : null;
            EngineMantle engineMantle = mock(EngineMantle.class);
            Engine engine = mock(Engine.class);
            IrisComplex complex = mock(IrisComplex.class);
            Mantle<Matter> mantle = mock(Mantle.class);
            TectonicPlate<Matter> plate = mock(TectonicPlate.class);
            MantleChunk<Matter> chunk = mock(MantleChunk.class);
            Matter matter = mock(Matter.class);
            MatterSlice<HydrologyCaveCell> hydrologySlice = mock(MatterSlice.class);
            MatterSlice<MatterCavern> cavernSlice = mock(MatterSlice.class);
            when(engineMantle.getEngine()).thenReturn(engine);
            when(engineMantle.getComplex()).thenReturn(complex);
            when(engineMantle.getMantle()).thenReturn(mantle);
            when(engineMantle.isCarved(anyInt(), anyInt(), anyInt())).thenCallRealMethod();
            when(engine.getSubterrainCell(X, Y, Z)).thenReturn(subterrain);
            when(complex.transformedColumn(X, Z)).thenReturn(transformed);
            when(complex.terrainColumn(X, Z)).thenReturn(transformed != null ? transformed : naturalOpening ? column(true) : null);
            when(complex.isTerrain3DOpening(X, Y, Z)).thenReturn(naturalOpening);
            when(mantle.getWorldHeight()).thenReturn(HEIGHT);
            when(mantle.useChunk(0, 0)).thenReturn(chunk);
            when(mantle.getLoadedRegion(0, 0)).thenReturn(plate);
            when(mantle.get(X, Y, Z, MatterCavern.class)).thenReturn(carved);
            when(plate.get(0, 0)).thenReturn(chunk);
            when(chunk.exists(0)).thenReturn(true);
            when(chunk.get(0)).thenReturn(matter);
            when(matter.hasSlice(HydrologyCaveCell.class)).thenReturn(true);
            when(matter.hasSlice(MatterCavern.class)).thenReturn(true);
            doReturn(hydrologySlice).when(matter).getSlice(HydrologyCaveCell.class);
            doReturn(cavernSlice).when(matter).getSlice(MatterCavern.class);
            when(hydrologySlice.get(X, Y, Z)).thenReturn(hydrology);
            when(cavernSlice.get(X, Y, Z)).thenReturn(carved);
            return new Sources(engineMantle, mantle);
        }
    }
}

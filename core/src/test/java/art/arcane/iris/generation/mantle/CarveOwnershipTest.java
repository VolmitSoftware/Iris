package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stage.IrisCarveModifier;
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
    private static final SubterrainRoom ROOM = new SubterrainRoom("room", "", IrisSubterrainFamily.CENOTE,
            0, 0, 0, 0, 0, 0, -10, 10, 1D, 0, false, true, SubterrainCell.Kind.AIR);

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void resolveRanksHydrologyThenSubterrainThenTransformed() {
        assertEquals(CarveOwnership.Owner.NATURAL, CarveOwnership.resolve(false, false, false));
        assertEquals(CarveOwnership.Owner.TRANSFORMED, CarveOwnership.resolve(false, false, true));
        assertEquals(CarveOwnership.Owner.SUBTERRAIN, CarveOwnership.resolve(false, true, false));
        assertEquals(CarveOwnership.Owner.SUBTERRAIN, CarveOwnership.resolve(false, true, true));
        assertEquals(CarveOwnership.Owner.HYDROLOGY, CarveOwnership.resolve(true, false, false));
        assertEquals(CarveOwnership.Owner.HYDROLOGY, CarveOwnership.resolve(true, false, true));
        assertEquals(CarveOwnership.Owner.HYDROLOGY, CarveOwnership.resolve(true, true, false));
        assertEquals(CarveOwnership.Owner.HYDROLOGY, CarveOwnership.resolve(true, true, true));
    }

    @Test
    public void allReadersAgree() {
        List<String> disagreements = new ArrayList<>();
        for (int flags = 0; flags < 8; flags++) {
            boolean hydrology = (flags & 4) != 0;
            boolean subterrain = (flags & 2) != 0;
            boolean transformed = (flags & 1) != 0;
            CarveOwnership.Owner expected = CarveOwnership.resolve(hydrology, subterrain, transformed);
            Map<String, CarveOwnership.Owner> owners = new LinkedHashMap<>();
            owners.put("IrisCarveModifier", IrisCarveModifier.carveOwner(
                    hydrology ? HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR) : null,
                    subterrain ? new SubterrainCell(SubterrainCell.Kind.AIR, "", ROOM) : SubterrainCell.OUTSIDE,
                    transformed ? column(true) : null,
                    Y));
            owners.put("MantleWriter.isCarved",
                    witnessedOwner(hydrology, subterrain, transformed, CarveOwnershipTest::writerCarved));
            owners.put("MantleWriter.isPrerequisiteCarved",
                    witnessedOwner(hydrology, subterrain, transformed, CarveOwnershipTest::writerPrerequisiteCarved));
            owners.put("EngineMantle.isCarved",
                    witnessedOwner(hydrology, subterrain, transformed, CarveOwnershipTest::engineMantleCarved));
            for (Map.Entry<String, CarveOwnership.Owner> owner : owners.entrySet()) {
                if (owner.getValue() != expected) {
                    disagreements.add("hydrology=" + hydrology + " subterrain=" + subterrain + " transformed=" + transformed
                            + ": " + owner.getKey() + " gives " + owner.getValue() + ", the rule gives " + expected);
                }
            }
        }
        assertTrue(String.join("\n", disagreements), disagreements.isEmpty());
    }

    private static CarveOwnership.Owner witnessedOwner(boolean hydrology, boolean subterrain, boolean transformed,
                                                       Predicate<Sources> reader) {
        List<CarveOwnership.Owner> carving = new ArrayList<>();
        for (CarveOwnership.Owner candidate : CarveOwnership.Owner.values()) {
            boolean present = switch (candidate) {
                case HYDROLOGY -> hydrology;
                case SUBTERRAIN -> subterrain;
                case TRANSFORMED -> transformed;
                case NATURAL -> true;
            };
            if (present && reader.test(Sources.carvedOnlyBy(candidate, hydrology, subterrain, transformed))) {
                carving.add(candidate);
            }
        }
        return carving.size() == 1 ? carving.getFirst() : null;
    }

    private static boolean writerCarved(Sources sources) {
        try (MantleWriter writer = new MantleWriter(sources.engineMantle(), sources.mantle(), 0, 0, 0, false)) {
            return writer.isCarved(X, Y, Z);
        }
    }

    private static boolean writerPrerequisiteCarved(Sources sources) {
        try (MantleWriter writer = new MantleWriter(sources.engineMantle(), sources.mantle(), 0, 0, 0, false)) {
            return writer.isPrerequisiteCarved(X, Y, Z);
        }
    }

    private static boolean engineMantleCarved(Sources sources) {
        return sources.engineMantle().isCarved(X, Y, Z);
    }

    private static Terrain3DColumn column(boolean openAtY) {
        return Terrain3DColumn.fromOccupancy(HEIGHT - 1, HEIGHT, y -> !openAtY || y != Y);
    }

    private record Sources(EngineMantle engineMantle, Mantle<Matter> mantle) {
        private static Sources carvedOnlyBy(CarveOwnership.Owner carving, boolean hydrology, boolean subterrain,
                                            boolean transformed) {
            return mocked(
                    hydrology ? HydrologyCaveCell.of(carving == CarveOwnership.Owner.HYDROLOGY
                            ? HydrologyCaveAction.DRY_AIR : HydrologyCaveAction.SEAL_GUARD) : null,
                    subterrain ? new SubterrainCell(carving == CarveOwnership.Owner.SUBTERRAIN
                            ? SubterrainCell.Kind.AIR : SubterrainCell.Kind.SOLID, "", ROOM) : SubterrainCell.OUTSIDE,
                    transformed ? column(carving == CarveOwnership.Owner.TRANSFORMED) : null,
                    carving == CarveOwnership.Owner.NATURAL ? new MatterCavern(true, "", (byte) 0) : null);
        }

        @SuppressWarnings("unchecked")
        private static Sources mocked(HydrologyCaveCell hydrology, SubterrainCell subterrain, Terrain3DColumn transformed,
                                      MatterCavern cavern) {
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
            when(mantle.getWorldHeight()).thenReturn(HEIGHT);
            when(mantle.useChunk(0, 0)).thenReturn(chunk);
            when(mantle.getLoadedRegion(0, 0)).thenReturn(plate);
            when(mantle.get(X, Y, Z, MatterCavern.class)).thenReturn(cavern);
            when(plate.get(0, 0)).thenReturn(chunk);
            when(chunk.exists(0)).thenReturn(true);
            when(chunk.get(0)).thenReturn(matter);
            when(matter.hasSlice(HydrologyCaveCell.class)).thenReturn(true);
            when(matter.hasSlice(MatterCavern.class)).thenReturn(true);
            doReturn(hydrologySlice).when(matter).getSlice(HydrologyCaveCell.class);
            doReturn(cavernSlice).when(matter).getSlice(MatterCavern.class);
            when(hydrologySlice.get(X, Y, Z)).thenReturn(hydrology);
            when(cavernSlice.get(X, Y, Z)).thenReturn(cavern);
            return new Sources(engineMantle, mantle);
        }
    }
}

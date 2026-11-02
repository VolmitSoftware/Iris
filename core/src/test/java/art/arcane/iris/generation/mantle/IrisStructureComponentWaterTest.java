package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.structure.object.HydrologyWaterFixture;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisStructureComponentWaterTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    private final HydrologyWaterFixture world = new HydrologyWaterFixture();

    @Test
    public void forcedSinglePieceStructureStaysOutOfRiverWater() {
        world.water(WaterRole.CHANNEL, 9, 13);
        HydrologyWaterFixture.Placer placer = world.placer();
        IrisObject object = HydrologyWaterFixture.raft();
        IrisObjectPlacement config = IrisStructureComponent.piecePlacement(new IrisStructure(), piece(object), ObjectPlaceMode.CENTER_HEIGHT);

        assertTrue(config.isForcePlace());
        assertEquals(-1, object.place(0, -1, 0, placer, config, new RNG(7L), world.data()));
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void forcedSinglePieceStructureStillPlacesOnDryLand() {
        HydrologyWaterFixture.Placer placer = world.placer();
        IrisObject object = HydrologyWaterFixture.raft();
        IrisObjectPlacement config = IrisStructureComponent.piecePlacement(new IrisStructure(), piece(object), ObjectPlaceMode.CENTER_HEIGHT);

        assertTrue(object.place(0, -1, 0, placer, config, new RNG(7L), world.data()) >= 0);
        assertFalse(placer.writes().isEmpty());
    }

    private static PlacedStructurePiece piece(IrisObject object) {
        return new PlacedStructurePiece(null, object, 0, 0, 0, IrisObjectRotation.of(0, 0, 0), -1, 0, -1, 1, 2, 1);
    }
}

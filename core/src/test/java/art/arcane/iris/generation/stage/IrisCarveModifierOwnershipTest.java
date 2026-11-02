package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.mantle.CarveOwnership;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainRoom;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class IrisCarveModifierOwnershipTest {
    private static final int Y = 2;
    private static final int HEIGHT = 64;

    @Test
    public void carveOwnerAgreesOnTheNaturalLayer() {
        SubterrainCell openRoom = new SubterrainCell(SubterrainCell.Kind.AIR, "", room(false, false));
        SubterrainCell reservedSolid = new SubterrainCell(SubterrainCell.Kind.SOLID, "", room(false, true));
        SubterrainCell reservedPassage = new SubterrainCell(SubterrainCell.Kind.AIR, "", room(true, false));
        SubterrainCell shell = new SubterrainCell(SubterrainCell.Kind.SOLID, "", room(false, false));
        Terrain3DColumn openTerrain = column(true);
        HydrologyCaveCell[] hydrologyCells = {
                null, new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, "water"), new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, "water")};
        SubterrainCell[] subterrainCells = {SubterrainCell.OUTSIDE, openRoom, reservedSolid, reservedPassage, shell};
        Terrain3DColumn[] terrainColumns = {null, column(false), openTerrain};
        List<String> disagreements = new ArrayList<>();
        for (HydrologyCaveCell hydrology : hydrologyCells) {
            for (SubterrainCell subterrain : subterrainCells) {
                for (Terrain3DColumn terrain : terrainColumns) {
                    boolean claims = subterrain == openRoom || subterrain == reservedSolid || subterrain == reservedPassage;
                    CarveOwnership.Owner expected = CarveOwnership.resolve(hydrology != null, claims, terrain == openTerrain);
                    CarveOwnership.Owner actual = IrisCarveModifier.carveOwner(hydrology, subterrain, terrain, Y);
                    if (actual != expected) {
                        disagreements.add("hydrology=" + (hydrology == null ? "none" : hydrology.action())
                                + " subterrain=" + subterrain.kind() + " reservedSolid="
                                + (subterrain.room() != null && subterrain.room().reservedSolid())
                                + " reservedPassage=" + (subterrain.room() != null && subterrain.room().reservedPassage())
                                + " terrain=" + (terrain == null ? "none" : terrain == openTerrain ? "open" : "solid")
                                + ": carveOwner gives " + actual + ", the rule gives " + expected);
                    }
                }
            }
        }
        assertTrue(String.join("\n", disagreements), disagreements.isEmpty());
    }

    private static SubterrainRoom room(boolean reservedPassage, boolean reservedSolid) {
        return new SubterrainRoom("room", "", IrisSubterrainFamily.CENOTE,
                0, 0, 0, 0, 0, 0, -10, 10, 1D, 0, reservedPassage, reservedSolid, SubterrainCell.Kind.AIR);
    }

    private static Terrain3DColumn column(boolean openAtY) {
        return Terrain3DColumn.fromOccupancy(HEIGHT - 1, HEIGHT, y -> !openAtY || y != Y);
    }
}

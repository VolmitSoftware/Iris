package art.arcane.iris.generation.mantle;

import art.arcane.iris.world.history.BoundaryColumnGeometry;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MantleObjectComponentCaveAnchorTest {
    @Test
    public void retainedCaveAnchorsUseResolvedFluidAndOccupancyWithoutRawCarveFacts() {
        BoundaryColumnGeometry.Voxel air = new BoundaryColumnGeometry.Voxel(
                "minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);
        BoundaryColumnGeometry.Voxel water = new BoundaryColumnGeometry.Voxel(
                "minecraft:water[level=0]", BoundaryColumnGeometry.Phase.FLUID, "minecraft:water[level=0]", false);
        BoundaryColumnGeometry.Voxel stone = new BoundaryColumnGeometry.Voxel(
                "minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false);
        assertTrue(MantleObjectComponent.acceptsResolvedCaveAnchor(false, air));
        assertFalse(MantleObjectComponent.acceptsResolvedCaveAnchor(false, water));
        assertTrue(MantleObjectComponent.acceptsResolvedCaveAnchor(true, water));
        assertFalse(MantleObjectComponent.acceptsResolvedCaveAnchor(true, stone));
    }

    @Test
    public void biomeOwnedPlacementsRejectForeignCaveBands() {
        IrisBiome frozen = biome("carving/ice");
        IrisBiome deepDark = biome("carving/standard-deepdark");
        IrisBiome surface = biome("frozen/ice-spikes");

        assertFalse(MantleObjectComponent.caveAnchorBiomeConflicts(frozen, surface, "carving/ice"));
        assertTrue(MantleObjectComponent.caveAnchorBiomeConflicts(deepDark, surface, "carving/ice"));
        assertFalse(MantleObjectComponent.caveAnchorBiomeConflicts(deepDark, surface, null));
    }

    @Test
    public void dryPlacementsRejectFluidAndDefaultLavaCells() {
        MatterCavern air = new MatterCavern(true, "", (byte) 0);
        MatterCavern water = new MatterCavern(true, "", (byte) 1);
        MatterCavern lava = new MatterCavern(true, "", (byte) 2);

        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(false, water, 20, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(false, lava, 20, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(false, air, 8, 8));
        assertTrue(MantleObjectComponent.acceptsCaveAnchorFluid(false, air, 9, 8));
    }

    @Test
    public void explicitWetAndForcedAirPlacementsRemainEligible() {
        MatterCavern water = new MatterCavern(true, "", (byte) 1);
        MatterCavern forcedAir = new MatterCavern(true, "", (byte) 3);

        assertTrue(MantleObjectComponent.acceptsCaveAnchorFluid(true, water, 20, 8));
        assertTrue(MantleObjectComponent.acceptsCaveAnchorFluid(false, forcedAir, 0, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(
                true,
                new MatterCavern(false, "", (byte) 0),
                20,
                8
        ));
    }

    @Test
    public void plannedHydrologyCannotBecomeAnObjectAnchor() {
        MatterCavern water = new MatterCavern(true, "", (byte) 1);
        MatterCavern forcedAir = new MatterCavern(true, "", (byte) 3);

        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(
                true, water, HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE), 20, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(
                true, water, HydrologyCaveCell.of(HydrologyCaveAction.FALLING_FLUID), 20, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(
                true, null, HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD), 20, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(
                false, forcedAir, HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR), 20, 8));
    }

    private static IrisBiome biome(String loadKey) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(loadKey);
        return biome;
    }
}
